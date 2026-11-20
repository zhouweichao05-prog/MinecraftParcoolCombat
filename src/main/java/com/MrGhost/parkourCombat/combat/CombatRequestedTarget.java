package com.MrGhost.parkourCombat.combat;

import java.util.UUID;

public record CombatRequestedTarget(
        int rawEntityId,
        UUID uuid,
        UUID parentUuid,
        CombatTargetResolution resolution
) {
}
