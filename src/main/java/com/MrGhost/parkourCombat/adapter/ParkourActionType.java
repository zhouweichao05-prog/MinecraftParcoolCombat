package com.MrGhost.parkourCombat.adapter;

/**
 * Parkour actions exposed by the adapter.
 *
 * <p>{@link #WALL_RUN} is an aggregate query type derived from
 * {@link #WALL_RUN_HORIZONTAL} and {@link #WALL_RUN_VERTICAL}; it is not a
 * separate ParCool action class.</p>
 */
public enum ParkourActionType {
    FAST_RUN,
    SLIDE,
    VAULT,
    WALL_RUN,
    WALL_RUN_HORIZONTAL,
    WALL_RUN_VERTICAL,
    WALL_JUMP,
    DIVE,
    DODGE,
    HANG_DOWN;

    public boolean isWallRun() {
        return this == WALL_RUN || this == WALL_RUN_HORIZONTAL || this == WALL_RUN_VERTICAL;
    }

    public boolean isConcreteWallRun() {
        return this == WALL_RUN_HORIZONTAL || this == WALL_RUN_VERTICAL;
    }
}
