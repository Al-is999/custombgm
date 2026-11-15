package com.aurora.custombgm.music;

import com.aurora.custombgm.audio.dsp.EffectSettings;

/**
 * 播放列表中的一首曲目。file 是相对 config/custombgm/music 的文件名。
 */
public final class TrackConfig {

    public String file = "";

    /** 单曲音量倍率，用于平衡不同曲子的响度。 */
    public float volume = 1.0f;

    /** 加权随机模式的权重。 */
    public int weight = 1;

    /** 非空时覆盖全局调音参数。 */
    public EffectSettings effects;

    public TrackConfig() {
    }

    public TrackConfig(String file) {
        this.file = file;
    }

    public EffectSettings effectiveEffects(EffectSettings global) {
        if (effects != null) {
            return effects.copy();
        }
        return global.copy();
    }
}
