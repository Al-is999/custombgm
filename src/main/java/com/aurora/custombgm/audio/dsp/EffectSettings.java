package com.aurora.custombgm.audio.dsp;

/**
 * 一组调音参数。全局默认值 + 单曲目覆盖值都是这个结构。
 */
public final class EffectSettings {

    public float gainDb = 0f;

    public boolean eqEnabled = false;
    public float lowShelfFreq = 200f;
    public float lowShelfGainDb = 0f;
    public float peak1Freq = 800f;
    public float peak1GainDb = 0f;
    public float peak1Q = 1.0f;
    public float peak2Freq = 3000f;
    public float peak2GainDb = 0f;
    public float peak2Q = 1.0f;
    public float highShelfFreq = 6000f;
    public float highShelfGainDb = 0f;

    public boolean compressorEnabled = false;
    public float compThresholdDb = -18f;
    public float compRatio = 3f;
    public float compAttackMs = 10f;
    public float compReleaseMs = 200f;
    public float compMakeupDb = 0f;

    public boolean reverbEnabled = false;
    public float reverbMix = 0.2f;
    public float reverbRoom = 0.6f;
    public float reverbDamp = 0.4f;

    public float stereoWidth = 1.0f;

    /** 播放速度。1.0 为原速，会同时改变音高（不做独立变调）。 */
    public float speed = 1.0f;

    public EffectSettings copy() {
        EffectSettings e = new EffectSettings();
        e.gainDb = gainDb;
        e.eqEnabled = eqEnabled;
        e.lowShelfFreq = lowShelfFreq;
        e.lowShelfGainDb = lowShelfGainDb;
        e.peak1Freq = peak1Freq;
        e.peak1GainDb = peak1GainDb;
        e.peak1Q = peak1Q;
        e.peak2Freq = peak2Freq;
        e.peak2GainDb = peak2GainDb;
        e.peak2Q = peak2Q;
        e.highShelfFreq = highShelfFreq;
        e.highShelfGainDb = highShelfGainDb;
        e.compressorEnabled = compressorEnabled;
        e.compThresholdDb = compThresholdDb;
        e.compRatio = compRatio;
        e.compAttackMs = compAttackMs;
        e.compReleaseMs = compReleaseMs;
        e.compMakeupDb = compMakeupDb;
        e.reverbEnabled = reverbEnabled;
        e.reverbMix = reverbMix;
        e.reverbRoom = reverbRoom;
        e.reverbDamp = reverbDamp;
        e.stereoWidth = stereoWidth;
        e.speed = speed;
        return e;
    }

    /** 把手写配置里的越界值夹回合法范围，防止 NaN / 爆音。 */
    public EffectSettings sanitize() {
        gainDb = clamp(gainDb, -60f, 24f, 0f);
        lowShelfFreq = clamp(lowShelfFreq, 20f, 20000f, 200f);
        lowShelfGainDb = clamp(lowShelfGainDb, -24f, 24f, 0f);
        peak1Freq = clamp(peak1Freq, 20f, 20000f, 800f);
        peak1GainDb = clamp(peak1GainDb, -24f, 24f, 0f);
        peak1Q = clamp(peak1Q, 0.1f, 10f, 1f);
        peak2Freq = clamp(peak2Freq, 20f, 20000f, 3000f);
        peak2GainDb = clamp(peak2GainDb, -24f, 24f, 0f);
        peak2Q = clamp(peak2Q, 0.1f, 10f, 1f);
        highShelfFreq = clamp(highShelfFreq, 20f, 20000f, 6000f);
        highShelfGainDb = clamp(highShelfGainDb, -24f, 24f, 0f);
        compThresholdDb = clamp(compThresholdDb, -60f, 0f, -18f);
        compRatio = clamp(compRatio, 1f, 20f, 3f);
        compAttackMs = clamp(compAttackMs, 0.1f, 200f, 10f);
        compReleaseMs = clamp(compReleaseMs, 1f, 2000f, 200f);
        compMakeupDb = clamp(compMakeupDb, 0f, 24f, 0f);
        reverbMix = clamp(reverbMix, 0f, 1f, 0.2f);
        reverbRoom = clamp(reverbRoom, 0f, 0.95f, 0.6f);
        reverbDamp = clamp(reverbDamp, 0f, 1f, 0.4f);
        stereoWidth = clamp(stereoWidth, 0f, 2f, 1f);
        speed = clamp(speed, 0.25f, 4f, 1f);
        return this;
    }

    private static float clamp(float v, float lo, float hi, float fallback) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return fallback;
        return Math.max(lo, Math.min(hi, v));
    }
}
