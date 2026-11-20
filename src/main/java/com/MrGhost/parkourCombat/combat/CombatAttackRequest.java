package com.MrGhost.parkourCombat.combat;

import java.util.Arrays;

/**
 * Neutral, bounded representation of a Better Combat attack request.
 *
 * <p>The raw target count is retained even when only the first
 * {@link #MAX_TARGETS} ids are captured. A truncated request never claims a
 * complete target set.</p>
 */
public final class CombatAttackRequest {
    public static final int MAX_TARGETS = 64;

    private final int comboCount;
    private final boolean sneaking;
    private final int selectedSlot;
    private final int rawTargetCount;
    private final int[] targetIds;
    private final boolean truncated;

    public CombatAttackRequest(int comboCount, boolean sneaking, int selectedSlot,
                               int rawTargetCount, int[] targetIds, boolean truncated) {
        if (rawTargetCount < 0) {
            throw new IllegalArgumentException("rawTargetCount must be >= 0");
        }
        if (targetIds == null) {
            throw new IllegalArgumentException("targetIds must not be null");
        }
        if (targetIds.length > MAX_TARGETS) {
            throw new IllegalArgumentException("captured target ids exceed cap");
        }
        if (targetIds.length > rawTargetCount) {
            throw new IllegalArgumentException("captured target ids exceed raw count");
        }
        this.comboCount = comboCount;
        this.sneaking = sneaking;
        this.selectedSlot = selectedSlot;
        this.rawTargetCount = rawTargetCount;
        this.targetIds = targetIds.clone();
        this.truncated = truncated || rawTargetCount > targetIds.length;
    }

    public int comboCount() {
        return comboCount;
    }

    public boolean sneaking() {
        return sneaking;
    }

    public int selectedSlot() {
        return selectedSlot;
    }

    public int rawTargetCount() {
        return rawTargetCount;
    }

    public int[] targetIds() {
        return targetIds.clone();
    }

    public int capturedTargetCount() {
        return targetIds.length;
    }

    public boolean truncated() {
        return truncated;
    }

    public boolean hasRequestedTargets() {
        return rawTargetCount > 0;
    }

    @Override
    public String toString() {
        return "CombatAttackRequest[combo=" + comboCount
                + ", rawTargets=" + rawTargetCount
                + ", captured=" + targetIds.length
                + ", truncated=" + truncated
                + ", ids=" + Arrays.toString(targetIds) + "]";
    }
}
