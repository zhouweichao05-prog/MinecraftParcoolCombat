package com.MrGhost.parkourCombat.debug;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParCoolActionAdapter;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import com.MrGhost.parkourCombat.combat.CombatBridge;
import com.MrGhost.parkourCombat.combat.CombatAttackContext;
import com.MrGhost.parkourCombat.compat.bettercombat.BetterCombatCompat;
import com.MrGhost.parkourCombat.network.ClientboundCombatDebugSnapshot;
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;
import com.MrGhost.parkourCombat.network.DebugNetwork;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Server-side sender. It only reads the server adapter and only sends to
 * players that explicitly enabled the debug HUD.
 */
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DebugHudBroadcaster {
    private static final int SEND_INTERVAL_TICKS = 4;
    private static int tickCounter;

    private DebugHudBroadcaster() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!shouldSend(++tickCounter)) {
            return;
        }

        MinecraftServer server = event.getServer();
        ParCoolActionAdapter adapter = ParkourAdapterEvents.getServerAdapter();
        if (server == null || adapter == null) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!DebugHudSubscriptions.isEnabled(player.getUUID())) {
                continue;
            }

            Optional<MotionSnapshot> snapshot = adapter.getMotionSnapshot(player);
            int capacity = adapter.getMotionHistoryCapacity(player);
            int count = snapshot.isPresent() && capacity > 0
                    ? adapter.getMotionHistory(player, capacity).size()
                    : 0;
            ClientboundMotionDebugSnapshot packet =
                    ClientboundMotionDebugSnapshot.from(snapshot.orElse(null), count, capacity);
            DebugNetwork.sendSnapshot(player, packet);

            CombatAttackContext context = CombatBridge.latestContextForHud(player);
            ClientboundCombatDebugSnapshot combat = ClientboundCombatDebugSnapshot.from(
                    context,
                    BetterCombatCompat.installationState().name(),
                    BetterCombatCompat.handlerIdentity().name());
            DebugNetwork.sendCombatSnapshot(player, combat);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() != null) {
            DebugHudSubscriptions.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DebugHudSubscriptions.clear();
        tickCounter = 0;
    }

    static boolean shouldSend(int tick) {
        return tick % SEND_INTERVAL_TICKS == 0;
    }
}
