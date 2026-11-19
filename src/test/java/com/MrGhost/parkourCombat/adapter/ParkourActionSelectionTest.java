package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ParkourActionSelectionTest {
    @Test
    void noActiveActionSelectsNull() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        assertNull(ParkourActionSelection.select(tracker));
    }

    @Test
    void slideWinsOverFastRun() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        start(tracker, ParkourActionType.FAST_RUN, 0L);
        start(tracker, ParkourActionType.SLIDE, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.FAST_RUN, ParkourActionType.SLIDE));

        ParkourActionState state = ParkourActionSelection.select(tracker);
        assertEquals(ParkourActionType.SLIDE, state.type());
    }

    @Test
    void vaultWinsOverSlide() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        start(tracker, ParkourActionType.SLIDE, 0L);
        start(tracker, ParkourActionType.VAULT, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.SLIDE, ParkourActionType.VAULT));

        assertEquals(ParkourActionType.VAULT, ParkourActionSelection.select(tracker).type());
    }

    @Test
    void wallJumpWinsOverVault() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        start(tracker, ParkourActionType.VAULT, 0L);
        start(tracker, ParkourActionType.WALL_JUMP, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.VAULT, ParkourActionType.WALL_JUMP));

        assertEquals(ParkourActionType.WALL_JUMP, ParkourActionSelection.select(tracker).type());
    }

    @Test
    void wallRunAggregateWinsOverAll() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        start(tracker, ParkourActionType.WALL_RUN_HORIZONTAL, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.WALL_RUN_HORIZONTAL));

        ParkourActionState state = ParkourActionSelection.select(tracker);
        assertEquals(ParkourActionType.WALL_RUN, state.type());
    }

    @Test
    void terminalStateIsNotSelected() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, null);
        start(tracker, ParkourActionType.FAST_RUN, 0L);
        tracker.onTick(1L, Set.of(ParkourActionType.FAST_RUN));
        tracker.onFinish(ParkourActionType.FAST_RUN, 2L);

        assertNull(ParkourActionSelection.select(tracker));
    }

    private static void start(ParkourActionTracker tracker,
                              ParkourActionType type,
                              long gameTime) {
        tracker.onStart(type, ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM,
                false, gameTime);
    }
}
