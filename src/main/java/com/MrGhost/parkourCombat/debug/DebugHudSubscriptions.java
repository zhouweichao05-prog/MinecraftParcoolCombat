package com.MrGhost.parkourCombat.debug;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side debug HUD subscription state. Main-thread only.
 */
public final class DebugHudSubscriptions {
    private static final Set<UUID> SUBSCRIBED = new HashSet<>();

    private DebugHudSubscriptions() {
    }

    public static void set(UUID playerId, boolean enabled) {
        if (playerId == null) {
            return;
        }
        if (enabled) {
            SUBSCRIBED.add(playerId);
        } else {
            SUBSCRIBED.remove(playerId);
        }
    }

    public static boolean isEnabled(UUID playerId) {
        return playerId != null && SUBSCRIBED.contains(playerId);
    }

    public static void remove(UUID playerId) {
        if (playerId != null) {
            SUBSCRIBED.remove(playerId);
        }
    }

    public static int size() {
        return SUBSCRIBED.size();
    }

    public static void clear() {
        SUBSCRIBED.clear();
    }
}
