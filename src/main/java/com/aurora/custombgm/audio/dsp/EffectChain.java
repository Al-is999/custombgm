package com.aurora.custombgm.audio.dsp;

/**
 * 效果链：4 段 EQ -> 压缩 -> 立体声宽度 -> 混响 -> 增益 -> 软限幅。
 * 所有处理都在交错 float PCM 上原地进行。
 */
public final class EffectChain {

    private Biquad[] lowShelf = new Biquad[0];
    private Biquad[] peak1 = new Biquad[0];
    private Biquad[] peak2 = new Biquad[0];
    private Biquad[] highShelf = new Biquad[0];
    private final Compressor compressor = new Compressor();
    private final Reverb reverb = new Reverb();

    private int channels = 2;
    private int sampleRate = 44100;

    private boolean eqOn;
    private boolean compOn;
    private boolean reverbOn;
    private float gainLinear = 1f;
    private float width = 1f;

    public void configure(int sampleRate, int channels) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        alloc(sampleRate, channels);
    }

    private void alloc(int sampleRate, int channels) {
        lowShelf = newBiquads(channels);
        peak1 = newBiquads(channels);
        peak2 = newBiquads(channels);
        highShelf = newBiquads(channels);
    }

    private static Biquad[] newBiquads(int channels) {
        Biquad[] arr = new Biquad[channels];
        for (int i = 0; i < channels; i++) arr[i] = new Biquad();
        return arr;
    }

    /** 应用一组参数。系数在这里重算，调用频率很低（换曲 / 玩家改滑块）。 */
    public void apply(EffectSettings s) {
        s.sanitize();
        eqOn = s.eqEnabled;
        compOn = s.compressorEnabled;
        reverbOn = s.reverbEnabled;
        gainLinear = Compressor.dbToLinear(s.gainDb);
        width = s.stereoWidth;

        if (eqOn) {
            for (Biquad b : lowShelf) b.setLowShelf(sampleRate, s.lowShelfFreq, s.lowShelfGainDb);
            for (Biquad b : peak1) b.setPeaking(sampleRate, s.peak1Freq, s.peak1GainDb, s.peak1Q);
            for (Biquad b : peak2) b.setPeaking(sampleRate, s.peak2Freq, s.peak2GainDb, s.peak2Q);
            for (Biquad b : highShelf) b.setHighShelf(sampleRate, s.highShelfFreq, s.highShelfGainDb);
        }

        if (compOn) {
            compressor.configure(sampleRate, s.compThresholdDb, s.compRatio,
                    s.compAttackMs, s.compReleaseMs, s.compMakeupDb);
        }

        if (reverbOn) {
            reverb.configure(sampleRate, s.reverbMix, s.reverbRoom, s.reverbDamp);
        }
    }

    public void process(float[] buffer, int frames) {
        int ch = channels;

        if (eqOn) {
            for (int i = 0; i < frames; i++) {
                int base = i * ch;
                for (int c = 0; c < ch; c++) {
                    float v = buffer[base + c];
                    v = lowShelf[c].process(v);
                    v = peak1[c].process(v);
                    v = peak2[c].process(v);
                    v = highShelf[c].process(v);
                    buffer[base + c] = v;
                }
            }
        }

        if (compOn) {
            compressor.process(buffer, frames, ch);
        }

        if (ch == 2 && Math.abs(width - 1f) > 0.001f) {
            applyStereoWidth(buffer, frames, width);
        }

        if (reverbOn) {
            reverb.process(buffer, frames, ch);
        }

        if (Math.abs(gainLinear - 1f) > 0.0001f) {
            for (int i = 0; i < frames * ch; i++) {
                buffer[i] *= gainLinear;
            }
        }

        // 软限幅，只在接近满幅时介入，避免削波失真
        for (int i = 0; i < frames * ch; i++) {
            float v = buffer[i];
            if (v > 0.85f || v < -0.85f) {
                buffer[i] = (float) Math.tanh(v);
            }
        }
    }

    private static void applyStereoWidth(float[] buffer, int frames, float width) {
        for (int i = 0; i < frames; i++) {
            int base = i * 2;
            float l = buffer[base];
            float r = buffer[base + 1];
            float mid = (l + r) * 0.5f;
            float side = (l - r) * 0.5f * width;
            buffer[base] = mid + side;
            buffer[base + 1] = mid - side;
        }
    }

    public void resetState() {
        for (Biquad b : lowShelf) b.reset();
        for (Biquad b : peak1) b.reset();
        for (Biquad b : peak2) b.reset();
        for (Biquad b : highShelf) b.reset();
        compressor.reset();
        reverb.reset();
    }
}
