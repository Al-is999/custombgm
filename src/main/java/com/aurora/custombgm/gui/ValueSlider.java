package com.aurora.custombgm.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;

/**
 * 参数滑块。值域用真实单位，内部换算成 0..1。
 */
public final class ValueSlider extends AbstractSliderButton {

    private final double min;
    private final double max;
    private final String labelKey;
    private final DoubleFunction<String> formatter;
    private final DoubleConsumer onChange;

    public ValueSlider(int x, int y, int width, int height, String labelKey,
                       double min, double max, double current,
                       DoubleFunction<String> formatter, DoubleConsumer onChange) {
        super(x, y, width, height, Component.empty(), toRatio(current, min, max));
        this.min = min;
        this.max = max;
        this.labelKey = labelKey;
        this.formatter = formatter;
        this.onChange = onChange;
        updateMessage();
    }

    private static double toRatio(double value, double min, double max) {
        if (max <= min) return 0.0;
        return Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
    }

    public double realValue() {
        return min + (max - min) * this.value;
    }

    @Override
    protected void updateMessage() {
        setMessage(Component.translatable(labelKey)
                .append(Component.literal(": "))
                .append(Component.literal(formatter.apply(realValue()))));
    }

    @Override
    protected void applyValue() {
        onChange.accept(realValue());
    }
}
