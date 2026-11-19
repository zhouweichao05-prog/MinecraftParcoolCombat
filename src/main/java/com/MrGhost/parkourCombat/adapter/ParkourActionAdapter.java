package com.MrGhost.parkourCombat.adapter;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.entity.player.Player;

public interface ParkourActionAdapter {
    Optional<ParkourActionState> getState(Player player, ParkourActionType type);

    Set<ParkourActionType> getActiveActions(Player player);

    boolean isActive(Player player, ParkourActionType type);

    Optional<FallSnapshot> getFallState(Player player);

    Optional<WallRunSnapshot> getWallRunState(Player player, ParkourActionType type);

    Optional<MotionSnapshot> getMotionSnapshot(Player player);

    List<MotionSnapshot> getMotionHistory(Player player, int maxSamples);
}
