package com.aurora.custombgm.audio.dsp;

/**
 * 二阶 IIR 双二阶滤波器（RBJ Audio EQ Cookbook 系数，转置直接 II 型结构）。
 * 每个声道需要独立的实例。
 */
public final class Biquad {

    public enum Type { PEAKING, LOW_SHELF, HIGH_SHELF, LOW_PASS, HIGH_PASS }

    private float b0 = 1f, b1, b2, a1, a2;
    private float z1, z2;

    public void setPeaking(float sampleRate, float freq, float gainDb, float q) {
        float a = (float) Math.pow(10.0, gainDb / 40.0);
        float w0 = omega(sampleRate, freq);
        float cos = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0 * Math.max(0.05f, q)));
        normalize(1f + alpha * a, -2f * cos, 1f - alpha * a,
                1f + alpha / a, -2f * cos, 1f - alpha / a);
    }

    public void setLowShelf(float sampleRate, float freq, float gainDb) {
        float a = (float) Math.pow(10.0, gainDb / 40.0);
        float w0 = omega(sampleRate, freq);
        float cos = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / 2.0 * Math.sqrt(2.0));
        float sqrtA2alpha = 2f * (float) Math.sqrt(a) * alpha;
        normalize(
                a * ((a + 1) - (a - 1) * cos + sqrtA2alpha),
                2 * a * ((a - 1) - (a + 1) * cos),
                a * ((a + 1) - (a - 1) * cos - sqrtA2alpha),
                (a + 1) + (a - 1) * cos + sqrtA2alpha,
                -2 * ((a - 1) + (a + 1) * cos),
                (a + 1) + (a - 1) * cos - sqrtA2alpha);
    }

    public void setHighShelf(float sampleRate, float freq, float gainDb) {
        float a = (float) Math.pow(10.0, gainDb / 40.0);
        float w0 = omega(sampleRate, freq);
        float cos = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / 2.0 * Math.sqrt(2.0));
        float sqrtA2alpha = 2f * (float) Math.sqrt(a) * alpha;
        normalize(
                a * ((a + 1) + (a - 1) * cos + sqrtA2alpha),
                -2 * a * ((a - 1) + (a + 1) * cos),
                a * ((a + 1) + (a - 1) * cos - sqrtA2alpha),
                (a + 1) - (a - 1) * cos + sqrtA2alpha,
                2 * ((a - 1) - (a + 1) * cos),
                (a + 1) - (a - 1) * cos - sqrtA2alpha);
    }

    public void setLowPass(float sampleRate, float freq, float q) {
        float w0 = omega(sampleRate, freq);
        float cos = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0 * Math.max(0.05f, q)));
        normalize((1 - cos) / 2, 1 - cos, (1 - cos) / 2,
                1 + alpha, -2 * cos, 1 - alpha);
    }

    public void setHighPass(float sampleRate, float freq, float q) {
        float w0 = omega(sampleRate, freq);
        float cos = (float) Math.cos(w0);
        float alpha = (float) (Math.sin(w0) / (2.0 * Math.max(0.05f, q)));
        normalize((1 + cos) / 2, -(1 + cos), (1 + cos) / 2,
                1 + alpha, -2 * cos, 1 - alpha);
    }

    public void reset() {
        z1 = 0f;
        z2 = 0f;
    }

    public float process(float x) {
        float y = b0 * x + z1;
        z1 = b1 * x - a1 * y + z2;
        z2 = b2 * x - a2 * y;
        return y;
    }

    private void normalize(float nb0, float nb1, float nb2, float na0, float na1, float na2) {
        if (Math.abs(na0) < 1e-9f) {
            b0 = 1f;
            b1 = b2 = a1 = a2 = 0f;
            return;
        }
        float inv = 1f / na0;
        b0 = nb0 * inv;
        b1 = nb1 * inv;
        b2 = nb2 * inv;
        a1 = na1 * inv;
        a2 = na2 * inv;
    }

    private static float omega(float sampleRate, float freq) {
        float f = Math.min(Math.max(freq, 10f), sampleRate * 0.49f);
        return (float) (2.0 * Math.PI * f / sampleRate);
    }
}
