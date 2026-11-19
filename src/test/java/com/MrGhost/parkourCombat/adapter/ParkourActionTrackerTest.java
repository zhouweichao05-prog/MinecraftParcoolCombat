package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ParkourActionTrackerTest {
    private ParkourActionTracker tracker;
    private List<ParkourActionState> events;

    @BeforeEach
    void setUp() {
        events = new ArrayList<>();
        tracker = new ParkourActionTracker(false, events::add);
    }

    @Test
    void startThenActiveThenFinish() {
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);

        ParkourActionState started = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.STARTED, started.phase());
        assertTrue(started.active());

        tracker.onTick(1L, Set.of(ParkourActionType.FAST_RUN));
        ParkourActionState active = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.ACTIVE, active.phase());
        assertEquals(1, active.ticks());

        tracker.onFinish(ParkourActionType.FAST_RUN, 2L);
        ParkourActionState finished = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.FINISHED, finished.phase());
        assertFalse(finished.active());
        assertTrue(tracker.getActiveTypes().isEmpty());
    }

    @Test
    void startVetoedIsCancelledNotActive() {
        tracker.onCancelledAttempt(ParkourActionType.SLIDE, 5L);
        ParkourActionState state = tracker.getState(ParkourActionType.SLIDE).orElseThrow();
        assertEquals(ParkourActionPhase.CANCELLED, state.phase());
        assertFalse(state.active());
        assertTrue(state.evidence().contains("TRY_TO_START_VETOED"));
    }

    @Test
    void continueVetoedResultsInCancelled() {
        tracker.onStart(ParkourActionType.SLIDE,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.SLIDE));
        tracker.markCancelRequested(ParkourActionType.SLIDE, 2L);
        tracker.onFinish(ParkourActionType.SLIDE, 2L);

        ParkourActionState state = tracker.getState(ParkourActionType.SLIDE).orElseThrow();
        assertEquals(ParkourActionPhase.CANCELLED, state.phase());
        assertTrue(state.evidence().contains("CANCEL"));
    }

    @Test
    void replacementIsInterruptedOnClient() {
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.FAST_RUN));
        tracker.onStart(ParkourActionType.SLIDE,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 2L);
        tracker.onFinish(ParkourActionType.FAST_RUN, 2L);

        ParkourActionState state = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.INTERRUPTED, state.phase());
        assertFalse(state.inferred());
        assertTrue(state.evidence().contains("REPLACED_BY"));
    }

    @Test
    void serverReplacementTimeoutIsMarkedInterrupted() {
        tracker = new ParkourActionTracker(true, events::add);
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.PARCOOL_SYNCED, ParkourTrust.LOW, false, 0L);
        tracker.onStart(ParkourActionType.SLIDE,
                ParkourStateSource.PARCOOL_SYNCED, ParkourTrust.LOW, false, 0L);

        tracker.onTick(1L, Set.of(ParkourActionType.SLIDE));
        tracker.onTick(2L, Set.of(ParkourActionType.SLIDE));

        ParkourActionState state = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.INTERRUPTED, state.phase());
        assertTrue(state.inferred());
        assertTrue(state.evidence().contains("SYNC_LOST_REPLACED_BY"));
    }

    @Test
    void serverTimeoutWithoutReplacementIsUnknown() {
        tracker = new ParkourActionTracker(true, events::add);
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.PARCOOL_SYNCED, ParkourTrust.LOW, false, 0L);
        tracker.onTick(1L, Set.of());
        tracker.onTick(2L, Set.of());

        ParkourActionState state = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.UNKNOWN, state.phase());
        assertTrue(state.inferred());
        assertTrue(state.evidence().contains("SYNC_LOST"));
    }

    @Test
    void orphanFinishIsUnknown() {
        tracker.onFinish(ParkourActionType.VAULT, 3L);
        ParkourActionState state = tracker.getState(ParkourActionType.VAULT).orElseThrow();
        assertEquals(ParkourActionPhase.UNKNOWN, state.phase());
        assertTrue(state.evidence().contains("ORPHAN_FINISH"));
    }

    @Test
    void lateFinishAfterTimeoutDoesNotOverwriteInterrupted() {
        tracker = new ParkourActionTracker(true, events::add);
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.PARCOOL_SYNCED, ParkourTrust.LOW, false, 0L);
        tracker.onTick(1L, Set.of());
        tracker.onTick(2L, Set.of());

        tracker.onFinish(ParkourActionType.FAST_RUN, 3L);

        ParkourActionState state = tracker.getState(ParkourActionType.FAST_RUN).orElseThrow();
        assertEquals(ParkourActionPhase.UNKNOWN, state.phase());
        assertTrue(state.evidence().contains("SYNC_LOST"));
    }

    @Test
    void wallRunAggregateAndBothKinds() {
        tracker.onStart(ParkourActionType.WALL_RUN_HORIZONTAL,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.WALL_RUN_HORIZONTAL));

        ParkourActionState aggregate = tracker.getState(ParkourActionType.WALL_RUN).orElseThrow();
        assertTrue(aggregate.active());
        assertTrue(aggregate.evidence().contains("AGGREGATE"));

        tracker.onStart(ParkourActionType.WALL_RUN_VERTICAL,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 2L);
        tracker.onTick(3L, Set.of(ParkourActionType.WALL_RUN_HORIZONTAL,
                ParkourActionType.WALL_RUN_VERTICAL));

        ParkourActionState both = tracker.getState(ParkourActionType.WALL_RUN).orElseThrow();
        assertTrue(both.evidence().contains("BOTH"));
        assertTrue(tracker.isActive(ParkourActionType.WALL_RUN));
    }

    @Test
    void terminalStateExpiresAfterFortyTicks() {
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.FAST_RUN));
        tracker.onFinish(ParkourActionType.FAST_RUN, 2L);

        for (int i = 0; i < 40; i++) {
            tracker.onTick(3L + i, Set.of());
        }

        assertTrue(tracker.getState(ParkourActionType.FAST_RUN).isEmpty());
    }

    @Test
    void clearRemovesEverything() {
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.clear();
        assertTrue(tracker.getActiveTypes().isEmpty());
        assertTrue(tracker.getState(ParkourActionType.FAST_RUN).isEmpty());
    }
}
