package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CombatAttackRequestTest {
    @Test
    void capturesBoundedPrefixAndKeepsRawCount() {
        int[] ids = new int[64];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1;
        }
        CombatAttackRequest request = new CombatAttackRequest(3, true, 1, 100, ids, true);

        assertEquals(100, request.rawTargetCount());
        assertEquals(64, request.capturedTargetCount());
        assertTrue(request.truncated());
        assertArrayEquals(ids, request.targetIds());
    }

    @Test
    void targetIdsAreDefensivelyCopied() {
        int[] ids = {10, 11};
        CombatAttackRequest request = new CombatAttackRequest(0, false, 0, 2, ids, false);
        ids[0] = 99;

        int[] first = request.targetIds();
        first[0] = 77;
        assertArrayEquals(new int[]{10, 11}, request.targetIds());
        assertNotSame(first, request.targetIds());
    }

    @Test
    void rejectsOversizedCaptureAndCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new CombatAttackRequest(0, false, 0, 2, new int[65], false));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatAttackRequest(0, false, 0, -1, new int[0], false));
        assertThrows(IllegalArgumentException.class,
                () -> new CombatAttackRequest(0, false, 0, 1, new int[2], false));
    }

    @Test
    void idGeneratorProducesUniqueMonotonicIds() {
        Set<Long> seen = new HashSet<>();
        long previous = 0;
        for (int i = 0; i < 100; i++) {
            long id = CombatAttackIdGenerator.nextId();
            assertTrue(id > previous);
            assertTrue(seen.add(id));
            previous = id;
        }
        assertFalse(seen.isEmpty());
    }
}
