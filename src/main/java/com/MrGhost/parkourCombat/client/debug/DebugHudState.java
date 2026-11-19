package com.MrGhost.parkourCombat.client.debug;

import com.MrGhost.parkourCombat.adapter.FallSnapshot;
import com.MrGhost.parkourCombat.adapter.MotionSnapshot;
import com.MrGhost.parkourCombat.adapter.ParkourActionState;
import com.MrGhost.parkourCombat.adapter.ParkourTrust;
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Client-only HUD cache. Client-local and server-packet data are stored in
 * separate fields and are never merged.
 *
 * <p>No Minecraft classes are referenced, so the state and its formatting can
 * be unit tested.</p>
 */
public final class DebugHudState {
    public static final int STALE_AFTER_TICKS = 10;
    public static final int DROP_AFTER_TICKS = 20;

    private static final int WHITE = 0xFFE0E0E0;
    private static final int HEADER_CLIENT = 0xFF55FFFF;
    private static final int HEADER_SERVER = 0xFFFFD060;
    private static final int HIGH = 0xFF55FF55;
    private static final int MEDIUM = 0xFFFFFF55;
    private static final int LOW = 0xFFFFAA00;
    private static final int UNKNOWN = 0xFFFF5555;

    private boolean enabled;
    private MotionSnapshot clientSnapshot;
    private int clientHistoryCount;
    private int clientHistoryCapacity;

    private ClientboundMotionDebugSnapshot serverSnapshot;
    private int serverAgeTicks;

    private List<HudLine> clientLines = List.of();
    private List<HudLine> serverLines = List.of();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clearServer();
            clientLines = List.of();
            serverLines = List.of();
        }
    }

    public void updateClient(MotionSnapshot snapshot, int historyCount, int historyCapacity) {
        this.clientSnapshot = snapshot;
        this.clientHistoryCount = Math.max(0, historyCount);
        this.clientHistoryCapacity = Math.max(0, historyCapacity);
    }

    public void acceptServer(ClientboundMotionDebugSnapshot snapshot) {
        if (snapshot == null || !snapshot.hasSnapshot()) {
            clearServer();
            return;
        }
        this.serverSnapshot = snapshot;
        this.serverAgeTicks = 0;
    }

    public void tickServerAge() {
        if (serverSnapshot == null) {
            return;
        }
        serverAgeTicks++;
        if (serverAgeTicks > DROP_AFTER_TICKS) {
            clearServer();
        }
    }

    public void clearServer() {
        serverSnapshot = null;
        serverAgeTicks = 0;
        serverLines = List.of();
    }

    public void clearAll() {
        enabled = false;
        clientSnapshot = null;
        clientHistoryCount = 0;
        clientHistoryCapacity = 0;
        serverSnapshot = null;
        serverAgeTicks = 0;
        clientLines = List.of();
        serverLines = List.of();
    }

    /**
     * Rebuilds cached strings. Call at most every 4 client ticks.
     */
    public void refresh() {
        if (!enabled) {
            clientLines = List.of();
            serverLines = List.of();
            return;
        }
        clientLines = buildClientLines();
        serverLines = buildServerLines();
    }

    public List<HudLine> getClientLines() {
        return clientLines;
    }

    public List<HudLine> getServerLines() {
        return serverLines;
    }

    public int getServerAgeTicks() {
        return serverAgeTicks;
    }

    public MotionSnapshot getClientSnapshot() {
        return clientSnapshot;
    }

    public ClientboundMotionDebugSnapshot getServerSnapshot() {
        return serverSnapshot;
    }

    public boolean isServerStale() {
        return serverSnapshot != null && serverAgeTicks > STALE_AFTER_TICKS;
    }

    private List<HudLine> buildClientLines() {
        List<HudLine> lines = new ArrayList<>();
        if (clientSnapshot == null) {
            lines.add(new HudLine("NO SNAPSHOT", UNKNOWN));
            return Collections.unmodifiableList(lines);
        }

        MotionSnapshot snapshot = clientSnapshot;
        lines.add(new HudLine("ACTIVE", HIGH));
        lines.add(new HudLine("motionSource=" + snapshot.source(),
                trustColor(snapshot.trust())));
        lines.add(new HudLine("motionTrust=" + snapshot.trust(), trustColor(snapshot.trust())));
        lines.add(new HudLine("sampleTick=" + snapshot.sampleTick(), WHITE));
        lines.add(new HudLine("serverAuthoritative=false", WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "hSpeed=%.4f", snapshot.horizontalSpeed()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "vel=(%.4f, %.4f, %.4f)",
                snapshot.velocityX(), snapshot.velocityY(), snapshot.velocityZ()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "hAccel=%.4f vAccel=%.4f",
                snapshot.horizontalAcceleration(), snapshot.verticalAcceleration()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "dir=(%.4f, %.4f)",
                snapshot.directionX(), snapshot.directionZ()), WHITE));
        lines.add(new HudLine(String.format(Locale.ROOT, "disp=(%.4f, %.4f, %.4f)",
                snapshot.displacementX(), snapshot.displacementY(), snapshot.displacementZ()), WHITE));
        lines.add(new HudLine("movingTicks=" + snapshot.continuousMotionTicks()
                + " onGround=" + snapshot.onGround(), WHITE));
        lines.add(new HudLine("history=" + clientHistoryCount + " / "
                + (clientHistoryCapacity > 0 ? clientHistoryCapacity : "N/A"), WHITE));

        ParkourActionState action = snapshot.actionState();
        if (action == null) {
            lines.add(new HudLine("action=NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("action=" + action.type() + " phase=" + action.phase(), WHITE));
            lines.add(new HudLine("actionSource=" + action.source(),
                    trustColor(action.trust())));
            lines.add(new HudLine("actionTrust=" + action.trust(), trustColor(action.trust())));
        }

        FallSnapshot fall = snapshot.fallSnapshot();
        if (fall == null) {
            lines.add(new HudLine("fall=NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("fallPhase=" + fall.phase(), WHITE));
            lines.add(new HudLine("fallDistance=" + format(fall.fallDistance()), WHITE));
            lines.add(new HudLine("fallVerticalSpeed=" + format(fall.verticalVelocity()), WHITE));
            lines.add(new HudLine("fallSource=" + fall.source(), trustColor(fall.trust())));
            lines.add(new HudLine("fallTrust=" + fall.trust(), trustColor(fall.trust())));
        }
        return Collections.unmodifiableList(lines);
    }

    private List<HudLine> buildServerLines() {
        List<HudLine> lines = new ArrayList<>();
        ClientboundMotionDebugSnapshot snapshot = serverSnapshot;
        if (snapshot == null) {
            lines.add(new HudLine("NO SNAPSHOT", UNKNOWN));
            return Collections.unmodifiableList(lines);
        }

        String status = isServerStale() ? "STALE" : "ACTIVE";
        lines.add(new HudLine(status + " ageTicks=" + serverAgeTicks,
                isServerStale() ? UNKNOWN : HIGH));
        lines.add(new HudLine("motionSource=" + snapshot.motionSource(),
                trustColor(snapshot.motionTrust())));
        lines.add(new HudLine("motionTrust=" + snapshot.motionTrust(),
                trustColor(snapshot.motionTrust())));
        lines.add(new HudLine("sampleTick=" + snapshot.sampleTick(), WHITE));
        lines.add(new HudLine("serverAuthoritative=" + snapshot.serverAuthoritative(), WHITE));
        lines.add(new HudLine("hSpeed=" + format(snapshot.horizontalSpeed()), WHITE));
        lines.add(new HudLine("vel=(" + format(snapshot.velocityX()) + ", "
                + format(snapshot.velocityY()) + ", "
                + format(snapshot.velocityZ()) + ")", WHITE));
        lines.add(new HudLine("hAccel=" + format(snapshot.horizontalAcceleration())
                + " vAccel=" + format(snapshot.verticalAcceleration()), WHITE));
        lines.add(new HudLine("dir=(" + format(snapshot.directionX()) + ", "
                + format(snapshot.directionZ()) + ")", WHITE));
        lines.add(new HudLine("disp=(" + format(snapshot.displacementX()) + ", "
                + format(snapshot.displacementY()) + ", "
                + format(snapshot.displacementZ()) + ")", WHITE));
        lines.add(new HudLine("movingTicks=" + snapshot.continuousMotionTicks()
                + " onGround=" + snapshot.onGround(), WHITE));
        lines.add(new HudLine("history=" + snapshot.historyCount() + " / "
                + snapshot.historyCapacity(), WHITE));

        if (!snapshot.hasAction()) {
            lines.add(new HudLine("action=NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("action=" + snapshot.actionType()
                    + " phase=" + snapshot.actionPhase(), WHITE));
            lines.add(new HudLine("actionSource=" + snapshot.actionSource(),
                    trustColor(snapshot.actionTrust())));
            lines.add(new HudLine("actionTrust=" + snapshot.actionTrust(),
                    trustColor(snapshot.actionTrust())));
        }

        if (!snapshot.hasFall()) {
            lines.add(new HudLine("fall=NONE", UNKNOWN));
        } else {
            lines.add(new HudLine("fallPhase=" + snapshot.fallPhase(), WHITE));
            lines.add(new HudLine("fallDistance=" + format(snapshot.fallDistance()), WHITE));
            lines.add(new HudLine("fallVerticalSpeed=" + format(snapshot.fallVerticalSpeed()), WHITE));
            lines.add(new HudLine("fallSource=" + snapshot.fallSource(),
                    trustColor(snapshot.fallTrust())));
            lines.add(new HudLine("fallTrust=" + snapshot.fallTrust(),
                    trustColor(snapshot.fallTrust())));
        }
        return Collections.unmodifiableList(lines);
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static int trustColor(String trust) {
        return switch (trust) {
            case "HIGH" -> HIGH;
            case "MEDIUM" -> MEDIUM;
            case "LOW" -> LOW;
            default -> UNKNOWN;
        };
    }

    private static int trustColor(ParkourTrust trust) {
        if (trust == null) {
            return UNKNOWN;
        }
        return switch (trust) {
            case HIGH -> HIGH;
            case MEDIUM -> MEDIUM;
            case LOW -> LOW;
            default -> UNKNOWN;
        };
    }
}
