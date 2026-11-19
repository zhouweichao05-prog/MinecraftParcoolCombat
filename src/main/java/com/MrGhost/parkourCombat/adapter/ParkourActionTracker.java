package com.MrGhost.parkourCombat.adapter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Per-player action state machine. This class deliberately has no Minecraft
 * dependency so it can be unit tested without a game runtime.
 */
public final class ParkourActionTracker {
    private static final int TERMINAL_TTL_TICKS = 40;
    private static final int SYNC_LOST_TIMEOUT_TICKS = 2;

    private final boolean serverSide;
    private final Consumer<ParkourActionState> listener;

    private final EnumMap<ParkourActionType, MutableAction> active =
            new EnumMap<>(ParkourActionType.class);
    private final EnumMap<ParkourActionType, ParkourActionState> terminal =
            new EnumMap<>(ParkourActionType.class);
    private final EnumMap<ParkourActionType, Integer> terminalAge =
            new EnumMap<>(ParkourActionType.class);

    public ParkourActionTracker(boolean serverSide, Consumer<ParkourActionState> listener) {
        this.serverSide = serverSide;
        this.listener = listener;
    }

    public void onStart(ParkourActionType type,
                        ParkourStateSource source,
                        ParkourTrust trust,
                        boolean serverAuthoritative,
                        long gameTime) {
        Objects.requireNonNull(type);
        if (type == ParkourActionType.WALL_RUN) {
            return;
        }

        MutableAction existing = active.get(type);
        if (existing != null) {
            existing.evidence = "DUPLICATE_START";
            existing.changedGameTime = gameTime;
            notifyListener(existing.toState());
            return;
        }

        for (MutableAction other : active.values()) {
            if (other.type != type) {
                other.replacementType = type;
                other.replacementGameTime = gameTime;
            }
        }

        MutableAction action = new MutableAction(type, source, trust,
                serverAuthoritative, gameTime, "START");
        active.put(type, action);
        terminal.remove(type);
        terminalAge.remove(type);
        notifyListener(action.toState());
    }

    public void onFinish(ParkourActionType type, long gameTime) {
        MutableAction action = active.get(type);
        if (action == null) {
            // A late Finish after a timeout must not overwrite the inferred
            // INTERRUPTED/UNKNOWN diagnosis with a misleading ORPHAN_FINISH.
            if (terminal.containsKey(type)) {
                return;
            }
            putTerminal(type, ParkourActionPhase.UNKNOWN, false,
                    sideSource(), sideTrust(), false, false,
                    "ORPHAN_FINISH", gameTime, gameTime, 0);
            return;
        }

        ParkourActionPhase phase;
        String evidence;
        boolean inferred = false;

        if (action.cancelRequested) {
            phase = ParkourActionPhase.CANCELLED;
            evidence = "TRY_TO_CONTINUE_CANCELLED";
        } else if (action.replacementType != null
                && gameTime - action.replacementGameTime <= 1L) {
            phase = ParkourActionPhase.INTERRUPTED;
            evidence = "REPLACED_BY(" + action.replacementType + ")";
            inferred = serverSide;
        } else {
            phase = ParkourActionPhase.FINISHED;
            evidence = "FINISH";
        }

        active.remove(type);
        putTerminal(type, phase, false,
                action.source, action.trust, action.serverAuthoritative,
                inferred, evidence, action.startedGameTime, gameTime, action.ticks);
    }

    /**
     * Client-only: a start attempt was vetoed by a ParCool TryToStart listener.
     */
    public void onCancelledAttempt(ParkourActionType type, long gameTime) {
        if (active.containsKey(type)) {
            return;
        }
        ParkourActionState previous = terminal.get(type);
        if (previous != null
                && previous.phase() == ParkourActionPhase.CANCELLED
                && previous.lastChangedGameTime() == gameTime) {
            return;
        }
        putTerminal(type, ParkourActionPhase.CANCELLED, false,
                sideSource(), sideTrust(), false, false,
                "TRY_TO_START_VETOED", gameTime, gameTime, 0);
    }

    /**
     * Client-only: TryToContinue was cancelled; remember it until Finish arrives.
     */
    public void markCancelRequested(ParkourActionType type, long gameTime) {
        MutableAction action = active.get(type);
        if (action != null) {
            action.cancelRequested = true;
            action.evidence = "CANCEL_REQUESTED";
            action.changedGameTime = gameTime;
            notifyListener(action.toState());
        }
    }

    public void onTick(long gameTime, Set<ParkourActionType> doing) {
        ageTerminalStates();

        List<MutableAction> snapshot = new ArrayList<>(active.values());
        for (MutableAction action : snapshot) {
            if (doing.contains(action.type)) {
                action.missingTicks = 0;
                if (action.replacementType != null
                        && gameTime - action.replacementGameTime > 1L) {
                    action.replacementType = null;
                }
                action.ticks++;
                if (action.phase == ParkourActionPhase.STARTED) {
                    action.phase = ParkourActionPhase.ACTIVE;
                    action.changedGameTime = gameTime;
                    notifyListener(action.toState());
                }
                continue;
            }

            action.missingTicks++;
            if (action.missingTicks >= SYNC_LOST_TIMEOUT_TICKS) {
                ParkourActionPhase phase;
                String evidence;
                if (action.replacementType != null
                        && active.containsKey(action.replacementType)) {
                    phase = ParkourActionPhase.INTERRUPTED;
                    evidence = "SYNC_LOST_REPLACED_BY(" + action.replacementType + ")";
                } else {
                    phase = ParkourActionPhase.UNKNOWN;
                    evidence = "SYNC_LOST";
                }
                active.remove(action.type);
                putTerminal(action.type, phase, false,
                        action.source, action.trust, action.serverAuthoritative,
                        serverSide, evidence, action.startedGameTime, gameTime, action.ticks);
            }
        }

        for (ParkourActionType type : doing) {
            if (type == ParkourActionType.WALL_RUN || active.containsKey(type)) {
                continue;
            }
            onStart(type, sideSource(), sideTrust(), false, gameTime);
        }
    }

    public Optional<ParkourActionState> getState(ParkourActionType type) {
        if (type == ParkourActionType.WALL_RUN) {
            return getAggregateWallRunState();
        }
        MutableAction action = active.get(type);
        if (action != null) {
            return Optional.of(action.toState());
        }
        return Optional.ofNullable(terminal.get(type));
    }

    public Set<ParkourActionType> getActiveTypes() {
        EnumSet<ParkourActionType> result = EnumSet.noneOf(ParkourActionType.class);
        result.addAll(active.keySet());
        if (active.containsKey(ParkourActionType.WALL_RUN_HORIZONTAL)
                || active.containsKey(ParkourActionType.WALL_RUN_VERTICAL)) {
            result.add(ParkourActionType.WALL_RUN);
        }
        return result;
    }

    public boolean isActive(ParkourActionType type) {
        if (type == ParkourActionType.WALL_RUN) {
            return active.containsKey(ParkourActionType.WALL_RUN_HORIZONTAL)
                    || active.containsKey(ParkourActionType.WALL_RUN_VERTICAL);
        }
        return active.containsKey(type);
    }

    public void clear() {
        active.clear();
        terminal.clear();
        terminalAge.clear();
    }

    private Optional<ParkourActionState> getAggregateWallRunState() {
        MutableAction horizontal = active.get(ParkourActionType.WALL_RUN_HORIZONTAL);
        MutableAction vertical = active.get(ParkourActionType.WALL_RUN_VERTICAL);
        if (horizontal == null && vertical == null) {
            return Optional.empty();
        }

        MutableAction base = horizontal != null ? horizontal : vertical;
        String evidence;
        if (horizontal != null && vertical != null) {
            evidence = "BOTH(HORIZONTAL,VERTICAL)";
        } else if (horizontal != null) {
            evidence = "AGGREGATE(HORIZONTAL)";
        } else {
            evidence = "AGGREGATE(VERTICAL)";
        }

        int ticks = Math.max(horizontal == null ? 0 : horizontal.ticks,
                vertical == null ? 0 : vertical.ticks);
        long started = Math.min(horizontal == null ? base.startedGameTime : horizontal.startedGameTime,
                vertical == null ? base.startedGameTime : vertical.startedGameTime);
        long changed = Math.max(horizontal == null ? base.changedGameTime : horizontal.changedGameTime,
                vertical == null ? base.changedGameTime : vertical.changedGameTime);

        return Optional.of(new ImmutableParkourActionState(
                ParkourActionType.WALL_RUN,
                ParkourActionPhase.ACTIVE,
                true,
                ticks,
                started,
                changed,
                base.source,
                base.trust,
                base.serverAuthoritative,
                false,
                evidence
        ));
    }

    private void ageTerminalStates() {
        Iterator<ParkourActionType> iterator = terminalAge.keySet().iterator();
        while (iterator.hasNext()) {
            ParkourActionType type = iterator.next();
            int age = terminalAge.get(type) + 1;
            if (age >= TERMINAL_TTL_TICKS) {
                iterator.remove();
                terminal.remove(type);
            } else {
                terminalAge.put(type, age);
            }
        }
    }

    private void putTerminal(ParkourActionType type,
                             ParkourActionPhase phase,
                             boolean active,
                             ParkourStateSource source,
                             ParkourTrust trust,
                             boolean serverAuthoritative,
                             boolean inferred,
                             String evidence,
                             long startedGameTime,
                             long gameTime,
                             int ticks) {
        ParkourActionState state = new ImmutableParkourActionState(
                type, phase, active, ticks, startedGameTime, gameTime,
                source, trust, serverAuthoritative, inferred, evidence);
        terminal.put(type, state);
        terminalAge.put(type, 0);
        notifyListener(state);
    }

    private ParkourStateSource sideSource() {
        return serverSide ? ParkourStateSource.PARCOOL_SYNCED : ParkourStateSource.CLIENT_LOCAL;
    }

    private ParkourTrust sideTrust() {
        return serverSide ? ParkourTrust.LOW : ParkourTrust.MEDIUM;
    }

    private void notifyListener(ParkourActionState state) {
        if (listener != null) {
            listener.accept(state);
        }
    }

    private static final class MutableAction {
        private final ParkourActionType type;
        private final long startedGameTime;
        private final ParkourStateSource source;
        private final ParkourTrust trust;
        private final boolean serverAuthoritative;
        private ParkourActionPhase phase = ParkourActionPhase.STARTED;
        private int ticks;
        private long changedGameTime;
        private String evidence;
        private boolean cancelRequested;
        private int missingTicks;
        private ParkourActionType replacementType;
        private long replacementGameTime;

        private MutableAction(ParkourActionType type,
                              ParkourStateSource source,
                              ParkourTrust trust,
                              boolean serverAuthoritative,
                              long gameTime,
                              String evidence) {
            this.type = type;
            this.startedGameTime = gameTime;
            this.changedGameTime = gameTime;
            this.source = source;
            this.trust = trust;
            this.serverAuthoritative = serverAuthoritative;
            this.evidence = evidence;
        }

        private ParkourActionState toState() {
            return new ImmutableParkourActionState(
                    type, phase, true, ticks, startedGameTime, changedGameTime,
                    source, trust, serverAuthoritative, false, evidence);
        }
    }
}
