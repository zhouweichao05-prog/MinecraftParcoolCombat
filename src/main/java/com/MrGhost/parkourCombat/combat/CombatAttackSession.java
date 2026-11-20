package com.MrGhost.parkourCombat.combat;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.entity.PartEntity;

/**
 * Server-thread-only session. The target registry is authoritative for every
 * target UUID and overflow; all public sets are bounded snapshots.
 */
final class CombatAttackSession {
    private static final int MAX_RECENT_SEALED_TARGETS = 8 * CombatTargetRegistry.MAX_TARGETS;
    private final UUID attackerId;
    private final long attackId;
    private final int attackTick;
    private long serverTick;
    private final CombatAttackRequest request;
    private final boolean orderingKnown;
    private final MotionSnapshot motionSnapshot;
    private final CombatWeaponSnapshot weaponSnapshot;
    private final CombatTargetRegistry registry = new CombatTargetRegistry();
    private final List<CombatRequestedTarget> requestedResolutions = new ArrayList<>();
    private final Set<Integer> unresolvedRequestedEntityIds = new LinkedHashSet<>();
    private final Set<UUID> recentSealedTargets;

    private boolean observedPartUnmatched;
    private boolean requestedTargetsComplete;
    private CombatTargetCoverage targetCoverage = CombatTargetCoverage.UNKNOWN;
    private boolean requestedTargetsHaveDuplicates;
    private int requestedTargetsDuplicateCount;
    private boolean targetBudgetOverflowed;
    private boolean attackEntityCanceled;
    private boolean livingAttackCanceled;
    private boolean livingHurtCanceled;
    private boolean livingDamageCanceled;
    private boolean originalHandlerInvoked;
    private boolean originalHandlerThrew;
    private boolean taskOutcomeUnknown;
    private int lateEventCount;
    private boolean sealed;
    private CombatAttackPhase phase = CombatAttackPhase.REQUESTED;

    CombatAttackSession(UUID attackerId, long attackId, int attackTick, long serverTick,
                        CombatAttackRequest request, MotionSnapshot motionSnapshot,
                        CombatWeaponSnapshot weaponSnapshot, boolean orderingKnown) {
        this(attackerId, attackId, attackTick, serverTick, request, motionSnapshot,
                weaponSnapshot, orderingKnown, Set.of(), Set.of(), Map.of(), Set.of(),
                CombatTargetCoverage.UNKNOWN);
    }

    CombatAttackSession(UUID attackerId, long attackId, int attackTick, long serverTick,
                        CombatAttackRequest request, MotionSnapshot motionSnapshot,
                        CombatWeaponSnapshot weaponSnapshot, boolean orderingKnown,
                        Set<UUID> recentSealedTargets) {
        this(attackerId, attackId, attackTick, serverTick, request, motionSnapshot,
                weaponSnapshot, orderingKnown, Set.of(), Set.of(), Map.of(), Set.of(),
                CombatTargetCoverage.UNKNOWN);
        for (UUID target : recentSealedTargets) {
            if (this.recentSealedTargets.size() >= MAX_RECENT_SEALED_TARGETS) {
                break;
            }
            this.recentSealedTargets.add(target);
        }
    }

    /** Test seam: seed a bounded registry without touching Minecraft levels. */
    CombatAttackSession(UUID attackerId, long attackId, int attackTick, long serverTick,
                        CombatAttackRequest request, MotionSnapshot motionSnapshot,
                        CombatWeaponSnapshot weaponSnapshot, boolean orderingKnown,
                        Set<UUID> seededTargets, Set<UUID> seededParts,
                        Map<UUID, UUID> seededPartParents, Set<Integer> seededUnresolved,
                        CombatTargetCoverage seededCoverage) {
        this.attackerId = attackerId;
        this.attackId = attackId;
        this.attackTick = attackTick;
        this.serverTick = serverTick;
        this.request = request;
        this.motionSnapshot = motionSnapshot;
        this.weaponSnapshot = weaponSnapshot;
        this.orderingKnown = orderingKnown;
        this.recentSealedTargets = new LinkedHashSet<>();
        for (UUID target : seededTargets) {
            registry.admit(target, CombatTargetState.Kind.NORMAL, null, true);
        }
        for (UUID part : seededParts) {
            registry.admit(part, CombatTargetState.Kind.PART, seededPartParents.get(part), true);
        }
        unresolvedRequestedEntityIds.addAll(seededUnresolved);
        this.targetCoverage = seededCoverage;
        this.requestedTargetsComplete = seededCoverage == CombatTargetCoverage.COMPLETE;
        this.targetBudgetOverflowed = registry.targetBudgetOverflowCount() > 0;
    }

    void seedRecentSealedTargetsForTest(Set<UUID> targets) {
        for (UUID target : targets) {
            if (recentSealedTargets.size() >= MAX_RECENT_SEALED_TARGETS) {
                break;
            }
            recentSealedTargets.add(target);
        }
    }

    /** FOR_TEST_ONLY: seed a registry counter through the controlled registry seam. */
    void seedRegistryCounterForTest(CombatTargetRegistry.Counter counter, long value) {
        registry.seedCounterForTest(counter, value);
    }

    void prepare(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int[] rawIds = request.targetIds();
        Set<Integer> seenRaw = new HashSet<>();
        for (int rawId : rawIds) {
            if (!seenRaw.add(rawId)) {
                requestedTargetsHaveDuplicates = true;
                requestedTargetsDuplicateCount++;
                requestedResolutions.add(new CombatRequestedTarget(
                        rawId, UUID.randomUUID(), null, CombatTargetResolution.DUPLICATE_RAW_ID));
                continue;
            }
            Entity entity = level.getEntity(rawId);
            if (entity == null) {
                entity = level.getEntityOrPart(rawId);
            }
            if (entity == null) {
                unresolvedRequestedEntityIds.add(rawId);
                requestedResolutions.add(new CombatRequestedTarget(
                        rawId, UUID.randomUUID(), null, CombatTargetResolution.NOT_FOUND));
                continue;
            }
            UUID uuid = entity.getUUID();
            if (!entity.isAlive()) {
                unresolvedRequestedEntityIds.add(rawId);
                requestedResolutions.add(new CombatRequestedTarget(
                        rawId, uuid, null, CombatTargetResolution.RESOLVED_NOT_ALIVE));
                continue;
            }
            if (entity instanceof PartEntity<?> part) {
                UUID parent = part.getParent() == null ? null : part.getParent().getUUID();
                CombatTargetState state = registry.admit(uuid, CombatTargetState.Kind.PART, parent, true);
                if (state == null) {
                    targetBudgetOverflowed = true;
                } else if (parent == null) {
                    observedPartUnmatched = true;
                }
                requestedResolutions.add(new CombatRequestedTarget(
                        rawId, uuid, parent, CombatTargetResolution.PART_RESOLVED));
            } else {
                CombatTargetState state = registry.admit(uuid, CombatTargetState.Kind.NORMAL, null, true);
                if (state == null) {
                    targetBudgetOverflowed = true;
                }
                requestedResolutions.add(new CombatRequestedTarget(
                        rawId, uuid, null, CombatTargetResolution.RESOLVED_ALIVE));
            }
        }
        this.targetCoverage = computeCoverage();
        this.requestedTargetsComplete = targetCoverage == CombatTargetCoverage.COMPLETE;
        this.phase = CombatAttackPhase.PREPARED;
    }

    void markExecuting() {
        this.originalHandlerInvoked = true;
        this.phase = CombatAttackPhase.EXECUTING;
    }

    void markOriginalThrew() {
        this.originalHandlerThrew = true;
    }

    void markTaskOutcomeUnknown() {
        this.taskOutcomeUnknown = true;
    }

    void observeAttackTarget(Entity target, boolean canceled) {
        if (sealed || target == null) {
            return;
        }
        boolean part = target instanceof PartEntity<?>;
        UUID parent = part && ((PartEntity<?>) target).getParent() != null
                ? ((PartEntity<?>) target).getParent().getUUID() : null;
        observeAttackTarget(target.getUUID(), part, parent, canceled);
    }

    void observeAttackTarget(UUID targetUuid, boolean part, UUID parentUuid, boolean canceled) {
        if (sealed || targetUuid == null) {
            return;
        }
        CombatTargetState target = registry.get(targetUuid);
        if (target == null) {
            CombatTargetState parent = parentUuid == null ? null : registry.get(parentUuid);
            if (parent == null || (!parent.requested() && !parent.observed())) {
                return;
            }
            target = registry.admit(targetUuid,
                    part ? CombatTargetState.Kind.PART : CombatTargetState.Kind.NORMAL,
                    parentUuid, false);
            if (target == null) {
                targetBudgetOverflowed = true;
                targetCoverage = finalCoverage();
                requestedTargetsComplete = false;
                return;
            }
        }
        if (recentSealedTargets.contains(targetUuid)
                || (parentUuid != null && recentSealedTargets.contains(parentUuid))) {
            lateEventCount++;
            return;
        }
        target.markObserved();
        if (part) {
            target.parentUuid(parentUuid);
            if (parentUuid == null) {
                observedPartUnmatched = true;
            }
        }

        CombatTargetState.ChainState current = target.chainState();
        CombatDamageAssociation association;
        CombatEventState eventState;
        if (canceled) {
            attackEntityCanceled = true;
            target.chainState(CombatTargetState.ChainState.CANCELED);
            association = CombatDamageAssociation.ATTACK_CANCELED;
            eventState = CombatEventState.CANCELED;
        } else if (current == CombatTargetState.ChainState.CANCELED) {
            association = CombatDamageAssociation.ATTACK_CANCELED;
            eventState = CombatEventState.ASSOCIATION_UNCERTAIN;
        } else if (current == CombatTargetState.ChainState.VALID
                || current == CombatTargetState.ChainState.OVERLAP) {
            target.chainState(CombatTargetState.ChainState.OVERLAP);
            association = CombatDamageAssociation.OVERLAP_AMBIGUOUS;
            eventState = CombatEventState.ASSOCIATION_UNCERTAIN;
        } else {
            target.chainState(CombatTargetState.ChainState.VALID);
            association = CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN;
            eventState = CombatEventState.OBSERVED;
        }
        addEvent(target, CombatEventType.ATTACK_ENTITY, canceled, null, false,
                association, eventState);
        targetCoverage = finalCoverage();
        requestedTargetsComplete = targetCoverage == CombatTargetCoverage.COMPLETE;
    }

    void observeLivingEvent(LivingEntity target, CombatEventType type, float amount, boolean canceled) {
        if (target == null) {
            return;
        }
        observeLivingEvent(target.getUUID(), type, amount, canceled);
    }

    void observeLivingEvent(UUID targetUuid, CombatEventType type, float amount, boolean canceled) {
        if (sealed || targetUuid == null || type == null || type == CombatEventType.ATTACK_ENTITY) {
            return;
        }
        CombatTargetState state = findState(targetUuid);
        if (state == null) {
            if (targetBudgetOverflowed) {
                registry.recordOverflowEvent(canceled);
            }
            return;
        }
        if (recentSealedTargets.contains(targetUuid)) {
            lateEventCount++;
            return;
        }
        if (type == CombatEventType.LIVING_ATTACK && canceled) {
            livingAttackCanceled = true;
        } else if (type == CombatEventType.LIVING_HURT && canceled) {
            livingHurtCanceled = true;
        } else if (type == CombatEventType.LIVING_DAMAGE && canceled) {
            livingDamageCanceled = true;
        }

        CombatDamageAssociation association;
        if (state.chainState() == CombatTargetState.ChainState.VALID) {
            association = CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN;
        } else if (state.chainState() == CombatTargetState.ChainState.CANCELED) {
            association = CombatDamageAssociation.ATTACK_CANCELED;
        } else if (state.chainState() == CombatTargetState.ChainState.OVERLAP) {
            association = CombatDamageAssociation.OVERLAP_AMBIGUOUS;
        } else {
            association = CombatDamageAssociation.NO_ATTACK_EVENT;
        }
        boolean matched = association == CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN;
        boolean positive = matched && type == CombatEventType.LIVING_DAMAGE && !canceled && amount > 0.0F;
        CombatEventState eventState = canceled ? CombatEventState.CANCELED
                : matched ? CombatEventState.OBSERVED : CombatEventState.ASSOCIATION_UNCERTAIN;
        addEvent(state, type, canceled, amount, positive, association, eventState);
    }

    private CombatTargetState findState(UUID targetUuid) {
        CombatTargetState exact = registry.get(targetUuid);
        if (exact != null) {
            return exact;
        }
        CombatTargetState parentPart = registry.findParentOf(targetUuid);
        return parentPart;
    }

    private void addEvent(CombatTargetState state, CombatEventType type, boolean canceled,
                          Float amount, boolean positive, CombatDamageAssociation association,
                          CombatEventState eventState) {
        boolean wasTruncated = state.detailTruncated();
        boolean stored = state.record(new CombatEventObservation(
                type, state.totalEventCount() + 1, (int) Math.min(Integer.MAX_VALUE, serverTick), canceled, amount, positive,
                CombatDamageSourceKind.DIRECT_PLAYER_MELEE,
                CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK,
                association, eventState, false), CombatTargetRegistry.MAX_EVENTS_PER_TARGET,
                registry::recordDetailOverflow);
        if (!stored && !wasTruncated) {
            registry.incrementTruncatedTargetCount();
        }
    }

    CombatAttackContext finish(CombatAttackEndReason endReason) {
        return finish(endReason, serverTick);
    }

    CombatAttackContext finish(CombatAttackEndReason endReason, long finishTick) {
        if (sealed) {
            return null;
        }
        sealed = true;
        this.serverTick = finishTick;
        this.targetCoverage = finalCoverage();
        this.requestedTargetsComplete = targetCoverage == CombatTargetCoverage.COMPLETE;

        Set<UUID> requestedTargets = new LinkedHashSet<>();
        Set<UUID> requestedParts = new LinkedHashSet<>();
        Map<UUID, UUID> requestedParents = new LinkedHashMap<>();
        Set<UUID> observedParts = new LinkedHashSet<>();
        Map<UUID, UUID> observedParents = new LinkedHashMap<>();
        Set<UUID> validAttack = new LinkedHashSet<>();
        Set<UUID> validDamage = new LinkedHashSet<>();
        Map<UUID, CombatTargetEventObservation> events = new LinkedHashMap<>();
        int historicalAttackCount = 0;
        int canceledAttackCount = 0;
        int historicalDamageCount = 0;
        int canceledDamageCount = 0;
        int attackEntityCount = 0;
        int livingAttackCount = 0;
        int livingHurtCount = 0;
        int livingDamageCount = 0;
        int canceledEvents = 0;
        boolean anyMatchedDamage = false;
        boolean anyPositive = false;
        boolean anyDamage = false;

        for (CombatTargetState state : registry.targets().values()) {
            if (state.totalEventCount() > 0) {
                events.put(state.targetUuid(), state.toObservation());
            }
            if (state.requested()) {
                if (state.kind() == CombatTargetState.Kind.PART) {
                    requestedParts.add(state.targetUuid());
                    if (state.parentUuid() != null) {
                        requestedParents.put(state.targetUuid(), state.parentUuid());
                    }
                } else {
                    requestedTargets.add(state.targetUuid());
                }
            }
            if (state.kind() == CombatTargetState.Kind.PART) {
                observedParts.add(state.targetUuid());
                if (state.parentUuid() != null) {
                    observedParents.put(state.targetUuid(), state.parentUuid());
                } else {
                    observedPartUnmatched = true;
                }
            }
            if (state.attackEntityCount() > 0) {
                historicalAttackCount++;
            }
            if (state.chainState() == CombatTargetState.ChainState.VALID) {
                validAttack.add(state.targetUuid());
            }
            if (state.chainState() == CombatTargetState.ChainState.CANCELED) {
                canceledAttackCount++;
            }
            if (state.livingHurtCount() + state.livingDamageCount() > 0) {
                historicalDamageCount++;
            }
            attackEntityCount += state.attackEntityCount();
            livingAttackCount += state.livingAttackCount();
            livingHurtCount += state.livingHurtCount();
            livingDamageCount += state.livingDamageCount();
            canceledEvents += state.canceledCount();
            boolean canceledDamageTarget = false;
            for (CombatEventObservation event : state.events()) {
                if (event.canceledAtObservation()
                        && (event.eventType() == CombatEventType.LIVING_HURT
                        || event.eventType() == CombatEventType.LIVING_DAMAGE)) {
                    canceledDamageTarget = true;
                }
                if (event.eventType() == CombatEventType.LIVING_HURT
                        || event.eventType() == CombatEventType.LIVING_DAMAGE) {
                    anyDamage = true;
                    if (state.chainState() == CombatTargetState.ChainState.VALID
                            && !event.canceledAtObservation()
                            && event.association() == CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN) {
                        anyMatchedDamage = true;
                        anyPositive |= event.positiveAtObservation();
                        validDamage.add(state.targetUuid());
                    }
                }
            }
            if (canceledDamageTarget) {
                canceledDamageCount++;
            }
        }
        if (observedPartUnmatched || !observedParts.isEmpty() || !requestedParts.isEmpty()) {
            targetCoverage = finalCoverage();
            requestedTargetsComplete = targetCoverage == CombatTargetCoverage.COMPLETE;
        }

        boolean windowComplete = orderingKnown && originalHandlerInvoked && !taskOutcomeUnknown
                && !originalHandlerThrew && endReason == CombatAttackEndReason.NORMAL_FINISH
                && targetCoverage == CombatTargetCoverage.COMPLETE;
        CombatAttackResult result;
        if (request.rawTargetCount() == 0) {
            result = CombatAttackResult.NO_REQUESTED_TARGETS;
        } else if (anyMatchedDamage && anyPositive) {
            result = CombatAttackResult.DAMAGE_AMOUNT_OBSERVED_POSITIVE;
        } else if (anyMatchedDamage) {
            result = CombatAttackResult.DAMAGE_EVENT_OBSERVED;
        } else if (historicalAttackCount > 0 || anyDamage) {
            result = CombatAttackResult.TARGET_OBSERVED;
        } else if (lateEventCount > 0) {
            result = CombatAttackResult.UNKNOWN;
        } else if (windowComplete) {
            result = CombatAttackResult.NO_ATTACK_EVENT_OBSERVED;
        } else {
            result = CombatAttackResult.UNKNOWN;
        }

        phase = CombatAttackPhase.FINALIZED;
        CombatWeaponSnapshot snapshot = weaponSnapshot;
        return new CombatAttackContext(
                attackerId, attackId, attackTick, serverTick, serverTick, snapshot,
                snapshot == null ? request.comboCount() : snapshot.comboCurrent(),
                snapshot == null ? 0 : snapshot.comboTotal(),
                snapshot != null && snapshot.offHand(),
                request.rawTargetCount(), request.capturedTargetCount(), request.truncated(),
                requestedTargetsHaveDuplicates, requestedTargetsDuplicateCount,
                List.copyOf(requestedResolutions), Set.copyOf(requestedTargets),
                Set.copyOf(requestedParts), Map.copyOf(requestedParents), observedPartUnmatched,
                Set.copyOf(unresolvedRequestedEntityIds), requestedTargetsComplete, targetCoverage,
                Set.copyOf(validAttack), Set.copyOf(validDamage), Map.of(),
                isEventTruncated(), livingAttackCount, livingHurtCount, livingDamageCount,
                lateEventCount, windowComplete, true, orderingKnown,
                attackEntityCanceled, livingAttackCanceled, livingHurtCanceled, livingDamageCanceled,
                originalHandlerInvoked, originalHandlerThrew, taskOutcomeUnknown, endReason, phase,
                result, CombatAttackSource.BETTER_COMBAT, motionSnapshot, true, false,
                Set.copyOf(requestedParts), Map.copyOf(requestedParents), Set.copyOf(observedParts),
                Map.copyOf(observedParents), observedPartUnmatched, Map.copyOf(events),
                attackEntityCount, livingAttackCount, livingHurtCount, livingDamageCount,
                canceledEvents, (int) Math.min(Integer.MAX_VALUE, registry.targetBudgetOverflowCount()),
                (int) Math.min(Integer.MAX_VALUE, registry.overflowEventCount()), isEventTruncated(),
                Set.copyOf(validAttack), Set.copyOf(validDamage), historicalAttackCount,
                canceledAttackCount, historicalDamageCount, canceledDamageCount,
                registry.targetBudgetOverflowCount(), registry.partTargetOverflowCount(),
                registry.overflowEventCount(), registry.overflowCanceledEventCount(),
                registry.eventDetailOverflowCount(), registry.eventDetailTruncatedTargetCount());
    }

    private boolean isEventTruncated() {
        for (CombatTargetState state : registry.targets().values()) {
            if (state.detailTruncated()) {
                return true;
            }
        }
        return registry.overflowEventCount() > 0;
    }

    private CombatTargetCoverage finalCoverage() {
        if (request.truncated()) {
            return CombatTargetCoverage.PARTIAL_TRUNCATED;
        }
        if (targetBudgetOverflowed || registry.targetBudgetOverflowCount() > 0) {
            return CombatTargetCoverage.PARTIAL_TARGET_OVERFLOW;
        }
        if (hasPartState() || observedPartUnmatched) {
            return CombatTargetCoverage.PARTIAL_PART;
        }
        if (!unresolvedRequestedEntityIds.isEmpty()) {
            return CombatTargetCoverage.PARTIAL_UNRESOLVED;
        }
        return CombatTargetCoverage.COMPLETE;
    }

    private CombatTargetCoverage computeCoverage() {
        if (request.truncated()) {
            return CombatTargetCoverage.PARTIAL_TRUNCATED;
        }
        if (targetBudgetOverflowed) {
            return CombatTargetCoverage.PARTIAL_TARGET_OVERFLOW;
        }
        if (hasPartState() || observedPartUnmatched) {
            return CombatTargetCoverage.PARTIAL_PART;
        }
        if (!unresolvedRequestedEntityIds.isEmpty()) {
            return CombatTargetCoverage.PARTIAL_UNRESOLVED;
        }
        return CombatTargetCoverage.COMPLETE;
    }

    private boolean hasPartState() {
        for (CombatTargetState state : registry.targets().values()) {
            if (state.kind() == CombatTargetState.Kind.PART) {
                return true;
            }
        }
        return false;
    }

    long attackId() {
        return attackId;
    }

    boolean sealed() {
        return sealed;
    }

    long serverTick() {
        return serverTick;
    }

    CombatAttackPhase phase() {
        return phase;
    }

    CombatWeaponSnapshot weaponSnapshot() {
        return weaponSnapshot;
    }

    CombatAttackMatchResult matchTargetForDiagnostics(Entity target) {
        if (target == null) {
            return new CombatAttackMatchResult(CombatTargetMatch.UNMATCHED, null);
        }
        if (target instanceof PartEntity<?> part) {
            UUID uuid = part.getUUID();
            if (registry.get(uuid) != null) {
                return new CombatAttackMatchResult(CombatTargetMatch.PART_MATCH, uuid);
            }
            UUID parent = part.getParent() == null ? null : part.getParent().getUUID();
            if (parent != null && registry.get(parent) != null) {
                return new CombatAttackMatchResult(CombatTargetMatch.PARENT_MATCH, uuid);
            }
            return new CombatAttackMatchResult(CombatTargetMatch.PART_TARGET_UNMATCHED, uuid);
        }
        return registry.get(target.getUUID()) != null
                ? new CombatAttackMatchResult(CombatTargetMatch.DIRECT_MATCH, target.getUUID())
                : new CombatAttackMatchResult(CombatTargetMatch.UNMATCHED, target.getUUID());
    }

    public record CombatAttackMatchResult(CombatTargetMatch match, UUID targetUuid) {
    }
}
