package com.aurora.custombgm.audio;

/**
 * 线性插值变速包装。speed &gt; 1 变快（音高升高），&lt; 1 变慢。
 * 严格来说这会同时改变音高，这是"磁带变速"式实现，成本极低。
 */
public final class ResampledSource implements AudioSource {

    private final AudioSource in;
    private final float speed;
    private final int ch;

    private final float[] cur;
    private final float[] nxt;
    private float[] tmp;
    private final float[] readBuf;

    private int tmpLen;
    private int tmpPos;

    private float frac;
    private boolean primed;
    private boolean finished;

    public ResampledSource(AudioSource in, float speed) {
        this.in = in;
        this.speed = Math.max(0.1f, Math.min(4f, speed));
        this.ch = Math.max(1, in.channels());
        this.cur = new float[ch];
        this.nxt = new float[ch];
        this.tmp = new float[ch * 4096];
        this.readBuf = new float[ch * 1024];
    }

    @Override
    public int sampleRate() {
        return in.sampleRate();
    }

    @Override
    public int channels() {
        return ch;
    }

    @Override
    public float durationSeconds() {
        float d = in.durationSeconds();
        return d > 0 ? d / speed : d;
    }

    @Override
    public int read(float[] out, int frames) {
        if (!primed) prime();
        if (finished && tmpPos >= tmpLen) {
            return 0;
        }
        int produced = 0;
        for (int f = 0; f < frames; f++) {
            while (frac >= 1f) {
                System.arraycopy(nxt, 0, cur, 0, ch);
                if (!pull(nxt)) {
                    finished = true;
                    break;
                }
                frac -= 1f;
            }
            if (finished) break;
            int base = f * ch;
            for (int c = 0; c < ch; c++) {
                out[base + c] = cur[c] + (nxt[c] - cur[c]) * frac;
            }
            frac += speed;
            produced++;
        }
        return produced;
    }

    private void prime() {
        primed = true;
        if (!pull(cur) || !pull(nxt)) {
            System.arraycopy(cur, 0, nxt, 0, ch);
            finished = true;
        }
    }

    private boolean pull(float[] dst) {
        if (tmpPos + ch > tmpLen) {
            if (!refill()) return false;
        }
        System.arraycopy(tmp, tmpPos, dst, 0, ch);
        tmpPos += ch;
        return true;
    }

    private boolean refill() {
        int remain = tmpLen - tmpPos;
        if (remain > 0 && tmpPos > 0) {
            System.arraycopy(tmp, tmpPos, tmp, 0, remain);
        }
        tmpLen = Math.max(0, remain);
        tmpPos = 0;
        if (finished) return tmpLen >= ch;

        int got = in.read(readBuf, readBuf.length / ch);
        if (got <= 0) {
            finished = true;
            return tmpLen >= ch;
        }
        int need = tmpLen + got * ch;
        if (tmp.length < need) {
            float[] bigger = new float[Math.max(need, tmp.length * 2)];
            System.arraycopy(tmp, 0, bigger, 0, tmpLen);
            tmp = bigger;
        }
        System.arraycopy(readBuf, 0, tmp, tmpLen, got * ch);
        tmpLen += got * ch;
        return true;
    }

    @Override
    public void close() {
        in.close();
    }
}
