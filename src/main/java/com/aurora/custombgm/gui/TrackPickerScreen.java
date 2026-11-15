package com.aurora.custombgm.gui;

import com.aurora.custombgm.ConfigManager;
import com.aurora.custombgm.music.Section;
import com.aurora.custombgm.music.SectionConfig;
import com.aurora.custombgm.music.TrackConfig;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从音乐目录挑文件加入当前情境的播放列表。
 */
public class TrackPickerScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int ROW_H = 18;

    private final Screen parent;
    private final Section section;
    private final List<String> files = new ArrayList<>();
    private int scroll;
    private int listY;
    private int listH;
    private String hovered;

    public TrackPickerScreen(Screen parent, Section section) {
        super(Component.translatable("custombgm.picker.title"));
        this.parent = parent;
        this.section = section;
    }

    @Override
    protected void init() {
        reloadFiles();
        int w = this.width;
        int h = this.height;
        listY = 44;
        listH = Math.max(40, h - 44 - 30);

        int bw = (w - MARGIN * 2 - 8) / 3;
        int y = h - 24;
        addRenderableWidget(Button.builder(Component.translatable("custombgm.picker.open_folder"),
                        b -> Util.getPlatform().openFile(ConfigManager.get().musicDir().toFile()))
                .bounds(MARGIN, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.picker.refresh"),
                        b -> reloadFiles()).bounds(MARGIN + bw + 4, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.done"),
                        b -> onClose()).bounds(MARGIN + (bw + 4) * 2, y, bw, 18).build());
    }

    private void reloadFiles() {
        files.clear();
        files.addAll(ConfigManager.get().listMusicFiles());
        scroll = 0;
    }

    private boolean alreadyAdded(String file) {
        SectionConfig sc = ConfigManager.get().config().section(section);
        for (TrackConfig t : sc.tracks) {
            if (file.equalsIgnoreCase(t.file)) return true;
        }
        return false;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawString(this.font,
                Component.translatable("custombgm.picker.subtitle",
                        Component.translatable("custombgm.section." + section.id)),
                MARGIN, 24, 0xA0A0A0, false);

        int listX = MARGIN;
        int listW = this.width - MARGIN * 2;
        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0x70000000);
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);

        hovered = null;
        int y = listY - scroll;
        for (int i = 0; i < files.size(); i++) {
            String file = files.get(i);
            if (y + ROW_H > listY && y < listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= y && mouseY < y + ROW_H;
                if (hover) {
                    graphics.fill(listX, y, listX + listW, y + ROW_H, 0x30FFFFFF);
                    hovered = file;
                }
                boolean added = alreadyAdded(file);
                graphics.drawString(this.font, file, listX + 4, y + 5,
                        added ? 0x777777 : 0xFFFFFF, false);
                if (added) {
                    String tag = Component.translatable("custombgm.picker.added").getString();
                    graphics.drawString(this.font, tag, listX + listW - this.font.width(tag) - 4,
                            y + 5, 0x55FF55, false);
                }
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        if (files.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("custombgm.picker.empty"),
                    listX + listW / 2, listY + listH / 2 - 4, 0x9A9A9A);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0 && hovered != null) {
            addTrack(hovered);
            return true;
        }
        return false;
    }

    private void addTrack(String file) {
        if (alreadyAdded(file)) return;
        SectionConfig sc = ConfigManager.get().config().section(section);
        sc.tracks.add(new TrackConfig(file));
        ConfigManager.get().config().autoExclude.removeIf(e -> e.equalsIgnoreCase(file));
        ConfigManager.get().save();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listW = this.width - MARGIN * 2;
        if (mouseX >= MARGIN && mouseX < MARGIN + listW
                && mouseY >= listY && mouseY < listY + listH) {
            scroll -= (int) Math.round(scrollY * ROW_H);
            int max = Math.max(0, files.size() * ROW_H - listH);
            if (scroll > max) scroll = max;
            if (scroll < 0) scroll = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
