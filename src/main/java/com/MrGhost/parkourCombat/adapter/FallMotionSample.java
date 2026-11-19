package com.MrGhost.parkourCombat.adapter;

/**
 * Side-effect free motion sample used by {@link FallStateMachine}.
 */
public record FallMotionSample(
        boolean onGround,
        double fallDistance,
        double verticalVelocity,
        boolean inWater,
        boolean inLava,
        boolean fallFlying,
        boolean climbing,
        boolean inVehicle,
        boolean dead,
        boolean dimensionChanged,
        boolean teleported,
        ParkourPosition position
) {
}
