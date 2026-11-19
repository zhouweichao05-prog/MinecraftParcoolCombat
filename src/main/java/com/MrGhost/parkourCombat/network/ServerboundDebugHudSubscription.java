package com.MrGhost.parkourCombat.network;

import com.MrGhost.parkourCombat.debug.DebugHudSubscriptions;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * C2S: the only client-provided data is a boolean subscription flag.
 */
public record ServerboundDebugHudSubscription(boolean enabled) {
    private static final Logger LOGGER =
            LogManager.getLogger("parkourcombat.debug");
    public static void encode(ServerboundDebugHudSubscription message,
                              FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.enabled());
    }

    public static ServerboundDebugHudSubscription decode(FriendlyByteBuf buffer) {
        return new ServerboundDebugHudSubscription(buffer.readBoolean());
    }

    public static void handle(ServerboundDebugHudSubscription message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            boolean previous = DebugHudSubscriptions.isEnabled(sender.getUUID());
            DebugHudSubscriptions.set(sender.getUUID(), message.enabled());
            if (previous != message.enabled()) {
                LOGGER.debug("[DebugHUD] subscription player={} enabled={}",
                        sender.getGameProfile().getName(), message.enabled());
            }
        }
        context.setPacketHandled(true);
    }
}
