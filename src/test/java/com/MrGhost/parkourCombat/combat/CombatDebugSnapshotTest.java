package com.MrGhost.parkourCombat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MrGhost.parkourCombat.network.ClientboundCombatDebugSnapshot;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class CombatDebugSnapshotTest {
    @Test
    void combatSnapshotRoundTrips() {
        ClientboundCombatDebugSnapshot snapshot = new ClientboundCombatDebugSnapshot(
                true, 125L, "FINALIZED", "TARGET_OBSERVED", 2, 3,
                "minecraft:iron_sword", "FAST_RUN", "ACTIVE",
                0.42D, -0.1D, 120L, 121L, "PARTIAL_PART",
                2, 1, 1, 0, 0, 0,
                false, false, true, false, "DEGRADED_OR_UNKNOWN",
                "UNKNOWN_PREEXISTING");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundCombatDebugSnapshot.encode(snapshot, buffer);
        ClientboundCombatDebugSnapshot decoded = ClientboundCombatDebugSnapshot.decode(buffer);
        assertTrue(decoded.hasContext());
        assertEquals(125L, decoded.attackId());
        assertEquals("FAST_RUN", decoded.actionType());
        assertEquals(2, decoded.requestedTargets());
        assertEquals(1, decoded.observedTargets());
        assertFalse(decoded.healthReductionVerified());
    }

    @Test
    void emptyCombatSnapshotRoundTripsWithoutContext() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundCombatDebugSnapshot.encode(ClientboundCombatDebugSnapshot.empty(), buffer);
        assertFalse(ClientboundCombatDebugSnapshot.decode(buffer).hasContext());
    }

    @Test
    void negativeCountIsRejected() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeBoolean(true);
        buffer.writeLong(1L);
        buffer.writeUtf("FINALIZED", 64);
        buffer.writeUtf("TARGET_OBSERVED", 64);
        buffer.writeVarInt(-1);
        assertThrows(DecoderException.class, () -> ClientboundCombatDebugSnapshot.decode(buffer));
    }
}
