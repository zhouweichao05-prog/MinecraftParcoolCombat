package com.MrGhost.parkourCombat.combat;

public record CombatDamageEventObservation(
        CombatDamageEventType eventType,
        float amountAtObservation,
        boolean canceledAtObservation,
        boolean positiveAtObservation,
        CombatDamageSourceClassification sourceClassification,
        int observationTick
) {
}
