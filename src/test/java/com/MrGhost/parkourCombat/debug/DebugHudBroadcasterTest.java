package com.MrGhost.parkourCombat.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DebugHudBroadcasterTest {
    @Test
    void sendsAtMostEveryFourTicks() {
        assertFalse(DebugHudBroadcaster.shouldSend(1));
        assertFalse(DebugHudBroadcaster.shouldSend(2));
        assertFalse(DebugHudBroadcaster.shouldSend(3));
        assertTrue(DebugHudBroadcaster.shouldSend(4));
        assertTrue(DebugHudBroadcaster.shouldSend(8));
    }
}
