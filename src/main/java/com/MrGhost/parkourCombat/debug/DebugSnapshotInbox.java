package com.MrGhost.parkourCombat.debug;

import com.MrGhost.parkourCombat.network.ClientboundCombatDebugSnapshot;
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;

/**
 * Common-side single-slot inbox for S2C debug packets.
 *
 * <p>Only the latest packet is retained. No unbounded queue is created, and no
 * client-only class is referenced from the common packet handler.</p>
 */
public final class DebugSnapshotInbox {
    private static ClientboundMotionDebugSnapshot latest;
    private static ClientboundCombatDebugSnapshot latestCombat;

    private DebugSnapshotInbox() {
    }

    public static void offer(ClientboundMotionDebugSnapshot snapshot) {
        latest = snapshot;
    }

    public static ClientboundMotionDebugSnapshot poll() {
        ClientboundMotionDebugSnapshot current = latest;
        latest = null;
        return current;
    }

    public static void clear() {
        latest = null;
        latestCombat = null;
    }

    public static void offerCombat(ClientboundCombatDebugSnapshot snapshot) {
        latestCombat = snapshot;
    }

    public static ClientboundCombatDebugSnapshot pollCombat() {
        ClientboundCombatDebugSnapshot current = latestCombat;
        latestCombat = null;
        return current;
    }

    public static boolean isEmpty() {
        return latest == null;
    }
}
