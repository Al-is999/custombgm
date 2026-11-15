package com.aurora.custombgm.music;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个情境的播放列表配置。
 */
public final class SectionConfig {

    public boolean enabled = true;

    public PlayMode mode = PlayMode.SHUFFLE;

    /** 一首播完后静默等待的秒数，0 表示立刻接下一首。 */
    public float gapSeconds = 30f;

    public List<TrackConfig> tracks = new ArrayList<>();

    public boolean hasTracks() {
        return tracks != null && !tracks.isEmpty();
    }

    public void sanitize() {
        if (mode == null) mode = PlayMode.SHUFFLE;
        if (gapSeconds < 0f) gapSeconds = 0f;
        if (gapSeconds > 3600f) gapSeconds = 3600f;
        if (tracks == null) tracks = new ArrayList<>();
        tracks.removeIf(t -> t == null || t.file == null || t.file.isBlank());
        for (TrackConfig t : tracks) {
            if (t.volume < 0f) t.volume = 0f;
            if (t.volume > 2f) t.volume = 2f;
            if (t.weight < 1) t.weight = 1;
            if (t.effects != null) t.effects.sanitize();
        }
    }
}
