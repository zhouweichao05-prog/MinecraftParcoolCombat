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
 * Common Forge event bridge. The adapter instance is created by the mod
 * constructor for the current physical side.
 */
public final class ParkourAdapterEvents {
    private static ParCoolActionAdapter adapter;

    private ParkourAdapterEvents() {
    }

    public static void init(ParCoolActionAdapter adapter) {
        ParkourAdapterEvents.adapter = adapter;
    }

    public static ParCoolActionAdapter getAdapter() {
        return adapter;
    }

    @SubscribeEvent
    public static void onActionStart(ParCoolActionEvent.Start.Pre event) {
        if (adapter != null) {
            adapter.onActionStart(event.getPlayer(), event.getAction());
        }
    }

    @SubscribeEvent
    public static void onActionFinish(ParCoolActionEvent.Finish.Pre event) {
        if (adapter != null) {
            adapter.onActionFinish(event.getPlayer(), event.getAction());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTryToStart(ParCoolActionEvent.TryToStart event) {
        if (adapter != null) {
            adapter.onTryToStart(event.getPlayer(), event.getAction(), event.isCanceled());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTryToContinue(ParCoolActionEvent.TryToContinue event) {
        if (adapter != null) {
            adapter.onTryToContinue(event.getPlayer(), event.getAction(), event.isCanceled());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && adapter != null) {
            adapter.onPlayerTick(event.player);
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (adapter != null) {
            adapter.onLogout(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (adapter != null) {
            adapter.onClone(event.getOriginal(), event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (adapter != null) {
            adapter.onDimensionChange(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (adapter != null && event.getEntity() instanceof Player player) {
            adapter.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (adapter != null && event.getLevel() instanceof Level level) {
            adapter.onLevelUnload(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (adapter != null) {
            adapter.clearAll("SERVER_STOPPING");
        }
    }
}
