package com.MrGhost.parkourCombat.adapter;

public record FallSnapshot(
        FallPhase phase,
        ParkourPosition startPosition,
        double fallDistance,
        double verticalVelocity,
        int durationTicks,
        boolean onGround,
        FallEndReason endReason,
        ParkourStateSource source,
        ParkourTrust trust,
        long lastChangedGameTime
) {
    public boolean active() {
        return phase == FallPhase.FALLING;
    }
}
