package com.aurora.custombgm.music;

/**
 * 播放顺序策略。
 */
public enum PlayMode {
    /** 按列表顺序循环。 */
    SEQUENTIAL("Sequential"),
    /** 洗牌：每轮把整个列表打乱后依次播完。 */
    SHUFFLE("Shuffle"),
    /** 加权随机：每次独立抽取，weight 越大越容易被抽到，允许连续重复。 */
    WEIGHTED_RANDOM("Weighted Random"),
    /** 只播一遍列表，播完停住不再自动继续。 */
    ONCE("Once");

    public final String displayName;

    PlayMode(String displayName) {
        this.displayName = displayName;
    }

    public PlayMode next() {
        PlayMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
