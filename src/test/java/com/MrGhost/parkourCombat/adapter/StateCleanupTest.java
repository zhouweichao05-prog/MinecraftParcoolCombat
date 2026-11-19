package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StateCleanupTest {
    @Test
    void actionTrackerClearRemovesActiveAndTerminalStates() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, state -> {
        });
        tracker.onStart(ParkourActionType.FAST_RUN,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM, false, 0L);
        tracker.onTick(1L, java.util.Set.of(ParkourActionType.FAST_RUN));
        tracker.onFinish(ParkourActionType.FAST_RUN, 2L);

        tracker.clear();

        assertTrue(tracker.getActiveTypes().isEmpty());
        assertTrue(tracker.getState(ParkourActionType.FAST_RUN).isEmpty());
    }

    @Test
    void fallMachineClearResetsPhase() {
        FallStateMachine machine = new FallStateMachine();
        machine.tick(new FallMotionSample(false, 2.0D, -0.5D,
                        false, false, false, false, false, false, false, false,
                        new ParkourPosition(0.0D, 10.0D, 0.0D)), 0L);
        assertEquals(FallPhase.FALLING, machine.phase());

        machine.clear();

        assertEquals(FallPhase.NOT_FALLING, machine.phase());
    }
}
