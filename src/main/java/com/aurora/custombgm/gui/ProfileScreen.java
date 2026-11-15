package com.aurora.custombgm.gui;

import com.aurora.custombgm.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置包导入导出。导出的是 zip，里面既有 config.json 也有它引用的音频，
 * 换一台机器解压导入即可得到一模一样的播放列表和调音结果。
 */
public class ProfileScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int ROW_H = 18;

    private final Screen parent;
    private final List<Path> profiles = new ArrayList<>();

    private EditBox nameBox;
    private int selected = -1;
    private int scroll;
    private int listY;
    private int listH;

    private Component status = Component.empty();
    private long statusAt;
    private Path pendingDelete;
    private long pendingAt;

    public ProfileScreen(Screen parent) {
        super(Component.translatable("custombgm.profile.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        reloadProfiles();
        int w = this.width;
        int h = this.height;

        int exportW = 84;
        nameBox = new EditBox(this.font, MARGIN, 24, w - MARGIN * 2 - exportW - 4, 18,
                Component.translatable("custombgm.profile.name"));
        nameBox.setMaxLength(48);
        nameBox.setValue("my-pack");
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("custombgm.profile.export"),
                        b -> doExport())
                .bounds(w - MARGIN - exportW, 24, exportW, 18).build());

        listY = 50;
        listH = Math.max(36, h - 50 - 54);

        int bw = (w - MARGIN * 2 - 12) / 4;
        int y = h - 24;
        addRenderableWidget(Button.builder(Component.translatable("custombgm.profile.import"),
                        b -> doImport()).bounds(MARGIN, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.profile.delete"),
                        b -> doDelete()).bounds(MARGIN + bw + 4, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.profile.refresh"),
                        b -> reloadProfiles()).bounds(MARGIN + (bw + 4) * 2, y, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("custombgm.btn.done"),
                        b -> onClose()).bounds(MARGIN + (bw + 4) * 3, y, bw, 18).build());
    }

    private void reloadProfiles() {
        profiles.clear();
        profiles.addAll(ConfigManager.get().listProfiles());
        if (selected >= profiles.size()) selected = profiles.size() - 1;
        if (scroll > Math.max(0, profiles.size() * ROW_H - listH)) {
            scroll = Math.max(0, profiles.size() * ROW_H - listH);
        }
    }

    private void setStatus(Component component) {
        this.status = component;
        this.statusAt = System.currentTimeMillis();
    }

    private void doExport() {
        String name = nameBox == null ? "" : nameBox.getValue();
        if (name == null || name.isBlank()) {
            setStatus(Component.translatable("custombgm.profile.need_name"));
            return;
        }
        try {
            Path out = ConfigManager.get().exportProfile(name);
            setStatus(Component.translatable("custombgm.profile.exported", out.toString()));
            reloadProfiles();
        } catch (Exception e) {
            setStatus(Component.translatable("custombgm.profile.failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void doImport() {
        if (selected < 0 || selected >= profiles.size()) {
            setStatus(Component.translatable("custombgm.profile.select_first"));
            return;
        }
        try {
            int count = ConfigManager.get().importProfile(profiles.get(selected));
            setStatus(Component.translatable("custombgm.profile.imported", count));
            reloadProfiles();
        } catch (Exception e) {
            setStatus(Component.translatable("custombgm.profile.failed",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private void doDelete() {
        if (selected < 0 || selected >= profiles.size()) {
            setStatus(Component.translatable("custombgm.profile.select_first"));
            return;
        }
        Path target = profiles.get(selected);
        long now = System.currentTimeMillis();
        if (pendingDelete == null || !pendingDelete.equals(target) || now - pendingAt > 4000L) {
            pendingDelete = target;
            pendingAt = now;
            setStatus(Component.translatable("custombgm.profile.confirm_delete",
                    target.getFileName().toString()));
            return;
        }
        pendingDelete = null;
        if (ConfigManager.get().deleteProfile(target)) {
            setStatus(Component.translatable("custombgm.profile.deleted"));
            reloadProfiles();
        } else {
            setStatus(Component.translatable("custombgm.profile.failed", "delete"));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);

        int listX = MARGIN;
        int listW = this.width - MARGIN * 2;
        graphics.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0x70000000);
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);

        int y = listY - scroll;
        for (int i = 0; i < profiles.size(); i++) {
            Path p = profiles.get(i);
            if (y + ROW_H > listY && y < listY + listH) {
                boolean hover = mouseX >= listX && mouseX < listX + listW
                        && mouseY >= y && mouseY < y + ROW_H;
                if (i == selected) {
                    graphics.fill(listX, y, listX + listW, y + ROW_H, 0x66FFFFFF);
                } else if (hover) {
                    graphics.fill(listX, y, listX + listW, y + ROW_H, 0x22FFFFFF);
                }
                String name = p.getFileName().toString();
                graphics.drawString(this.font, name, listX + 4, y + 5, 0xFFFFFF, false);
                long size = safeSize(p);
                String info = size >= 0 ? (size / 1024 + " KB") : "";
                graphics.drawString(this.font, info, listX + listW - this.font.width(info) - 4,
                        y + 5, 0xA0A0A0, false);
            }
            y += ROW_H;
        }
        graphics.disableScissor();

        if (profiles.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("custombgm.profile.empty"),
                    listX + listW / 2, listY + listH / 2 - 4, 0x9A9A9A);
        }

        if (System.currentTimeMillis() - statusAt < 12000L) {
            graphics.drawString(this.font, this.status, MARGIN, listY + listH + 4, 0xFFD37F, false);
        }
    }

    private static long safeSize(Path p) {
        try {
            return java.nio.file.Files.size(p);
        } catch (Exception e) {
            return -1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listX = MARGIN;
        int listW = this.width - MARGIN * 2;
        if (button == 0 && mouseX >= listX && mouseX < listX + listW
                && mouseY >= listY && mouseY < listY + listH) {
            int idx = (int) ((mouseY - listY + scroll) / ROW_H);
            selected = (idx >= 0 && idx < profiles.size()) ? idx : -1;
            pendingDelete = null;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listW = this.width - MARGIN * 2;
        if (mouseX >= MARGIN && mouseX < MARGIN + listW
                && mouseY >= listY && mouseY < listY + listH) {
            scroll -= (int) Math.round(scrollY * ROW_H);
            int max = Math.max(0, profiles.size() * ROW_H - listH);
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
