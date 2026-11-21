package com.MrGhost.parkourCombat.combat.special;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import com.MrGhost.parkourCombat.adapter.ParkourPosition;
import com.MrGhost.parkourCombat.adapter.ParkourStateSource;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpecialAttackClassifierTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void emptyAndNullActiveCollectionsAreSafe() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        MotionSnapshot motion = motion(0.0D, 0.0D);
        assertEquals(SpecialAttackStyle.UNKNOWN,
                SpecialAttackClassifier.classify(1L, PLAYER, null, windows, 1L, motion, 0.0D, false).style());
        assertEquals(SpecialAttackStyle.UNKNOWN,
                SpecialAttackClassifier.classify(2L, PLAYER, java.util.Set.of(), windows, 1L, motion, 0.0D, false).style());
        assertEquals(SpecialAttackStyle.UNKNOWN,
                SpecialAttackClassifier.classify(3L, PLAYER, java.util.Collections.emptySet(), windows, 1L, motion, 0.0D, false).style());
        java.util.Set<ParkourActionType> hash = new java.util.HashSet<>();
        hash.add(ParkourActionType.FAST_RUN);
        assertEquals(SpecialAttackStyle.KINETIC,
                SpecialAttackClassifier.classify(4L, PLAYER, hash, windows, 1L, motion(0.5D, 0.0D), 0.0D, false).style());
        assertEquals(SpecialAttackStyle.KINETIC,
                SpecialAttackClassifier.classify(5L, PLAYER, EnumSet.of(ParkourActionType.FAST_RUN),
                        windows, 1L, motion(0.5D, 0.0D), 0.0D, false).style());
    }

    @Test
    void canceledInterruptedAndUnknownDoNotCreateWindows() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP,
                com.MrGhost.parkourCombat.adapter.ParkourActionPhase.CANCELLED, 1L);
        windows.recordFinish(PLAYER, ParkourActionType.DODGE,
                com.MrGhost.parkourCombat.adapter.ParkourActionPhase.INTERRUPTED, 1L);
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP,
                com.MrGhost.parkourCombat.adapter.ParkourActionPhase.UNKNOWN, 1L);
        assertFalse(windows.consume(PLAYER, SpecialAttackStyle.WALL_JUMP, 2L));
        assertFalse(windows.consume(PLAYER, SpecialAttackStyle.DODGE, 2L));
    }

    @Test
    void activeFastRunClassifiesKineticWithCappedDamage() {
        SpecialAttackContext context = SpecialAttackClassifier.classify(7L, PLAYER,
                EnumSet.of(ParkourActionType.FAST_RUN), new SpecialAttackWindowTracker(), 100L,
                motion(0.8D, 0.0D), 0.0D, false);
        assertEquals(SpecialAttackStyle.KINETIC, context.style());
        assertEquals(ParkourActionType.FAST_RUN, context.sourceAction());
        assertTrue(context.effects().damageBonus() <= SpecialAttackEffects.MAX_DAMAGE_BONUS);
    }

    @Test
    void diveTakesPrecedenceOverFallAndFinishedActionDoesNotTrigger() {
        SpecialAttackContext dive = SpecialAttackClassifier.classify(1L, PLAYER,
                EnumSet.of(ParkourActionType.DIVE), new SpecialAttackWindowTracker(), 10L,
                motion(0.0D, -1.2D), 4.0D, true);
        assertEquals(SpecialAttackStyle.GRAVITY, dive.style());
        assertEquals(ParkourActionType.DIVE, dive.sourceAction());

        SpecialAttackContext finished = SpecialAttackClassifier.classify(2L, PLAYER,
                EnumSet.noneOf(ParkourActionType.class), new SpecialAttackWindowTracker(), 11L,
                motion(0.0D, -1.2D), 4.0D, true);
        assertEquals(SpecialAttackStyle.GRAVITY, finished.style());
        assertEquals("FALL", finished.sourceLabel());
    }

    @Test
    void wallJumpWindowIsTwelveTicksAndConsumesOnce() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP, 100L);
        SpecialAttackContext atEnd = SpecialAttackClassifier.classify(1L, PLAYER,
                EnumSet.noneOf(ParkourActionType.class), windows, 112L,
                motion(0.2D, 0.1D), 0.0D, false);
        assertEquals(SpecialAttackStyle.WALL_JUMP, atEnd.style());
        assertTrue(windows.consume(PLAYER, SpecialAttackStyle.WALL_JUMP, 112L));

        SpecialAttackContext after = SpecialAttackClassifier.classify(2L, PLAYER,
                EnumSet.noneOf(ParkourActionType.class), windows, 112L,
                motion(0.2D, 0.1D), 0.0D, false);
        assertFalse(after.accepted());
    }

    @Test
    void canceledOrInterruptedActionDoesNotCreateWindow() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.DODGE, 100L);
        assertTrue(windows.consume(PLAYER, SpecialAttackStyle.DODGE, 110L));
        assertFalse(windows.consume(PLAYER, SpecialAttackStyle.DODGE, 110L));
    }

    @Test
    void eightStylesCanBeClassified() {
        assertStyle(ParkourActionType.FAST_RUN, false, SpecialAttackStyle.KINETIC);
        assertStyle(ParkourActionType.DIVE, false, SpecialAttackStyle.GRAVITY);
        assertStyle(ParkourActionType.VAULT, false, SpecialAttackStyle.VAULT);
        assertStyle(ParkourActionType.WALL_RUN_HORIZONTAL, false, SpecialAttackStyle.WALL_RUN);
        assertStyle(ParkourActionType.SLIDE, false, SpecialAttackStyle.SLIDE);
        assertStyle(ParkourActionType.HANG_DOWN, false, SpecialAttackStyle.HANG_DOWN);
    }

    @Test
    void wallJumpAndDodgeWindowsBothClassify() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP, 1L);
        assertEquals(SpecialAttackStyle.WALL_JUMP,
                SpecialAttackClassifier.classify(1L, PLAYER, EnumSet.noneOf(ParkourActionType.class),
                        windows, 2L, motion(0.1D, 0.0D), 0.0D, false).style());

        windows.clear(PLAYER);
        windows.recordFinish(PLAYER, ParkourActionType.DODGE, 1L);
        assertEquals(SpecialAttackStyle.DODGE,
                SpecialAttackClassifier.classify(2L, PLAYER, EnumSet.noneOf(ParkourActionType.class),
                        windows, 2L, motion(0.1D, 0.0D), 0.0D, false).style());
    }

    @Test
    void conflictHasDeterministicPriority() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP, 1L);
        SpecialAttackContext context = SpecialAttackClassifier.classify(1L, PLAYER,
                EnumSet.of(ParkourActionType.WALL_RUN_HORIZONTAL,
                        ParkourActionType.SLIDE, ParkourActionType.FAST_RUN),
                windows, 2L, motion(0.5D, 0.0D), 0.0D, false);
        assertEquals(SpecialAttackStyle.WALL_RUN, context.style());
    }

    @Test
    void effectsAreBounded() {
        SpecialAttackEffects effects = SpecialAttackClassifier.effects(
                SpecialAttackStyle.SLIDE, 100.0D, 100.0D);
        assertTrue(effects.damageBonus() <= SpecialAttackEffects.MAX_DAMAGE_BONUS);
        assertTrue(effects.knockback() <= SpecialAttackEffects.MAX_KNOCKBACK);
        assertTrue(effects.slowTicks() <= SpecialAttackEffects.MAX_SLOW_TICKS);
        assertTrue(effects.slowAmplifier() <= SpecialAttackEffects.MAX_SLOW_AMPLIFIER);
    }

    @Test
    void windowTrackerIsBoundedAndClearable() {
        SpecialAttackWindowTracker windows = new SpecialAttackWindowTracker();
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP, 1L);
        windows.recordFinish(PLAYER, ParkourActionType.DODGE, 1L);
        windows.recordFinish(PLAYER, ParkourActionType.WALL_JUMP, 2L);
        assertEquals(1, windows.size());
        windows.clearAll();
        assertEquals(0, windows.size());
        assertFalse(windows.consume(PLAYER, SpecialAttackStyle.WALL_JUMP, 2L));
    }

    private static void assertStyle(ParkourActionType action, boolean fall,
                                    SpecialAttackStyle expected) {
        SpecialAttackContext context = SpecialAttackClassifier.classify(1L, PLAYER,
                EnumSet.of(action), new SpecialAttackWindowTracker(), 1L,
                motion(0.4D, fall ? -1.0D : 0.0D), fall ? 3.0D : 0.0D, fall);
        assertEquals(expected, context.style());
    }

    private static MotionSnapshot motion(double horizontalSpeed, double verticalSpeed) {
        return new MotionSnapshot(1L, new ParkourPosition(0, 0, 0),
                0, verticalSpeed, 0, horizontalSpeed, 1, 0,
                0, 0, 0, 0, 0, 0, false, null, null,
                ParkourStateSource.SERVER_NATIVE, ParkourTrust.HIGH);
    }
}
