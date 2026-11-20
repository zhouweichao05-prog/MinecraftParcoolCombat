package com.MrGhost.parkourCombat.combat;

public enum CombatEventAggregateState {
    NOT_OBSERVED,
    OBSERVED_VALID,
    OBSERVED_CANCELED,
    MIXED,
    TRUNCATED,
    ASSOCIATION_UNCERTAIN,
    UNKNOWN
}
