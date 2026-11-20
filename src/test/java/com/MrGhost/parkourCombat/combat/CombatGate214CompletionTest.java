package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatGate214CompletionTest {
    @Test
    void s01TargetBudgetOverflowSaturatesThroughAdmit() {
        CombatTargetRegistry registry = filledRegistry();
        registry.seedCounterForTest(CombatTargetRegistry.Counter.TARGET_BUDGET_OVERFLOW, Long.MAX_VALUE - 1);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        assertEquals(Long.MAX_VALUE, registry.targetBudgetOverflowCount());
    }

    @Test
    void s02PartBudgetOverflowSaturatesThroughAdmit() {
        CombatTargetRegistry registry = filledRegistry();
        registry.seedCounterForTest(CombatTargetRegistry.Counter.TARGET_BUDGET_OVERFLOW, Long.MAX_VALUE - 1);
        registry.seedCounterForTest(CombatTargetRegistry.Counter.PART_TARGET_OVERFLOW, Long.MAX_VALUE - 1);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, UUID.randomUUID(), false);
        registry.admit(UUID.randomUUID(), CombatTargetState.Kind.PART, UUID.randomUUID(), false);
        assertEquals(Long.MAX_VALUE, registry.targetBudgetOverflowCount());
        assertEquals(Long.MAX_VALUE, registry.partTargetOverflowCount());
        assertTrue(registry.partTargetOverflowCount() <= registry.targetBudgetOverflowCount());
    }

    @Test
    void s03OverflowEventSaturatesThroughRecordOverflowEvent() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        registry.seedCounterForTest(CombatTargetRegistry.Counter.OVERFLOW_EVENT, Long.MAX_VALUE - 1);
        registry.recordOverflowEvent(false);
        registry.recordOverflowEvent(false);
        assertEquals(Long.MAX_VALUE, registry.overflowEventCount());
        assertEquals(0L, registry.overflowCanceledEventCount());
    }

    @Test
    void s04OverflowCanceledSaturatesThroughRecordOverflowEvent() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        registry.seedCounterForTest(CombatTargetRegistry.Counter.OVERFLOW_EVENT, Long.MAX_VALUE - 1);
        registry.seedCounterForTest(CombatTargetRegistry.Counter.OVERFLOW_CANCELED, Long.MAX_VALUE - 1);
        registry.recordOverflowEvent(true);
        registry.recordOverflowEvent(true);
        assertEquals(Long.MAX_VALUE, registry.overflowEventCount());
        assertEquals(Long.MAX_VALUE, registry.overflowCanceledEventCount());
        assertTrue(registry.overflowCanceledEventCount() <= registry.overflowEventCount());
    }

    @Test
    void s05EventDetailOverflowSaturatesThroughTargetStateRecord() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        CombatTargetState state = registry.admit(UUID.randomUUID(),
                CombatTargetState.Kind.NORMAL, null, false);
        for (int i = 0; i < 8; i++) {
            state.record(event(), 8, registry::recordDetailOverflow);
        }
        registry.seedCounterForTest(CombatTargetRegistry.Counter.EVENT_DETAIL_OVERFLOW,
                Long.MAX_VALUE - 1);
        state.record(event(), 8, registry::recordDetailOverflow);
        state.record(event(), 8, registry::recordDetailOverflow);
        assertEquals(Long.MAX_VALUE, registry.eventDetailOverflowCount());
        assertEquals(8, state.events().size());
    }

    @Test
    void s06TruncatedTargetCounterUsesSessionAddEventProductionPath() {
        UUID player = UUID.randomUUID();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        CombatAttackSession session = session(player, 1L, Set.of(a, b), Set.of(), Map.of(),
                CombatTargetCoverage.COMPLETE);
        session.markExecuting();
        for (int i = 0; i < 8; i++) {
            session.observeLivingEvent(a, CombatEventType.LIVING_HURT, 1.0F, false);
        }
        session.observeLivingEvent(a, CombatEventType.LIVING_HURT, 1.0F, false);
        session.observeLivingEvent(a, CombatEventType.LIVING_HURT, 1.0F, false);
        session.observeLivingEvent(a, CombatEventType.LIVING_HURT, 1.0F, false);
        for (int i = 0; i < 8; i++) {
            session.observeLivingEvent(b, CombatEventType.LIVING_HURT, 1.0F, false);
        }
        session.observeLivingEvent(b, CombatEventType.LIVING_HURT, 1.0F, false);
        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertEquals(2, context.eventDetailTruncatedTargetCount());
    }

    @Test
    void s07LateEventSaturatesThroughTrackerDispatch() {
        UUID player = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, normalContext(player, target));
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_EVENT, Long.MAX_VALUE - 1);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_DAMAGE, 1.0F, false);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_DAMAGE, 1.0F, false);
        assertEquals(Long.MAX_VALUE, tracker.lateEventCount());
    }

    @Test
    void s08LatePartSaturatesThroughTrackerDispatch() {
        UUID player = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, partContext(player, part, parent));
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_EVENT, Long.MAX_VALUE - 1);
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_PART, Long.MAX_VALUE - 1);
        tracker.dispatchAttackForTest(player, part, parent, true, false);
        tracker.dispatchAttackForTest(player, part, parent, true, false);
        assertEquals(Long.MAX_VALUE, tracker.lateEventCount());
        assertEquals(Long.MAX_VALUE, tracker.latePartEventCount());
        assertTrue(tracker.latePartEventCount() <= tracker.lateEventCount());
    }

    @Test
    void s09LateCanceledSaturatesThroughTrackerDispatch() {
        UUID player = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player, normalContext(player, target));
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_EVENT, Long.MAX_VALUE - 1);
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_CANCELED, Long.MAX_VALUE - 1);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_HURT, 1.0F, true);
        tracker.dispatchLivingForTest(player, target, CombatEventType.LIVING_HURT, 1.0F, true);
        assertEquals(Long.MAX_VALUE, tracker.lateEventCount());
        assertEquals(Long.MAX_VALUE, tracker.lateCanceledEventCount());
        assertTrue(tracker.lateCanceledEventCount() <= tracker.lateEventCount());
    }

    @Test
    void s10LateUnknownSaturatesThroughTruncatedDispatch() {
        UUID player = UUID.randomUUID();
        CombatAttackTracker tracker = new CombatAttackTracker();
        for (int i = 0; i < 8; i++) {
            Set<UUID> targets = new LinkedHashSet<>();
            for (int j = 0; j < 63; j++) {
                targets.add(UUID.randomUUID());
            }
            UUID part = UUID.randomUUID();
            UUID parent = UUID.randomUUID();
            CombatAttackSession session = session(player, i + 1L, targets, Set.of(part),
                    Map.of(part, parent), CombatTargetCoverage.PARTIAL_PART);
            session.markExecuting();
            session.observeAttackTarget(part, true, parent, false);
            tracker.seedRecentContextForTest(player, session.finish(
                    CombatAttackEndReason.NORMAL_FINISH, 0L));
        }
        assertTrue(tracker.recentLookupForTest(player).truncated());
        tracker.seedLateCounterForTest(CombatAttackTracker.LateCounter.LATE_UNKNOWN, Long.MAX_VALUE - 1);
        tracker.dispatchLivingForTest(player, UUID.randomUUID(), CombatEventType.LIVING_DAMAGE, 1.0F, false);
        tracker.dispatchLivingForTest(player, UUID.randomUUID(), CombatEventType.LIVING_DAMAGE, 1.0F, false);
        assertEquals(Long.MAX_VALUE, tracker.lateUnknownEventCount());
        assertEquals(0L, tracker.lateEventCount());
    }

    @Test
    void lookup511And512AreNotTruncated() {
        CombatRecentTargetLookup lookup = new CombatRecentTargetLookup();
        for (int i = 0; i < 511; i++) {
            lookup.add(UUID.randomUUID());
        }
        assertFalse(lookup.truncated());
        assertEquals(511, lookup.size());
        lookup.add(UUID.randomUUID());
        assertFalse(lookup.truncated());
        assertEquals(512, lookup.size());
    }

    @Test
    void lookup513TruncatesWithoutStoring() {
        CombatRecentTargetLookup lookup = new CombatRecentTargetLookup();
        for (int i = 0; i < 512; i++) {
            lookup.add(UUID.randomUUID());
        }
        UUID extra = UUID.randomUUID();
        lookup.add(extra);
        assertTrue(lookup.truncated());
        assertEquals(512, lookup.size());
        assertFalse(lookup.contains(extra));
    }

    @Test
    void lookupDuplicateDoesNotConsumeCapacityOrTruncate() {
        CombatRecentTargetLookup lookup = new CombatRecentTargetLookup();
        Set<UUID> ids = new LinkedHashSet<>();
        for (int i = 0; i < 511; i++) {
            UUID id = UUID.randomUUID();
            ids.add(id);
            lookup.add(id);
        }
        UUID existing = ids.iterator().next();
        lookup.add(existing);
        assertEquals(511, lookup.size());
        assertFalse(lookup.truncated());

        UUID twelfth = UUID.randomUUID();
        lookup.add(twelfth);
        assertEquals(512, lookup.size());
        assertFalse(lookup.truncated());

        UUID thirteenth = UUID.randomUUID();
        lookup.add(thirteenth);
        assertTrue(lookup.truncated());
        assertEquals(512, lookup.size());
        assertFalse(lookup.contains(thirteenth));

        lookup.add(existing);
        assertTrue(lookup.truncated());
        assertEquals(512, lookup.size());
    }

    @Test
    void parentAndNormalUUIDShareOneLookupSlot() {
        UUID player = UUID.randomUUID();
        UUID normal = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        CombatAttackSession session = session(player, 1L, Set.of(normal), Set.of(part),
                Map.of(part, normal), CombatTargetCoverage.PARTIAL_PART);
        session.markExecuting();
        session.observeAttackTarget(part, true, normal, false);
        CombatAttackTracker tracker = new CombatAttackTracker();
        tracker.seedRecentContextForTest(player,
                session.finish(CombatAttackEndReason.NORMAL_FINISH, 0L));

        CombatRecentTargetLookup lookup = tracker.recentLookupForTest(player);
        assertEquals(2, lookup.size());
        assertTrue(lookup.contains(normal));
        assertTrue(lookup.contains(part));
        assertFalse(lookup.truncated());
    }

    private static CombatTargetRegistry filledRegistry() {
        CombatTargetRegistry registry = new CombatTargetRegistry();
        for (int i = 0; i < 64; i++) {
            registry.admit(UUID.randomUUID(), CombatTargetState.Kind.NORMAL, null, false);
        }
        return registry;
    }

    private static CombatEventObservation event() {
        return new CombatEventObservation(CombatEventType.LIVING_HURT, 1, 0, false, 1.0F, false,
                CombatDamageSourceKind.DIRECT_PLAYER_MELEE,
                CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN,
                CombatEventState.OBSERVED, false);
    }

    private static CombatAttackContext normalContext(UUID player, UUID target) {
        CombatAttackSession s = session(player, 1L, Set.of(target), Set.of(), Map.of(),
                CombatTargetCoverage.COMPLETE);
        s.markExecuting();
        s.observeAttackTarget(target, false, null, false);
        return s.finish(CombatAttackEndReason.NORMAL_FINISH, 0L);
    }

    private static CombatAttackContext partContext(UUID player, UUID part, UUID parent) {
        CombatAttackSession s = session(player, 1L, Set.of(), Set.of(part),
                Map.of(part, parent), CombatTargetCoverage.PARTIAL_PART);
        s.markExecuting();
        s.observeAttackTarget(part, true, parent, false);
        return s.finish(CombatAttackEndReason.NORMAL_FINISH, 0L);
    }

    private static CombatAttackSession session(UUID player, long id, Set<UUID> targets,
                                               Set<UUID> parts, Map<UUID, UUID> parents,
                                               CombatTargetCoverage coverage) {
        return new CombatAttackSession(player, id, 0, 0L,
                new CombatAttackRequest(0, false, 0, 1, new int[]{0}, false),
                null, null, false, targets, parts, parents, Set.of(), coverage);
    }
}
