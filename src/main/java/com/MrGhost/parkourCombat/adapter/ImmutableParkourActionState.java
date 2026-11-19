package com.MrGhost.parkourCombat.adapter;

public record ImmutableParkourActionState(
        ParkourActionType type,
        ParkourActionPhase phase,
        boolean active,
        int ticks,
        long startedGameTime,
        long lastChangedGameTime,
        ParkourStateSource source,
        ParkourTrust trust,
        boolean serverAuthoritative,
        boolean inferred,
        String evidence
) implements ParkourActionState {
}
