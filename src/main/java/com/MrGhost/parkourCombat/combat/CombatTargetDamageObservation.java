package com.MrGhost.parkourCombat.combat;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Immutable per-target observation. Amounts are event observations only and
 * are never summed or presented as final damage.
 */
public final class CombatTargetDamageObservation {
    public static final int MAX_OBSERVATIONS = 8;

    private final UUID targetUuid;
    private final List<CombatDamageEventObservation> observations;
    private final int totalEventCount;
    private final boolean truncated;

    public CombatTargetDamageObservation(UUID targetUuid,
                                         List<CombatDamageEventObservation> observations,
                                         int totalEventCount,
                                         boolean truncated) {
        if (targetUuid == null) {
            throw new IllegalArgumentException("targetUuid must not be null");
        }
        if (observations == null) {
            throw new IllegalArgumentException("observations must not be null");
        }
        if (observations.size() > MAX_OBSERVATIONS) {
            throw new IllegalArgumentException("too many observations");
        }
        this.targetUuid = targetUuid;
        this.observations = Collections.unmodifiableList(List.copyOf(observations));
        this.totalEventCount = Math.max(totalEventCount, observations.size());
        this.truncated = truncated || totalEventCount > observations.size();
    }

    public UUID targetUuid() {
        return targetUuid;
    }

    public List<CombatDamageEventObservation> observations() {
        return observations;
    }

    public int totalEventCount() {
        return totalEventCount;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean positiveObserved() {
        return observations.stream().anyMatch(CombatDamageEventObservation::positiveAtObservation);
    }

    public boolean canceledObserved() {
        return observations.stream().anyMatch(CombatDamageEventObservation::canceledAtObservation);
    }

    public boolean ambiguousSourceObserved() {
        return observations.stream().anyMatch(observation ->
                observation.sourceClassification() == CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK);
    }
}
