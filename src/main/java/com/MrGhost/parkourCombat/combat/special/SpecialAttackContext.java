package com.MrGhost.parkourCombat.combat.special;

import com.MrGhost.parkourCombat.adapter.ParkourActionType;

/**
 * Immutable result of one special-attack classification. It never represents
 * verified final damage.
 */
public record SpecialAttackContext(
        long attackId,
        SpecialAttackStyle style,
        ParkourActionType sourceAction,
        String sourceLabel,
        String triggerId,
        double horizontalSpeed,
        double verticalSpeed,
        double fallDistance,
        EvidenceConfidence confidence,
        SpecialAttackEffects effects
) {
    public enum EvidenceConfidence {
        HIGH,
        MEDIUM,
        LOW,
        UNKNOWN
    }

    public SpecialAttackContext {
        if (style == null || confidence == null || effects == null) {
            throw new IllegalArgumentException("style/confidence/effects required");
        }
        if (attackId < 0L) {
            throw new IllegalArgumentException("attackId must be >= 0");
        }
        horizontalSpeed = finiteNonNegative(horizontalSpeed);
        verticalSpeed = Double.isFinite(verticalSpeed) ? verticalSpeed : 0.0D;
        fallDistance = finiteNonNegative(fallDistance);
        sourceLabel = sourceLabel == null ? "" : sourceLabel;
        triggerId = triggerId == null ? "" : triggerId;
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, value) : 0.0D;
    }

    public boolean accepted() {
        return style != SpecialAttackStyle.UNKNOWN;
    }
}
