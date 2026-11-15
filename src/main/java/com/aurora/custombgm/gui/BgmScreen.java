package com.aurora.custombgm.gui;

import com.aurora.custombgm.ConfigManager;
import com.aurora.custombgm.music.BgmConfig;
import com.aurora.custombgm.music.MusicScheduler;
import com.aurora.custombgm.music.PlayMode;
import com.aurora.custombgm.music.Section;
import com.aurora.custombgm.music.SectionConfig;
import com.aurora.custombgm.music.TrackConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 主界面：情境切换 + 曲目列表 + 播放策略。
 */
public class BgmScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int ROW_H = 18;

    private final Screen parent;
    private Section selected = Section.GAME;
    private int selectedTrack = -1;
    private int scroll;

    private int listX;
    private int listY;
    private int listW;
    private int listH;

    private Button btnEnabled;
    private Button btnReplace;
    private Button btnDebug;
    private Button btnMode;
    private Button btnAdd;
    private Button btnUp;
    private Button btnDown;
    private Button btnRemove;
    private Button btnPreview;
    private Button btnGapDown;
    private Button btnGapUp;
    private Button btnFx;
    private Button btnProfile;
    private final Button[] sectionButtons = new Button[Section.values().length];

    public BgmScreen(Screen parent) {
        super(Component.translatable("custombgm.screen.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        BgmConfig cfg = ConfigManager.get().config();
        int w = this.width;
        int h = this.height;

        int topW = (w - MARGIN * 2 - 8) / 3;
        btnEnabled = addRenderableWidget(Button.builder(Component.empty(),
                        b -> {
                            cfg.enabled = !cfg.enabled;
                            ConfigManager.get().save();
                            refreshState();
                        })
                .bounds(MARGIN, 22, topW, 18).build());

        btnReplace = addRenderableWidget(Button.builder(Component.empty(),
                        b -> {
                            cfg.replaceVanillaMusic = !cfg.replaceVanillaMusic;
                            ConfigManager.get().save();
                            refreshState();
                        })
                .bounds(MARGIN + topW + 4, 22, topW, 18).build());

        btnDebug = addRenderableWidget(Button.builder(Component.empty(),
                        b -> {
                            cfg.debugMode = !cfg.debugMode;
                            ConfigManager.get().save();
                            refreshState();
                        })
                .bounds(MARGIN + (topW + 4) * 2, 22, topW, 18).build());

        Section[] all = Section.values();
        int secW = (w - MARGIN * 2 - (all.length - 1) * 2) / all.length;
        for (int i = 0; i < all.length; i++) {
            final Section s = all[i];
            sectionButtons[i] = addRenderableWidget(Button.builder(
                            Component.translatable("custombgm.section." + s.id),
                            b -> {
                                selected = s;
                                selectedTrack = -1;
                                scroll = 0;
                                refreshState();
                            })
                    .bounds(MARGIN + i * (secW + 2), 44, secW, 18).build());
        }

        listX = MARGIN;
        listY = 66;
        listW = w - MARGIN * 2;
        listH = Math.max(40, h - 66 - 56);

        int rowY = h - 50;
        int bw = (listW - 4 * 4) / 5;
        btnAdd = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.add"),
                        b -> openPicker()).bounds(MARGIN, rowY, bw, 18).build());
        btnUp = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.up"),
                        b -> move(-1)).bounds(MARGIN + (bw + 4), rowY, bw, 18).build());
        btnDown = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.down"),
                        b -> move(1)).bounds(MARGIN + (bw + 4) * 2, rowY, bw, 18).build());
        btnRemove = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.remove"),
                        b -> removeSelected()).bounds(MARGIN + (bw + 4) * 3, rowY, bw, 18).build());
        btnPreview = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.preview"),
                        b -> preview()).bounds(MARGIN + (bw + 4) * 4, rowY, bw, 18).build());

        int row2Y = h - 26;
        int bw2 = (listW - 5 * 4) / 6;
        btnMode = addRenderableWidget(Button.builder(Component.empty(),
                        b -> cycleMode()).bounds(MARGIN, row2Y, bw2, 18).build());
        btnGapDown = addRenderableWidget(Button.builder(Component.literal("-5s"),
                        b -> changeGap(-5f)).bounds(MARGIN + (bw2 + 4), row2Y, bw2, 18).build());
        btnGapUp = addRenderableWidget(Button.builder(Component.literal("+5s"),
                        b -> changeGap(5f)).bounds(MARGIN + (bw2 + 4) * 2, row2Y, bw2, 18).build());
        btnFx = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.effects"),
                        b -> openEffects()).bounds(MARGIN + (bw2 + 4) * 3, row2Y, bw2, 18).build());
        btnProfile = addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.profile"),
                        b -> openProfiles()).bounds(MARGIN + (bw2 + 4) * 4, row2Y, bw2, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.done"),
                        b -> onClose()).bounds(MARGIN + (bw2 + 4) * 5, row2Y, bw2, 18).build());

        refreshState();
    }

    private void refreshState() {
        BgmConfig cfg = ConfigManager.get().config();
        SectionConfig sc = cfg.section(selected);

        btnEnabled.setMessage(Component.translatable("custombgm.btn.enabled")
                .append(Component.literal(": "))
                .append(onOff(cfg.enabled)));
        btnReplace.setMessage(Component.translatable("custombgm.btn.replace_vanilla")
                .append(Component.literal(": "))
                .append(onOff(cfg.replaceVanillaMusic)));
        btnDebug.setMessage(Component.translatable("custombgm.btn.debug")
                .append(Component.literal(": "))
                .append(onOff(cfg.debugMode)));

        Section[] all = Section.values();
        for (int i = 0; i < all.length; i++) {
            sectionButtons[i].active = all[i] != selected;
            SectionConfig s = cfg.section(all[i]);
            sectionButtons[i].setMessage(Component.translatable("custombgm.section." + all[i].id)
                    .append(Component.literal(" (" + s.tracks.size() + ")")));
        }

        btnMode.setMessage(Component.translatable("custombgm.btn.mode")
                .append(Component.literal(": "))
                .append(Component.translatable("custombgm.mode." + sc.mode.name())));

        boolean debug = cfg.debugMode;
        btnAdd.active = debug;
        btnRemove.active = debug && selectedTrack >= 0;
        btnUp.active = debug && selectedTrack > 0;
        btnDown.active = debug && selectedTrack >= 0 && selectedTrack < sc.tracks.size() - 1;
        btnPreview.active = selectedTrack >= 0;
        btnMode.active = debug;
        btnGapDown.active = debug;
        btnGapUp.active = debug;
        btnFx.active = debug;
        btnProfile.active = debug;

        clampScroll();
    }

    private static Component onOff(boolean value) {
        return Component.translatable(value ? "custombgm.on" : "custombgm.off");
    }

    private List<TrackConfig> tracks() {
        return ConfigManager.get().config().section(selected).tracks;
    }

    private void cycleMode() {
        SectionConfig sc = ConfigManager.get().config().section(selected);
        sc.mode = sc.mode.next();
        ConfigManager.get().save();
        refreshState();
    }

    private void changeGap(float delta) {
        SectionConfig sc = ConfigManager.get().config().section(selected);
        sc.gapSeconds = Math.max(0f, Math.min(3600f, sc.gapSeconds + delta));
        ConfigManager.get().save();
        refreshState();
    }

    private void move(int direction) {
        List<TrackConfig> list = tracks();
        int from = selectedTrack;
        int to = from + direction;
        if (from < 0 || to < 0 || from >= list.size() || to >= list.size()) return;
        TrackConfig moved = list.remove(from);
        list.add(to, moved);
        selectedTrack = to;
        ConfigManager.get().save();
        refreshState();
    }

    private void removeSelected() {
        List<TrackConfig> list = tracks();
        if (selectedTrack < 0 || selectedTrack >= list.size()) return;
        TrackConfig removed = list.remove(selectedTrack);
        BgmConfig cfg = ConfigManager.get().config();
        boolean dup = false;
        for (String e : cfg.autoExclude) {
            if (e.equalsIgnoreCase(removed.file)) {
                dup = true;
                break;
            }
        }
        if (!dup) cfg.autoExclude.add(removed.file);
        if (selectedTrack >= list.size()) selectedTrack = list.size() - 1;
        ConfigManager.get().save();
        refreshState();
    }

    private void preview() {
        List<TrackConfig> list = tracks();
        if (selectedTrack < 0 || selectedTrack >= list.size()) return;
        MusicScheduler.get().previewTrack(list.get(selectedTrack).file);
    }

    private void openPicker() {
        if (minecraft != null) {
            minecraft.setScreen(new TrackPickerScreen(this, selected));
        }
    }

    private void openEffects() {
        if (minecraft != null) {
            minecraft.setScreen(new EffectScreen(this, selected, selectedTrack));
        }
    }

    private void openProfiles() {
        if (minecraft != null) {
            minecraft.setScreen(new ProfileScreen(this));
        }
    }

    // ------------------------------------------------------------------ 绘制

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        BgmConfig cfg = ConfigManager.get().config();
        SectionConfig sc = cfg.section(selected);

        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0x70000000);

        String playing = MusicScheduler.get().currentTrackName();
        List<TrackConfig> list = sc.tracks;

        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        int y = listY - scroll;
        for (int i = 0; i < list.size(); i++) {
            if (y + ROW_H > listY && y < listY + listH) {
                TrackConfig t = list.get(i);
                boolean hover = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= y && mouseY < y + ROW_H;
                if (i == selectedTrack) {
                    graphics.fill(listX, y, listX + listW, y + ROW_H, 0x66FFFFFF);
                } else if (hover) {
                    graphics.fill(listX, y, listX + listW, y + ROW_H, 0x22FFFFFF);
                }

                boolean isPlaying = playing != null && playing.equals(t.file);
                String prefix = isPlaying ? "\u25B6 " : (i + 1) + ". ";
                String name = prefix + t.file;
                int nameWidth = listW - 96;
                if (this.font.width(name) > nameWidth) {
                    name = this.font.plainSubstrByWidth(name, nameWidth - 6) + "...";
                }
                graphics.drawString(this.font, name, listX + 4, y + 5,
                        isPlaying ? 0x55FF55 : 0xFFFFFF, false);

                String info = String.format(java.util.Locale.ROOT, "%d%%%s", Math.round(t.volume * 100f),
                        t.effects != null ? " \u2699" : "");
                graphics.drawString(this.font, info, listX + listW - this.font.width(info) - 4,
                        y + 5, 0xB0B0B0, false);
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        if (list.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("custombgm.list.empty"),
                    listX + listW / 2, listY + listH / 2 - 4, 0x9A9A9A);
        }

        String footer = Component.translatable("custombgm.hint.gap", Math.round(sc.gapSeconds)).getString()
                + "   "
                + Component.translatable("custombgm.hint.total", list.size()).getString();
        graphics.drawString(this.font, footer, MARGIN, listY + listH + 4, 0x9A9A9A, false);

        if (!cfg.debugMode) {
            graphics.drawString(this.font, Component.translatable("custombgm.hint.locked"),
                    MARGIN, listY - 12, 0xFFAA55, false);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        if (button == 0 && inside(mouseX, mouseY, listX, listY, listW, listH)) {
            int idx = (int) ((mouseY - listY + scroll) / ROW_H);
            List<TrackConfig> list = tracks();
            selectedTrack = (idx >= 0 && idx < list.size()) ? idx : -1;
            refreshState();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (inside(mouseX, mouseY, listX, listY, listW, listH)) {
            scroll -= (int) Math.round(scrollY * ROW_H);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampScroll() {
        int content = tracks().size() * ROW_H;
        int max = Math.max(0, content - listH);
        if (scroll > max) scroll = max;
        if (scroll < 0) scroll = 0;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
