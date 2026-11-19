package com.MrGhost.parkourCombat.adapter;

/**
 * Wall-run data shared by the horizontal and vertical variants.
 *
 * <p>ParCool 3.4.3.3 does not expose a public wall-direction getter; therefore
 * {@code directionAvailable} is always false and no direction is fabricated.</p>
 */
public record WallRunSnapshot(
        ParkourActionType type,
        boolean active,
        int ticks,
        boolean directionAvailable
) {
}
