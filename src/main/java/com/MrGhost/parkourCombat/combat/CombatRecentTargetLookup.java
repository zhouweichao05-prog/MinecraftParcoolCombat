package com.MrGhost.parkourCombat.combat;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Bounded deduplicated recent-target lookup. */
final class CombatRecentTargetLookup {
    static final int MAX_UUIDS = 512;

    private final Set<UUID> uuids = new LinkedHashSet<>();
    private boolean truncated;

    void add(UUID uuid) {
        if (uuid == null || truncated) {
            return;
        }
        if (uuids.contains(uuid)) {
            return;
        }
        if (uuids.size() == MAX_UUIDS) {
            truncated = true;
            return;
        }
        uuids.add(uuid);
    }

    boolean contains(UUID uuid) {
        return uuid != null && uuids.contains(uuid);
    }

    Set<UUID> uuids() {
        return Collections.unmodifiableSet(uuids);
    }

    boolean truncated() {
        return truncated;
    }

    int size() {
        return uuids.size();
    }
}
