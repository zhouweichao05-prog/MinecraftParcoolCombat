package com.MrGhost.parkourCombat.adapter;

import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Per-player bridge between Minecraft movement data, the stage 02 FALL state
 * machine and the pure {@link MotionAccumulator}.
 *
 * <p>One call to {@link #tick(Player, long, ParkourActionState)} performs the
 * only entity read for this player on this logical side for this tick.</p>
 */
public final class MotionTracker {
    private static final double FALL_TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;

    private final FallStateMachine stateMachine = new FallStateMachine();
    private final MotionAccumulator accumulator = new MotionAccumulator();
    private final ParkourStateSource motionSource;
    private final ParkourTrust motionTrust;
    private final ParkourStateSource fallSource;
    private final ParkourTrust fallTrust;

    private ParkourPosition lastPosition;
    private MotionSnapshot latestSnapshot;

    public MotionTracker(ParkourStateSource motionSource,
                         ParkourTrust motionTrust,
                         ParkourStateSource fallSource,
                         ParkourTrust fallTrust) {
        this.motionSource = motionSource;
        this.motionTrust = motionTrust;
        this.fallSource = fallSource;
        this.fallTrust = fallTrust;
    }

    public void tick(Player player, long gameTime, ParkourActionState actionState) {
        long sampleTick = player.tickCount;
        if (latestSnapshot != null && latestSnapshot.sampleTick() == sampleTick) {
            // Guard against a duplicate PlayerTick event for the same tick.
            return;
        }

        Vec3 position = player.position();
        Vec3 movement = player.getDeltaMovement();
        ParkourPosition current = new ParkourPosition(position.x, position.y, position.z);

        boolean teleported = lastPosition != null
                && lastPosition.distanceSquared(current) > FALL_TELEPORT_DISTANCE_SQUARED;

        FallMotionSample fallSample = new FallMotionSample(
                player.onGround(),
                player.fallDistance,
                movement.y,
                player.isInWater(),
                player.isInLava(),
                player.isFallFlying(),
                player.onClimbable(),
                player.getVehicle() != null,
                player.isDeadOrDying(),
                false,
                teleported,
                current
        );
        stateMachine.tick(fallSample, gameTime);
        FallSnapshot fallSnapshot = stateMachine.snapshot(fallSource, fallTrust);

        MotionFrame frame = new MotionFrame(
                sampleTick,
                current,
                movement.x,
                movement.y,
                movement.z,
                player.onGround(),
                player.isInWater(),
                player.isInLava(),
                player.isFallFlying(),
                player.onClimbable(),
                player.getVehicle() != null,
                player.isDeadOrDying(),
                false,
                teleported
        );

        latestSnapshot = accumulator.accept(
                frame, fallSnapshot, actionState, motionSource, motionTrust);
        lastPosition = current;
    }

    public MotionSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    public List<MotionSnapshot> getHistory(int maxSamples) {
        return accumulator.recent(maxSamples);
    }

    public int historyCapacity() {
        return accumulator.history().capacity();
    }

    public String consumeResetReason() {
        return accumulator.consumeResetReason();
    }

    public FallSnapshot getFallSnapshot() {
        return stateMachine.snapshot(fallSource, fallTrust);
    }

    public FallSnapshot snapshot(ParkourStateSource source, ParkourTrust trust) {
        return stateMachine.snapshot(source, trust);
    }

    public FallPhase phase() {
        return stateMachine.phase();
    }

    public void onDimensionChange(long gameTime) {
        stateMachine.forceEnd(FallEndReason.DIMENSION_CHANGE, gameTime);
        accumulator.clear();
        latestSnapshot = null;
        lastPosition = null;
    }

    public void onDeath(long gameTime) {
        stateMachine.forceEnd(FallEndReason.DEATH, gameTime);
        accumulator.clear();
        latestSnapshot = null;
    }

    public void clear() {
        stateMachine.clear();
        accumulator.clear();
        latestSnapshot = null;
        lastPosition = null;
    }
}
