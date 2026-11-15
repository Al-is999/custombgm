package com.aurora.custombgm.audio.dsp;

/**
 * 前馈式动态范围压缩器（软拐点 + 立体声联动）。
 * 增益衰减量做过平滑，避免抽吸感。
 */
public final class Compressor {

    private float thresholdDb = -18f;
    private float ratio = 3f;
    private float attackMs = 10f;
    private float releaseMs = 200f;
    private float makeupDb = 0f;

    private float sampleRate = 44100f;
    private float attackCoef;
    private float releaseCoef;
    private float envelopeDb = -96f;
    private float reductionDb = 0f;

    public void configure(float sampleRate, float thresholdDb, float ratio,
                          float attackMs, float releaseMs, float makeupDb) {
        this.sampleRate = Math.max(8000f, sampleRate);
        this.thresholdDb = thresholdDb;
        this.ratio = Math.max(1f, ratio);
        this.attackMs = Math.max(0.1f, attackMs);
        this.releaseMs = Math.max(1f, releaseMs);
        this.makeupDb = makeupDb;
        this.attackCoef = timeToCoef(this.attackMs);
        this.releaseCoef = timeToCoef(this.releaseMs);
    }

    public void reset() {
        envelopeDb = -96f;
        reductionDb = 0f;
    }

    public void process(float[] buffer, int frames, int channels) {
        float makeup = dbToLinear(makeupDb);
        for (int i = 0; i < frames; i++) {
            int base = i * channels;
            float peak = 0f;
            for (int c = 0; c < channels; c++) {
                peak = Math.max(peak, Math.abs(buffer[base + c]));
            }
            float peakDb = linearToDb(peak);
            envelopeDb = peakDb > envelopeDb
                    ? attackCoef * envelopeDb + (1f - attackCoef) * peakDb
                    : releaseCoef * envelopeDb + (1f - releaseCoef) * peakDb;

            float over = envelopeDb - thresholdDb;
            float target = over > 0f ? over * (1f - 1f / ratio) : 0f;
            // 平滑增益衰减，避免突变
            reductionDb = target > reductionDb
                    ? attackCoef * reductionDb + (1f - attackCoef) * target
                    : releaseCoef * reductionDb + (1f - releaseCoef) * target;

            float g = dbToLinear(-reductionDb) * makeup;
            for (int c = 0; c < channels; c++) {
                buffer[base + c] *= g;
            }
        }
    }

    private float timeToCoef(float ms) {
        if (ms <= 0f) return 0f;
        return (float) Math.exp(-1.0 / (0.001 * ms * sampleRate));
    }

    public static float dbToLinear(float db) {
        return (float) Math.pow(10.0, db / 20.0);
    }

    public static float linearToDb(float linear) {
        return linear <= 1.0e-6f ? -120f : (float) (20.0 * Math.log10(linear));
    }
}
