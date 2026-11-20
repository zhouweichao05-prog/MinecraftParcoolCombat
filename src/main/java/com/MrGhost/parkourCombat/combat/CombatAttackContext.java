package com.MrGhost.parkourCombat.combat;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable result of one Better Combat request observation window.
 *
 * <p>All event amounts are observations only. No field represents final
 * damage or a verified health reduction.</p>
 */
public record CombatAttackContext(
        UUID attackerId,
        long attackId,
        int attackTick,
        long serverTick,
        long sealedTick,
        CombatWeaponSnapshot weapon,
        int comboCurrent,
        int comboTotal,
        boolean offHand,
        int requestedTargetsRawCount,
        int requestedTargetsCapturedCount,
        boolean requestedTargetsTruncated,
        boolean requestedTargetsHaveDuplicates,
        int requestedTargetsDuplicateCount,
        List<CombatRequestedTarget> requestedTargetResolutions,
        Set<UUID> requestedTargets,
        Set<UUID> partTargetUuids,
        Map<UUID, UUID> partParentUuids,
        boolean partTargetUnmatched,
        Set<Integer> unresolvedRequestedEntityIds,
        boolean requestedTargetsComplete,
        CombatTargetCoverage targetCoverage,
        Set<UUID> attackObservedTargets,
        Set<UUID> damageObservedTargets,
        Map<UUID, CombatTargetDamageObservation> targetDamageObservations,
        boolean damageObservationTruncated,
        int attackEventCount,
        int hurtEventCount,
        int damageEventCount,
        int lateEventCount,
        boolean observationWindowComplete,
        boolean observationWindowSealed,
        boolean orderingKnown,
        boolean attackEntityCanceled,
        boolean livingAttackCanceled,
        boolean livingHurtCanceled,
        boolean livingDamageCanceled,
        boolean originalHandlerInvoked,
        boolean originalHandlerThrew,
        boolean taskOutcomeUnknown,
        CombatAttackEndReason endReason,
        CombatAttackPhase phase,
        CombatAttackResult result,
        CombatAttackSource source,
        MotionSnapshot motionSnapshot,
        boolean serverAuthoritative,
        boolean healthReductionVerified,
        Set<UUID> requestedPartUuids,
        Map<UUID, UUID> requestedPartParentUuids,
        Set<UUID> observedPartUuids,
        Map<UUID, UUID> observedPartParentUuids,
        boolean observedPartUnmatched,
        Map<UUID, CombatTargetEventObservation> targetEventObservations,
        int attackEntityEventCount,
        int livingAttackEventCount,
        int livingHurtEventCount,
        int livingDamageEventCount,
        int canceledEventCount,
        int damageTargetOverflowCount,
        int eventObservationOverflowCount,
        boolean eventObservationTruncated,
        Set<UUID> validAttackTargets,
        Set<UUID> validDamageTargets,
        int historicalAttackTargetCount,
        int canceledAttackTargetCount,
        int historicalDamageTargetCount,
        int canceledDamageTargetCount,
        long targetBudgetOverflowCount,
        long partTargetOverflowCount,
        long overflowEventCount,
        long overflowCanceledEventCount,
        long eventDetailOverflowCount,
        long eventDetailTruncatedTargetCount
) {
    public CombatAttackContext {
        if (attackerId == null) {
            throw new IllegalArgumentException("attackerId must not be null");
        }
        if (endReason == null || phase == null || result == null || source == null) {
            throw new IllegalArgumentException("enums must not be null");
        }
        requestedTargetResolutions = List.copyOf(requestedTargetResolutions);
        requestedTargets = Set.copyOf(requestedTargets);
        partTargetUuids = Set.copyOf(partTargetUuids);
        partParentUuids = Map.copyOf(partParentUuids);
        unresolvedRequestedEntityIds = Set.copyOf(unresolvedRequestedEntityIds);
        attackObservedTargets = Set.copyOf(attackObservedTargets);
        damageObservedTargets = Set.copyOf(damageObservedTargets);
        targetDamageObservations = Map.copyOf(targetDamageObservations);
        requestedPartUuids = Set.copyOf(requestedPartUuids);
        requestedPartParentUuids = Map.copyOf(requestedPartParentUuids);
        observedPartUuids = Set.copyOf(observedPartUuids);
        observedPartParentUuids = Map.copyOf(observedPartParentUuids);
        targetEventObservations = Map.copyOf(targetEventObservations);
        validAttackTargets = Set.copyOf(validAttackTargets);
        validDamageTargets = Set.copyOf(validDamageTargets);
    }

    /**
     * Candidate only. Ordinary Better Combat animation is not driven by this
     * method. Current production orderingKnown is false, so this is false; a
     * future known-ordering review may use the true branch for a non-damage
     * animation candidate.
     */
    public boolean parkourSpecialAnimationCandidate() {
        if (!orderingKnown || targetCoverage != CombatTargetCoverage.COMPLETE || lateEventCount > 0) {
            return false;
        }
        boolean observedAttack = false;
        for (CombatTargetEventObservation target : targetEventObservations.values()) {
            for (CombatEventObservation event : target.observations()) {
                if (event.eventState() == CombatEventState.CANCELED
                        || event.association() != CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN) {
                    return false;
                }
                if (event.eventType() == CombatEventType.ATTACK_ENTITY) {
                    observedAttack = true;
                }
            }
        }
        return observedAttack;
    }
}
