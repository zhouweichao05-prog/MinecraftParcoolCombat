package com.MrGhost.parkourCombat.combat;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe request id source. It intentionally owns no player or session
 * state and may be called from the network thread.
 */
public final class CombatAttackIdGenerator {
    private static final AtomicLong NEXT = new AtomicLong();

    private CombatAttackIdGenerator() {
    }

    public static long nextId() {
        return NEXT.incrementAndGet();
    }
}
