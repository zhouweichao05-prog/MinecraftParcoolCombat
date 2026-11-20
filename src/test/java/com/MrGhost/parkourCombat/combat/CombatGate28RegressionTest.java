package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatGate28RegressionTest {
    @Test
    void sharedPartBudgetStopsThe65thPartAndKeepsAllSetsBounded() {
        UUID parent = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(parent), Set.of(), Map.of());

        session.markExecuting();
        for (int i = 0; i < 65; i++) {
            session.observeAttackTarget(UUID.randomUUID(), true, parent, false);
        }

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertTrue(context.observedPartUuids().size() <= 64);
        assertTrue(context.targetEventObservations().size() <= 64);
        assertTrue(context.partTargetOverflowCount() >= 1);
        assertTrue(context.targetBudgetOverflowCount() >= 1);
        assertEquals(CombatTargetCoverage.PARTIAL_TARGET_OVERFLOW, context.targetCoverage());
        assertFalse(context.requestedTargetsComplete());
    }

    @Test
    void cancelAfterValidRemovesValidSetButKeepsHistory() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of());

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeAttackTarget(target, false, null, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertTrue(context.validAttackTargets().isEmpty());
        assertTrue(context.attackObservedTargets().isEmpty());
        assertEquals(1, context.canceledAttackTargetCount());
        CombatTargetEventObservation events = context.targetEventObservations().get(target);
        assertEquals(2, events.attackEntityCount());
        assertEquals(1, events.canceledCount());
    }

    @Test
    void overlapThenCancelPrioritizesCancel() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of());

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeAttackTarget(target, false, null, false);
        session.observeAttackTarget(target, false, null, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertTrue(context.validAttackTargets().isEmpty());
        assertEquals(1, context.canceledAttackTargetCount());
        assertTrue(context.attackEntityCanceled());
    }

    @Test
    void overlapRemovesValidAndNeverProducesValidChain() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of());

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 4.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertTrue(context.validAttackTargets().isEmpty());
        assertTrue(context.validDamageTargets().isEmpty());
        assertEquals(CombatDamageAssociation.OVERLAP_AMBIGUOUS,
                context.targetEventObservations().get(target).observations().get(2).association());
    }

    @Test
    void registryRejects65thTargetAndCountsPartSubset() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        UUID parent = UUID.randomUUID();
        registry.admit(parent, CombatTargetState.Kind.NORMAL, null, true);
        for (int i = 0; i < 63; i++) {
            registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, parent, false);
        }
        assertTrue(registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, parent, false) == null);
        assertEquals(64, registry.size());
        assertEquals(1, registry.targetBudgetOverflowCount());
        assertEquals(1, registry.partTargetOverflowCount());
    }

    @Test
    void serverClockUsesLongAndDeduplicatesSameRawTick() {
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.resetClockForTest(100L);
        assertEquals(0L, tracker.currentServerTickForTest());
        tracker.advanceClockForTest(101L);
        assertEquals(1L, tracker.currentServerTickForTest());
        tracker.advanceClockForTest(101L);
        assertEquals(1L, tracker.currentServerTickForTest());
        tracker.advanceClockForTest(140L);
        assertEquals(40L, tracker.currentServerTickForTest());
        tracker.advanceClockForTest(141L);
        assertEquals(41L, tracker.currentServerTickForTest());
    }

    @Test
    void unsignedRawWrapAndRegressionAreHandledWithoutNegativeTick() {
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.resetClockForTest(0xFFFFFFFFL);
        tracker.advanceClockForTest(0L);
        assertEquals(1L, tracker.currentServerTickForTest());
        assertTrue(tracker.currentServerTickForTest() >= 0L);

        tracker.resetClockForTest(1000L);
        tracker.advanceClockForTest(100L);
        assertEquals(0L, tracker.currentServerTickForTest());
        assertTrue(tracker.currentServerTickForTest() >= 0L);
    }

    @Test
    void counterSaturationDoesNotWrapNegative() {
        assertEquals(Long.MAX_VALUE,
                CombatTargetRegistry.saturatingIncrement(Long.MAX_VALUE));
    }

    private static CombatAttackSession session(Set<UUID> targets, Set<UUID> parts,
                                                Map<UUID, UUID> parents) {
        return new CombatAttackSession(UUID.randomUUID(), 1L, 0, 0L,
                new CombatAttackRequest(0, false, 0, 1, new int[]{0}, false),
                null, null, false, targets, parts, parents, Set.of(),
                CombatTargetCoverage.COMPLETE);
    }
}
