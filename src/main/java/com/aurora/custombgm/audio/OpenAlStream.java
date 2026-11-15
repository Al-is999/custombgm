package com.aurora.custombgm.audio;

import com.aurora.custombgm.audio.dsp.EffectChain;
import com.aurora.custombgm.audio.dsp.EffectSettings;
import org.lwjgl.openal.AL10;
import org.lwjgl.system.MemoryUtil;

import java.nio.ShortBuffer;

/**
 * 基于 OpenAL 的流式播放器。复用 Minecraft 已创建的 AL context，
 * 自己开一个 source，与 SoundEngine 互不干扰。
 * 三缓冲轮转，全部调用都在客户端主线程上完成。
 */
public final class OpenAlStream implements AutoCloseable {

    private static final int BUFFER_COUNT = 3;
    private static final float BUFFER_SECONDS = 1.0f;

    private final AudioSource audio;
    private final EffectChain chain = new EffectChain();
    private final int channels;
    private final int format;
    private final int framesPerBuffer;
    private final float[] pcm;
    private final ShortBuffer shortBuf;

    private int source;
    private int[] buffers = new int[0];

    private boolean alive;
    private boolean eof;
    private boolean finished;
    private boolean paused;
    private boolean closed;

    private float targetGain;
    private float appliedGain = -1f;

    public OpenAlStream(AudioSource audio, EffectSettings settings) {
        this.audio = audio;
        this.channels = Math.max(1, audio.channels());
        int sampleRate = Math.max(8000, audio.sampleRate());
        this.framesPerBuffer = Math.max(2048, (int) (sampleRate * BUFFER_SECONDS));
        this.pcm = new float[framesPerBuffer * channels];
        this.shortBuf = MemoryUtil.memAllocShort(framesPerBuffer * channels);
        this.format = channels >= 2 ? AL10.AL_FORMAT_STEREO16 : AL10.AL_FORMAT_MONO16;

        chain.configure(sampleRate, channels);
        chain.apply(settings);

        try {
            this.source = AL10.alGenSources();
            if (this.source == 0) {
                alive = false;
                return;
            }
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE);
            AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSource3f(source, AL10.AL_VELOCITY, 0f, 0f, 0f);
            AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_FALSE);
            AL10.alSourcef(source, AL10.AL_PITCH, 1f);
            AL10.alSourcef(source, AL10.AL_GAIN, 0f);

            buffers = new int[BUFFER_COUNT];
            AL10.alGenBuffers(buffers);
            alive = true;
        } catch (Throwable t) {
            alive = false;
        }
    }

    public boolean isUsable() {
        return alive;
    }

    public boolean isFinished() {
        return finished || !alive;
    }

    public void setGain(float gain) {
        this.targetGain = Math.max(0f, Math.min(4f, gain));
    }

    public void setPaused(boolean paused) {
        if (!alive) return;
        this.paused = paused;
        try {
            if (paused) {
                AL10.alSourcePause(source);
            } else {
                AL10.alSourcePlay(source);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 预填缓冲并开始播放。 */
    public boolean start() {
        if (!alive) return false;
        try {
            for (int i = 0; i < BUFFER_COUNT; i++) {
                if (!fillBuffer(buffers[i])) {
                    eof = true;
                    return false;
                }
                AL10.alSourceQueueBuffers(source, buffers[i]);
            }
            AL10.alSourcePlay(source);
            return true;
        } catch (Throwable t) {
            alive = false;
            return false;
        }
    }

    public void tick() {
        if (!alive || finished) return;
        try {
            if (!AL10.alIsSource(source)) {
                alive = false;
                return;
            }
            int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
            while (processed > 0) {
                processed--;
                int buffer = AL10.alSourceUnqueueBuffers(source);
                if (buffer == 0) continue;
                if (eof || !fillBuffer(buffer)) {
                    eof = true;
                } else {
                    AL10.alSourceQueueBuffers(source, buffer);
                }
            }

            int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
            if (eof && queued <= 0) {
                finished = true;
                return;
            }
            if (!paused && queued > 0
                    && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                AL10.alSourcePlay(source);
            }

            if (Math.abs(targetGain - appliedGain) > 0.0008f) {
                AL10.alSourcef(source, AL10.AL_GAIN, targetGain);
                appliedGain = targetGain;
            }
        } catch (Throwable t) {
            alive = false;
        }
    }

    private boolean fillBuffer(int buffer) {
        int got = audio.read(pcm, framesPerBuffer);
        if (got <= 0) return false;
        chain.process(pcm, got);

        int samples = got * channels;
        shortBuf.clear();
        for (int i = 0; i < samples; i++) {
            float v = pcm[i];
            if (v > 1f) v = 1f;
            else if (v < -1f) v = -1f;
            shortBuf.put(i, (short) (v * 32767f));
        }
        shortBuf.position(0);
        shortBuf.limit(samples);
        AL10.alBufferData(buffer, format, shortBuf, audio.sampleRate());
        shortBuf.clear();
        return true;
    }

    public void recycleSettings(EffectSettings settings) {
        chain.apply(settings);
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (source != 0) {
            try {
                AL10.alSourceStop(source);
                int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                while (queued > 0) {
                    AL10.alSourceUnqueueBuffers(source);
                    queued--;
                }
                if (buffers.length > 0) {
                    AL10.alDeleteBuffers(buffers);
                }
                AL10.alDeleteSources(source);
            } catch (Throwable ignored) {
            }
        }
        alive = false;
        source = 0;
        buffers = new int[0];
        MemoryUtil.memFree(shortBuf);
        audio.close();
    }
}
