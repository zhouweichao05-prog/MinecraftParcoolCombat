package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FallStateMachineTest {
    private FallStateMachine machine;

    @BeforeEach
    void setUp() {
        machine = new FallStateMachine();
    }

    @Test
    void jumpAscentDoesNotEnterFalling() {
        machine.tick(sample(false, 0.0D, 0.5D), 0L);
        assertEquals(FallPhase.NOT_FALLING, machine.phase());
    }

    @Test
    void descendingPlayerEntersFalling() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        assertEquals(FallPhase.FALLING, machine.phase());

        FallSnapshot snapshot = machine.snapshot(
                ParkourStateSource.VANILLA_DERIVED, ParkourTrust.MEDIUM);
        assertTrue(snapshot.active());
        assertEquals(2.0D, snapshot.fallDistance());
        assertEquals(-0.5D, snapshot.verticalVelocity());
    }

    @Test
    void landingEndsFall() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(sample(true, 2.0D, 0.0D), 1L);

        FallSnapshot snapshot = machine.snapshot(
                ParkourStateSource.VANILLA_DERIVED, ParkourTrust.HIGH);
        assertEquals(FallPhase.FALL_END, snapshot.phase());
        assertEquals(FallEndReason.LANDED, snapshot.endReason());
        assertTrue(snapshot.onGround());
    }

    @Test
    void waterLavaFlyingClimbingVehicleAreExcluded() {
        assertEndReason(FallEndReason.WATER,
                sample(false, 2.0D, -0.5D, true, false, false, false, false));
        machine.clear();

        assertEndReason(FallEndReason.LAVA,
                sample(false, 2.0D, -0.5D, false, true, false, false, false));
        machine.clear();

        assertEndReason(FallEndReason.FLYING,
                sample(false, 2.0D, -0.5D, false, false, true, false, false));
        machine.clear();

        assertEndReason(FallEndReason.CLIMBING,
                sample(false, 2.0D, -0.5D, false, false, false, true, false));
        machine.clear();

        assertEndReason(FallEndReason.VEHICLE,
                sample(false, 2.0D, -0.5D, false, false, false, false, true));
    }

    @Test
    void teleportDimensionAndDeathEndFall() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(new FallMotionSample(false, 2.0D, -0.5D,
                false, false, false, false, false, false, false, true,
                new ParkourPosition(0.0D, 10.0D, 0.0D)), 1L);
        assertEquals(FallEndReason.TELEPORT, currentEndReason());

        machine.clear();
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(new FallMotionSample(false, 2.0D, -0.5D,
                false, false, false, false, false, false, true, false,
                new ParkourPosition(0.0D, 10.0D, 0.0D)), 1L);
        assertEquals(FallEndReason.DIMENSION_CHANGE, currentEndReason());

        machine.clear();
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(new FallMotionSample(false, 2.0D, -0.5D,
                false, false, false, false, false, true, false, false,
                new ParkourPosition(0.0D, 10.0D, 0.0D)), 1L);
        assertEquals(FallEndReason.DEATH, currentEndReason());
    }

    @Test
    void fallEndExpiresAfterFortyTicks() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(sample(true, 2.0D, 0.0D), 1L);

        for (int i = 0; i < 40; i++) {
            machine.tick(sample(true, 2.0D, 0.0D), 2L + i);
        }
        assertEquals(FallPhase.NOT_FALLING, machine.phase());
    }

    @Test
    void newFallDuringFallEndRestarts() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(sample(true, 2.0D, 0.0D), 1L);
        machine.tick(sample(false, 3.0D, -0.7D), 2L);

        assertEquals(FallPhase.FALLING, machine.phase());
    }

    @Test
    void clearResetsMachine() {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.clear();
        assertEquals(FallPhase.NOT_FALLING, machine.phase());
        assertFalse(machine.isFalling());
    }

    private void assertEndReason(FallEndReason expected, FallMotionSample sample) {
        machine.tick(sample(false, 2.0D, -0.5D), 0L);
        machine.tick(sample, 1L);
        assertEquals(expected, currentEndReason());
    }

    private FallEndReason currentEndReason() {
        return machine.snapshot(ParkourStateSource.VANILLA_DERIVED, ParkourTrust.HIGH)
                .endReason();
    }

    private static FallMotionSample sample(boolean onGround,
                                           double fallDistance,
                                           double verticalVelocity) {
        return sample(onGround, fallDistance, verticalVelocity,
                false, false, false, false, false);
    }

    private static FallMotionSample sample(boolean onGround,
                                           double fallDistance,
                                           double verticalVelocity,
                                           boolean inWater,
                                           boolean inLava,
                                           boolean fallFlying,
                                           boolean climbing,
                                           boolean inVehicle) {
        return new FallMotionSample(
                onGround, fallDistance, verticalVelocity, inWater, inLava,
                fallFlying, climbing, inVehicle, false, false, false,
                new ParkourPosition(0.0D, 10.0D, 0.0D));
    }
}
