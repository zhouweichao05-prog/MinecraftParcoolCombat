package com.MrGhost.parkourCombat.combat;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-authoritative attack observation bridge.
 *
 * <p>The bridge never changes Better Combat damage. It observes the C2S
 * request boundary through the optional compat layer and attaches only
 * server-side Forge events.</p>
 */
@Mod.EventBusSubscriber(modid = ParkourCombat.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatBridge {
    private static final Logger LOGGER = LogManager.getLogger("parkourcombat.combat");
    private static final CombatAttackTracker TRACKER = new CombatAttackTracker();
    private static volatile CombatWeaponProvider weaponProvider;

    private CombatBridge() {
    }

    public static void setWeaponProvider(CombatWeaponProvider provider) {
        weaponProvider = provider;
    }

    /**
     * Called on the network thread by the optional compat wrapper.
     *
     * @return attack id, or 0 when the request was deliberately skipped
     */
    public static long beginBetterCombatRequest(MinecraftServer server, ServerPlayer player,
                                                CombatAttackRequest request,
                                                boolean orderingKnown) {
        if (server == null || player == null || request == null) {
            return 0;
        }
        if (!server.isRunning() || server.isStopped()) {
            LOGGER.debug("[CombatBridge] skipped request reason=SERVER_STOPPING player={}",
                    player.getGameProfile().getName());
            return 0;
        }

        long attackId = CombatAttackIdGenerator.nextId();
        Runnable prepare = () -> {
            if (!server.isRunning() || player.hasDisconnected() || player.isRemoved()) {
                LOGGER.debug("[CombatBridge] skipped prepare reason=PLAYER_OR_SERVER_GONE player={}",
                        player.getGameProfile().getName());
                return;
            }
            CombatWeaponSnapshot weapon = null;
            CombatWeaponProvider provider = weaponProvider;
            if (provider != null) {
                try {
                    Optional<CombatWeaponSnapshot> snapshot = provider.snapshot(player, request.comboCount());
                    weapon = snapshot.orElse(null);
                } catch (Throwable throwable) {
                    LOGGER.warn("[CombatBridge] weapon snapshot failed for {}", player.getGameProfile().getName(), throwable);
                }
            }
            MotionSnapshot motion = null;
            try {
                if (ParkourAdapterEvents.getServerAdapter() != null) {
                    motion = ParkourAdapterEvents.getServerAdapter()
                            .getMotionSnapshot(player).orElse(null);
                }
            } catch (Throwable throwable) {
                LOGGER.warn("[CombatBridge] motion snapshot failed for {}", player.getGameProfile().getName(), throwable);
            }
            try {
                TRACKER.prepare(player, attackId, request, weapon, motion, orderingKnown);
            } catch (Throwable throwable) {
                LOGGER.error("[CombatBridge] prepare failed for {}", player.getGameProfile().getName(), throwable);
            }
        };
        Runnable executing = () -> {
            try {
                TRACKER.markExecuting(attackId);
            } catch (Throwable throwable) {
                LOGGER.error("[CombatBridge] markExecuting failed", throwable);
            }
        };

        try {
            schedule(server, prepare);
            schedule(server, executing);
        } catch (Throwable throwable) {
            LOGGER.error("[CombatBridge] scheduling failed", throwable);
        }
        return attackId;
    }

    public static void finishRequest(MinecraftServer server, long attackId) {
        if (server == null || attackId == 0L) {
            return;
        }
        try {
            schedule(server, () -> {
                try {
                    TRACKER.finish(attackId);
                } catch (Throwable throwable) {
                    LOGGER.error("[CombatBridge] finish failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            LOGGER.error("[CombatBridge] finish scheduling failed", throwable);
        }
    }

    public static void markOriginalThrew(MinecraftServer server, long attackId) {
        if (server == null || attackId == 0L) {
            return;
        }
        try {
            schedule(server, () -> {
                try {
                    TRACKER.markOriginalThrew(attackId);
                } catch (Throwable throwable) {
                    LOGGER.error("[CombatBridge] markOriginalThrew failed", throwable);
                }
            });
        } catch (Throwable throwable) {
            LOGGER.error("[CombatBridge] markOriginalThrew scheduling failed", throwable);
        }
    }

    private static void schedule(MinecraftServer server, Runnable task) {
        if (server.isSameThread()) {
            if (!server.isStopped()) {
                server.execute(task);
            }
        } else {
            server.tell(new TickTask(server.getTickCount(), task));
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        TRACKER.resetForServerStart(event.getServer());
        if (!ModList.get().isLoaded("bettercombat")) {
            return;
        }
        try {
            Class<?> compat = Class.forName("com.MrGhost.parkourCombat.compat.bettercombat.BetterCombatCompat");
            compat.getMethod("tryInstall").invoke(null);
        } catch (Throwable throwable) {
            LOGGER.error("[CombatBridge] Better Combat compat install failed", throwable);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        try {
            TRACKER.onServerTick(event.getServer());
        } catch (Throwable throwable) {
            LOGGER.error("[CombatBridge] tracker tick failed", throwable);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        TRACKER.onAttackEntity(player, event.getTarget(), event.isCanceled());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        ServerPlayer attacker = directMeleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        TRACKER.onLivingEvent(attacker, event.getEntity(), event.getSource(),
                CombatDamageEventType.LIVING_ATTACK, event.getAmount(),
                event.isCanceled(), CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        ServerPlayer attacker = directMeleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        TRACKER.onLivingEvent(attacker, event.getEntity(), event.getSource(),
                CombatDamageEventType.LIVING_HURT, event.getAmount(),
                event.isCanceled(), CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        ServerPlayer attacker = directMeleeAttacker(event.getSource());
        if (attacker == null) {
            return;
        }
        TRACKER.onLivingEvent(attacker, event.getEntity(), event.getSource(),
                CombatDamageEventType.LIVING_DAMAGE, event.getAmount(),
                event.isCanceled(), CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK);
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TRACKER.onLogout(player);
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer original) {
            TRACKER.onLogout(original);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TRACKER.onDimensionChange(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TRACKER.onDeath(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        TRACKER.clearAll("SERVER_STOPPING");
    }

    private static ServerPlayer directMeleeAttacker(DamageSource source) {
        if (source == null) {
            return null;
        }
        Entity direct = source.getDirectEntity();
        Entity causing = source.getEntity();
        if (direct instanceof ServerPlayer player
                && causing == player
                && source.is(DamageTypes.PLAYER_ATTACK)) {
            return player;
        }
        return null;
    }
}
