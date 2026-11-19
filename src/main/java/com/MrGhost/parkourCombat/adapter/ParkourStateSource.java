package com.MrGhost.parkourCombat.adapter;

/**
 * Where an action state came from.
 *
 * <p>{@link #PARCOOL_SYNCED} is server-side state reconstructed from ParCool's
 * client synchronisation packet. It is not an independent server simulation and
 * must never be treated as a high-trust authority.</p>
 */
public enum ParkourStateSource {
    SERVER_NATIVE,
    PARCOOL_SYNCED,
    CLIENT_LOCAL,
    VANILLA_DERIVED
}
