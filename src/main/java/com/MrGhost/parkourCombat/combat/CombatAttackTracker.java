package com.MrGhost.parkourCombat.combat;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Server-thread-only owner of attack sessions, a unified long tick clock, and
 * bounded recent contexts. No level game time or wall clock is used.
 */
public final class CombatAttackTracker {
    private static final Logger LOGGER = LogManager.getLogger("parkourcombat.combat");
    private static final int RECENT_LIMIT = 8;
    private static final int ACTIVE_TTL_TICKS = 40;
    private static final int RECENT_SEALED_TTL_TICKS = 40;
    private static final long MAX_FORWARD_TICK_JUMP = 200L;

    private final Map<UUID, PlayerState> players = new HashMap<>();
    private long serverTick;
    private long lastRawServerTick;
    private boolean clockStarted;
    private long lateEventCount;
    private long latePartEventCount;
    private long lateCanceledEventCount;
    private long lateUnknownEventCount;

    public void resetForServerStart(MinecraftServer server) {
        clearPlayers();
        this.serverTick = 0L;
        this.lastRawServerTick = rawTick(server);
        this.clockStarted = true;
        this.lateEventCount = 0L;
        this.latePartEventCount = 0L;
        this.lateCanceledEventCount = 0L;
        this.lateUnknownEventCount = 0L;
    }

    public void prepare(ServerPlayer player, long attackId, CombatAttackRequest request,
                        CombatWeaponSnapshot weapon, MotionSnapshot motion,
                        boolean orderingKnown) {
        if (player == null || request == null) {
            return;
        }
        PlayerState state = players.computeIfAbsent(player.getUUID(), ignored -> new PlayerState());
        if (state.active != null) {
            CombatAttackContext old = state.active.finish(
                    CombatAttackEndReason.INTERRUPTED_BY_NEW_REQUEST, serverTick);
            if (old != null) {
                addRecent(state, old);
            }
            state.active = null;
        }
        CombatAttackSession session = new CombatAttackSession(
                player.getUUID(), attackId, player.tickCount, serverTick,
                request, motion, weapon, orderingKnown, recentTargets(state).uuids());
        session.prepare(player);
        state.active = session;
    }

    public void markExecuting(long attackId) {
        CombatAttackSession session = activeSession(attackId);
        if (session != null) {
            session.markExecuting();
        }
    }

    /** Server-thread read-only HUD query: latest sealed context, never mutable. */
    public CombatAttackContext latestContextForHud(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        PlayerState state = players.get(player.getUUID());
        return state == null || state.recent.isEmpty() ? null : state.recent.peekLast();
    }

    public void markOriginalThrew(long attackId) {
        CombatAttackSession session = activeSession(attackId);
        if (session != null) {
            session.markOriginalThrew();
        }
    }

    public void finish(long attackId) {
        PlayerState state = stateForAttack(attackId);
        if (state == null || state.active == null || state.active.attackId() != attackId) {
            return;
        }
        CombatAttackContext context = state.active.finish(
                CombatAttackEndReason.NORMAL_FINISH, serverTick);
        state.active = null;
        if (context != null) {
            addRecent(state, context);
        }
        trimRecent(state);
    }

    public void onAttackEntity(ServerPlayer attacker, Entity target, boolean canceled) {
        if (attacker == null || target == null) {
            return;
        }
        PlayerState state = players.get(attacker.getUUID());
        if (state == null) {
            return;
        }
        boolean part = target instanceof net.minecraftforge.entity.PartEntity<?>;
        UUID parentUuid = part && ((net.minecraftforge.entity.PartEntity<?>) target).getParent() != null
                ? ((net.minecraftforge.entity.PartEntity<?>) target).getParent().getUUID() : null;
        dispatchAttack(state, target.getUUID(), parentUuid, part, canceled);
    }

    public void onLivingEvent(ServerPlayer attacker, LivingEntity target,
                              DamageSource source, CombatDamageEventType type,
                              float amount, boolean canceled,
                              CombatDamageSourceClassification classification) {
        if (attacker == null || target == null) {
            return;
        }
        PlayerState state = players.get(attacker.getUUID());
        if (state == null) {
            return;
        }
        CombatEventType eventType = switch (type) {
            case LIVING_ATTACK -> CombatEventType.LIVING_ATTACK;
            case LIVING_HURT -> CombatEventType.LIVING_HURT;
            case LIVING_DAMAGE -> CombatEventType.LIVING_DAMAGE;
        };
        dispatchLiving(state, target.getUUID(), eventType, amount, canceled);
    }

    private void dispatchAttack(PlayerState state, UUID targetUuid, UUID parentUuid,
                                boolean part, boolean canceled) {
        LateMatch match = isLateTarget(state, targetUuid, parentUuid, part);
        if (match == LateMatch.LATE_MATCHED) {
            recordLate(part, canceled);
            return;
        }
        if (match == LateMatch.UNKNOWN_TRUNCATED) {
            recordLateUnknown();
            return;
        }
        if (state.active != null) {
            state.active.observeAttackTarget(targetUuid, part, parentUuid, canceled);
        }
    }

    private void dispatchLiving(PlayerState state, UUID targetUuid, CombatEventType type,
                                float amount, boolean canceled) {
        LateMatch match = isLateTarget(state, targetUuid, null, false);
        if (match == LateMatch.LATE_MATCHED) {
            recordLate(false, canceled);
            return;
        }
        if (match == LateMatch.UNKNOWN_TRUNCATED) {
            recordLateUnknown();
            return;
        }
        if (state.active != null) {
            state.active.observeLivingEvent(targetUuid, type, amount, canceled);
        }
    }

    public void onServerTick(MinecraftServer server) {
        if (!clockStarted) {
            return;
        }
        advanceClock(server);
        long now = serverTick;
        var iterator = players.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerState> entry = iterator.next();
            PlayerState state = entry.getValue();
            if (state.active != null && now - state.active.serverTick() > ACTIVE_TTL_TICKS) {
                CombatAttackContext context = state.active.finish(
                        CombatAttackEndReason.TTL_EXPIRED, now);
                state.active = null;
                if (context != null) {
                    addRecent(state, context);
                }
            }
            state.recent.removeIf(context -> context.sealedTick() < 0
                    || now < context.sealedTick()
                    || now - context.sealedTick() > RECENT_SEALED_TTL_TICKS);
            trimRecent(state);
            if (state.active == null && state.recent.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void onLogout(ServerPlayer player) {
        if (player == null) {
            return;
        }
        PlayerState state = players.remove(player.getUUID());
        if (state != null && state.active != null) {
            state.active.finish(CombatAttackEndReason.PLAYER_DISCONNECTED, serverTick);
        }
    }

    public void onDeath(ServerPlayer player) {
        removeWithReason(player, CombatAttackEndReason.PLAYER_DIED);
    }

    public void onDimensionChange(ServerPlayer player) {
        removeWithReason(player, CombatAttackEndReason.DIMENSION_CHANGED);
    }

    public void clearAll(String reason) {
        clearPlayers();
        clockStarted = false;
        serverTick = 0L;
        lastRawServerTick = 0L;
        lateEventCount = 0L;
        latePartEventCount = 0L;
        lateCanceledEventCount = 0L;
        lateUnknownEventCount = 0L;
        LOGGER.debug("[CombatBridge] cleared all contexts reason={}", reason);
    }

    long lateEventCount() {
        return lateEventCount;
    }

    long latePartEventCount() {
        return latePartEventCount;
    }

    long lateCanceledEventCount() {
        return lateCanceledEventCount;
    }

    long lateUnknownEventCount() {
        return lateUnknownEventCount;
    }

    enum LateCounter {
        LATE_EVENT,
        LATE_PART,
        LATE_CANCELED,
        LATE_UNKNOWN
    }

    /** FOR_TEST_ONLY: seed one late counter at Long.MAX_VALUE - 1. */
    void seedLateCounterForTest(LateCounter counter, long value) {
        if (value != Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("only MAX-1 seeding is allowed");
        }
        switch (counter) {
            case LATE_EVENT -> lateEventCount = value;
            case LATE_PART -> latePartEventCount = value;
            case LATE_CANCELED -> lateCanceledEventCount = value;
            case LATE_UNKNOWN -> lateUnknownEventCount = value;
        }
    }

    /** Test seam for the late diagnostic counters; still uses the same path. */
    void recordLateDiagnosticForTest(boolean part, boolean canceled) {
        recordLate(part, canceled);
    }

    void seedRecentContextForTest(UUID playerId, CombatAttackContext context) {
        PlayerState state = players.computeIfAbsent(playerId, ignored -> new PlayerState());
        addRecent(state, context);
    }

    void seedActiveSessionForTest(UUID playerId, CombatAttackSession session) {
        players.computeIfAbsent(playerId, ignored -> new PlayerState()).active = session;
    }

    CombatRecentTargetLookup recentLookupForTest(UUID playerId) {
        PlayerState state = players.get(playerId);
        return state == null ? new CombatRecentTargetLookup() : recentTargets(state);
    }

    void dispatchAttackForTest(UUID playerId, UUID targetUuid, UUID parentUuid,
                               boolean part, boolean canceled) {
        PlayerState state = players.get(playerId);
        if (state == null) {
            return;
        }
        dispatchAttack(state, targetUuid, parentUuid, part, canceled);
    }

    void dispatchLivingForTest(UUID playerId, UUID targetUuid, CombatEventType type,
                               float amount, boolean canceled) {
        PlayerState state = players.get(playerId);
        if (state == null) {
            return;
        }
        dispatchLiving(state, targetUuid, type, amount, canceled);
    }

    private LateMatch isLateTarget(PlayerState state, net.minecraft.world.entity.Entity target) {
        if (target == null) {
            return LateMatch.NOT_LATE;
        }
        boolean part = target instanceof net.minecraftforge.entity.PartEntity<?>;
        UUID parentUuid = part && ((net.minecraftforge.entity.PartEntity<?>) target).getParent() != null
                ? ((net.minecraftforge.entity.PartEntity<?>) target).getParent().getUUID() : null;
        return isLateTarget(state, target.getUUID(), parentUuid, part);
    }

    private LateMatch isLateTarget(PlayerState state, UUID targetUuid, UUID parentUuid, boolean part) {
        if (state == null || targetUuid == null) {
            return LateMatch.NOT_LATE;
        }
        CombatRecentTargetLookup recent = recentTargets(state);
        if (recent.contains(targetUuid)) {
            return LateMatch.LATE_MATCHED;
        }
        if (part && parentUuid != null && recent.contains(parentUuid)) {
            return LateMatch.LATE_MATCHED;
        }
        return recent.truncated() ? LateMatch.UNKNOWN_TRUNCATED : LateMatch.NOT_LATE;
    }

    private void recordLate(net.minecraft.world.entity.Entity target, boolean canceled) {
        recordLate(target instanceof net.minecraftforge.entity.PartEntity<?>, canceled);
    }

    private void recordLate(boolean part, boolean canceled) {
        lateEventCount = saturatingIncrement(lateEventCount);
        if (part) {
            latePartEventCount = saturatingIncrement(latePartEventCount);
        }
        if (canceled) {
            lateCanceledEventCount = saturatingIncrement(lateCanceledEventCount);
        }
    }

    private void recordLateUnknown() {
        lateUnknownEventCount = saturatingIncrement(lateUnknownEventCount);
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private enum LateMatch {
        LATE_MATCHED,
        NOT_LATE,
        UNKNOWN_TRUNCATED
    }

    private void clearPlayers() {
        for (PlayerState state : players.values()) {
            if (state.active != null) {
                state.active.finish(CombatAttackEndReason.SERVER_STOPPING, serverTick);
            }
        }
        players.clear();
    }

    private void removeWithReason(ServerPlayer player, CombatAttackEndReason reason) {
        if (player == null) {
            return;
        }
        PlayerState state = players.remove(player.getUUID());
        if (state != null && state.active != null) {
            state.active.finish(reason, serverTick);
        }
    }

    private void advanceClock(MinecraftServer server) {
        advanceRaw(rawTick(server));
    }

    /** Test seam for the long server clock; no player or level is touched. */
    long currentServerTickForTest() {
        return serverTick;
    }

    /** Test seam for the long server clock; no player or level is touched. */
    void resetClockForTest(long raw) {
        clearPlayers();
        serverTick = 0L;
        lastRawServerTick = raw;
        clockStarted = true;
    }

    /** Test seam for the long server clock; no player or level is touched. */
    void advanceClockForTest(long raw) {
        if (!clockStarted) {
            resetClockForTest(raw);
            return;
        }
        advanceRaw(raw);
    }

    private void advanceRaw(long raw) {
        long delta = (raw - lastRawServerTick) & 0xFFFFFFFFL;
        if (delta == 0L) {
            return;
        }
        if (delta <= MAX_FORWARD_TICK_JUMP) {
            serverTick = saturatedAdd(serverTick, delta);
            lastRawServerTick = raw;
            return;
        }
        clearPlayers();
        serverTick = 0L;
        lastRawServerTick = raw;
        LOGGER.warn("[CombatBridge] server tick reset detected; cleared attack contexts");
    }

    private long rawTick(MinecraftServer server) {
        return server == null ? 0L : Integer.toUnsignedLong(server.getTickCount());
    }

    private long saturatedAdd(long value, long delta) {
        if (delta <= 0L) {
            return value;
        }
        return value > Long.MAX_VALUE - delta ? Long.MAX_VALUE : value + delta;
    }

    private CombatRecentTargetLookup recentTargets(PlayerState state) {
        CombatRecentTargetLookup result = new CombatRecentTargetLookup();
        for (CombatAttackContext context : state.recent) {
            if (context.sealedTick() < 0 || serverTick < context.sealedTick()
                    || serverTick - context.sealedTick() > RECENT_SEALED_TTL_TICKS) {
                continue;
            }
            for (UUID uuid : context.requestedTargets()) {
                result.add(uuid);
            }
            for (UUID uuid : context.validAttackTargets()) {
                result.add(uuid);
            }
            for (UUID uuid : context.observedPartUuids()) {
                result.add(uuid);
            }
            for (UUID uuid : context.requestedPartParentUuids().values()) {
                result.add(uuid);
            }
            for (UUID uuid : context.observedPartParentUuids().values()) {
                result.add(uuid);
            }
            if (result.truncated()) {
                break;
            }
        }
        return result;
    }

    private CombatAttackSession activeSession(long attackId) {
        PlayerState state = stateForAttack(attackId);
        return state != null && state.active != null && state.active.attackId() == attackId
                ? state.active : null;
    }

    private PlayerState stateForAttack(long attackId) {
        for (PlayerState state : players.values()) {
            if (state.active != null && state.active.attackId() == attackId) {
                return state;
            }
        }
        return null;
    }

    private void addRecent(PlayerState state, CombatAttackContext context) {
        state.recent.addLast(context);
        trimRecent(state);
    }

    private void trimRecent(PlayerState state) {
        while (state.recent.size() > RECENT_LIMIT) {
            state.recent.removeFirst();
        }
    }

    private static final class PlayerState {
        private CombatAttackSession active;
        private final Deque<CombatAttackContext> recent = new ArrayDeque<>();
    }
}
