package com.aurora.custombgm;

import com.aurora.custombgm.gui.BgmScreen;
import com.aurora.custombgm.music.MusicScheduler;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

@Mod(value = CustomBgm.MODID, dist = Dist.CLIENT)
public final class CustomBgm {

    public static final String MODID = "custombgm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final net.minecraft.client.KeyMapping OPEN_GUI = new net.minecraft.client.KeyMapping(
            "key.custombgm.open",
            net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,
            com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            "key.categories.misc");

    public CustomBgm(IEventBus modBus, ModContainer container) {
        ConfigManager.get().load();

        // Explicitly typed so the compiler picks registerExtensionPoint(Class, T)
        IConfigScreenFactory configScreen = (minecraft, screen) -> new BgmScreen(screen);
        container.registerExtensionPoint(IConfigScreenFactory.class, configScreen);

        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OPEN_GUI));

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> {
            MusicScheduler.get().tick();
            while (OPEN_GUI.consumeClick()) {
                Minecraft.getInstance().setScreen(new BgmScreen(Minecraft.getInstance().screen));
            }
        });

        LOGGER.info("[CustomBGM] client music engine initialised");
    }
}
