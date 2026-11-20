package com.MrGhost.parkourCombat.combat;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable weapon summary. It deliberately does not retain an ItemStack.
 */
public record CombatWeaponSnapshot(
        ResourceLocation itemId,
        int count,
        String category,
        boolean twoHanded,
        int comboCurrent,
        int comboTotal,
        String animation,
        double attackRange,
        double damageMultiplier,
        String hitbox,
        double angle,
        boolean offHand,
        boolean hasCustomData
) {
}
