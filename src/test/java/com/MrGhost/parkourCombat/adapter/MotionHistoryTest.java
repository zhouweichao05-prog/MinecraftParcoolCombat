package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MotionHistoryTest {
    @Test
    void capacityReturnsActualInstanceCapacity() {
        assertEquals(20, new MotionHistory().capacity());
        assertEquals(5, new MotionHistory(5).capacity());
    }

    @Test
    void keepsOnlyNewestTwentySamples() {
        MotionHistory history = new MotionHistory();
        for (int tick = 0; tick < 25; tick++) {
            history.add(snapshot(tick));
        }

        assertEquals(20, history.size());
        assertEquals(24L, history.latest().sampleTick());
        assertEquals(5L, history.get(19).sampleTick());
        assertNull(history.get(20));
    }

    @Test
    void recentIsNewestFirstAndBounded() {
        MotionHistory history = new MotionHistory();
        for (int tick = 0; tick < 5; tick++) {
            history.add(snapshot(tick));
        }

        List<MotionSnapshot> recent = history.recent(3);
        assertEquals(3, recent.size());
        assertEquals(4L, recent.get(0).sampleTick());
        assertEquals(3L, recent.get(1).sampleTick());
        assertEquals(2L, recent.get(2).sampleTick());
    }

    @Test
    void clearRemovesAllReferences() {
        MotionHistory history = new MotionHistory();
        history.add(snapshot(0));
        history.clear();

        assertEquals(0, history.size());
        assertNull(history.latest());
        assertNull(history.get(0));
    }

    private static MotionSnapshot snapshot(long tick) {
        return new MotionSnapshot(
                tick,
                new ParkourPosition(0, 0, 0),
                0, 0, 0,
                0, 0, 0,
                0, 0,
                0, 0, 0,
                0,
                true,
                new FallSnapshot(
                        FallPhase.NOT_FALLING,
                        new ParkourPosition(0, 0, 0),
                        0, 0, 0, true,
                        FallEndReason.UNKNOWN,
                        ParkourStateSource.VANILLA_DERIVED,
                        ParkourTrust.HIGH,
                        0L),
                null,
                ParkourStateSource.SERVER_NATIVE,
                ParkourTrust.HIGH);
    }
}
