package com.aurora.custombgm.audio;

/**
 * 交错浮点 PCM 数据源。所有实现必须是"顺序读取"的单向流。
 */
public interface AudioSource extends AutoCloseable {

    int sampleRate();

    int channels();

    /**
     * 读取最多 {@code frames} 帧交错样本到 {@code out}。
     *
     * @return 实际读取的帧数；0 表示流已结束
     */
    int read(float[] out, int frames);

    /** 总时长（秒），未知返回 -1。 */
    default float durationSeconds() {
        return -1f;
    }

    @Override
    void close();
}
