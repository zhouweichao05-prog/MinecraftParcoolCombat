package com.MrGhost.parkourCombat;

import net.minecraftforge.fml.common.Mod;

/**
 * ParkourCombat 2.0 entry point.
 *
 * <p>Scaffold stage only: no parkour adapter, no combat logic, no Mixin.
 * The next stage will add the read-only ParCool adapter under
 * {@code com.MrGhost.parkourCombat.adapter}.</p>
 */
@Mod(ParkourCombat.MOD_ID)
public final class ParkourCombat {
    public static final String MOD_ID = "parkourcombat";

    public ParkourCombat() {
        // Intentionally empty: scaffold stage.
    }
}
