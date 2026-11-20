package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CombatAttackSessionRemediationTest {
    private static final int RAW_ONE_TARGET = 1;

    @Test
    void sweepSecondaryTargetDoesNotEnterPrimaryTarget() {
        UUID primary = UUID.randomUUID();
        UUID secondary = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(primary), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(primary, false, null, false);
        session.observeLivingEvent(primary, CombatEventType.LIVING_DAMAGE, 2.0F, false);
        session.observeLivingEvent(secondary, CombatEventType.LIVING_HURT, 1.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertFalse(context.targetEventObservations().containsKey(secondary));
        assertTrue(context.targetEventObservations().containsKey(primary));
    }

    @Test
    void otherModSameSourceRemainsAmbiguous() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 1.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        for (CombatEventObservation event : observation.observations()) {
            assertEquals(CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                    event.sourceClassification());
            assertFalse(event.betterCombatExclusiveProven());
        }
    }

    @Test
    void differentSessionTargetsDoNotCrossContaminate() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        CombatAttackSession firstSession = session(Set.of(first), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);
        CombatAttackSession secondSession = session(Set.of(second), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        firstSession.markExecuting();
        secondSession.markExecuting();
        firstSession.observeAttackTarget(first, false, null, false);
        firstSession.observeLivingEvent(first, CombatEventType.LIVING_DAMAGE, 2.0F, false);
        secondSession.observeLivingEvent(second, CombatEventType.LIVING_DAMAGE, 3.0F, false);

        CombatAttackContext firstContext = firstSession.finish(CombatAttackEndReason.NORMAL_FINISH);
        CombatAttackContext secondContext = secondSession.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(firstContext);
        assertNotNull(secondContext);
        assertTrue(firstContext.targetEventObservations().containsKey(first));
        assertFalse(firstContext.targetEventObservations().containsKey(second));
        assertFalse(secondContext.targetEventObservations().containsKey(first));
        assertEquals(CombatDamageAssociation.NO_ATTACK_EVENT,
                secondContext.targetEventObservations().get(second).observations().get(0).association());
    }

    @Test
    void canceledAttackWithoutDamageRemainsObservationOnly() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        assertEquals(CombatEventAggregateState.OBSERVED_CANCELED, observation.aggregateState());
        assertFalse(observation.positiveObserved());
    }

    @Test
    void livingAttackCancellationDoesNotProducePositiveDamage() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_ATTACK, 2.0F, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.livingAttackCanceled());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
    }

    @Test
    void livingDamageCancellationDoesNotProducePositiveDamage() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 4.0F, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.livingDamageCanceled());
        assertFalse(context.targetEventObservations().get(target).positiveObserved());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
    }

    @Test
    void invulnerabilityOnlyRecordsObservation() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_ATTACK, 0.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
        assertFalse(context.targetEventObservations().get(target).positiveObserved());
    }

    @Test
    void partAndNormalTargetsStayIndependentAndCoveragePartial() {
        UUID normal = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        Map<UUID, UUID> parents = Map.of(part, parent);
        CombatAttackSession session = session(Set.of(normal, parent), Set.of(part), parents,
                Set.of(), CombatTargetCoverage.PARTIAL_PART);

        session.markExecuting();
        session.observeAttackTarget(normal, false, null, false);
        session.observeLivingEvent(normal, CombatEventType.LIVING_DAMAGE, 2.0F, false);
        session.observeAttackTarget(part, true, parent, false);
        session.observeLivingEvent(parent, CombatEventType.LIVING_DAMAGE, 3.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(CombatTargetCoverage.PARTIAL_PART, context.targetCoverage());
        assertTrue(context.targetEventObservations().containsKey(normal));
        assertTrue(context.targetEventObservations().containsKey(part));
    }

    @Test
    void partParentMatchLowersCoverageAndPreservesObservedPart() {
        UUID parent = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(parent), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(part, true, parent, false);
        session.observeLivingEvent(parent, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(CombatTargetCoverage.PARTIAL_PART, context.targetCoverage());
        assertFalse(context.requestedTargetsComplete());
        assertTrue(context.observedPartUuids().contains(part));
        assertEquals(parent, context.observedPartParentUuids().get(part));
        assertTrue(context.requestedPartUuids().isEmpty());
    }

    @Test
    void damageWithoutAttackEntityNeverBecomesValidChain() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 3.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        assertEquals(1, observation.livingDamageCount());
        assertEquals(CombatDamageAssociation.NO_ATTACK_EVENT,
                observation.observations().get(0).association());
        assertFalse(observation.positiveObserved());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
    }

    @Test
    void requestedPartAndObservedPartRemainSeparated() {
        UUID parent = UUID.randomUUID();
        UUID part = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(), Set.of(part), Map.of(part, parent),
                Set.of(), CombatTargetCoverage.PARTIAL_PART);

        session.markExecuting();
        session.observeAttackTarget(part, true, parent, false);
        session.observeLivingEvent(parent, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.requestedPartUuids().contains(part));
        assertTrue(context.observedPartUuids().contains(part));
        assertEquals(parent, context.requestedPartParentUuids().get(part));
        assertEquals(parent, context.observedPartParentUuids().get(part));
        assertEquals(CombatTargetCoverage.PARTIAL_PART, context.targetCoverage());
        assertFalse(context.requestedTargetsComplete());
    }

    @Test
    void partWithoutParentStaysUnmatchedAndPartial() {
        UUID part = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(), Set.of(part), Map.of(),
                Set.of(), CombatTargetCoverage.PARTIAL_PART);

        session.markExecuting();
        session.observeAttackTarget(part, true, null, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.observedPartUnmatched());
        assertEquals(CombatTargetCoverage.PARTIAL_PART, context.targetCoverage());
        assertFalse(context.requestedTargetsComplete());
        assertTrue(context.observedPartUuids().contains(part));
    }

    @Test
    void uncanceledThenCanceledEventKeepsBothAndAggregateMixed() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 2.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 3.0F, true);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        assertEquals(3, observation.observations().size());
        assertEquals(1, observation.canceledCount());
        assertEquals(CombatEventAggregateState.MIXED, observation.aggregateState());
        assertFalse(observation.positiveObserved());
    }

    @Test
    void orderingUnknownFullChainIsNotAnimationCandidate() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE, false);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_ATTACK, 2.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 2.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertFalse(context.orderingKnown());
        assertFalse(context.observationWindowComplete());
        assertFalse(context.parkourSpecialAnimationCandidate());
    }

    @Test
    void knownOrderingFullChainIsOnlyAnimationCandidate() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE, true);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_ATTACK, 2.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 2.0F, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.parkourSpecialAnimationCandidate());
        assertEquals(CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                context.targetEventObservations().get(target).observations().get(1).sourceClassification());
    }

    @Test
    void canceledAttackEntityCannotBeReactivated() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, true);
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 4.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.attackEntityCanceled());
        assertTrue(context.attackObservedTargets().isEmpty());
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        assertEquals(2, observation.attackEntityCount());
        assertEquals(1, observation.canceledCount());
        assertFalse(observation.positiveObserved());
        assertEquals(CombatAttackResult.TARGET_OBSERVED, context.result());
    }

    @Test
    void multipleEventsAreBoundedAndCanceledCountsRemain() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        for (int i = 0; i < 9; i++) {
            session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 1.0F, i == 8);
        }

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        assertEquals(10, observation.totalEventCount());
        assertEquals(8, observation.observations().size());
        assertEquals(9, observation.livingHurtCount());
        assertEquals(1, observation.canceledCount());
        assertTrue(observation.truncated());
        assertFalse(observation.positiveObserved());
    }

    @Test
    void recentSealedTargetIsIgnoredByNewSession() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);
        session.seedRecentSealedTargetsForTest(Set.of(target));

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 5.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertEquals(2, context.lateEventCount());
        assertTrue(context.targetEventObservations().isEmpty());
        assertEquals(CombatAttackResult.UNKNOWN, context.result());
    }

    @Test
    void sourceClassificationIsAlwaysAmbiguousAndExclusiveProofIsFalse() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(target, false, null, false);
        session.observeLivingEvent(target, CombatEventType.LIVING_DAMAGE, 2.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        CombatTargetEventObservation observation = context.targetEventObservations().get(target);
        assertNotNull(observation);
        for (CombatEventObservation event : observation.observations()) {
            assertEquals(CombatDamageSourceKind.DIRECT_PLAYER_MELEE, event.sourceKind());
            assertEquals(CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                    event.sourceClassification());
            assertFalse(event.betterCombatExclusiveProven());
        }
    }

    @Test
    void targetObservationLimitIsIndependentFromRequestedTargets() {
        Set<UUID> targets = new java.util.LinkedHashSet<>();
        for (int i = 0; i < 65; i++) {
            targets.add(UUID.randomUUID());
        }
        CombatAttackSession session = session(targets, Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        for (UUID target : targets) {
            session.observeLivingEvent(target, CombatEventType.LIVING_HURT, 1.0F, false);
        }

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.targetEventObservations().size() <= 64);
        assertEquals(1, context.damageTargetOverflowCount());
    }

    @Test
    void nonMatchingTargetsDoNotEnterSession() {
        UUID requested = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(requested), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);

        session.markExecuting();
        session.observeAttackTarget(unrelated, false, null, false);
        session.observeLivingEvent(unrelated, CombatEventType.LIVING_HURT, 1.0F, false);

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.targetEventObservations().isEmpty());
        assertEquals(CombatAttackResult.NO_ATTACK_EVENT_OBSERVED, context.result());
    }

    @Test
    void sealedFinishReturnsNullAndDoesNotMutateContext() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE);
        session.markExecuting();
        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertTrue(context.observationWindowSealed());
        assertNull(session.finish(CombatAttackEndReason.NORMAL_FINISH));
    }

    @Test
    void noEventsWithUnknownOrderingRemainsUnknown() {
        UUID target = UUID.randomUUID();
        CombatAttackSession session = session(Set.of(target), Set.of(), Map.of(),
                Set.of(), CombatTargetCoverage.COMPLETE, false);
        session.markExecuting();

        CombatAttackContext context = session.finish(CombatAttackEndReason.NORMAL_FINISH);
        assertNotNull(context);
        assertFalse(context.observationWindowComplete());
        assertEquals(CombatAttackResult.UNKNOWN, context.result());
    }

    @Test
    void targetEventObservationRejectsMoreThanEightDetails() {
        UUID target = UUID.randomUUID();
        List<CombatEventObservation> events = List.of(
                event(CombatEventType.LIVING_HURT, 1),
                event(CombatEventType.LIVING_HURT, 2),
                event(CombatEventType.LIVING_HURT, 3),
                event(CombatEventType.LIVING_HURT, 4),
                event(CombatEventType.LIVING_HURT, 5),
                event(CombatEventType.LIVING_HURT, 6),
                event(CombatEventType.LIVING_HURT, 7),
                event(CombatEventType.LIVING_HURT, 8),
                event(CombatEventType.LIVING_HURT, 9)
        );
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new CombatTargetEventObservation(target, events, 9, 0, 0,
                        9, 0, 0, false, CombatEventAggregateState.OBSERVED_VALID));
    }

    private static CombatEventObservation event(CombatEventType type, int ordinal) {
        return new CombatEventObservation(type, ordinal, 1, false, 1.0F, true,
                CombatDamageSourceKind.DIRECT_PLAYER_MELEE,
                CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN,
                CombatEventState.OBSERVED, false);
    }

    private static CombatAttackSession session(Set<UUID> targets, Set<UUID> parts,
                                               Map<UUID, UUID> parents, Set<Integer> unresolved,
                                               CombatTargetCoverage coverage) {
        return session(targets, parts, parents, unresolved, coverage, true);
    }

    private static CombatAttackSession session(Set<UUID> targets, Set<UUID> parts,
                                               Map<UUID, UUID> parents, Set<Integer> unresolved,
                                               CombatTargetCoverage coverage, boolean orderingKnown) {
        return new CombatAttackSession(UUID.randomUUID(), 1L, 0, 0,
                new CombatAttackRequest(0, false, 0, RAW_ONE_TARGET,
                        new int[]{0}, false),
                null, null, orderingKnown, targets, parts, parents, unresolved, coverage);
    }
}
