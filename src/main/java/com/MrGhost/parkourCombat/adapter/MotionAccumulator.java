package com.MrGhost.parkourCombat.adapter;

import java.util.List;
import java.util.Objects;

/**
 * Pure per-tick motion accumulator.
 *
 * <p>All acceleration values are per tick:
 * {@code (currentVelocity - previousVelocity) / deltaTicks}. A normal sample
 * has deltaTicks=1. Short gaps (2..5 ticks) are averaged and reset continuous
 * motion/direction tracking; larger gaps, teleports, dimension changes,
 * death and non-finite data reset the baseline without emitting a snapshot.</p>
 */
public final class MotionAccumulator {
    public static final int HISTORY_TICKS = MotionHistory.DEFAULT_CAPACITY;
    public static final double MIN_HORIZONTAL_SPEED = 0.05D;
    public static final double MIN_VERTICAL_SPEED = 0.08D;
    public static final int DIRECTION_HOLD_TICKS = 5;
    public static final double MAX_TICK_DISPLACEMENT = 8.0D;
    public static final int MAX_SAMPLE_GAP_TICKS = 5;

    private final MotionHistory history;
    private MotionFrame previous;
    private double lastDirectionX;
    private double lastDirectionZ;
    private int directionHoldTicks;
    private int continuousMotionTicks;
    private String resetReason;

    public MotionAccumulator() {
        this(new MotionHistory(HISTORY_TICKS));
    }

    public MotionAccumulator(MotionHistory history) {
        this.history = Objects.requireNonNull(history, "history");
    }

    /**
     * @return snapshot, or null when the frame was a reset/baseline-only frame
     */
    public MotionSnapshot accept(MotionFrame frame,
                                 FallSnapshot fallSnapshot,
                                 ParkourActionState actionState,
                                 ParkourStateSource source,
                                 ParkourTrust trust) {
        if (frame == null || !frame.isFinite()
                || fallSnapshot == null || source == null || trust == null) {
            reset("INVALID_DATA");
            return null;
        }
        if (frame.dead()) {
            reset("DEAD");
            return null;
        }
        if (frame.dimensionChanged()) {
            reset("DIMENSION_CHANGE");
            return null;
        }

        if (previous == null) {
            return firstSample(frame, fallSnapshot, actionState, source, trust);
        }

        long deltaTicks = frame.gameTime() - previous.gameTime();
        if (deltaTicks == 0L) {
            // A duplicate PlayerTick event for the same game tick must not
            // clear an otherwise valid history.
            return null;
        }
        if (deltaTicks < 0L) {
            reset("TICK_INVALID");
            return null;
        }
        if (deltaTicks > MAX_SAMPLE_GAP_TICKS) {
            reset("TICK_GAP");
            return null;
        }

        double dx = frame.position().x() - previous.position().x();
        double dy = frame.position().y() - previous.position().y();
        double dz = frame.position().z() - previous.position().z();
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        if (frame.teleported()
                || distanceSquared > MAX_TICK_DISPLACEMENT * MAX_TICK_DISPLACEMENT) {
            reset("TELEPORT");
            return null;
        }

        boolean gap = deltaTicks > 1L;
        double dt = deltaTicks;

        double velocityX = frame.velocityX();
        double velocityY = frame.velocityY();
        double velocityZ = frame.velocityZ();
        double horizontalSpeed = Math.hypot(velocityX, velocityZ);

        if (gap) {
            // A gap cannot prove per-tick direction/continuity; start fresh
            // while still reporting averaged kinematic changes.
            directionHoldTicks = 0;
            continuousMotionTicks = 0;
        }

        updateDirection(horizontalSpeed, velocityX, velocityZ);
        updateContinuousMotion(horizontalSpeed, velocityY, gap);

        double deltaVx = velocityX - previous.velocityX();
        double deltaVy = velocityY - previous.velocityY();
        double deltaVz = velocityZ - previous.velocityZ();
        double horizontalAcceleration =
                Math.sqrt(deltaVx * deltaVx + deltaVz * deltaVz) / dt;
        double verticalAcceleration = deltaVy / dt;
        double displacementX = dx / dt;
        double displacementY = dy / dt;
        double displacementZ = dz / dt;

        MotionSnapshot snapshot = new MotionSnapshot(
                frame.gameTime(),
                frame.position(),
                velocityX,
                velocityY,
                velocityZ,
                horizontalSpeed,
                lastDirectionX,
                lastDirectionZ,
                horizontalAcceleration,
                verticalAcceleration,
                displacementX,
                displacementY,
                displacementZ,
                continuousMotionTicks,
                frame.onGround(),
                fallSnapshot,
                actionState,
                source,
                trust
        );

        previous = frame;
        history.add(snapshot);
        return snapshot;
    }

    public MotionSnapshot latest() {
        return history.latest();
    }

    public List<MotionSnapshot> recent(int maxSamples) {
        return history.recent(maxSamples);
    }

    public MotionHistory history() {
        return history;
    }

    public void clear() {
        reset(null);
        resetReason = null;
    }

    /**
     * Consumes the reason for the most recent baseline reset. Null means the
     * last sample was not a reset.
     */
    public String consumeResetReason() {
        String reason = resetReason;
        resetReason = null;
        return reason;
    }

    private MotionSnapshot firstSample(MotionFrame frame,
                                       FallSnapshot fallSnapshot,
                                       ParkourActionState actionState,
                                       ParkourStateSource source,
                                       ParkourTrust trust) {
        previous = frame;
        double horizontalSpeed = Math.hypot(frame.velocityX(), frame.velocityZ());
        lastDirectionX = 0.0D;
        lastDirectionZ = 0.0D;
        directionHoldTicks = 0;
        if (horizontalSpeed >= MIN_HORIZONTAL_SPEED) {
            lastDirectionX = frame.velocityX() / horizontalSpeed;
            lastDirectionZ = frame.velocityZ() / horizontalSpeed;
            directionHoldTicks = DIRECTION_HOLD_TICKS;
        }

        MotionSnapshot snapshot = new MotionSnapshot(
                frame.gameTime(),
                frame.position(),
                frame.velocityX(),
                frame.velocityY(),
                frame.velocityZ(),
                horizontalSpeed,
                lastDirectionX,
                lastDirectionZ,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0.0D,
                0,
                frame.onGround(),
                fallSnapshot,
                actionState,
                source,
                trust
        );
        history.add(snapshot);
        return snapshot;
    }

    private void updateDirection(double horizontalSpeed, double velocityX, double velocityZ) {
        if (horizontalSpeed >= MIN_HORIZONTAL_SPEED) {
            lastDirectionX = velocityX / horizontalSpeed;
            lastDirectionZ = velocityZ / horizontalSpeed;
            directionHoldTicks = DIRECTION_HOLD_TICKS;
            return;
        }
        if (directionHoldTicks > 0) {
            directionHoldTicks--;
            if (directionHoldTicks == 0) {
                lastDirectionX = 0.0D;
                lastDirectionZ = 0.0D;
            }
            return;
        }
        lastDirectionX = 0.0D;
        lastDirectionZ = 0.0D;
    }

    private void updateContinuousMotion(double horizontalSpeed,
                                        double velocityY,
                                        boolean gap) {
        if (gap) {
            continuousMotionTicks = 0;
            return;
        }
        boolean moving = horizontalSpeed >= MIN_HORIZONTAL_SPEED
                || Math.abs(velocityY) >= MIN_VERTICAL_SPEED;
        if (!moving) {
            continuousMotionTicks = 0;
        } else if (continuousMotionTicks < Integer.MAX_VALUE) {
            continuousMotionTicks++;
        }
    }

    private void reset(String reason) {
        previous = null;
        lastDirectionX = 0.0D;
        lastDirectionZ = 0.0D;
        directionHoldTicks = 0;
        continuousMotionTicks = 0;
        history.clear();
        resetReason = reason;
    }
}
