package com.MrGhost.parkourCombat.combat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable, bounded per-target event record. Detail records are capped at
 * eight; counts remain available after truncation.
 */
public final class CombatTargetEventObservation {
    public static final int MAX_EVENTS = 8;

    private final UUID targetUuid;
    private final List<CombatEventObservation> observations;
    private final int totalEventCount;
    private final int attackEntityCount;
    private final int livingAttackCount;
    private final int livingHurtCount;
    private final int livingDamageCount;
    private final int canceledCount;
    private final boolean truncated;
    private final CombatEventAggregateState aggregateState;

    public CombatTargetEventObservation(UUID targetUuid,
                                        List<CombatEventObservation> observations,
                                        int totalEventCount,
                                        int attackEntityCount,
                                        int livingAttackCount,
                                        int livingHurtCount,
                                        int livingDamageCount,
                                        int canceledCount,
                                        boolean truncated,
                                        CombatEventAggregateState aggregateState) {
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid must not be null");
        }
        if (observations == null) {
            throw new IllegalArgumentException("observations must not be null");
        }
        if (observations.size() > MAX_EVENTS) {
            throw new IllegalArgumentException("too many event observations");
        }
        this.targetUuid = targetUuid;
        this.observations = Collections.unmodifiableList(List.copyOf(observations));
        this.totalEventCount = Math.max(totalEventCount, observations.size());
        this.attackEntityCount = attackEntityCount;
        this.livingAttackCount = livingAttackCount;
        this.livingHurtCount = livingHurtCount;
        this.livingDamageCount = livingDamageCount;
        this.canceledCount = canceledCount;
        this.truncated = truncated || totalEventCount > observations.size();
        this.aggregateState = aggregateState == null
                ? CombatEventAggregateState.UNKNOWN
                : aggregateState;
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public List<CombatEventObservation> observations() {
        return observations;
    }

    public int totalEventCount() {
        return totalEventCount;
    }

    public int attackEntityCount() {
        return attackEntityCount;
    }

    public int livingAttackCount() {
        return livingAttackCount;
    }

    public int livingHurtCount() {
        return livingHurtCount;
    }

    public int livingDamageCount() {
        return livingDamageCount;
    }

    public int canceledCount() {
        return canceledCount;
    }

    public boolean truncated() {
        return truncated;
    }

    public CombatEventAggregateState aggregateState() {
        return aggregateState;
    }

    public boolean positiveObserved() {
        return observations.stream().anyMatch(CombatEventObservation::positiveAtObservation);
    }
}
