package com.aurora.custombgm.audio;

/**
 * 把底层解码器的"块"输出转成按帧请求的流。
 */
public abstract class ChunkedAudioSource implements AudioSource {

    protected static final int CHUNK_FRAMES = 2048;

    protected float[] chunk = new float[0];
    protected int chunkPos;
    protected int chunkLen;

    /** 解码下一块到 {@link #chunk}，返回是否还有数据。 */
    protected abstract boolean fillChunk();

    @Override
    public int read(float[] out, int frames) {
        int ch = channels();
        if (ch <= 0) return 0;
        int need = frames * ch;
        int written = 0;
        while (written < need) {
            if (chunkPos >= chunkLen) {
                chunkPos = 0;
                chunkLen = 0;
                if (!fillChunk()) break;
                if (chunkLen <= 0) break;
            }
            int avail = chunkLen - chunkPos;
            int copy = Math.min(avail, need - written);
            System.arraycopy(chunk, chunkPos, out, written, copy);
            chunkPos += copy;
            written += copy;
        }
        return written / ch;
    }

    protected float[] ensureChunk(int frames, int channels) {
        int need = frames * channels;
        if (chunk.length < need) {
            chunk = new float[need];
        }
        return chunk;
    }

    protected static short clampShort(float v) {
        int i = Math.round(v * 32767f);
        if (i > 32767) i = 32767;
        if (i < -32768) i = -32768;
        return (short) i;
    }
}
