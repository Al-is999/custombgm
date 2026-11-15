package com.aurora.custombgm.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;

/**
 * WAV / AIFF 解码，走 JDK 自带的 JavaSound，统一转成 16bit 有符号 PCM。
 */
public final class WavSource extends ChunkedAudioSource {

    private final AudioInputStream stream;
    private final int srcChannels;
    private final int outChannels;
    private final int sampleRate;
    private final float duration;
    private final byte[] bytes;
    private boolean eof;

    public WavSource(Path file) throws IOException {
        AudioInputStream in;
        try {
            in = AudioSystem.getAudioInputStream(file.toFile());
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file: " + file.getFileName(), e);
        }
        AudioFormat base = in.getFormat();
        int ch = Math.max(1, base.getChannels());
        float rate = base.getSampleRate() <= 0 ? 44100f : base.getSampleRate();

        AudioFormat pcmFormat = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                rate, 16, ch, ch * 2, rate, false);
        AudioInputStream pcm;
        try {
            pcm = AudioSystem.getAudioInputStream(pcmFormat, in);
        } catch (IllegalArgumentException e) {
            in.close();
            throw new IOException("Cannot convert to 16bit PCM: " + file.getFileName(), e);
        }

        this.stream = pcm;
        this.srcChannels = ch;
        this.outChannels = Math.min(2, ch);
        this.sampleRate = Math.round(rate);
        long frameLength = pcm.getFrameLength();
        this.duration = frameLength > 0 ? frameLength / rate : -1f;
        this.bytes = new byte[CHUNK_FRAMES * srcChannels * 2];
    }

    @Override
    public int sampleRate() {
        return sampleRate;
    }

    @Override
    public int channels() {
        return outChannels;
    }

    @Override
    public float durationSeconds() {
        return duration;
    }

    @Override
    protected boolean fillChunk() {
        if (eof) return false;
        int frameBytes = srcChannels * 2;
        int want = bytes.length;
        int total = 0;
        try {
            while (total < want) {
                int n = stream.read(bytes, total, want - total);
                if (n < 0) break;
                total += n;
            }
        } catch (IOException e) {
            eof = true;
            return false;
        }
        if (total <= 0) {
            eof = true;
            return false;
        }
        int frames = total / frameBytes;
        if (frames <= 0) {
            eof = true;
            return false;
        }
        float[] out = ensureChunk(frames, outChannels);
        if (outChannels == 1) {
            for (int f = 0; f < frames; f++) {
                out[f] = readShort(bytes, f * 2) / 32768f;
            }
        } else {
            for (int f = 0; f < frames; f++) {
                int base = f * frameBytes;
                float l = readShort(bytes, base) / 32768f;
                float r = srcChannels >= 2 ? readShort(bytes, base + 2) / 32768f : l;
                out[f * 2] = l;
                out[f * 2 + 1] = r;
            }
        }
        chunkPos = 0;
        chunkLen = frames * outChannels;
        return true;
    }

    private static short readShort(byte[] b, int offset) {
        return (short) ((b[offset] & 0xFF) | (b[offset + 1] << 8));
    }

    @Override
    public void close() {
        try {
            stream.close();
        } catch (IOException ignored) {
        }
    }
}
