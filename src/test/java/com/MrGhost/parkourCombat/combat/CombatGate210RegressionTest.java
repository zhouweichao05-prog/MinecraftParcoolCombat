package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatGate210RegressionTest {
    @Test
    void priorPositiveDamageThenCancelDoesNotProduceValidDamage() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target));

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 5.0F, false);
        session.observeAttackTarget(target, false, null, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(1, context.canceledAttackTargetCount());
        assertTrue(context.validDamageTargets().isEmpty());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
        CombatTargetEventObservation events = context.targetEventObservations().get(target);
        assertEquals(3, events.totalEventCount());
        assertEquals(1, events.canceledCount());
    }

    @Test
    void priorZeroDamageThenCancelDoesNotProducePositive() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target));

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 0.0F, false);
        session.observeAttackTarget(target, false, null, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.validDamageTargets().isEmpty());
        assertFalse(context.result() == CombatAttackResult.DAMAGE_AMOUNT_OBSERVED_POSITIVE);
    }

    @Test
    void cancelingOneTargetDoesNotAffectAnotherValidTarget() {
        UUID canceled = UUID.randomUUID();
        UUID valid = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(canceled, valid));

        session.markExecuting();
        session.observeAttackTarget(canceled, false, null, false);
        session.observeLivingEvent(canceled, CombatEventType.LIVING_DAMAGE, 5.0F, false);
        session.observeAttackTarget(canceled, false, null, true);
        session.observeAttackTarget(valid, false, null, false);
        session.observeLivingEvent(valid, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertFalse(context.validDamageTargets().contains(canceled));
        assertTrue(context.validDamageTargets().contains(valid));
        assertEquals(CombatAttackResult.DAMAGE_AMOUNT_OBSERVED_POSITIVE, context.result());
    }

    @Test
    void canceledChainCannotBeReactivated() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target));

        session.markExecuting();
        session.observeAttackTarget(target, false, null, true);
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 4.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.validAttackTargets().isEmpty());
        assertTrue(context.validDamageTargets().isEmpty());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
    }

    @Test
    void eventDetailAndTargetOverflowCountersAreIndependent() {
        Set<UUID> targets = new LinkedHashSet<>();
        for (int i = 0; i < 65; i++) {
            targets.add(UUID.randomUUID());
        }
        CombatAttackSession session = session(targets);
        session.markExecuting();
        for (UUID target : targets) {
            session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 1.0F, false);
        }

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(1, context.targetBudgetOverflowCount());
        assertEquals(0, context.partTargetOverflowCount());
        assertEquals(1, context.overflowEventCount());
        assertEquals(0, context.overflowCanceledEventCount());
        assertTrue(context.targetCoverage() == CombatTargetCoverage.PARTIAL_TARGET_OVERFLOW);
    }

    @Test
    void canceledOverflowEventIncrementsCanceledSubset() {
        Set<UUID> targets = new LinkedHashSet<>();
        for (int i = 0; i < 65; i++) {
            targets.add(UUID.randomUUID());
        }
        CombatAttackSession session = session(targets);
        session.markExecuting();
        UUID last = targets.stream().reduce((a, b) -> b).orElseThrow();
        session.observeLivingEvent(last, CombatEventType.LIVING_DAMAGE, 1.0F, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(1, context.overflowEventCount());
        assertEquals(1, context.overflowCanceledEventCount());
    }

    @Test
    void trackerLateCountersAreSubsetsAndReset() {
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.resetForServerStart(null);
        tracker.recordLateDiagnosticForTest(false, false);
        tracker.recordLateDiagnosticForTest(true, false);
        tracker.recordLateDiagnosticForTest(true, true);
        assertEquals(3L, tracker.lateEventCount());
        assertEquals(2L, tracker.latePartEventCount());
        assertEquals(1L, tracker.lateCanceledEventCount());
        assertTrue(tracker.latePartEventCount() <= tracker.lateEventCount());
        assertTrue(tracker.lateCanceledEventCount() <= tracker.lateEventCount());

        tracker.clearAll("TEST");
        assertEquals(0L, tracker.lateEventCount());
        assertEquals(0L, tracker.latePartEventCount());
        assertEquals(0L, tracker.lateCanceledEventCount());
    }

    private static CombatAttackSession session(Set<UUID> targets) {
        return new CombatAttackSession(UUID.randomUUID(), 1L, 0, 0L,
                new CombatAttackRequest(0, false, 0, 1, new int[]{0}, false),
                null, null, false, targets, Set.of(), Map.of(), Set.of(),
                CombatTargetCoverage.COMPLETE);
    }
}
