package com.MrGhost.parkourCombat.adapter;

import com.MrGhost.parkourCombat.ParkourCombat;
import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.capability.Parkourability;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Common implementation of the read-only ParCool adapter.
 *
 * <p>All state is keyed by player UUID and accessed from the main thread. No
 * client animation classes are referenced from this class.</p>
 */
public class ParCoolActionAdapter implements ParkourActionAdapter {
    private static final Logger LOGGER =
            LogManager.getLogger(ParkourCombat.MOD_ID + ".adapter");

    protected final boolean clientSide;
    private final Map<UUID, PlayerTrackers> players = new HashMap<>();
    private final ParCoolActionMapping mapping = new ParCoolActionMapping();
    private final boolean enabled;

    public ParCoolActionAdapter(boolean clientSide) {
        this.clientSide = clientSide;
        boolean versionOk = mapping.isVersionSupported();
        this.enabled = mapping.isAvailable() && versionOk;
        if (this.enabled) {
            LOGGER.info("[ParkourAdapter] initialized side={} parCoolVersion=3.4.3.3",
                    sideName());
        } else {
            LOGGER.error("[ParkourAdapter] disabled: mappingAvailable={} parCoolVersionSupported={}",
                    mapping.isAvailable(), versionOk);
        }
    }

    public boolean isClientSide() {
        return clientSide;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void onActionStart(Player player, Action action) {
        if (!enabled || !isTrackable(player)) {
            return;
        }
        ParkourActionType type = mapping.typeOf(action).orElse(null);
        if (type == null) {
            return;
        }
        getOrCreate(player).actions.onStart(
                type, source(), trust(), false, gameTime(player));
    }

    public void onActionFinish(Player player, Action action) {
        if (!enabled || !isTrackable(player)) {
            return;
        }
        ParkourActionType type = mapping.typeOf(action).orElse(null);
        if (type == null) {
            return;
        }
        PlayerTrackers trackers = players.get(player.getUUID());
        if (trackers != null) {
            trackers.actions.onFinish(type, gameTime(player));
        }
    }

    public void onTryToStart(Player player, Action action, boolean cancelled) {
        if (!enabled || !clientSide || !cancelled || !isTrackable(player)) {
            return;
        }
        ParkourActionType type = mapping.typeOf(action).orElse(null);
        if (type != null) {
            getOrCreate(player).actions.onCancelledAttempt(type, gameTime(player));
        }
    }

    public void onTryToContinue(Player player, Action action, boolean cancelled) {
        if (!enabled || !clientSide || !cancelled || !isTrackable(player)) {
            return;
        }
        ParkourActionType type = mapping.typeOf(action).orElse(null);
        if (type != null) {
            getOrCreate(player).actions.markCancelRequested(type, gameTime(player));
        }
    }

    public void onPlayerTick(Player player) {
        if (!enabled || !isTrackable(player)) {
            return;
        }
        if (player.isDeadOrDying()) {
            // The death event normally removes the entry; this also covers the
            // death-screen ticks before respawn and avoids repeated DEAD resets.
            remove(player.getUUID(), "DEATH_TICK");
            return;
        }

        PlayerTrackers trackers = getOrCreate(player);
        ResourceKey<Level> dimension = player.level().dimension();
        if (!dimension.equals(trackers.dimension)) {
            onDimensionChange(player);
            return;
        }

        long gameTime = gameTime(player);
        Parkourability parkourability = Parkourability.get(player);
        Set<ParkourActionType> doing = parkourability == null
                ? Set.of()
                : mapping.activeTypes(parkourability);
        trackers.actions.onTick(gameTime, doing);

        ParkourActionState primary = ParkourActionSelection.select(trackers.actions);
        trackers.motion.tick(player, gameTime, primary);

        FallSnapshot fall = trackers.motion.getFallSnapshot();
        if (fall.phase() != trackers.lastFallPhase) {
            logFall(trackers.name, fall);
            trackers.lastFallPhase = fall.phase();
        }

        String resetReason = trackers.motion.consumeResetReason();
        if (resetReason != null) {
            LOGGER.info("[MotionTracker] reset side={} player={} tick={} reason={}",
                    sideName(), trackers.name, gameTime, resetReason);
        }

        MotionSnapshot motion = trackers.motion.getLatestSnapshot();
        if (motion != null
                && (trackers.lastMotionLogTick < 0L
                || gameTime - trackers.lastMotionLogTick >= 20L)) {
            logMotion(trackers.name, motion);
            trackers.lastMotionLogTick = gameTime;
        }
    }

    public void onLogout(Player player) {
        remove(player.getUUID(), "LOGOUT");
    }

    public void onClone(Player original, Player replacement) {
        remove(original.getUUID(), "CLONE_ORIGINAL");
        remove(replacement.getUUID(), "CLONE_REPLACEMENT");
    }

    public void onDimensionChange(Player player) {
        remove(player.getUUID(), "DIMENSION_CHANGE");
    }

    public void onDeath(Player player) {
        PlayerTrackers trackers = players.get(player.getUUID());
        if (trackers != null) {
            trackers.motion.onDeath(gameTime(player));
        }
        remove(player.getUUID(), "DEATH");
    }

    public void onLevelUnload(ResourceKey<Level> dimension) {
        players.entrySet().removeIf(entry -> {
            if (entry.getValue().dimension.equals(dimension)) {
                LOGGER.info("[ParkourAdapter] cleanup side={} player={} reason=LEVEL_UNLOAD",
                        sideName(), entry.getValue().name);
                return true;
            }
            return false;
        });
    }

    public void clearAll(String reason) {
        if (players.isEmpty()) {
            return;
        }
        int count = players.size();
        players.clear();
        LOGGER.info("[ParkourAdapter] cleanup all side={} players={} reason={}",
                sideName(), count, reason);
    }

    @Override
    public Optional<ParkourActionState> getState(Player player, ParkourActionType type) {
        PlayerTrackers trackers = players.get(player.getUUID());
        return trackers == null ? Optional.empty() : trackers.actions.getState(type);
    }

    @Override
    public Set<ParkourActionType> getActiveActions(Player player) {
        PlayerTrackers trackers = players.get(player.getUUID());
        if (trackers == null) {
            return Set.of();
        }
        return Collections.unmodifiableSet(trackers.actions.getActiveTypes());
    }

    @Override
    public boolean isActive(Player player, ParkourActionType type) {
        PlayerTrackers trackers = players.get(player.getUUID());
        return trackers != null && trackers.actions.isActive(type);
    }

    @Override
    public Optional<FallSnapshot> getFallState(Player player) {
        PlayerTrackers trackers = players.get(player.getUUID());
        if (trackers == null) {
            return Optional.empty();
        }
        FallSnapshot snapshot = trackers.motion.getFallSnapshot();
        return snapshot.phase() == FallPhase.NOT_FALLING
                ? Optional.empty()
                : Optional.of(snapshot);
    }

    @Override
    public Optional<MotionSnapshot> getMotionSnapshot(Player player) {
        PlayerTrackers trackers = players.get(player.getUUID());
        return trackers == null
                ? Optional.empty()
                : Optional.ofNullable(trackers.motion.getLatestSnapshot());
    }

    @Override
    public List<MotionSnapshot> getMotionHistory(Player player, int maxSamples) {
        PlayerTrackers trackers = players.get(player.getUUID());
        return trackers == null
                ? List.of()
                : trackers.motion.getHistory(maxSamples);
    }

    @Override
    public int getMotionHistoryCapacity(Player player) {
        PlayerTrackers trackers = players.get(player.getUUID());
        return trackers == null ? 0 : trackers.motion.historyCapacity();
    }

    @Override
    public Optional<WallRunSnapshot> getWallRunState(Player player, ParkourActionType type) {
        if (!type.isConcreteWallRun()) {
            return Optional.empty();
        }
        return getState(player, type).map(state ->
                new WallRunSnapshot(type, state.active(), state.ticks(), false));
    }

    protected ParkourStateSource source() {
        return clientSide
                ? ParkourStateSource.CLIENT_LOCAL
                : ParkourStateSource.PARCOOL_SYNCED;
    }

    protected ParkourTrust trust() {
        return clientSide ? ParkourTrust.MEDIUM : ParkourTrust.LOW;
    }

    /**
     * FALL is derived from vanilla physics, not from a ParCool sync packet.
     */
    protected ParkourStateSource fallSource() {
        return ParkourStateSource.VANILLA_DERIVED;
    }

    protected ParkourTrust fallTrust() {
        return clientSide ? ParkourTrust.MEDIUM : ParkourTrust.HIGH;
    }

    /**
     * Kinematic source only; nested fall/action states keep their own trust.
     */
    protected ParkourStateSource motionSource() {
        return clientSide
                ? ParkourStateSource.CLIENT_LOCAL
                : ParkourStateSource.SERVER_NATIVE;
    }

    protected ParkourTrust motionTrust() {
        return clientSide ? ParkourTrust.MEDIUM : ParkourTrust.HIGH;
    }

    protected boolean isTrackable(Player player) {
        if (player == null) {
            return false;
        }
        if (clientSide) {
            return player.level().isClientSide && player.isLocalPlayer();
        }
        return !player.level().isClientSide;
    }

    private PlayerTrackers getOrCreate(Player player) {
        PlayerTrackers existing = players.get(player.getUUID());
        if (existing != null) {
            return existing;
        }
        String name = player.getGameProfile().getName();
        PlayerTrackers created = new PlayerTrackers(
                new ParkourActionTracker(!clientSide, state -> logAction(name, state)),
                new MotionTracker(motionSource(), motionTrust(), fallSource(), fallTrust()),
                player.level().dimension(),
                name
        );
        players.put(player.getUUID(), created);
        return created;
    }

    private void remove(UUID uuid, String reason) {
        PlayerTrackers removed = players.remove(uuid);
        if (removed != null) {
            removed.motion.clear();
            LOGGER.info("[ParkourAdapter] cleanup side={} player={} reason={}",
                    sideName(), removed.name, reason);
        }
    }

    private long gameTime(Player player) {
        return player.level().getGameTime();
    }

    private void logAction(String name, ParkourActionState state) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "[ParkourAdapter] side={} player={} type={} phase={} source={} trust={} "
                        + "ticks={} serverAuthoritative={} inferred={} evidence={}",
                sideName(), name, state.type(), state.phase(), state.source(),
                state.trust(), state.ticks(), state.serverAuthoritative(),
                state.inferred(), state.evidence());
    }

    private void logFall(String name, FallSnapshot snapshot) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        LOGGER.debug(
                "[ParkourAdapter] side={} player={} type=FALL phase={} source={} trust={} "
                        + "serverAuthoritative={} ticks={} fallStart={} fallDistance={} "
                        + "fallVy={} endReason={}",
                sideName(), name, snapshot.phase(), snapshot.source(), snapshot.trust(),
                !clientSide, snapshot.durationTicks(), snapshot.startPosition(),
                snapshot.fallDistance(), snapshot.verticalVelocity(), snapshot.endReason());
    }

    private void logMotion(String name, MotionSnapshot snapshot) {
        if (!LOGGER.isDebugEnabled()) {
            return;
        }
        ParkourActionState action = snapshot.actionState();
        String actionType = action == null ? "NONE" : action.type().name();
        String actionPhase = action == null ? "NONE" : action.phase().name();
        String actionSource = action == null ? "NONE" : action.source().name();
        String actionTrust = action == null ? "NONE" : action.trust().name();
        FallSnapshot fall = snapshot.fallSnapshot();

        LOGGER.debug(
                "[MotionTracker] side={} player={} tick={} pos={} vel=({},{},{}) "
                        + "hSpeed={} dir=({},{}) hAccel={} vAccel={} disp=({},{},{}) "
                        + "movingTicks={} onGround={} motionSource={} motionTrust={} "
                        + "fallPhase={} fallSource={} fallTrust={} "
                        + "actionType={} actionPhase={} actionSource={} actionTrust={}",
                sideName(), name, snapshot.sampleTick(), snapshot.position(),
                snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ(),
                snapshot.horizontalSpeed(), snapshot.directionX(), snapshot.directionZ(),
                snapshot.horizontalAcceleration(), snapshot.verticalAcceleration(),
                snapshot.displacementX(), snapshot.displacementY(), snapshot.displacementZ(),
                snapshot.continuousMotionTicks(), snapshot.onGround(),
                snapshot.source(), snapshot.trust(),
                fall.phase(), fall.source(), fall.trust(),
                actionType, actionPhase, actionSource, actionTrust);
    }

    private String sideName() {
        return clientSide ? "CLIENT" : "SERVER";
    }

    private static final class PlayerTrackers {
        private final ParkourActionTracker actions;
        private final MotionTracker motion;
        private final ResourceKey<Level> dimension;
        private final String name;
        private FallPhase lastFallPhase = FallPhase.NOT_FALLING;
        private long lastMotionLogTick = -1L;

        private PlayerTrackers(ParkourActionTracker actions,
                               MotionTracker motion,
                               ResourceKey<Level> dimension,
                               String name) {
            this.actions = actions;
            this.motion = motion;
            this.dimension = dimension;
            this.name = name;
        }
    }
}
