package com.MrGhost.parkourCombat.combat.special;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import java.util.EnumSet;
import java.util.Set;

/** Pure classifier; it never damages an entity and never scans the world. */
public final class SpecialAttackClassifier {
    public static final double MIN_FALL_DISTANCE = 1.5D;
    public static final double MIN_DOWNWARD_SPEED = 0.05D;

    private SpecialAttackClassifier() {
    }

    public static SpecialAttackContext classify(long attackId,
                                                java.util.UUID playerId,
                                                Set<ParkourActionType> active,
                                                SpecialAttackWindowTracker windows,
                                                long now,
                                                MotionSnapshot motion,
                                                double fallDistance,
                                                boolean falling) {
        EnumSet<ParkourActionType> actions = EnumSet.noneOf(ParkourActionType.class);
        if (active != null) {
            actions.addAll(active);
        }
        Set<SpecialAttackStyle> windowStyles = windows == null
                ? Set.of() : windows.availableStyles(playerId, now);
        double hSpeed = motion == null ? 0.0D : Math.abs(motion.horizontalSpeed());
        double vSpeed = motion == null ? 0.0D : motion.velocityY();
        double safeFall = Math.max(0.0D, fallDistance);
        boolean fall = falling || (!actions.isEmpty() && actions.contains(ParkourActionType.DIVE))
                || (safeFall >= MIN_FALL_DISTANCE && vSpeed < -MIN_DOWNWARD_SPEED);

        if (actions.contains(ParkourActionType.HANG_DOWN)) {
            return context(attackId, SpecialAttackStyle.HANG_DOWN,
                    ParkourActionType.HANG_DOWN, "HANG_DOWN", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.HANG_DOWN, hSpeed, safeFall));
        }
        if (actions.contains(ParkourActionType.WALL_RUN_HORIZONTAL)
                || actions.contains(ParkourActionType.WALL_RUN_VERTICAL)
                || actions.contains(ParkourActionType.WALL_RUN)) {
            ParkourActionType source = actions.contains(ParkourActionType.WALL_RUN_HORIZONTAL)
                    ? ParkourActionType.WALL_RUN_HORIZONTAL
                    : actions.contains(ParkourActionType.WALL_RUN_VERTICAL)
                    ? ParkourActionType.WALL_RUN_VERTICAL : ParkourActionType.WALL_RUN;
            return context(attackId, SpecialAttackStyle.WALL_RUN, source,
                    "WALL_RUN", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.WALL_RUN, hSpeed, safeFall));
        }
        if (actions.contains(ParkourActionType.DIVE)
                || (fall && safeFall >= MIN_FALL_DISTANCE && vSpeed < -MIN_DOWNWARD_SPEED)) {
            ParkourActionType source = actions.contains(ParkourActionType.DIVE)
                    ? ParkourActionType.DIVE : null;
            return context(attackId, SpecialAttackStyle.GRAVITY, source,
                    source == null ? "FALL" : "DIVE", hSpeed, vSpeed, safeFall,
                    source == null ? SpecialAttackContext.EvidenceConfidence.HIGH
                            : SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.GRAVITY, hSpeed, safeFall));
        }
        if (actions.contains(ParkourActionType.VAULT)) {
            return context(attackId, SpecialAttackStyle.VAULT, ParkourActionType.VAULT,
                    "VAULT", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.VAULT, hSpeed, safeFall));
        }
        if (actions.contains(ParkourActionType.SLIDE)) {
            return context(attackId, SpecialAttackStyle.SLIDE, ParkourActionType.SLIDE,
                    "SLIDE", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.SLIDE, hSpeed, safeFall));
        }
        if (actions.contains(ParkourActionType.FAST_RUN)) {
            return context(attackId, SpecialAttackStyle.KINETIC, ParkourActionType.FAST_RUN,
                    "FAST_RUN", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.MEDIUM,
                    effects(SpecialAttackStyle.KINETIC, hSpeed, safeFall));
        }
        if (windowStyles.contains(SpecialAttackStyle.WALL_JUMP)) {
            return context(attackId, SpecialAttackStyle.WALL_JUMP, ParkourActionType.WALL_JUMP,
                    "WALL_JUMP", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.LOW,
                    effects(SpecialAttackStyle.WALL_JUMP, hSpeed, safeFall));
        }
        if (windowStyles.contains(SpecialAttackStyle.DODGE)) {
            return context(attackId, SpecialAttackStyle.DODGE, ParkourActionType.DODGE,
                    "DODGE", hSpeed, vSpeed, safeFall,
                    SpecialAttackContext.EvidenceConfidence.LOW,
                    effects(SpecialAttackStyle.DODGE, hSpeed, safeFall));
        }
        return context(attackId, SpecialAttackStyle.UNKNOWN, null, "NONE",
                hSpeed, vSpeed, safeFall, SpecialAttackContext.EvidenceConfidence.UNKNOWN,
                SpecialAttackEffects.none());
    }

    public static SpecialAttackEffects effects(SpecialAttackStyle style,
                                               double horizontalSpeed,
                                               double fallDistance) {
        double speed = Math.max(0.0D, horizontalSpeed);
        double fall = Math.max(0.0D, fallDistance);
        return switch (style) {
            case KINETIC -> new SpecialAttackEffects(speed * 2.0D, 0.15D, 0, 0);
            case GRAVITY -> new SpecialAttackEffects(fall * 1.5D + speed, 0.35D, 0, 0);
            case VAULT -> new SpecialAttackEffects(3.0D, 0.2D, 0, 0);
            case WALL_RUN -> new SpecialAttackEffects(2.5D + speed * 0.5D, 0.25D, 0, 0);
            case SLIDE -> new SpecialAttackEffects(2.0D + speed * 0.5D, 0.1D, 40, 1);
            case WALL_JUMP -> new SpecialAttackEffects(3.0D + speed * 0.5D, 0.5D, 0, 0);
            case DODGE -> new SpecialAttackEffects(2.5D + speed * 0.5D, 0.2D, 0, 0);
            case HANG_DOWN -> new SpecialAttackEffects(4.0D, 0.2D, 0, 0);
            case UNKNOWN -> SpecialAttackEffects.none();
        };
    }

    private static SpecialAttackContext context(long attackId, SpecialAttackStyle style,
                                                ParkourActionType sourceAction,
                                                String sourceLabel,
                                                double hSpeed, double vSpeed, double fall,
                                                SpecialAttackContext.EvidenceConfidence confidence,
                                                SpecialAttackEffects effects) {
        return new SpecialAttackContext(attackId, style, sourceAction, sourceLabel,
                style.name(), hSpeed, vSpeed, fall, confidence, effects);
    }

    private static boolean isServerMotion(MotionSnapshot motion) {
        return motion != null && motion.trust() == ParkourTrust.HIGH;
    }
}
