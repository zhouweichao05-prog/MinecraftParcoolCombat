package com.MrGhost.parkourCombat.combat.special;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import com.MrGhost.parkourCombat.adapter.ParkourActionPhase;
import com.MrGhost.parkourCombat.adapter.ParkourAdapterEvents;
import com.MrGhost.parkourCombat.combat.CombatAttackIdGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-thread bridge between ParCool lifecycle and real attack observation.
 * It classifies only; it never applies damage or edits Better Combat state.
 */
public final class SpecialAttackService {
    private static final int MAX_CONTEXTS = 64;
    private static final Map<UUID, SpecialAttackContext> LATEST = new HashMap<>();
    private static final SpecialAttackWindowTracker WINDOWS = new SpecialAttackWindowTracker();

    private SpecialAttackService() {
    }

    /** Called after ParkourActionTracker has produced its terminal phase. */
    public static void onActionFinish(ServerPlayer player, ParkourActionType type,
                                      ParkourActionPhase phase, long gameTime) {
        if (player == null || type == null || phase != ParkourActionPhase.FINISHED) {
            return;
        }
        if (type == ParkourActionType.WALL_JUMP || type == ParkourActionType.DODGE) {
            WINDOWS.recordFinish(player.getUUID(), type, gameTime);
        }
    }

    /** Called from the real server AttackEntityEvent path. */
    public static SpecialAttackContext onAttack(ServerPlayer player, long attackId) {
        if (player == null) {
            return null;
        }
        long now = player.serverLevel().getGameTime();
        Set<ParkourActionType> active = ParkourAdapterEvents.getServerAdapter() == null
                ? Set.of()
                : ParkourAdapterEvents.getServerAdapter().getActiveActions(player);
        MotionSnapshot motion = null;
        if (ParkourAdapterEvents.getServerAdapter() != null) {
            motion = ParkourAdapterEvents.getServerAdapter()
                    .getMotionSnapshot(player).orElse(null);
        }
        boolean falling = player.fallDistance > 0.0F || !player.onGround();
        SpecialAttackContext context = SpecialAttackClassifier.classify(
                attackId == 0L ? CombatAttackIdGenerator.nextId() : attackId,
                player.getUUID(), active, WINDOWS, now, motion,
                player.fallDistance, falling);
        if (context.accepted()) {
            WINDOWS.consume(player.getUUID(), context.style(), now);
            if (LATEST.size() >= MAX_CONTEXTS && !LATEST.containsKey(player.getUUID())) {
                LATEST.clear();
            }
            LATEST.put(player.getUUID(), context);
        }
        return context;
    }

    public static Optional<SpecialAttackContext> latest(ServerPlayer player) {
        return player == null ? Optional.empty()
                : Optional.ofNullable(LATEST.get(player.getUUID()));
    }

    public static void clear(UUID playerId) {
        if (playerId != null) {
            LATEST.remove(playerId);
            WINDOWS.clear(playerId);
        }
    }

    public static void clearAll() {
        LATEST.clear();
        WINDOWS.clearAll();
    }
}
