package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MotionAccumulatorTest {
    private static final ParkourStateSource MOTION_SOURCE = ParkourStateSource.SERVER_NATIVE;
    private static final ParkourTrust MOTION_TRUST = ParkourTrust.HIGH;

    @Test
    void stationaryFirstSampleHasZeroDerivedValues() {
        MotionAccumulator accumulator = new MotionAccumulator();
        MotionSnapshot snapshot = accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, true), null);

        assertNotNull(snapshot);
        assertEquals(0.0D, snapshot.horizontalSpeed(), 1.0E-9);
        assertEquals(0.0D, snapshot.horizontalAcceleration(), 1.0E-9);
        assertEquals(0.0D, snapshot.verticalAcceleration(), 1.0E-9);
        assertEquals(0, snapshot.continuousMotionTicks());
        assertTrue(snapshot.onGround());
        assertFalse(snapshot.falling());
    }

    @Test
    void horizontalMovementComputesSpeedDirectionAccelerationAndDisplacement() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);
        MotionSnapshot snapshot = accept(
                accumulator, frame(1L, 0.3D, 0, 0, 0.3D, 0, 0, false), null);

        assertEquals(0.3D, snapshot.horizontalSpeed(), 1.0E-9);
        assertEquals(1.0D, snapshot.directionX(), 1.0E-9);
        assertEquals(0.0D, snapshot.directionZ(), 1.0E-9);
        assertEquals(0.3D, snapshot.horizontalAcceleration(), 1.0E-9);
        assertEquals(0.3D, snapshot.displacementX(), 1.0E-9);
        assertEquals(1, snapshot.continuousMotionTicks());
    }

    @Test
    void directionChangeUsesCurrentVelocityAndVectorDelta() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0.3D, 0, 0, false), null);
        MotionSnapshot snapshot = accept(
                accumulator, frame(1L, 0.3D, 0, 0.4D, 0, 0, 0.4D, false), null);

        assertEquals(0.4D, snapshot.horizontalSpeed(), 1.0E-9);
        assertEquals(0.0D, snapshot.directionX(), 1.0E-9);
        assertEquals(1.0D, snapshot.directionZ(), 1.0E-9);
        assertEquals(0.5D, snapshot.horizontalAcceleration(), 1.0E-9);
    }

    @Test
    void jumpHasPositiveVerticalVelocityAndCountsAsMotion() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, true), null);
        MotionSnapshot snapshot = accept(
                accumulator, frame(1L, 0, 0.42D, 0, 0, 0.42D, 0, false), null);

        assertEquals(0.42D, snapshot.velocityY(), 1.0E-9);
        assertEquals(0.42D, snapshot.verticalAcceleration(), 1.0E-9);
        assertEquals(1, snapshot.continuousMotionTicks());
        assertFalse(snapshot.onGround());
    }

    @Test
    void fallingHasNegativeVerticalVelocity() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);
        MotionSnapshot snapshot = accept(
                accumulator, frame(1L, 0, -0.5D, 0, 0, -0.5D, 0, false), null);

        assertEquals(-0.5D, snapshot.velocityY(), 1.0E-9);
        assertEquals(1, snapshot.continuousMotionTicks());
    }

    @Test
    void landingUpdatesOnGroundAndVerticalAcceleration() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 5, 0, 0, -0.5D, 0, false), null);
        MotionSnapshot snapshot = accept(
                accumulator, frame(1L, 0, 0, 0, 0, 0, 0, true), null);

        assertTrue(snapshot.onGround());
        assertEquals(0.5D, snapshot.verticalAcceleration(), 1.0E-9);
        assertEquals(0, snapshot.continuousMotionTicks());
    }

    @Test
    void briefStopKeepsDirectionForFiveTicksThenClears() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0.3D, 0, 0, false), null);
        accept(accumulator, frame(1L, 0.3D, 0, 0, 0.3D, 0, 0, false), null);

        MotionSnapshot fourStopped = null;
        for (int tick = 2; tick <= 5; tick++) {
            fourStopped = accept(accumulator, frame(tick, 0.3D, 0, 0, 0, 0, 0, true), null);
        }
        assertEquals(1.0D, fourStopped.directionX(), 1.0E-9);
        assertEquals(0.0D, fourStopped.directionZ(), 1.0E-9);

        MotionSnapshot fiveStopped = accept(
                accumulator, frame(6L, 0.3D, 0, 0, 0, 0, 0, true), null);
        assertEquals(0.0D, fiveStopped.directionX(), 1.0E-9);
        assertEquals(0.0D, fiveStopped.directionZ(), 1.0E-9);
    }

    @Test
    void continuousMotionCountsAndResets() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, true), null);
        assertEquals(1, accept(accumulator, frame(1L, 0.1D, 0, 0, 0.1D, 0, 0, false), null)
                .continuousMotionTicks());
        assertEquals(2, accept(accumulator, frame(2L, 0.2D, 0, 0, 0.1D, 0, 0, false), null)
                .continuousMotionTicks());
        assertEquals(3, accept(accumulator, frame(3L, 0.3D, 0, 0, 0.1D, 0, 0, false), null)
                .continuousMotionTicks());
        assertEquals(0, accept(accumulator, frame(4L, 0.3D, 0, 0, 0, 0, 0, true), null)
                .continuousMotionTicks());
    }

    @Test
    void historyIsBoundedAndNewestFirst() {
        MotionAccumulator accumulator = new MotionAccumulator();
        for (int tick = 0; tick <= 24; tick++) {
            accept(accumulator, frame(tick, tick * 0.1D, 0, 0, 0.1D, 0, 0, false), null);
        }

        assertEquals(20, accumulator.history().size());
        assertEquals(24L, accumulator.latest().sampleTick());
        assertEquals(5L, accumulator.history().get(19).sampleTick());
        assertNull(accumulator.history().get(20));

        List<MotionSnapshot> recent = accumulator.recent(3);
        assertEquals(3, recent.size());
        assertEquals(24L, recent.get(0).sampleTick());
        assertEquals(23L, recent.get(1).sampleTick());
        assertEquals(22L, recent.get(2).sampleTick());
    }

    @Test
    void teleportResetsHistoryAndDoesNotEmitSpike() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);

        MotionSnapshot teleported = accept(
                accumulator, frame(1L, 10, 0, 0, 1.0D, 0, 0, false), null);
        assertNull(teleported);
        assertEquals(0, accumulator.history().size());
        assertEquals("TELEPORT", accumulator.consumeResetReason());

        MotionSnapshot baseline = accept(
                accumulator, frame(2L, 10.1D, 0, 0, 0.1D, 0, 0, false), null);
        assertEquals(0.0D, baseline.horizontalAcceleration(), 1.0E-9);
        assertEquals(0.0D, baseline.displacementX(), 1.0E-9);
    }

    @Test
    void shortSampleGapUsesDeltaTicksAndResetsContinuity() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);

        MotionSnapshot snapshot = accept(
                accumulator, frame(3L, 0.3D, 0, 0, 0.3D, 0, 0, false), null);
        assertNotNull(snapshot);
        assertEquals(0.1D, snapshot.horizontalAcceleration(), 1.0E-9);
        assertEquals(0.1D, snapshot.displacementX(), 1.0E-9);
        assertEquals(0, snapshot.continuousMotionTicks());
    }

    @Test
    void duplicateTickIsIgnoredWithoutClearingHistory() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);

        assertNull(accept(accumulator, frame(0L, 10, 0, 0, 1, 0, 0, false), null));
        assertEquals(1, accumulator.history().size());
        assertNull(accumulator.consumeResetReason());

        assertNotNull(accept(accumulator, frame(1L, 0.1D, 0, 0, 0.1D, 0, 0, false), null));
    }

    @Test
    void largeSampleGapResetsBaseline() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);
        assertNull(accept(accumulator, frame(6L, 1, 0, 0, 1, 0, 0, false), null));
        assertEquals("TICK_GAP", accumulator.consumeResetReason());
    }

    @Test
    void deadFrameResetsPlayerData() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);

        MotionFrame dead = new MotionFrame(1L, new ParkourPosition(0, 0, 0),
                0, 0, 0, false, false, false, false, false, false,
                true, false, false);
        assertNull(accumulator.accept(dead, fall(false), null, MOTION_SOURCE, MOTION_TRUST));
        assertEquals(0, accumulator.history().size());
        assertEquals("DEAD", accumulator.consumeResetReason());
    }

    @Test
    void trustIsolationKeepsFallAndActionSources() {
        MotionAccumulator accumulator = new MotionAccumulator();
        FallSnapshot fall = new FallSnapshot(
                FallPhase.FALLING,
                new ParkourPosition(0, 0, 0),
                1.0D, -0.5D, 1,
                false,
                FallEndReason.UNKNOWN,
                ParkourStateSource.VANILLA_DERIVED,
                ParkourTrust.HIGH,
                0L);
        ParkourActionState action = new ImmutableParkourActionState(
                ParkourActionType.FAST_RUN,
                ParkourActionPhase.ACTIVE,
                true,
                3,
                0L,
                0L,
                ParkourStateSource.PARCOOL_SYNCED,
                ParkourTrust.LOW,
                false,
                false,
                "START");

        MotionSnapshot snapshot = accumulator.accept(
                frame(0L, 0, 0, 0, 0, 0, 0, false),
                fall, action, MOTION_SOURCE, MOTION_TRUST);

        assertEquals(MOTION_SOURCE, snapshot.source());
        assertEquals(MOTION_TRUST, snapshot.trust());
        assertEquals(ParkourStateSource.VANILLA_DERIVED, snapshot.fallSnapshot().source());
        assertEquals(ParkourTrust.HIGH, snapshot.fallSnapshot().trust());
        assertEquals(ParkourStateSource.PARCOOL_SYNCED, snapshot.actionState().source());
        assertEquals(ParkourTrust.LOW, snapshot.actionState().trust());
        assertTrue(snapshot.falling());
    }

    @Test
    void nullActionIsAllowed() {
        MotionAccumulator accumulator = new MotionAccumulator();
        MotionSnapshot snapshot = accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, true), null);
        assertNull(snapshot.actionState());
    }

    @Test
    void nonFiniteFrameIsRejected() {
        MotionAccumulator accumulator = new MotionAccumulator();
        MotionFrame invalid = new MotionFrame(0L, new ParkourPosition(0, 0, 0),
                Double.NaN, 0, 0, false, false, false, false, false, false,
                false, false, false);
        assertNull(accumulator.accept(invalid, fall(false), null, MOTION_SOURCE, MOTION_TRUST));
        assertEquals("INVALID_DATA", accumulator.consumeResetReason());
    }

    @Test
    void clearRemovesHistoryAndBaseline() {
        MotionAccumulator accumulator = new MotionAccumulator();
        accept(accumulator, frame(0L, 0, 0, 0, 0, 0, 0, false), null);
        accumulator.clear();
        assertEquals(0, accumulator.history().size());
        assertNull(accumulator.latest());
    }

    private static MotionSnapshot accept(MotionAccumulator accumulator,
                                         MotionFrame frame,
                                         ParkourActionState action) {
        return accumulator.accept(frame, fall(false), action, MOTION_SOURCE, MOTION_TRUST);
    }

    private static FallSnapshot fall(boolean falling) {
        return new FallSnapshot(
                falling ? FallPhase.FALLING : FallPhase.NOT_FALLING,
                new ParkourPosition(0, 0, 0),
                0.0D, 0.0D, 0,
                !falling,
                FallEndReason.UNKNOWN,
                ParkourStateSource.VANILLA_DERIVED,
                ParkourTrust.HIGH,
                0L);
    }

    private static MotionFrame frame(long tick,
                                     double x,
                                     double y,
                                     double z,
                                     double vx,
                                     double vy,
                                     double vz,
                                     boolean onGround) {
        return new MotionFrame(
                tick,
                new ParkourPosition(x, y, z),
                vx, vy, vz,
                onGround,
                false, false, false, false, false,
                false, false, false);
    }
}
