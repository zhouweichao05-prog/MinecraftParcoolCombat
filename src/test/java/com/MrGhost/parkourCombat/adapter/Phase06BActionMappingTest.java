package com.MrGhost.parkourCombat.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alrex.parcool.common.action.impl.ClimbUp;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.FastRun;
import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.action.impl.SkyDive;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class Phase06BActionMappingTest {
    @Test
    void newActionTypesExist() {
        assertTrue(EnumSet.allOf(ParkourActionType.class).contains(ParkourActionType.DIVE));
        assertTrue(EnumSet.allOf(ParkourActionType.class).contains(ParkourActionType.DODGE));
        assertTrue(EnumSet.allOf(ParkourActionType.class).contains(ParkourActionType.HANG_DOWN));
    }

    @Test
    void newActionClassesMapToNewTypes() {
        ParCoolActionMapping mapping = new ParCoolActionMapping();
        assertEquals(ParkourActionType.DIVE, mapping.typeOf(new Dive()).orElseThrow());
        assertEquals(ParkourActionType.DODGE, mapping.typeOf(new Dodge()).orElseThrow());
        assertEquals(ParkourActionType.HANG_DOWN, mapping.typeOf(new HangDown()).orElseThrow());
    }

    @Test
    void deferredActionsAreNotMapped() {
        ParCoolActionMapping mapping = new ParCoolActionMapping();
        assertTrue(mapping.typeOf(new SkyDive()).isEmpty());
        assertTrue(mapping.typeOf(new ClimbUp()).isEmpty());
    }

    @Test
    void existingFiveActionMappingsRemain() {
        ParCoolActionMapping mapping = new ParCoolActionMapping();
        assertEquals(ParkourActionType.FAST_RUN, mapping.typeOf(new FastRun()).orElseThrow());
        assertEquals(ParkourActionType.SLIDE,
                mapping.typeOf(new com.alrex.parcool.common.action.impl.Slide()).orElseThrow());
        assertEquals(ParkourActionType.VAULT,
                mapping.typeOf(new com.alrex.parcool.common.action.impl.Vault()).orElseThrow());
        assertEquals(ParkourActionType.WALL_RUN_HORIZONTAL,
                mapping.typeOf(new com.alrex.parcool.common.action.impl.HorizontalWallRun()).orElseThrow());
        assertEquals(ParkourActionType.WALL_RUN_VERTICAL,
                mapping.typeOf(new com.alrex.parcool.common.action.impl.VerticalWallRun()).orElseThrow());
        assertEquals(ParkourActionType.WALL_JUMP,
                mapping.typeOf(new com.alrex.parcool.common.action.impl.WallJump()).orElseThrow());
    }

    @Test
    void newActionsUseExistingStateMachine() {
        List<ParkourActionState> events = new ArrayList<>();
        ParkourActionTracker tracker = new ParkourActionTracker(false, events::add);
        tracker.onStart(ParkourActionType.DIVE, ParkourStateSource.CLIENT_LOCAL,
                ParkourTrust.MEDIUM, false, 10L);
        tracker.onTick(11L, EnumSet.of(ParkourActionType.DIVE));
        assertEquals(ParkourActionPhase.ACTIVE,
                tracker.getState(ParkourActionType.DIVE).orElseThrow().phase());
        tracker.onFinish(ParkourActionType.DIVE, 12L);
        assertEquals(ParkourActionPhase.FINISHED,
                tracker.getState(ParkourActionType.DIVE).orElseThrow().phase());
    }

    @Test
    void dodgeCancellationUsesExistingPhase() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, state -> { });
        tracker.onStart(ParkourActionType.DODGE, ParkourStateSource.CLIENT_LOCAL,
                ParkourTrust.MEDIUM, false, 10L);
        tracker.markCancelRequested(ParkourActionType.DODGE, 11L);
        tracker.onFinish(ParkourActionType.DODGE, 12L);
        assertEquals(ParkourActionPhase.CANCELLED,
                tracker.getState(ParkourActionType.DODGE).orElseThrow().phase());
    }

    @Test
    void hangDownSyncLossUsesUnknown() {
        ParkourActionTracker tracker = new ParkourActionTracker(true, state -> { });
        tracker.onStart(ParkourActionType.HANG_DOWN, ParkourStateSource.PARCOOL_SYNCED,
                ParkourTrust.LOW, false, 10L);
        tracker.onTick(11L, EnumSet.noneOf(ParkourActionType.class));
        tracker.onTick(12L, EnumSet.noneOf(ParkourActionType.class));
        assertEquals(ParkourActionPhase.UNKNOWN,
                tracker.getState(ParkourActionType.HANG_DOWN).orElseThrow().phase());
    }

    @Test
    void newActionsDoNotChangePrimarySelectionPriority() {
        ParkourActionTracker tracker = new ParkourActionTracker(false, state -> { });
        tracker.onStart(ParkourActionType.DIVE, ParkourStateSource.CLIENT_LOCAL,
                ParkourTrust.MEDIUM, false, 1L);
        tracker.onStart(ParkourActionType.FAST_RUN, ParkourStateSource.CLIENT_LOCAL,
                ParkourTrust.MEDIUM, false, 1L);
        ParkourActionState selected = ParkourActionSelection.select(tracker);
        assertEquals(ParkourActionType.FAST_RUN, selected.type());
        assertFalse(selected.type() == ParkourActionType.DIVE);
    }
}
