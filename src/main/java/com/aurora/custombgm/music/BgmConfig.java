package com.aurora.custombgm.music;

import com.aurora.custombgm.audio.dsp.EffectSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * config/custombgm/config.json 的根结构。
 */
public final class BgmConfig {

    public int version = 1;

    /** 总开关。关掉就完全交还给原版音乐。 */
    public boolean enabled = true;

    /** 调试模式。只有开启后 GUI 才允许增删曲目、改效果、导入导出。 */
    public boolean debugMode = false;

    /** 是否压掉原版背景音乐。false 时原版与我们自己的播放列表各播各的（一般不建议）。 */
    public boolean replaceVanillaMusic = true;

    /** 全局主音量，叠在游戏"音乐"滑块的倍率之上。 */
    public float masterVolume = 1.0f;

    public float fadeInSeconds = 2.0f;

    public float fadeOutSeconds = 3.0f;

    public float crossfadeSeconds = 3.0f;

    /** 全局默认调音参数。单曲目可以覆盖。 */
    public EffectSettings effects = new EffectSettings();

    /**
     * 自动收录：把 music 目录里还没进列表的文件自动加进 {@link #autoSections} 指定的情境。
     * 这样整合包作者只要把音频丢进目录，重启游戏就能听到，不用一个个点进来加。
     */
    public boolean autoPopulate = true;

    /** 参与自动收录的情境 id。 */
    public java.util.List<String> autoSections = new java.util.ArrayList<>(java.util.List.of("game", "menu"));

    /** 被手动移除过的文件，不再被自动收录加回来。 */
    public java.util.List<String> autoExclude = new java.util.ArrayList<>();

    /** key 是 {@link Section#id}。 */
    public Map<String, SectionConfig> sections = new LinkedHashMap<>();

    public static BgmConfig defaults() {
        BgmConfig c = new BgmConfig();
        for (Section s : Section.values()) {
            SectionConfig sc = new SectionConfig();
            sc.enabled = true;
            sc.mode = PlayMode.SHUFFLE;
            sc.gapSeconds = s == Section.MENU ? 0f : 45f;
            c.sections.put(s.id, sc);
        }
        return c;
    }

    public SectionConfig section(Section section) {
        SectionConfig sc = sections.get(section.id);
        if (sc == null) {
            sc = new SectionConfig();
            sections.put(section.id, sc);
        }
        return sc;
    }

    public void sanitize() {
        if (masterVolume < 0f) masterVolume = 0f;
        if (masterVolume > 2f) masterVolume = 2f;
        if (fadeInSeconds < 0f) fadeInSeconds = 0f;
        if (fadeInSeconds > 30f) fadeInSeconds = 30f;
        if (fadeOutSeconds < 0f) fadeOutSeconds = 0f;
        if (fadeOutSeconds > 30f) fadeOutSeconds = 30f;
        if (crossfadeSeconds < 0f) crossfadeSeconds = 0f;
        if (crossfadeSeconds > 30f) crossfadeSeconds = 30f;

        if (effects == null) effects = new EffectSettings();
        effects.sanitize();

        if (sections == null) sections = new LinkedHashMap<>();
        if (autoSections == null) autoSections = new java.util.ArrayList<>();
        if (autoExclude == null) autoExclude = new java.util.ArrayList<>();
        for (Section s : Section.values()) {
            SectionConfig sc = sections.get(s.id);
            if (sc == null) {
                sc = new SectionConfig();
                sections.put(s.id, sc);
            }
            sc.sanitize();
        }
        sections.keySet().removeIf(k -> Section.byId(k) == null);
    }
}
