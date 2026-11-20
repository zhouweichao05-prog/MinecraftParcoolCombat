package com.MrGhost.parkourCombat.compat.bettercombat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MrGhost.parkourCombat.combat.CombatAttackRequest;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class BetterCombatAttackAdapterTest {
    @Test
    void decodesBoundedRequestAndPreservesRawCount() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeInt(7);
        buffer.writeBoolean(true);
        buffer.writeInt(4);
        buffer.writeVarInt(70);
        for (int i = 0; i < 70; i++) {
            buffer.writeVarInt(1000 + i);
        }

        CombatAttackRequest request = BetterCombatAttackAdapter.decodeRequest(buffer);

        assertEquals(7, request.comboCount());
        assertTrue(request.sneaking());
        assertEquals(4, request.selectedSlot());
        assertEquals(70, request.rawTargetCount());
        assertEquals(64, request.capturedTargetCount());
        assertTrue(request.truncated());
        assertEquals(1000, request.targetIds()[0]);
        assertEquals(1063, request.targetIds()[63]);
    }

    @Test
    void decodesEmptyRequest() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeInt(0);
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        buffer.writeVarInt(0);

        CombatAttackRequest request = BetterCombatAttackAdapter.decodeRequest(buffer);

        assertEquals(0, request.rawTargetCount());
        assertEquals(0, request.capturedTargetCount());
        assertFalse(request.truncated());
    }

    @Test
    void rejectsCountLargerThanRemainingBytes() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeInt(0);
        buffer.writeBoolean(false);
        buffer.writeInt(0);
        buffer.writeVarInt(1000);

        assertNull(BetterCombatAttackAdapter.decodeRequest(buffer));
    }
}
