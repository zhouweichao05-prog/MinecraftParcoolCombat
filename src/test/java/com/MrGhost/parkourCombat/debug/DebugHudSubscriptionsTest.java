package com.MrGhost.parkourCombat.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DebugHudSubscriptionsTest {
    @BeforeEach
    void clear() {
        DebugHudSubscriptions.clear();
    }

    @Test
    void subscribeAndUnsubscribe() {
        UUID id = UUID.randomUUID();
        assertFalse(DebugHudSubscriptions.isEnabled(id));

        DebugHudSubscriptions.set(id, true);
        assertTrue(DebugHudSubscriptions.isEnabled(id));
        assertEquals(1, DebugHudSubscriptions.size());

        DebugHudSubscriptions.set(id, false);
        assertFalse(DebugHudSubscriptions.isEnabled(id));
        assertEquals(0, DebugHudSubscriptions.size());
    }

    @Test
    void removeAndClear() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        DebugHudSubscriptions.set(first, true);
        DebugHudSubscriptions.set(second, true);

        DebugHudSubscriptions.remove(first);
        assertFalse(DebugHudSubscriptions.isEnabled(first));
        assertTrue(DebugHudSubscriptions.isEnabled(second));

        DebugHudSubscriptions.clear();
        assertEquals(0, DebugHudSubscriptions.size());
    }
}
