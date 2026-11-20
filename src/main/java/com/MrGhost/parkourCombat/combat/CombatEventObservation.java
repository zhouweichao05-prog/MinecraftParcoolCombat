package com.MrGhost.parkourCombat.combat;

/**
 * One bounded observation. Amount is an observation only; null means the
 * event type has no damage amount (for example AttackEntityEvent).
 */
public record CombatEventObservation(
        CombatEventType eventType,
        int eventOrdinal,
        int observationTick,
        boolean canceledAtObservation,
        Float amountAtObservation,
        boolean positiveAtObservation,
        CombatDamageSourceKind sourceKind,
        CombatDamageSourceClassification sourceClassification,
        CombatDamageAssociation association,
        CombatEventState eventState,
        boolean betterCombatExclusiveProven
) {
    public CombatEventObservation {
        if (eventType == null || sourceKind == null || sourceClassification == null
                || association == null || eventState == null) {
            throw new IllegalArgumentException("event enums must not be null");
        }
        if (betterCombatExclusiveProven) {
            throw new IllegalArgumentException("exclusive Better Combat provenance is unavailable");
        }
    }
}
