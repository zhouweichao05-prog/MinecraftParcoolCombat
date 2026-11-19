package com.MrGhost.parkourCombat.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.MrGhost.parkourCombat.adapter.FallEndReason;
import com.MrGhost.parkourCombat.adapter.FallPhase;
import com.MrGhost.parkourCombat.adapter.FallSnapshot;
import com.MrGhost.parkourCombat.adapter.ImmutableParkourActionState;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionPhase;
import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import com.MrGhost.parkourCombat.adapter.ParkourPosition;
import com.MrGhost.parkourCombat.adapter.ParkourStateSource;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class DebugNetworkCodecTest {
    @Test
    void subscriptionRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ServerboundDebugHudSubscription.encode(new ServerboundDebugHudSubscription(true), buffer);
        assertTrue(ServerboundDebugHudSubscription.decode(buffer).enabled());
    }

    @Test
    void emptySnapshotRoundTrip() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(ClientboundMotionDebugSnapshot.empty(), buffer);
        ClientboundMotionDebugSnapshot decoded = ClientboundMotionDebugSnapshot.decode(buffer);
        assertFalse(decoded.hasSnapshot());
    }

    @Test
    void fullSnapshotRoundTrip() {
        ClientboundMotionDebugSnapshot packet =
                ClientboundMotionDebugSnapshot.from(serverSnapshot(), 12, 20);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);
        ClientboundMotionDebugSnapshot decoded = ClientboundMotionDebugSnapshot.decode(buffer);

        assertTrue(decoded.hasSnapshot());
        assertEquals(42L, decoded.sampleTick());
        assertEquals(0.1D, decoded.velocityX(), 1.0E-9);
        assertEquals(12, decoded.historyCount());
        assertEquals(20, decoded.historyCapacity());
        assertTrue(decoded.hasAction());
        assertEquals("FAST_RUN", decoded.actionType());
        assertEquals("PARCOOL_SYNCED", decoded.actionSource());
        assertEquals("LOW", decoded.actionTrust());
        assertTrue(decoded.hasFall());
        assertEquals("FALLING", decoded.fallPhase());
        assertEquals(2.5D, decoded.fallDistance(), 1.0E-9);
        assertEquals(-0.4D, decoded.fallVerticalSpeed(), 1.0E-9);
        assertEquals("SERVER_NATIVE", decoded.motionSource());
        assertTrue(decoded.serverAuthoritative());
    }

    @Test
    void noActionAndNoFallRoundTrip() {
        ClientboundMotionDebugSnapshot packet = packet(
                false, "", false, 0.2D, 0, 20);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);
        ClientboundMotionDebugSnapshot decoded = ClientboundMotionDebugSnapshot.decode(buffer);

        assertFalse(decoded.hasAction());
        assertFalse(decoded.hasFall());
    }

    @Test
    void invalidEnumIsRejected() {
        ClientboundMotionDebugSnapshot packet = packet(
                true, "NOT_AN_ACTION", false, 0.2D, 0, 20);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);

        assertThrows(DecoderException.class,
                () -> ClientboundMotionDebugSnapshot.decode(buffer));
    }

    @Test
    void nanIsRejected() {
        ClientboundMotionDebugSnapshot packet = packet(
                false, "", false, Double.NaN, 0, 20);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);

        assertThrows(DecoderException.class,
                () -> ClientboundMotionDebugSnapshot.decode(buffer));
    }

    @Test
    void invalidHistoryCountIsRejected() {
        ClientboundMotionDebugSnapshot packet = packet(
                false, "", false, 0.2D, 21, 20);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);

        assertThrows(DecoderException.class,
                () -> ClientboundMotionDebugSnapshot.decode(buffer));
    }

    @Test
    void zeroHistoryCapacityIsRejected() {
        ClientboundMotionDebugSnapshot packet = packet(
                false, "", false, 0.2D, 0, 0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ClientboundMotionDebugSnapshot.encode(packet, buffer);

        assertThrows(DecoderException.class,
                () -> ClientboundMotionDebugSnapshot.decode(buffer));
    }

    private static MotionSnapshot serverSnapshot() {
        FallSnapshot fall = new FallSnapshot(
                FallPhase.FALLING,
                new ParkourPosition(1, 2, 3),
                2.5D, -0.4D, 3, false,
                FallEndReason.UNKNOWN,
                ParkourStateSource.VANILLA_DERIVED,
                ParkourTrust.HIGH,
                40L);
        ImmutableParkourActionState action = new ImmutableParkourActionState(
                ParkourActionType.FAST_RUN,
                ParkourActionPhase.ACTIVE,
                true,
                7,
                1L,
                1L,
                ParkourStateSource.PARCOOL_SYNCED,
                ParkourTrust.LOW,
                false,
                false,
                "START");
        return new MotionSnapshot(
                42L,
                new ParkourPosition(1, 2, 3),
                0.1D, 0.2D, 0.3D,
                0.36D, 1.0D, 0.0D,
                0.5D, -0.1D,
                0.1D, 0.2D, 0.3D,
                7, true,
                fall, action,
                ParkourStateSource.SERVER_NATIVE,
                ParkourTrust.HIGH);
    }

    private static ClientboundMotionDebugSnapshot packet(boolean hasAction,
                                                         String actionType,
                                                         boolean hasFall,
                                                         double velocityX,
                                                         int historyCount,
                                                         int historyCapacity) {
        return new ClientboundMotionDebugSnapshot(
                true, 42L,
                velocityX, 0.2D, 0.3D,
                0.36D, 1.0D, 0.0D,
                0.5D, -0.1D,
                0.1D, 0.2D, 0.3D,
                7, true,
                historyCount, historyCapacity,
                hasAction, actionType, "ACTIVE", "PARCOOL_SYNCED", "LOW",
                hasFall, "FALLING", 2.5D, -0.4D,
                "VANILLA_DERIVED", "HIGH",
                "SERVER_NATIVE", "HIGH", true);
    }
}
