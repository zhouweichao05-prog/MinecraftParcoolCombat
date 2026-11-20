package com.MrGhost.parkourCombat.client.debug;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientDebugHud {
    public static final String CATEGORY = "key.categories.parkourcombat.debug";
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.parkourcombat.toggle_motion_hud",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F10,
            CATEGORY
    );
    public static final KeyMapping DETAIL = new KeyMapping(
            "key.parkourcombat.toggle_hud_detail",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F9,
            CATEGORY
    );
    public static final KeyMapping PAGE_UP = new KeyMapping(
            "key.parkourcombat.hud_page_up",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F7,
            CATEGORY
    );
    public static final KeyMapping PAGE_DOWN = new KeyMapping(
            "key.parkourcombat.hud_page_down",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F8,
            CATEGORY
    );

    private ClientDebugHud() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(DETAIL);
        event.register(PAGE_UP);
        event.register(PAGE_DOWN);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("motion_debug", new DebugHudOverlay());
    }
}
