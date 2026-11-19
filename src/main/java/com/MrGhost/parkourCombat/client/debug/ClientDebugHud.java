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

    private ClientDebugHud() {
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("motion_debug", new DebugHudOverlay());
    }
}
