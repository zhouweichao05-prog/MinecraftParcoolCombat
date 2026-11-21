package com.MrGhost.parkourCombat.combat.special;

/** Display/effect calculation only; Milestone 1 does not apply these values. */
public record SpecialAttackEffects(
        double damageBonus,
        double knockback,
        int slowTicks,
        int slowAmplifier
) {
    public static final double MAX_DAMAGE_BONUS = 8.0D;
    public static final double MAX_KNOCKBACK = 1.0D;
    public static final int MAX_SLOW_TICKS = 100;
    public static final int MAX_SLOW_AMPLIFIER = 2;

    public SpecialAttackEffects {
        damageBonus = clamp(damageBonus, 0.0D, MAX_DAMAGE_BONUS);
        knockback = clamp(knockback, 0.0D, MAX_KNOCKBACK);
        slowTicks = (int) clamp(slowTicks, 0, MAX_SLOW_TICKS);
        slowAmplifier = (int) clamp(slowAmplifier, 0, MAX_SLOW_AMPLIFIER);
    }

    public static SpecialAttackEffects none() {
        return new SpecialAttackEffects(0.0D, 0.0D, 0, 0);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
