package com.MrGhost.parkourCombat.combat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-thread-only target state. A canonical target UUID owns exactly one
 * state. PartEntity parent UUIDs are relationships, never synthetic targets.
 */
final class CombatTargetState {
    enum Kind { NORMAL, PART }
    enum ChainState { NONE, VALID, CANCELED, OVERLAP }

    private final UUID targetUuid;
    private final Kind kind;
    private UUID parentUuid;
    private boolean requested;
    private boolean observed;
    private int attackEntityCount;
    private int livingAttackCount;
    private int livingHurtCount;
    private int livingDamageCount;
    private int canceledCount;
    private ChainState chainState = ChainState.NONE;
    private final List<CombatEventObservation> events = new ArrayList<>();
    private boolean detailTruncated;

    CombatTargetState(UUID targetUuid, Kind kind, UUID parentUuid, boolean requested) {
        this.targetUuid = targetUuid;
        this.kind = kind;
        this.parentUuid = parentUuid;
        this.requested = requested;
    }

    UUID targetUuid() {
        return targetUuid;
    }

    Kind kind() {
        return kind;
    }

    UUID parentUuid() {
        return parentUuid;
    }

    void parentUuid(UUID parentUuid) {
        this.parentUuid = parentUuid;
    }

    boolean requested() {
        return requested;
    }

    void markRequested() {
        this.requested = true;
    }

    boolean observed() {
        return observed;
    }

    void markObserved() {
        this.observed = true;
    }

    int attackEntityCount() {
        return attackEntityCount;
    }

    int livingAttackCount() {
        return livingAttackCount;
    }

    int livingHurtCount() {
        return livingHurtCount;
    }

    int livingDamageCount() {
        return livingDamageCount;
    }

    int canceledCount() {
        return canceledCount;
    }

    ChainState chainState() {
        return chainState;
    }

    void chainState(ChainState state) {
        this.chainState = state;
    }

    List<CombatEventObservation> events() {
        return Collections.unmodifiableList(events);
    }

    boolean detailTruncated() {
        return detailTruncated;
    }

    boolean record(CombatEventObservation event, int maxEvents, Runnable onDetailOverflow) {
        if (event.eventType() == CombatEventType.ATTACK_ENTITY) {
            attackEntityCount++;
        } else if (event.eventType() == CombatEventType.LIVING_ATTACK) {
            livingAttackCount++;
        } else if (event.eventType() == CombatEventType.LIVING_HURT) {
            livingHurtCount++;
        } else if (event.eventType() == CombatEventType.LIVING_DAMAGE) {
            livingDamageCount++;
        }
        if (event.canceledAtObservation()) {
            canceledCount++;
        }
        if (events.size() < maxEvents) {
            events.add(event);
            return true;
        }
        detailTruncated = true;
        onDetailOverflow.run();
        return false;
    }

    int totalEventCount() {
        return attackEntityCount + livingAttackCount + livingHurtCount + livingDamageCount;
    }

    CombatEventAggregateState aggregateState() {
        if (detailTruncated) {
            return CombatEventAggregateState.TRUNCATED;
        }
        if (totalEventCount() == 0) {
            return CombatEventAggregateState.NOT_OBSERVED;
        }
        boolean anyCanceled = canceledCount > 0;
        boolean anyValid = chainState == ChainState.VALID;
        boolean anyUncertain = chainState == ChainState.OVERLAP || chainState == ChainState.CANCELED;
        if (anyCanceled && anyValid) {
            return CombatEventAggregateState.MIXED;
        }
        if (anyCanceled) {
            return CombatEventAggregateState.OBSERVED_CANCELED;
        }
        if (anyUncertain) {
            return CombatEventAggregateState.ASSOCIATION_UNCERTAIN;
        }
        return CombatEventAggregateState.OBSERVED_VALID;
    }

    CombatTargetEventObservation toObservation() {
        return new CombatTargetEventObservation(targetUuid, events, totalEventCount(),
                attackEntityCount, livingAttackCount, livingHurtCount, livingDamageCount,
                canceledCount, detailTruncated, aggregateState());
    }
}
