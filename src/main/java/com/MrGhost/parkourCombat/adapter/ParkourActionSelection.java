package com.MrGhost.parkourCombat.adapter;

/**
 * Read-only primary-action policy for MotionSnapshot.
 *
 * <p>Only currently active actions are considered; terminal TTL states are
 * ignored. The priority is fixed and does not change stage 02 semantics.</p>
 */
public final class ParkourActionSelection {
    private static final ParkourActionType[] PRIORITY = {
            ParkourActionType.WALL_RUN,
            ParkourActionType.WALL_JUMP,
            ParkourActionType.VAULT,
            ParkourActionType.SLIDE,
            ParkourActionType.FAST_RUN
    };

    private ParkourActionSelection() {
    }

    public static ParkourActionState select(ParkourActionTracker tracker) {
        if (tracker == null) {
            return null;
        }
        for (ParkourActionType type : PRIORITY) {
            if (!tracker.isActive(type)) {
                continue;
            }
            ParkourActionState state = tracker.getState(type).orElse(null);
            if (state != null && state.active()) {
                return state;
            }
        }
        return null;
    }
}
