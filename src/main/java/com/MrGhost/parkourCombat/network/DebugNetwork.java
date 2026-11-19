package com.MrGhost.parkourCombat.network;

import com.MrGhost.parkourCombat.ParkourCombat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Debug-only network channel. This is not a gameplay network system.
 */
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DebugNetwork {
    private static final String PROTOCOL = "1";
    private static volatile SimpleChannel channel;

    private DebugNetwork() {
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DebugNetwork::initialize);
    }

    private static synchronized void initialize() {
        if (channel != null) {
            return;
        }
        SimpleChannel created = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(ParkourCombat.MOD_ID, "debug"),
                () -> PROTOCOL,
                PROTOCOL::equals,
                PROTOCOL::equals
        );

        created.messageBuilder(ServerboundDebugHudSubscription.class,
                        0, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ServerboundDebugHudSubscription::encode)
                .decoder(ServerboundDebugHudSubscription::decode)
                .consumerMainThread(ServerboundDebugHudSubscription::handle)
                .add();

        created.messageBuilder(ClientboundMotionDebugSnapshot.class,
                        1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ClientboundMotionDebugSnapshot::encode)
                .decoder(ClientboundMotionDebugSnapshot::decode)
                .consumerMainThread(ClientboundMotionDebugSnapshot::handle)
                .add();

        channel = created;
    }

    public static boolean isReady() {
        return channel != null;
    }

    public static void sendSubscription(boolean enabled) {
        SimpleChannel current = channel;
        if (current != null) {
            current.sendToServer(new ServerboundDebugHudSubscription(enabled));
        }
    }

    public static void sendSnapshot(ServerPlayer player,
                                    ClientboundMotionDebugSnapshot snapshot) {
        SimpleChannel current = channel;
        if (current != null && player != null) {
            current.send(PacketDistributor.PLAYER.with(() -> player), snapshot);
        }
    }
}
