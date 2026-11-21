package com.MrGhost.parkourCombat.adapter;

import com.alrex.parcool.common.action.Action;
import com.alrex.parcool.common.action.impl.Dive;
import com.alrex.parcool.common.action.impl.Dodge;
import com.alrex.parcool.common.action.impl.FastRun;
import com.alrex.parcool.common.action.impl.HangDown;
import com.alrex.parcool.common.action.impl.HorizontalWallRun;
import com.alrex.parcool.common.action.impl.Slide;
import com.alrex.parcool.common.action.impl.Vault;
import com.alrex.parcool.common.action.impl.VerticalWallRun;
import com.alrex.parcool.common.action.impl.WallJump;
import com.alrex.parcool.common.capability.Parkourability;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraftforge.fml.ModList;

/**
 * The only class that maps ParkourCombat action types to ParCool 3.4.3.3
 * classes. Keeping the mapping in one place makes the version lock auditable.
 */
public final class ParCoolActionMapping {
    private static final String REQUIRED_PARCOOL_VERSION = "3.4.3.3";

    private final Map<Class<? extends Action>, ParkourActionType> byClass =
            new LinkedHashMap<>();
    private boolean available;

    public ParCoolActionMapping() {
        try {
            register(FastRun.class, ParkourActionType.FAST_RUN);
            register(Slide.class, ParkourActionType.SLIDE);
            register(Vault.class, ParkourActionType.VAULT);
            register(HorizontalWallRun.class, ParkourActionType.WALL_RUN_HORIZONTAL);
            register(VerticalWallRun.class, ParkourActionType.WALL_RUN_VERTICAL);
            register(WallJump.class, ParkourActionType.WALL_JUMP);
            register(Dive.class, ParkourActionType.DIVE);
            register(Dodge.class, ParkourActionType.DODGE);
            register(HangDown.class, ParkourActionType.HANG_DOWN);
            this.available = true;
        } catch (Throwable error) {
            this.available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * The adapter is only allowed to run against the audited ParCool version.
     */
    public boolean isVersionSupported() {
        return ModList.get()
                .getModContainerById("parcool")
                .map(container -> REQUIRED_PARCOOL_VERSION.equals(
                        container.getModInfo().getVersion().toString()))
                .orElse(false);
    }

    public Optional<ParkourActionType> typeOf(Action action) {
        if (!available || action == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(byClass.get(action.getClass()));
    }

    public Set<ParkourActionType> activeTypes(Parkourability parkourability) {
        EnumSet<ParkourActionType> result = EnumSet.noneOf(ParkourActionType.class);
        if (!available || parkourability == null) {
            return result;
        }
        for (Map.Entry<Class<? extends Action>, ParkourActionType> entry : byClass.entrySet()) {
            try {
                if (parkourability.get(entry.getKey()).isDoing()) {
                    result.add(entry.getValue());
                }
            } catch (RuntimeException ignored) {
                // A missing/unregistered action must not break the whole sample.
            }
        }
        return result;
    }

    public boolean isWallRunType(ParkourActionType type) {
        return type == ParkourActionType.WALL_RUN_HORIZONTAL
                || type == ParkourActionType.WALL_RUN_VERTICAL;
    }

    private void register(Class<? extends Action> actionClass, ParkourActionType type) {
        this.byClass.put(actionClass, type);
    }
}
