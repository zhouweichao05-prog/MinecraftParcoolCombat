package com.MrGhost.parkourCombat.combat;

import java.util.Optional;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional Better Combat adapter. The common bridge never references Better
 * Combat types directly.
 */
@FunctionalInterface
public interface CombatWeaponProvider {
    Optional<CombatWeaponSnapshot> snapshot(ServerPlayer player, int comboCount);
}
