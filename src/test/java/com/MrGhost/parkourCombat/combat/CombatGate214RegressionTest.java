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

class CombatGate214RegressionTest {
    @Test
    void parentLateEventDoesNotReachActiveB() {
        UUID player = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        CombatAttackContext a = partContext(player, part, parent, false);
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, a);
        UUID bTarget = UUID.randomUUID();
        CombatAttackSession b = normalSession(player, 2L, bTarget);
        b.markExecuting();
        tracker.seedActiveSessionForTest(player, b);

        tracker.dispatchLivingForTest(player, parent, CombatEventType.LIVING_DAMAGE, 4.0F, false);
        assertEquals(1L, tracker.lateEventCount());
        assertTrue(tracker.recentLookupForTest(player).uuids().contains(parent));

        CombatAttackContext bContext = b.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(bContext);
        assertTrue(bContext.targetEventObservations().isEmpty());
        assertTrue(bContext.validDamageTargets().isEmpty());
        assertEquals(1, a.targetEventObservations().size());
    }

    @Test
    void requestedPartParentIsIncludedInLookup() {
        UUID player = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        CombatAttackContext a = partContext(player, part, parent, true);
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, a);
        UUID bTarget = UUID.randomUUID();
        CombatAttackSession b = normalSession(player, 2L, bTarget);
        b.markExecuting();
        tracker.seedActiveSessionForTest(player, b);

        tracker.dispatchLivingForTest(player, parent, CombatEventType.LIVING_HURT, 1.0F, false);
        assertEquals(1L, tracker.lateEventCount());
        assertTrue(b.finish(CombatAttackEndReason.NORMAL_FINISH).targetEventObservations().isEmpty());
    }

    @Test
    void repeatedPartLateCountsEachObservation() {
        UUID player = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, partContext(player, part, parent, false));

        tracker.dispatchAttackForTest(player, part, parent, true, false);
        tracker.dispatchAttackForTest(player, part, parent, true, false);
        assertEquals(2L, tracker.lateEventCount());
        assertEquals(2L, tracker.latePartEventCount());
    }

    @Test
    void noActiveSessionOnlyCountsDiagnostics() {
        UUID player = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, partContext(player, part, parent, false));

        tracker.dispatchLivingForTest(player, parent, CombatEventType.LIVING_DAMAGE, 2.0F, false);
        assertEquals(1L, tracker.lateEventCount());
        assertTrue(tracker.recentLookupForTest(player).size() > 0);
    }

    @Test
    void sameTargetConsecutiveAttackIsConservativelyLate() {
        UUID player = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, normalContext(player, target));
        CombatAttackSession b = normalSession(player, 2L, target);
        b.markExecuting();
        tracker.seedActiveSessionForTest(player, b);

        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_DAMAGE, 5.0F, false);
        assertEquals(1L, tracker.lateEventCount());
        assertTrue(b.finish(CombatAttackEndReason.NORMAL_FINISH).targetEventObservations().isEmpty());
    }

    @Test
    void truncatedLookupUsesUnknownCounterNotConfirmedLate() {
        UUID player = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        for (int i = 0; i < 8; i++) {
            Set<UUID> targets = new LinkedHashSet<>();
            for (int j = 0; j < 63; j++) {
                targets.add(UUID.randomUUID());
            }
            UUID part = UUID.randomUUID();
            UUID parent = UUID.randomUUID();
            CombatAttackSession session = session(player, i + 1L, 0L, targets,
                    Set.of(part), Map.of(part, parent), CombatTargetCoverage.PARTIAL_PART);
            session.markExecuting();
            session.observeAttackTarget(part, true, parent, false);
            tracker.seedRecentContextForTest(player, session.finish(
                    CombatAttackEndReason.NORMAL_FINISH, 0L));
        }
        assertTrue(tracker.recentLookupForTest(player).truncated());
        tracker.dispatchLivingForTest(player, UUID.randomUUID(), CombatEventType.LIVING_DAMAGE, 1.0F, false);
        assertEquals(0L, tracker.lateEventCount());
        assertEquals(1L, tracker.lateUnknownEventCount());
    }

    @Test
    void targetAndPartOverflowCountersSaturate() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        for (int i = 0; i < 64; i++) {
            registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        }
        registry.seedCounterForTest(CombatTargetRegistry.Counter.TARGET_BUDGET_OVERFLOW, Long.MAX_VALUE - 1);
        registry.seedCounterForTest(CombatTargetRegistry.Counter.PART_TARGET_OVERFLOW, Long.MAX_VALUE - 1);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, UUID.randomUUID(), false);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, UUID.randomUUID(), false);
        assertEquals(Long.MAX_VALUE, registry.targetBudgetOverflowCount());
        assertEquals(Long.MAX_VALUE, registry.partTargetOverflowCount());
        assertTrue(registry.partTargetOverflowCount() <= registry.targetBudgetOverflowCount());
    }

    @Test
    void eventDetailCountersSaturateAndTruncateOncePerTarget() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        CombatTargetState a = registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        CombatTargetState b = registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        for (int i = 0; i < 8; i++) {
            a.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        }
        registry.seedCounterForTest(CombatTargetRegistry.Counter.EVENT_DETAIL_OVERFLOW, Long.MAX_VALUE - 1);
        registry.seedCounterForTest(CombatTargetRegistry.Counter.EVENT_DETAIL_TRUNCATED_TARGET, Long.MAX_VALUE - 1);
        a.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        assertTrue(a.detailTruncated());
        registry.incrementTruncatedTargetCount();
        a.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        b.record(event(CombatEventType.LIVING_HURT), 8, registry::recordDetailOverflow);
        assertEquals(Long.MAX_VALUE, registry.eventDetailOverflowCount());
        assertEquals(Long.MAX_VALUE, registry.eventDetailTruncatedTargetCount());
        assertEquals(8, a.events().size());
    }

    @Test
    void lateCounterSaturationUsesDispatchPath() {
        UUID player = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, normalContext(player, target));
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_EVENT, Long.MAX_VALUE - 1);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_DAMAGE, 1.0F, false);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_DAMAGE, 1.0F, false);
        assertEquals(Long.MAX_VALUE, tracker.lateEventCount());
        assertEquals(0L, tracker.lateUnknownEventCount());
    }

    private static CombatEventObservation event(CombatEventType type) {
        return new CombatEventObservation(type, 1, 0, false, 1.0F, false,
                CombatDamageSourceKind.DIRECT_PLAYER_MELEE,
                CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN,
                CombatEventState.OBSERVED, false);
    }

    private static CombatAttackContext partContext(UUID player, UUID part, UUID parent,
                                                   boolean requestedOnly) {
        CombatAttackSession s = session(player, 1L, 0L, Set.of(), Set.of(part),
                Map.of(part, parent), CombatTargetCoverage.PARTIAL_PART);
        s.markExecuting();
        if (!requestedOnly) {
            s.observeAttackTarget(part, true, parent, false);
        }
        return s.finish(CombatAttackEndReason.NORMAL_FINISH, 0L);
    }

    private static CombatAttackContext normalContext(UUID player, UUID target) {
        CombatAttackSession s = normalSession(player, 1L, target);
        s.markExecuting();
        s.observeAttackTarget(target, false, null, false);
        return s.finish(CombatAttackEndReason.NORMAL_FINISH, 0L);
    }

    private static CombatAttackSession normalSession(UUID player, long id, UUID target) {
        return session(player, id, 0L, Set.of(target), Set.of(), Map.of(),
                CombatTargetCoverage.COMPLETE);
    }

    private static CombatAttackSession session(UUID player, long id, long tick, Set<UUID> targets,
                                               Set<UUID> parts, Map<UUID, UUID> parents,
                                               CombatTargetCoverage coverage) {
        return new CombatAttackSession(player, id, 0, tick,
                new CombatAttackRequest(0, false, 0, 1, new int[]{0}, false),
                null, null, false, targets, parts, parents, Set.of(), coverage);
    }
}
