package com.MrGhost.parkourCombat.network;

import com.MrGhost.parkourCombat.adapter.FallPhase;
import com.MrGhost.parkourCombat.adapter.FallSnapshot;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionPhase;
import com.MrGhost.parkourCombat.adapter.ParkourActionState;
import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import com.MrGhost.parkourCombat.adapter.ParkourStateSource;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import com.MrGhost.parkourCombat.debug.DebugSnapshotInbox;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * S2C: a complete server-side debug snapshot. This packet is only sent to the
 * subscribed player; it never carries client-authored motion data.
 */
public record ClientboundMotionDebugSnapshot(
        boolean hasSnapshot,
        long sampleTick,
        double velocityX,
        double velocityY,
        double velocityZ,
        double horizontalSpeed,
        double directionX,
        double directionZ,
        double horizontalAcceleration,
        double verticalAcceleration,
        double displacementX,
        double displacementY,
        double displacementZ,
        int continuousMotionTicks,
        boolean onGround,
        int historyCount,
        int historyCapacity,
        boolean hasAction,
        String actionType,
        String actionPhase,
        String actionSource,
        String actionTrust,
        boolean hasFall,
        String fallPhase,
        double fallDistance,
        double fallVerticalSpeed,
        String fallSource,
        String fallTrust,
        String motionSource,
        String motionTrust,
        boolean serverAuthoritative
) {
    private static final int MAX_HISTORY_CAPACITY = 1024;
    private static final Set<String> ACTION_TYPES = names(ParkourActionType.values());
    private static final Set<String> ACTION_PHASES = names(ParkourActionPhase.values());
    private static final Set<String> FALL_PHASES = names(FallPhase.values());
    private static final Set<String> SOURCES = names(ParkourStateSource.values());
    private static final Set<String> TRUSTS = names(ParkourTrust.values());

    private static final ClientboundMotionDebugSnapshot EMPTY =
            new ClientboundMotionDebugSnapshot(
                    false, 0L,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D, 0.0D,
                    0.0D, 0.0D, 0.0D,
                    0, false,
                    0, 0,
                    false, "", "", "", "",
                    false, "", 0.0D, 0.0D, "", "",
                    "", "", false);

    public static ClientboundMotionDebugSnapshot empty() {
        return EMPTY;
    }

    public static ClientboundMotionDebugSnapshot from(MotionSnapshot snapshot,
                                                       int historyCount,
                                                       int historyCapacity) {
        if (snapshot == null) {
            return EMPTY;
        }

        FallSnapshot fall = snapshot.fallSnapshot();
        ParkourActionState action = snapshot.actionState();
        boolean hasFall = fall != null;
        boolean hasAction = action != null;

        int capacity = Math.max(1, historyCapacity);
        int count = Math.max(0, Math.min(historyCount, capacity));

        return new ClientboundMotionDebugSnapshot(
                true,
                snapshot.sampleTick(),
                snapshot.velocityX(),
                snapshot.velocityY(),
                snapshot.velocityZ(),
                snapshot.horizontalSpeed(),
                snapshot.directionX(),
                snapshot.directionZ(),
                snapshot.horizontalAcceleration(),
                snapshot.verticalAcceleration(),
                snapshot.displacementX(),
                snapshot.displacementY(),
                snapshot.displacementZ(),
                snapshot.continuousMotionTicks(),
                snapshot.onGround(),
                count,
                capacity,
                hasAction,
                hasAction ? action.type().name() : "",
                hasAction ? action.phase().name() : "",
                hasAction ? action.source().name() : "",
                hasAction ? action.trust().name() : "",
                hasFall,
                hasFall ? fall.phase().name() : "",
                hasFall ? fall.fallDistance() : 0.0D,
                hasFall ? fall.verticalVelocity() : 0.0D,
                hasFall ? fall.source().name() : "",
                hasFall ? fall.trust().name() : "",
                snapshot.source().name(),
                snapshot.trust().name(),
                snapshot.source() == ParkourStateSource.SERVER_NATIVE);
    }

    public static void encode(ClientboundMotionDebugSnapshot message,
                              FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.hasSnapshot());
        if (!message.hasSnapshot()) {
            return;
        }

        buffer.writeLong(message.sampleTick());
        buffer.writeDouble(message.velocityX());
        buffer.writeDouble(message.velocityY());
        buffer.writeDouble(message.velocityZ());
        buffer.writeDouble(message.horizontalSpeed());
        buffer.writeDouble(message.directionX());
        buffer.writeDouble(message.directionZ());
        buffer.writeDouble(message.horizontalAcceleration());
        buffer.writeDouble(message.verticalAcceleration());
        buffer.writeDouble(message.displacementX());
        buffer.writeDouble(message.displacementY());
        buffer.writeDouble(message.displacementZ());
        buffer.writeVarInt(message.continuousMotionTicks());
        buffer.writeBoolean(message.onGround());
        buffer.writeVarInt(message.historyCount());
        buffer.writeVarInt(message.historyCapacity());

        buffer.writeBoolean(message.hasAction());
        if (message.hasAction()) {
            buffer.writeUtf(message.actionType(), 64);
            buffer.writeUtf(message.actionPhase(), 64);
            buffer.writeUtf(message.actionSource(), 64);
            buffer.writeUtf(message.actionTrust(), 64);
        }

        buffer.writeBoolean(message.hasFall());
        if (message.hasFall()) {
            buffer.writeUtf(message.fallPhase(), 64);
            buffer.writeDouble(message.fallDistance());
            buffer.writeDouble(message.fallVerticalSpeed());
            buffer.writeUtf(message.fallSource(), 64);
            buffer.writeUtf(message.fallTrust(), 64);
        }

        buffer.writeUtf(message.motionSource(), 64);
        buffer.writeUtf(message.motionTrust(), 64);
        buffer.writeBoolean(message.serverAuthoritative());
    }

    public static ClientboundMotionDebugSnapshot decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return EMPTY;
        }

        long sampleTick = buffer.readLong();
        if (sampleTick < 0L) {
            throw invalid("sampleTick < 0");
        }

        double velocityX = finite(buffer, "velocityX");
        double velocityY = finite(buffer, "velocityY");
        double velocityZ = finite(buffer, "velocityZ");
        double horizontalSpeed = finite(buffer, "horizontalSpeed");
        double directionX = finite(buffer, "directionX");
        double directionZ = finite(buffer, "directionZ");
        double horizontalAcceleration = finite(buffer, "horizontalAcceleration");
        double verticalAcceleration = finite(buffer, "verticalAcceleration");
        double displacementX = finite(buffer, "displacementX");
        double displacementY = finite(buffer, "displacementY");
        double displacementZ = finite(buffer, "displacementZ");

        int continuousMotionTicks = buffer.readVarInt();
        if (continuousMotionTicks < 0) {
            throw invalid("continuousMotionTicks < 0");
        }
        boolean onGround = buffer.readBoolean();

        int historyCount = buffer.readVarInt();
        int historyCapacity = buffer.readVarInt();
        if (historyCapacity <= 0 || historyCapacity > MAX_HISTORY_CAPACITY) {
            throw invalid("historyCapacity out of range");
        }
        if (historyCount < 0 || historyCount > historyCapacity) {
            throw invalid("historyCount out of range");
        }

        boolean hasAction = buffer.readBoolean();
        String actionType = "";
        String actionPhase = "";
        String actionSource = "";
        String actionTrust = "";
        if (hasAction) {
            actionType = readName(buffer, ACTION_TYPES, "actionType");
            actionPhase = readName(buffer, ACTION_PHASES, "actionPhase");
            actionSource = readName(buffer, SOURCES, "actionSource");
            actionTrust = readName(buffer, TRUSTS, "actionTrust");
        }

        boolean hasFall = buffer.readBoolean();
        String fallPhase = "";
        double fallDistance = 0.0D;
        double fallVerticalSpeed = 0.0D;
        String fallSource = "";
        String fallTrust = "";
        if (hasFall) {
            fallPhase = readName(buffer, FALL_PHASES, "fallPhase");
            fallDistance = finite(buffer, "fallDistance");
            if (fallDistance < 0.0D) {
                throw invalid("fallDistance < 0");
            }
            fallVerticalSpeed = finite(buffer, "fallVerticalSpeed");
            fallSource = readName(buffer, SOURCES, "fallSource");
            fallTrust = readName(buffer, TRUSTS, "fallTrust");
        }

        String motionSource = readName(buffer, SOURCES, "motionSource");
        String motionTrust = readName(buffer, TRUSTS, "motionTrust");
        boolean serverAuthoritative = buffer.readBoolean();

        return new ClientboundMotionDebugSnapshot(
                true, sampleTick,
                velocityX, velocityY, velocityZ,
                horizontalSpeed, directionX, directionZ,
                horizontalAcceleration, verticalAcceleration,
                displacementX, displacementY, displacementZ,
                continuousMotionTicks, onGround,
                historyCount, historyCapacity,
                hasAction, actionType, actionPhase, actionSource, actionTrust,
                hasFall, fallPhase, fallDistance, fallVerticalSpeed, fallSource, fallTrust,
                motionSource, motionTrust, serverAuthoritative);
    }

    public static void handle(ClientboundMotionDebugSnapshot message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        DebugSnapshotInbox.offer(message);
        contextSupplier.get().setPacketHandled(true);
    }

    private static double finite(FriendlyByteBuf buffer, String field) {
        double value = buffer.readDouble();
        if (!Double.isFinite(value)) {
            throw invalid(field + " is not finite");
        }
        return value;
    }

    private static String readName(FriendlyByteBuf buffer, Set<String> allowed, String field) {
        String value = buffer.readUtf(64);
        if (!allowed.contains(value)) {
            throw invalid("invalid " + field + ": " + value);
        }
        return value;
    }

    private static DecoderException invalid(String message) {
        return new DecoderException("Invalid motion debug packet: " + message);
    }

    private static Set<String> names(Enum<?>[] values) {
        Set<String> result = new HashSet<>();
        Arrays.stream(values).map(Enum::name).forEach(result::add);
        return Collections.unmodifiableSet(result);
    }
}
