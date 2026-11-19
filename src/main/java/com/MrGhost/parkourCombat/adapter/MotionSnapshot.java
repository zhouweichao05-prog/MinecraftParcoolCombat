package com.MrGhost.parkourCombat.adapter;

/**
 * Immutable motion snapshot for one logical side and one tick.
 *
 * <p>{@link #source()} and {@link #trust()} describe only the kinematic data
 * (position, velocity, acceleration, direction, displacement, continuous
 * motion). Nested {@link FallSnapshot} and {@link ParkourActionState} retain
 * their own source/trust and must not be re-labelled with the kinematic
 * trust.</p>
 *
 * <p>{@code actionState} is nullable when no parkour action is active. It is
 * intentionally not wrapped in {@link java.util.Optional} on the per-tick
 * hot path.</p>
 */
public record MotionSnapshot(
        long sampleTick,
        ParkourPosition position,
        double velocityX,
        double velocityY,
        double velocityZ,
        double horizontalSpeed,
        double directionX,
        double directionZ,
        double horizontalAcceleration,
        double verticalAcceleration,
        double displacementX,
        double displacementY,
        double displacementZ,
        int continuousMotionTicks,
        boolean onGround,
        FallSnapshot fallSnapshot,
        ParkourActionState actionState,
        ParkourStateSource source,
        ParkourTrust trust
) {
    public boolean falling() {
        return fallSnapshot != null && fallSnapshot.active();
    }
}
