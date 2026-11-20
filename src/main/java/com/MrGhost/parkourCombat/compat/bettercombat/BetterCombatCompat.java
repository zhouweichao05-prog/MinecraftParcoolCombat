package com.MrGhost.parkourCombat.compat.bettercombat;

import com.MrGhost.parkourCombat.combat.CombatAttackRequest;
import com.MrGhost.parkourCombat.combat.CombatBridge;
import java.util.Map;
import net.bettercombat.fabricmc.fabric.api.networking.v1.PacketSender;
import net.bettercombat.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.bettercombat.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Optional Better Combat integration.
 *
 * <p>Only installed when Better Combat is present. The unknown original
 * handler is wrapped conservatively, but its ordering is never assumed to be
 * audited. No reflection is used to inspect a foreign wrapper chain.</p>
 */
public final class BetterCombatCompat {
    private static final Logger LOGGER = LogManager.getLogger("parkourcombat.compat.bettercombat");

    public enum InstallationState {
        NOT_INSTALLED,
        INSTALLED,
        DEGRADED_OR_UNKNOWN
    }

    public enum HandlerIdentity {
        AUDITED_BETTER_COMBAT_ORIGINAL,
        OUR_WRAPPER,
        PREEXISTING_OTHER_WRAPPER,
        UNKNOWN_PREEXISTING,
        MISSING
    }

    private static InstallationState installationState = InstallationState.NOT_INSTALLED;
    private static HandlerIdentity handlerIdentity = HandlerIdentity.MISSING;
    private static Map<ResourceLocation, ServerPlayNetworking.PlayChannelHandler> installedMap;
    private static ServerPlayNetworking.PlayChannelHandler installedWrapper;
    private static ServerPlayNetworking.PlayChannelHandler wrappedOriginal;
    private static boolean wrappedOrderingKnown;

    private BetterCombatCompat() {
    }

    public static synchronized void tryInstall() {
        CombatBridge.setWeaponProvider(BetterCombatAttackAdapter::snapshot);

        Map<ResourceLocation, ServerPlayNetworking.PlayChannelHandler> map =
                ServerPlayNetworking.HANDLERS;
        ServerPlayNetworking.PlayChannelHandler current =
                map.get(Packets.C2S_AttackRequest.ID);

        if (current == null) {
            installationState = InstallationState.DEGRADED_OR_UNKNOWN;
            handlerIdentity = HandlerIdentity.MISSING;
            LOGGER.warn("[BetterCombatCompat] C2S AttackRequest handler missing");
            return;
        }

        if (current == installedWrapper) {
            handlerIdentity = HandlerIdentity.OUR_WRAPPER;
            installationState = wrappedOrderingKnown
                    ? InstallationState.INSTALLED
                    : InstallationState.DEGRADED_OR_UNKNOWN;
            return;
        }

        if (installedWrapper != null) {
            if (current == wrappedOriginal && wrappedOrderingKnown) {
                HandlerWrapper replacement = new HandlerWrapper(current, true);
                map.put(Packets.C2S_AttackRequest.ID, replacement);
                installedMap = map;
                installedWrapper = replacement;
                handlerIdentity = HandlerIdentity.OUR_WRAPPER;
                installationState = InstallationState.INSTALLED;
                LOGGER.info("[BetterCombatCompat] reinstalled audited original wrapper");
                return;
            }
            installationState = InstallationState.DEGRADED_OR_UNKNOWN;
            handlerIdentity = HandlerIdentity.UNKNOWN_PREEXISTING;
            LOGGER.warn("[BetterCombatCompat] unknown handler or map replacement; no rewrap");
            return;
        }

        // First install: no public stable marker proves this is the original
        // Better Combat handler. Wrap it but keep orderingUnknown.
        wrappedOriginal = current;
        wrappedOrderingKnown = false;
        HandlerWrapper wrapper = new HandlerWrapper(current, wrappedOrderingKnown);
        map.put(Packets.C2S_AttackRequest.ID, wrapper);
        installedMap = map;
        installedWrapper = wrapper;
        handlerIdentity = HandlerIdentity.UNKNOWN_PREEXISTING;
        installationState = InstallationState.DEGRADED_OR_UNKNOWN;
        LOGGER.warn("[BetterCombatCompat] installed with orderingUnknown=true");
    }

    public static synchronized InstallationState installationState() {
        return installationState;
    }

    public static synchronized HandlerIdentity handlerIdentity() {
        return handlerIdentity;
    }

    private static final class HandlerWrapper implements ServerPlayNetworking.PlayChannelHandler {
        private final ServerPlayNetworking.PlayChannelHandler original;
        private final boolean orderingKnown;

        private HandlerWrapper(ServerPlayNetworking.PlayChannelHandler original,
                               boolean orderingKnown) {
            this.original = original;
            this.orderingKnown = orderingKnown;
        }

        @Override
        public void receive(MinecraftServer server, ServerPlayer player,
                            ServerGamePacketListenerImpl handler,
                            FriendlyByteBuf buf, PacketSender sender) {
            if (server == null || player == null) {
                original.receive(server, player, handler, buf, sender);
                return;
            }

            // Deliberate stop degradation: do not invoke the original handler
            // when the server is already stopping.
            if (!server.isRunning() || server.isStopped()) {
                LOGGER.debug("[BetterCombatCompat] skipped original during stopping player={}",
                        player.getGameProfile().getName());
                return;
            }

            CombatAttackRequest request = null;
            try {
                request = BetterCombatAttackAdapter.decodeRequest(
                        new FriendlyByteBuf(buf.copy()));
            } catch (Throwable throwable) {
                LOGGER.warn("[BetterCombatCompat] request decode failed; forwarding original",
                        throwable);
            }

            if (request == null) {
                original.receive(server, player, handler, buf, sender);
                return;
            }

            long attackId;
            try {
                attackId = CombatBridge.beginBetterCombatRequest(
                        server, player, request, orderingKnown);
            } catch (Throwable throwable) {
                LOGGER.error("[BetterCombatCompat] bridge begin failed; forwarding original",
                        throwable);
                original.receive(server, player, handler, buf, sender);
                return;
            }

            if (attackId == 0L) {
                LOGGER.debug("[BetterCombatCompat] bridge skipped request during stopping");
                return;
            }

            try {
                original.receive(server, player, handler, buf, sender);
            } catch (Throwable throwable) {
                try {
                    CombatBridge.markOriginalThrew(server, attackId);
                } catch (Throwable bridgeFailure) {
                    throwable.addSuppressed(bridgeFailure);
                }
                throw throwable;
            } finally {
                CombatBridge.finishRequest(server, attackId);
            }
        }
    }
}
