package com.aurora.custombgm.audio.dsp;

/**
 * 简化的 Freeverb（Schroeder）混响：并联梳状滤波 + 串联全通，带阻尼与立体声扩散。
 * 输入为单声道混合信号，输出左右两路湿声。
 */
public final class Reverb {

    private static final int[] COMB_TUNING = {1116, 1188, 1277, 1356, 1422, 1491};
    private static final int[] ALLPASS_TUNING = {556, 441, 341, 225};
    private static final int STEREO_SPREAD = 23;

    private Comb[] combsL = new Comb[0];
    private Comb[] combsR = new Comb[0];
    private AllPass[] apL = new AllPass[0];
    private AllPass[] apR = new AllPass[0];

    private float room = 0.6f;
    private float damp = 0.4f;
    private float mix = 0.2f;

    public void configure(int sampleRate, float mix, float room, float damp) {
        this.mix = clamp(mix, 0f, 1f);
        this.room = clamp(room, 0f, 0.95f);
        this.damp = clamp(damp, 0f, 1f);

        float scale = Math.max(0.25f, sampleRate / 44100f);
        combsL = new Comb[COMB_TUNING.length];
        combsR = new Comb[COMB_TUNING.length];
        for (int i = 0; i < COMB_TUNING.length; i++) {
            combsL[i] = new Comb(Math.max(16, (int) (COMB_TUNING[i] * scale)));
            combsR[i] = new Comb(Math.max(16, (int) ((COMB_TUNING[i] + STEREO_SPREAD) * scale)));
        }
        apL = new AllPass[ALLPASS_TUNING.length];
        apR = new AllPass[ALLPASS_TUNING.length];
        for (int i = 0; i < ALLPASS_TUNING.length; i++) {
            apL[i] = new AllPass(Math.max(8, (int) (ALLPASS_TUNING[i] * scale)));
            apR[i] = new AllPass(Math.max(8, (int) ((ALLPASS_TUNING[i] + STEREO_SPREAD) * scale)));
        }
    }

    public void reset() {
        for (Comb c : combsL) c.reset();
        for (Comb c : combsR) c.reset();
        for (AllPass a : apL) a.reset();
        for (AllPass a : apR) a.reset();
    }

    /** 原地处理交错 PCM。声道数不足 2 时退化为干声直通。 */
    public void process(float[] buffer, int frames, int channels) {
        if (mix <= 0.001f || combsL.length == 0 || channels < 2) {
            return;
        }
        float dry = 1f - mix * 0.45f;
        float wet = mix;
        float feedback = 0.7f + room * 0.28f;

        for (int i = 0; i < frames; i++) {
            int base = i * channels;
            float in = (buffer[base] + buffer[base + 1]) * 0.5f;

            float outL = 0f;
            float outR = 0f;
            for (int c = 0; c < combsL.length; c++) {
                outL += combsL[c].process(in, feedback, damp);
                outR += combsR[c].process(in, feedback, damp);
            }
            outL *= 1f / combsL.length;
            outR *= 1f / combsR.length;

            for (AllPass ap : apL) outL = ap.process(outL);
            for (AllPass ap : apR) outR = ap.process(outR);

            buffer[base] = buffer[base] * dry + outL * wet;
            buffer[base + 1] = buffer[base + 1] * dry + outR * wet;
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static final class Comb {
        private final float[] buffer;
        private int index;
        private float store;

        Comb(int length) {
            buffer = new float[length];
        }

        float process(float in, float feedback, float damping) {
            float out = buffer[index];
            store = out * (1f - damping) + store * damping;
            buffer[index] = in + store * feedback;
            if (++index >= buffer.length) index = 0;
            return out;
        }

        void reset() {
            java.util.Arrays.fill(buffer, 0f);
            store = 0f;
            index = 0;
        }
    }

    private static final class AllPass {
        private final float[] buffer;
        private int index;

        AllPass(int length) {
            buffer = new float[length];
        }

        float process(float in) {
            float buffered = buffer[index];
            float out = buffered - in;
            buffer[index] = in + buffered * 0.5f;
            if (++index >= buffer.length) index = 0;
            return out;
        }

        void reset() {
            java.util.Arrays.fill(buffer, 0f);
            index = 0;
        }
    }
}
