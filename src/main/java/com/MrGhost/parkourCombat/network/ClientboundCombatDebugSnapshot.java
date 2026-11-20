package com.MrGhost.parkourCombat.network;

import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionState;
import com.MrGhost.parkourCombat.combat.CombatAttackContext;
import io.netty.handler.codec.DecoderException;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Bounded S2C summary of the latest sealed combat context. */
public record ClientboundCombatDebugSnapshot(
        boolean hasContext,
        long attackId,
        String phase,
        String result,
        int comboCurrent,
        int comboTotal,
        String weaponId,
        String actionType,
        String actionPhase,
        double horizontalSpeed,
        double verticalSpeed,
        long sampleTick,
        long serverTick,
        String targetCoverage,
        int requestedTargets,
        int observedTargets,
        int attackEventCount,
        int damageEventCount,
        int canceledEventCount,
        int lateEventCount,
        boolean truncated,
        boolean orderingKnown,
        boolean serverAuthoritative,
        boolean healthReductionVerified,
        String handlerState,
        String handlerIdentity
) {
    private static final int MAX_STRING = 64;

    public static ClientboundCombatDebugSnapshot empty() {
        return new ClientboundCombatDebugSnapshot(
                false, 0L, "", "", 0, 0, "", "NONE", "",
                0.0D, 0.0D, 0L, 0L, "UNKNOWN", 0, 0, 0, 0, 0, 0,
                false, false, false, false, "UNKNOWN", "UNKNOWN");
    }

    public static ClientboundCombatDebugSnapshot from(CombatAttackContext context,
                                                       String handlerState,
                                                       String handlerIdentity) {
        if (context == null) {
            return empty();
        }
        MotionSnapshot motion = context.motionSnapshot();
        ParkourActionState action = motion == null ? null : motion.actionState();
        String actionType = action == null ? "NONE" : action.type().name();
        String actionPhase = action == null ? "" : action.phase().name();
        String weapon = context.weapon() == null || context.weapon().itemId() == null
                ? "" : context.weapon().itemId().toString();
        return new ClientboundCombatDebugSnapshot(
                true,
                context.attackId(),
                context.phase() == null ? "UNKNOWN" : context.phase().name(),
                context.result() == null ? "UNKNOWN" : context.result().name(),
                context.comboCurrent(),
                context.comboTotal(),
                weapon,
                actionType,
                actionPhase,
                motion == null ? 0.0D : motion.horizontalSpeed(),
                motion == null ? 0.0D : motion.velocityY(),
                motion == null ? 0L : motion.sampleTick(),
                context.serverTick(),
                context.targetCoverage() == null ? "UNKNOWN" : context.targetCoverage().name(),
                context.requestedTargetsCapturedCount(),
                context.validAttackTargets().size(),
                context.attackEventCount(),
                context.damageEventCount(),
                context.canceledEventCount(),
                context.lateEventCount(),
                context.eventObservationTruncated(),
                context.orderingKnown(),
                context.serverAuthoritative(),
                context.healthReductionVerified(),
                handlerState == null ? "UNKNOWN" : handlerState,
                handlerIdentity == null ? "UNKNOWN" : handlerIdentity);
    }

    public static void encode(ClientboundCombatDebugSnapshot message, FriendlyByteBuf buffer) {
        buffer.writeBoolean(message.hasContext());
        if (!message.hasContext()) {
            return;
        }
        buffer.writeLong(message.attackId());
        writeString(buffer, message.phase());
        writeString(buffer, message.result());
        buffer.writeVarInt(message.comboCurrent());
        buffer.writeVarInt(message.comboTotal());
        writeString(buffer, message.weaponId());
        writeString(buffer, message.actionType());
        writeString(buffer, message.actionPhase());
        buffer.writeDouble(message.horizontalSpeed());
        buffer.writeDouble(message.verticalSpeed());
        buffer.writeLong(message.sampleTick());
        buffer.writeLong(message.serverTick());
        writeString(buffer, message.targetCoverage());
        buffer.writeVarInt(message.requestedTargets());
        buffer.writeVarInt(message.observedTargets());
        buffer.writeVarInt(message.attackEventCount());
        buffer.writeVarInt(message.damageEventCount());
        buffer.writeVarInt(message.canceledEventCount());
        buffer.writeVarInt(message.lateEventCount());
        buffer.writeBoolean(message.truncated());
        buffer.writeBoolean(message.orderingKnown());
        buffer.writeBoolean(message.serverAuthoritative());
        buffer.writeBoolean(message.healthReductionVerified());
        writeString(buffer, message.handlerState());
        writeString(buffer, message.handlerIdentity());
    }

    public static ClientboundCombatDebugSnapshot decode(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return empty();
        }
        long attackId = buffer.readLong();
        if (attackId < 0L) throw new DecoderException("attackId < 0");
        String phase = readString(buffer);
        String result = readString(buffer);
        int comboCurrent = nonNegative(buffer.readVarInt(), "comboCurrent");
        int comboTotal = nonNegative(buffer.readVarInt(), "comboTotal");
        String weapon = readString(buffer);
        String actionType = readString(buffer);
        String actionPhase = readString(buffer);
        double hSpeed = finite(buffer, "horizontalSpeed");
        double vSpeed = finite(buffer, "verticalSpeed");
        long sampleTick = buffer.readLong();
        long serverTick = buffer.readLong();
        if (sampleTick < 0L || serverTick < 0L) throw new DecoderException("negative tick");
        String coverage = readString(buffer);
        int requested = nonNegative(buffer.readVarInt(), "requestedTargets");
        int observed = nonNegative(buffer.readVarInt(), "observedTargets");
        int attacks = nonNegative(buffer.readVarInt(), "attackEventCount");
        int damage = nonNegative(buffer.readVarInt(), "damageEventCount");
        int canceled = nonNegative(buffer.readVarInt(), "canceledEventCount");
        int late = nonNegative(buffer.readVarInt(), "lateEventCount");
        return new ClientboundCombatDebugSnapshot(
                true, attackId, phase, result, comboCurrent, comboTotal, weapon,
                actionType, actionPhase, hSpeed, vSpeed, sampleTick, serverTick,
                coverage, requested, observed, attacks, damage, canceled, late,
                buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(),
                buffer.readBoolean(), readString(buffer), readString(buffer));
    }

    private static void writeString(FriendlyByteBuf buffer, String value) {
        String safe = value == null ? "" : value;
        buffer.writeUtf(safe.length() > MAX_STRING ? safe.substring(0, MAX_STRING) : safe, MAX_STRING);
    }

    private static String readString(FriendlyByteBuf buffer) {
        return buffer.readUtf(MAX_STRING);
    }

    private static int nonNegative(int value, String field) {
        if (value < 0) throw new DecoderException(field + " < 0");
        return value;
    }

    private static double finite(FriendlyByteBuf buffer, String field) {
        double value = buffer.readDouble();
        if (!Double.isFinite(value)) throw new DecoderException(field + " not finite");
        return value;
    }

    public static void handle(ClientboundCombatDebugSnapshot message,
                              Supplier<NetworkEvent.Context> contextSupplier) {
        com.MrGhost.parkourCombat.debug.DebugSnapshotInbox.offerCombat(message);
        contextSupplier.get().setPacketHandled(true);
    }
}
