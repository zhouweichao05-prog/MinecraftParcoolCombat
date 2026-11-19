package com.MrGhost.parkourCombat.adapter;

import com.alrex.parcool.api.unstable.action.ParCoolActionEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Common Forge event bridge.
 *
 * <p>An integrated server and its client share one JVM. Two adapter instances
 * are therefore kept: one for the logical client and one for the logical
 * server. Events are routed by the event player's level side, never by the
 * physical distribution alone.</p>
 */
public final class ParkourAdapterEvents {
    private static ParCoolActionAdapter clientAdapter;
    private static ParCoolActionAdapter serverAdapter;

    private ParkourAdapterEvents() {
    }

    public static void init(ParCoolActionAdapter server, ParCoolActionAdapter client) {
        serverAdapter = server;
        clientAdapter = client;
    }

    public static ParCoolActionAdapter getClientAdapter() {
        return clientAdapter;
    }

    public static ParCoolActionAdapter getServerAdapter() {
        return serverAdapter;
    }

    @SubscribeEvent
    public static void onActionStart(ParCoolActionEvent.Start.Pre event) {
        ParCoolActionAdapter adapter = adapterFor(event.getPlayer());
        if (adapter != null) {
            adapter.onActionStart(event.getPlayer(), event.getAction());
        }
    }

    @SubscribeEvent
    public static void onActionFinish(ParCoolActionEvent.Finish.Pre event) {
        ParCoolActionAdapter adapter = adapterFor(event.getPlayer());
        if (adapter != null) {
            adapter.onActionFinish(event.getPlayer(), event.getAction());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTryToStart(ParCoolActionEvent.TryToStart event) {
        if (clientAdapter != null) {
            clientAdapter.onTryToStart(event.getPlayer(), event.getAction(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTryToContinue(ParCoolActionEvent.TryToContinue event) {
        if (clientAdapter != null) {
            clientAdapter.onTryToContinue(event.getPlayer(), event.getAction(), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ParCoolActionAdapter adapter = adapterFor(event.player);
        if (adapter != null) {
            adapter.onPlayerTick(event.player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (serverAdapter != null) {
            serverAdapter.onLogout(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (serverAdapter != null) {
            serverAdapter.onClone(event.getOriginal(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (serverAdapter != null) {
            serverAdapter.onDimensionChange(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            ParCoolActionAdapter adapter = adapterFor(player);
            if (adapter != null) {
                adapter.onDeath(player);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level) {
            ParCoolActionAdapter adapter = level.isClientSide() ? clientAdapter : serverAdapter;
            if (adapter != null) {
                adapter.onLevelUnload(level.dimension());
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (serverAdapter != null) {
            serverAdapter.clearAll("SERVER_STOPPING");
        }
    }

    private static ParCoolActionAdapter adapterFor(Player player) {
        return player.level().isClientSide ? clientAdapter : serverAdapter;
    }
}
