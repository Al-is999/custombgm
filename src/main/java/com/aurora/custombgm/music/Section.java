package com.aurora.custombgm.music;

/**
 * 播放情境。调度器按当前游戏状态选择用哪一份播放列表。
 */
public enum Section {
    MENU("menu", "Main Menu"),
    GAME("game", "In Game"),
    CREATIVE("creative", "Creative Mode"),
    UNDERWATER("underwater", "Underwater"),
    NETHER("nether", "The Nether"),
    END("end", "The End");

    public final String id;
    public final String displayName;

    Section(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public static Section byId(String id) {
        for (Section s : values()) {
            if (s.id.equalsIgnoreCase(id)) return s;
        }
        return null;
    }
}
