package com.MrGhost.parkourCombat.combat.special;

import com.MrGhost.parkourCombat.adapter.ParkourActionType;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded event-driven windows for WallJump and Dodge. Only the most recent
 * unexpired window per style is retained; no unbounded history is kept.
 */
public final class SpecialAttackWindowTracker {
    public static final int WINDOW_TICKS = 12;
    private static final int MAX_STYLES_PER_PLAYER = 2;

    private final Map<UUID, EnumMap<SpecialAttackStyle, Long>> windows = new HashMap<>();

    public void recordFinish(UUID playerId, ParkourActionType action, long now) {
        if (playerId == null || now < 0L) {
            return;
        }
        SpecialAttackStyle style = windowStyle(action);
        if (style == null) {
            return;
        }
        EnumMap<SpecialAttackStyle, Long> player = windows.computeIfAbsent(
                playerId, ignored -> new EnumMap<>(SpecialAttackStyle.class));
        Long existing = player.get(style);
        if (existing != null && existing >= now) {
            return; // consecutive finish must not overwrite a live window
        }
        if (player.size() >= MAX_STYLES_PER_PLAYER) {
            player.entrySet().removeIf(entry -> entry.getValue() < now);
            if (player.size() >= MAX_STYLES_PER_PLAYER) {
                return;
            }
        }
        player.put(style, now + WINDOW_TICKS);
    }

    public Set<SpecialAttackStyle> availableStyles(UUID playerId, long now) {
        EnumMap<SpecialAttackStyle, Long> player = playerId == null ? null : windows.get(playerId);
        if (player == null) {
            return Set.of();
        }
        player.entrySet().removeIf(entry -> entry.getValue() < now);
        if (player.isEmpty()) {
            windows.remove(playerId);
            return Set.of();
        }
        return EnumSet.copyOf(player.keySet());
    }

    /** Consume only after the classified attack is accepted. */
    public boolean consume(UUID playerId, SpecialAttackStyle style, long now) {
        if (playerId == null || style == null) {
            return false;
        }
        EnumMap<SpecialAttackStyle, Long> player = windows.get(playerId);
        if (player == null) {
            return false;
        }
        Long expiry = player.get(style);
        if (expiry == null || expiry < now) {
            player.remove(style);
            return false;
        }
        player.remove(style);
        if (player.isEmpty()) {
            windows.remove(playerId);
        }
        return true;
    }

    public void clear(UUID playerId) {
        if (playerId != null) {
            windows.remove(playerId);
        }
    }

    public void clearAll() {
        windows.clear();
    }

    public int size() {
        return windows.size();
    }

    private static SpecialAttackStyle windowStyle(ParkourActionType action) {
        if (action == ParkourActionType.WALL_JUMP) return SpecialAttackStyle.WALL_JUMP;
        if (action == ParkourActionType.DODGE) return SpecialAttackStyle.DODGE;
        return null;
    }
}
