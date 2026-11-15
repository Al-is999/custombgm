package com.aurora.custombgm.audio;

import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * OGG Vorbis 解码，走 LWJGL 内置的 stb_vorbis。
 * 文件整体读入直接内存，避免中文路径与文件句柄问题。
 */
public final class OggSource extends ChunkedAudioSource {

    private static final long MAX_BYTES = 128L * 1024 * 1024;

    private final ByteBuffer data;
    private long decoder;
    private final int srcChannels;
    private final int outChannels;
    private final int sampleRate;
    private final float duration;
    private final ShortBuffer shortBuf;
    private boolean eof;

    public OggSource(Path file) throws IOException {
        long size = Files.size(file);
        if (size <= 0 || size > MAX_BYTES) {
            throw new IOException("OGG file size not supported: " + size);
        }
        ByteBuffer buf = MemoryUtil.memAlloc((int) size);
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            while (buf.hasRemaining()) {
                if (channel.read(buf) < 0) break;
            }
        } catch (IOException e) {
            MemoryUtil.memFree(buf);
            throw e;
        }
        buf.flip();
        this.data = buf;

        long handle;
        int channels;
        int rate;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(data, error, null);
            if (handle == MemoryUtil.NULL || error.get(0) != 0) {
                MemoryUtil.memFree(buf);
                throw new IOException("stb_vorbis failed to open " + file.getFileName()
                        + " (code " + error.get(0) + ")");
            }
            STBVorbisInfo info = STBVorbisInfo.malloc();
            try {
                STBVorbis.stb_vorbis_get_info(handle, info);
                channels = info.channels();
                rate = info.sample_rate();
            } finally {
                info.free();
            }
        }
        this.decoder = handle;
        this.srcChannels = Math.max(1, channels);
        this.outChannels = Math.min(2, srcChannels);
        this.sampleRate = rate <= 0 ? 44100 : rate;
        this.duration = STBVorbis.stb_vorbis_stream_length_in_seconds(decoder);
        this.shortBuf = MemoryUtil.memAllocShort(CHUNK_FRAMES * srcChannels);
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
        if (eof || decoder == MemoryUtil.NULL) return false;
        shortBuf.clear();
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, srcChannels, shortBuf);
        if (frames <= 0) {
            eof = true;
            return false;
        }
        float[] out = ensureChunk(frames, outChannels);
        if (outChannels == 1) {
            for (int f = 0; f < frames; f++) {
                out[f] = shortBuf.get(f * srcChannels) / 32768f;
            }
        } else {
            int stride = srcChannels >= 2 ? srcChannels : 1;
            for (int f = 0; f < frames; f++) {
                int base = f * stride;
                float l = shortBuf.get(base) / 32768f;
                float r = srcChannels >= 2 ? shortBuf.get(base + 1) / 32768f : l;
                out[f * 2] = l;
                out[f * 2 + 1] = r;
            }
        }
        chunkPos = 0;
        chunkLen = frames * outChannels;
        return true;
    }

    @Override
    public void close() {
        if (decoder != MemoryUtil.NULL) {
            STBVorbis.stb_vorbis_close(decoder);
            decoder = MemoryUtil.NULL;
        }
        MemoryUtil.memFree(shortBuf);
        MemoryUtil.memFree(data);
    }
}
