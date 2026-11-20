package com.MrGhost.parkourCombat.combat;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Single authoritative bounded target registry. New target UUIDs are only
 * admitted here; overflow is represented by fixed counters, never by UUIDs.
 */
final class CombatTargetRegistry {
    static final int MAX_TARGETS = 64;
    static final int MAX_EVENTS_PER_TARGET = 8;

    private final Map<UUID, CombatTargetState> targets = new LinkedHashMap<>();
    private long targetBudgetOverflowCount;
    private long partTargetOverflowCount;
    private long overflowEventCount;
    private long overflowCanceledEventCount;
    private long eventDetailOverflowCount;
    private long eventDetailTruncatedTargetCount;

    CombatTargetState get(UUID targetUuid) {
        return targets.get(targetUuid);
    }

    CombatTargetState admit(UUID targetUuid, CombatTargetState.Kind kind,
                            UUID parentUuid, boolean requested) {
        CombatTargetState existing = targets.get(targetUuid);
        if (existing != null) {
            if (requested) {
                existing.markRequested();
            }
            return existing;
        }
        if (targets.size() >= MAX_TARGETS) {
            targetBudgetOverflowCount = saturatingIncrement(targetBudgetOverflowCount);
            if (kind == CombatTargetState.Kind.PART) {
                partTargetOverflowCount = saturatingIncrement(partTargetOverflowCount);
            }
            return null;
        }
        CombatTargetState state = new CombatTargetState(targetUuid, kind, parentUuid, requested);
        targets.put(targetUuid, state);
        return state;
    }

    CombatTargetState findParentOf(UUID parentUuid) {
        for (CombatTargetState state : targets.values()) {
            if (state.kind() == CombatTargetState.Kind.PART
                    && parentUuid.equals(state.parentUuid())) {
                return state;
            }
        }
        return null;
    }

    boolean wasOverflowed(UUID targetUuid) {
        // No overflow UUIDs are retained; callers use counters and coverage.
        return false;
    }

    void recordOverflowEvent(boolean canceled) {
        overflowEventCount = saturatingIncrement(overflowEventCount);
        if (canceled) {
            overflowCanceledEventCount = saturatingIncrement(overflowCanceledEventCount);
        }
    }

    void recordDetailOverflow() {
        eventDetailOverflowCount = saturatingIncrement(eventDetailOverflowCount);
    }

    void recordTruncatedTarget(CombatTargetState state) {
        if (!state.detailTruncated()) {
            return;
        }
        // Counted once per TargetState by the caller's boolean transition.
    }

    long targetBudgetOverflowCount() {
        return targetBudgetOverflowCount;
    }

    long partTargetOverflowCount() {
        return partTargetOverflowCount;
    }

    long overflowEventCount() {
        return overflowEventCount;
    }

    long overflowCanceledEventCount() {
        return overflowCanceledEventCount;
    }

    long eventDetailOverflowCount() {
        return eventDetailOverflowCount;
    }

    long eventDetailTruncatedTargetCount() {
        return eventDetailTruncatedTargetCount;
    }

    void incrementTruncatedTargetCount() {
        eventDetailTruncatedTargetCount = saturatingIncrement(eventDetailTruncatedTargetCount);
    }

    Map<UUID, CombatTargetState> targets() {
        return Collections.unmodifiableMap(targets);
    }

    int size() {
        return targets.size();
    }

    enum Counter {
        TARGET_BUDGET_OVERFLOW,
        PART_TARGET_OVERFLOW,
        OVERFLOW_EVENT,
        OVERFLOW_CANCELED,
        EVENT_DETAIL_OVERFLOW,
        EVENT_DETAIL_TRUNCATED_TARGET
    }

    /** FOR_TEST_ONLY: seed one counter at Long.MAX_VALUE - 1. */
    void seedCounterForTest(Counter counter, long value) {
        if (value != Long.MAX_VALUE - 1L) {
            throw new IllegalArgumentException("only MAX-1 seeding is allowed");
        }
        switch (counter) {
            case TARGET_BUDGET_OVERFLOW -> targetBudgetOverflowCount = value;
            case PART_TARGET_OVERFLOW -> partTargetOverflowCount = value;
            case OVERFLOW_EVENT -> overflowEventCount = value;
            case OVERFLOW_CANCELED -> overflowCanceledEventCount = value;
            case EVENT_DETAIL_OVERFLOW -> eventDetailOverflowCount = value;
            case EVENT_DETAIL_TRUNCATED_TARGET -> eventDetailTruncatedTargetCount = value;
        }
    }

    static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
