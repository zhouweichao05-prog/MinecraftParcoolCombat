package com.MrGhost.parkourCombat.adapter;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Adapts a player's vanilla motion into {@link FallMotionSample} values and
 * drives the pure {@link FallStateMachine}.
 */
public final class MotionTracker {
    private static final double TELEPORT_DISTANCE_SQUARED = 64.0D * 64.0D;

    private final FallStateMachine stateMachine = new FallStateMachine();
    private ParkourPosition lastPosition;

    public void tick(Player player, long gameTime) {
        Vec3 position = player.position();
        ParkourPosition current = new ParkourPosition(position.x, position.y, position.z);
        boolean teleported = lastPosition != null
                && lastPosition.distanceSquared(current) > TELEPORT_DISTANCE_SQUARED;

        FallMotionSample sample = new FallMotionSample(
                player.onGround(),
                player.fallDistance,
                player.getDeltaMovement().y,
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

        stateMachine.tick(sample, gameTime);
        lastPosition = current;
    }

    public void onDimensionChange(long gameTime) {
        stateMachine.forceEnd(FallEndReason.DIMENSION_CHANGE, gameTime);
        lastPosition = null;
    }

    public void onDeath(long gameTime) {
        stateMachine.forceEnd(FallEndReason.DEATH, gameTime);
    }

    public FallSnapshot snapshot(ParkourStateSource source, ParkourTrust trust) {
        return stateMachine.snapshot(source, trust);
    }

    public FallPhase phase() {
        return stateMachine.phase();
    }

    public void clear() {
        stateMachine.clear();
        lastPosition = null;
    }
}
