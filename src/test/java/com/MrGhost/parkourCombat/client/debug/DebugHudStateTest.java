package com.MrGhost.parkourCombat.client.debug;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.MrGhost.parkourCombat.network.ClientboundMotionDebugSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class DebugHudStateTest {
    @Test
    void localAndServerViewsStaySeparate() {
        DebugHudState state = new DebugHudState();
        state.setEnabled(true);
        state.updateClient(clientSnapshot(), 12, 20);
        state.acceptServer(ClientboundMotionDebugSnapshot.from(serverSnapshot(), 8, 20));
        state.refresh();

        String clientText = text(state.getClientLines());
        String serverText = text(state.getServerLines());

        assertTrue(clientText.contains("运动来源：客户端本地数据"));
        assertTrue(clientText.contains("历史：12 / 20"));
        assertTrue(serverText.contains("运动来源：服务端原生数据"));
        assertTrue(serverText.contains("历史：8 / 20"));
        assertTrue(serverText.contains("服务端权威：true"));
        assertFalse(clientText.contains("服务端原生数据"));
    }

    @Test
    void missingServerSnapshotShowsNoSnapshotWithoutClientFallback() {
        DebugHudState state = new DebugHudState();
        state.setEnabled(true);
        state.updateClient(clientSnapshot(), 12, 20);
        state.acceptServer(ClientboundMotionDebugSnapshot.empty());
        state.refresh();

        assertTrue(text(state.getServerLines()).contains("无数据"));
        assertFalse(text(state.getServerLines()).contains("CLIENT_LOCAL"));
    }

    @Test
    void staleServerDataIsMarkedAndEventuallyCleared() {
        DebugHudState state = new DebugHudState();
        state.setEnabled(true);
        state.acceptServer(ClientboundMotionDebugSnapshot.from(serverSnapshot(), 8, 20));
        for (int i = 0; i < DebugHudState.STALE_AFTER_TICKS + 1; i++) {
            state.tickServerAge();
        }
        state.refresh();
        assertTrue(state.isServerStale());
        assertTrue(text(state.getServerLines()).contains("数据已过期"));

        for (int i = 0; i < DebugHudState.DROP_AFTER_TICKS - 10; i++) {
            state.tickServerAge();
        }
        state.refresh();
        assertNull(state.getServerSnapshot());
        assertTrue(text(state.getServerLines()).contains("无数据"));
    }

    @Test
    void localSnapshotDoesNotGetFilledByServerData() {
        DebugHudState state = new DebugHudState();
        state.setEnabled(true);
        state.acceptServer(ClientboundMotionDebugSnapshot.from(serverSnapshot(), 8, 20));
        state.refresh();

        assertNull(state.getClientSnapshot());
        assertTrue(text(state.getClientLines()).contains("无数据"));
        assertTrue(text(state.getServerLines()).contains("服务端原生数据"));
    }

    @Test
    void disableAndClearRemoveBothViews() {
        DebugHudState state = new DebugHudState();
        state.setEnabled(true);
        state.updateClient(clientSnapshot(), 12, 20);
        state.acceptServer(ClientboundMotionDebugSnapshot.from(serverSnapshot(), 8, 20));
        state.refresh();
        assertNotNull(state.getClientSnapshot());
        assertNotNull(state.getServerSnapshot());

        state.clearAll();
        assertFalse(state.isEnabled());
        assertNull(state.getClientSnapshot());
        assertNull(state.getServerSnapshot());
        assertTrue(state.getClientLines().isEmpty());
        assertTrue(state.getServerLines().isEmpty());
    }

    private static String text(List<HudLine> lines) {
        StringBuilder builder = new StringBuilder();
        for (HudLine line : lines) {
            builder.append(line.text()).append('\n');
        }
        return builder.toString();
    }

    private static MotionSnapshot clientSnapshot() {
        FallSnapshot fall = new FallSnapshot(
                FallPhase.NOT_FALLING,
                new ParkourPosition(0, 0, 0),
                0.0D, 0.0D, 0, true,
                FallEndReason.UNKNOWN,
                ParkourStateSource.VANILLA_DERIVED,
                ParkourTrust.MEDIUM,
                0L);
        ImmutableParkourActionState action = new ImmutableParkourActionState(
                ParkourActionType.SLIDE,
                ParkourActionPhase.ACTIVE,
                true, 5, 0L, 0L,
                ParkourStateSource.CLIENT_LOCAL,
                ParkourTrust.MEDIUM,
                false, false, "START");
        return new MotionSnapshot(
                10L, new ParkourPosition(0, 0, 0),
                0.2D, 0.0D, -0.1D,
                0.2236D, 0.8944D, -0.4472D,
                0.3D, 0.0D,
                0.2D, 0.0D, -0.1D,
                5, true, fall, action,
                ParkourStateSource.CLIENT_LOCAL, ParkourTrust.MEDIUM);
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
        return new MotionSnapshot(
                42L, new ParkourPosition(1, 2, 3),
                0.1D, 0.2D, 0.3D,
                0.36D, 1.0D, 0.0D,
                0.5D, -0.1D,
                0.1D, 0.2D, 0.3D,
                7, false, fall, null,
                ParkourStateSource.SERVER_NATIVE, ParkourTrust.HIGH);
    }
}
