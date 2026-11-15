package com.aurora.custombgm.audio;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * MP3 解码，走 JLayer（通过 jar-in-jar 打进模组）。
 */
public final class Mp3Source extends ChunkedAudioSource {

    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private final int srcChannels;
    private final int outChannels;
    private final int sampleRate;

    private float[] pending = new float[0];
    private int pendingLen;
    private boolean pendingReady;
    private boolean eof;

    public Mp3Source(Path file) throws IOException {
        InputStream raw = Files.newInputStream(file);
        this.bitstream = new Bitstream(new BufferedInputStream(raw, 64 * 1024));

        int ch = 2;
        int rate = 44100;
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                throw new IOException("Empty or unreadable MP3: " + file.getFileName());
            }
            SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            ch = Math.max(1, sample.getChannelCount());
            rate = header.frequency();
            int len = sample.getBufferLength();
            short[] buf = sample.getBuffer();
            pending = new float[len];
            for (int i = 0; i < len; i++) {
                pending[i] = buf[i] / 32768f;
            }
            pendingLen = len;
            pendingReady = len > 0;
            bitstream.closeFrame();
        } catch (Exception e) {
            close();
            throw new IOException("Failed to decode MP3 header: " + file.getFileName(), e);
        }

        this.srcChannels = ch;
        this.outChannels = Math.min(2, ch);
        this.sampleRate = rate <= 0 ? 44100 : rate;
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
    protected boolean fillChunk() {
        if (pendingReady) {
            pendingReady = false;
            return install(pending, pendingLen, srcChannels);
        }
        if (eof) return false;
        for (int guard = 0; guard < 64; guard++) {
            Header header;
            try {
                header = bitstream.readFrame();
            } catch (Exception e) {
                eof = true;
                return false;
            }
            if (header == null) {
                eof = true;
                return false;
            }
            try {
                SampleBuffer sample = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                int len = sample.getBufferLength();
                if (len > 0) {
                    short[] buf = sample.getBuffer();
                    float[] tmp = new float[len];
                    for (int i = 0; i < len; i++) {
                        tmp[i] = buf[i] / 32768f;
                    }
                    int srcCh = Math.max(1, sample.getChannelCount());
                    return install(tmp, len, srcCh);
                }
            } catch (Exception e) {
                // 损坏帧直接跳过
            } finally {
                try {
                    bitstream.closeFrame();
                } catch (Exception ignored) {
                }
            }
        }
        return false;
    }

    private boolean install(float[] samples, int len, int srcChannels) {
        int frames = len / Math.max(1, srcChannels);
        if (frames <= 0) return false;
        float[] out = ensureChunk(frames, outChannels);
        if (outChannels == 1) {
            for (int f = 0; f < frames; f++) {
                out[f] = samples[f * srcChannels];
            }
        } else {
            for (int f = 0; f < frames; f++) {
                int base = f * srcChannels;
                float l = samples[base];
                float r = srcChannels >= 2 ? samples[base + 1] : l;
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
        try {
            bitstream.close();
        } catch (Exception ignored) {
        }
    }
}
