package com.aurora.custombgm.gui;

import com.aurora.custombgm.ConfigManager;
import com.aurora.custombgm.audio.dsp.EffectSettings;
import com.aurora.custombgm.music.BgmConfig;
import com.aurora.custombgm.music.Section;
import com.aurora.custombgm.music.SectionConfig;
import com.aurora.custombgm.music.TrackConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * 调音台。逐项参数等价于 DeskFX 那类"音效处理器"的核心能力：
 * 均衡、压缩、混响、立体声宽度、速度。可以只作用于一首，也可以一键铺到全部曲目。
 */
public class EffectScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int ROW_STEP = 20;
    private static final int PAGE_COUNT = 2;

    private final Screen parent;
    private final Section section;
    private final int trackIndex;
    private boolean editingTrack;
    private EffectSettings target;
    private int page;

    private Button btnEq;
    private Button btnComp;
    private Button btnReverb;

    public EffectScreen(Screen parent, Section section, int trackIndex) {
        super(Component.translatable("custombgm.effect.title"));
        this.parent = parent;
        this.section = section;
        this.trackIndex = trackIndex;

        BgmConfig cfg = ConfigManager.get().config();
        SectionConfig sc = cfg.section(section);
        boolean track = trackIndex >= 0 && trackIndex < sc.tracks.size();
        this.editingTrack = track;
        if (track) {
            TrackConfig t = sc.tracks.get(trackIndex);
            if (t.effects == null) {
                t.effects = cfg.effects.copy();
            }
            this.target = t.effects;
        } else {
            this.target = cfg.effects;
        }
        this.target.sanitize();
    }

    @Override
    protected void init() {
        int w = this.width;
        int inner = w - MARGIN * 2;
        int b3 = (inner - 8) / 3;

        btnEq = addRenderableWidget(Button.builder(eqLabel(), b -> {
            target.eqEnabled = !target.eqEnabled;
            refreshToggles();
        }).bounds(MARGIN, 20, b3, 18).build());

        btnComp = addRenderableWidget(Button.builder(compLabel(), b -> {
            target.compressorEnabled = !target.compressorEnabled;
            refreshToggles();
        }).bounds(MARGIN + b3 + 4, 20, b3, 18).build());

        btnReverb = addRenderableWidget(Button.builder(reverbLabel(), b -> {
            target.reverbEnabled = !target.reverbEnabled;
            refreshToggles();
        }).bounds(MARGIN + (b3 + 4) * 2, 20, b3, 18).build());

        int navW = 56;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = (page + PAGE_COUNT - 1) % PAGE_COUNT;
            rebuildWidgets();
        }).bounds(w - MARGIN - navW * 2 - 4, 42, navW, 16).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = (page + 1) % PAGE_COUNT;
            rebuildWidgets();
        }).bounds(w - MARGIN - navW, 42, navW, 16).build());
        int y = 64;
        if (page == 0) {
            addSlider(y, "custombgm.effect.gain", -24, 24, target.gainDb,
                    v -> String.format(Locale.ROOT, "%+.1f dB", v), v -> target.gainDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.low", -24, 24, target.lowShelfGainDb,
                    v -> String.format(Locale.ROOT, "%+.1f dB", v), v -> target.lowShelfGainDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.peak1", -24, 24, target.peak1GainDb,
                    v -> String.format(Locale.ROOT, "%+.1f dB", v), v -> target.peak1GainDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.peak2", -24, 24, target.peak2GainDb,
                    v -> String.format(Locale.ROOT, "%+.1f dB", v), v -> target.peak2GainDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.high", -24, 24, target.highShelfGainDb,
                    v -> String.format(Locale.ROOT, "%+.1f dB", v), v -> target.highShelfGainDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.width", 0, 2, target.stereoWidth,
                    v -> String.format(Locale.ROOT, "%.0f%%", v * 100), v -> target.stereoWidth = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.speed", 0.5, 2.0, target.speed,
                    v -> String.format(Locale.ROOT, "%.2fx", v), v -> target.speed = (float) v);
        } else {
            addSlider(y, "custombgm.effect.threshold", -60, 0, target.compThresholdDb,
                    v -> String.format(Locale.ROOT, "%.0f dB", v), v -> target.compThresholdDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.ratio", 1, 20, target.compRatio,
                    v -> String.format(Locale.ROOT, "%.1f:1", v), v -> target.compRatio = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.makeup", 0, 24, target.compMakeupDb,
                    v -> String.format(Locale.ROOT, "%.1f dB", v), v -> target.compMakeupDb = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.reverb_mix", 0, 1, target.reverbMix,
                    v -> String.format(Locale.ROOT, "%.0f%%", v * 100), v -> target.reverbMix = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.reverb_room", 0, 1, target.reverbRoom,
                    v -> String.format(Locale.ROOT, "%.0f%%", v * 100), v -> target.reverbRoom = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.reverb_damp", 0, 1, target.reverbDamp,
                    v -> String.format(Locale.ROOT, "%.0f%%", v * 100), v -> target.reverbDamp = (float) v);
            y += ROW_STEP;
            addSlider(y, "custombgm.effect.master", 0, 2, ConfigManager.get().config().masterVolume,
                    v -> String.format(Locale.ROOT, "%.0f%%", v * 100),
                    v -> ConfigManager.get().config().masterVolume = (float) v);
        }

        int bottomY = this.height - 24;
        int b3b = (inner - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("custombgm.effect.apply_all"),
                        b -> applyToAllTracks()).bounds(MARGIN, bottomY, b3b, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.effect.reset"),
                        b -> resetCurrent()).bounds(MARGIN + b3b + 4, bottomY, b3b, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.done"),
                        b -> onClose()).bounds(MARGIN + (b3b + 4) * 2, bottomY, b3b, 18).build());
    }

    private void addSlider(int y, String key, double min, double max, double current,
                           java.util.function.DoubleFunction<String> fmt,
                           java.util.function.DoubleConsumer setter) {
        addRenderableWidget(new ValueSlider(MARGIN, y, this.width - MARGIN * 2, 18,
                key, min, max, current, fmt, setter));
    }

    private Component eqLabel() {
        return Component.translatable("custombgm.effect.eq")
                .append(Component.literal(": "))
                .append(Component.translatable(target.eqEnabled ? "custombgm.on" : "custombgm.off"));
    }

    private Component compLabel() {
        return Component.translatable("custombgm.effect.comp")
                .append(Component.literal(": "))
                .append(Component.translatable(target.compressorEnabled ? "custombgm.on" : "custombgm.off"));
    }

    private Component reverbLabel() {
        return Component.translatable("custombgm.effect.reverb")
                .append(Component.literal(": "))
                .append(Component.translatable(target.reverbEnabled ? "custombgm.on" : "custombgm.off"));
    }

    private void refreshToggles() {
        if (btnEq != null) btnEq.setMessage(eqLabel());
        if (btnComp != null) btnComp.setMessage(compLabel());
        if (btnReverb != null) btnReverb.setMessage(reverbLabel());
    }

    private void applyToAllTracks() {
        BgmConfig cfg = ConfigManager.get().config();
        target.sanitize();
        for (Section s : Section.values()) {
            SectionConfig sc = cfg.section(s);
            for (TrackConfig t : sc.tracks) {
                t.effects = target.copy();
            }
        }
        ConfigManager.get().save();
    }

    private void resetCurrent() {
        BgmConfig cfg = ConfigManager.get().config();
        if (editingTrack) {
            SectionConfig sc = cfg.section(section);
            if (trackIndex < sc.tracks.size()) {
                sc.tracks.get(trackIndex).effects = null;
            }
            // 回到"跟随全局"，后续滑块直接改全局参数
            editingTrack = false;
            target = cfg.effects;
        } else {
            EffectSettings fresh = new EffectSettings();
            target.gainDb = fresh.gainDb;
            target.eqEnabled = fresh.eqEnabled;
            target.lowShelfFreq = fresh.lowShelfFreq;
            target.lowShelfGainDb = fresh.lowShelfGainDb;
            target.peak1Freq = fresh.peak1Freq;
            target.peak1GainDb = fresh.peak1GainDb;
            target.peak1Q = fresh.peak1Q;
            target.peak2Freq = fresh.peak2Freq;
            target.peak2GainDb = fresh.peak2GainDb;
            target.peak2Q = fresh.peak2Q;
            target.highShelfFreq = fresh.highShelfFreq;
            target.highShelfGainDb = fresh.highShelfGainDb;
            target.compressorEnabled = fresh.compressorEnabled;
            target.compThresholdDb = fresh.compThresholdDb;
            target.compRatio = fresh.compRatio;
            target.compAttackMs = fresh.compAttackMs;
            target.compReleaseMs = fresh.compReleaseMs;
            target.compMakeupDb = fresh.compMakeupDb;
            target.reverbEnabled = fresh.reverbEnabled;
            target.reverbMix = fresh.reverbMix;
            target.reverbRoom = fresh.reverbRoom;
            target.reverbDamp = fresh.reverbDamp;
            target.stereoWidth = fresh.stereoWidth;
            target.speed = fresh.speed;
        }
        ConfigManager.get().save();
        rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        Component targetLabel;
        if (editingTrack) {
            SectionConfig sc = ConfigManager.get().config().section(section);
            String file = trackIndex < sc.tracks.size() ? sc.tracks.get(trackIndex).file : "?";
            targetLabel = Component.translatable("custombgm.effect.target_track", file);
        } else {
            targetLabel = Component.translatable("custombgm.effect.target_global");
        }
        graphics.drawString(this.font, targetLabel, MARGIN, 45, 0xFFD37F, false);

        String pageLabel = Component.translatable("custombgm.effect.page", page + 1, PAGE_COUNT).getString();
        graphics.drawString(this.font, pageLabel,
                this.width - MARGIN - 116 - this.font.width(pageLabel) - 6, 46, 0xA0A0A0, false);
    }

    @Override
    public void onClose() {
        target.sanitize();
        ConfigManager.get().save();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
