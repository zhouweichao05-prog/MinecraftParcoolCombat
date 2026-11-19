package com.MrGhost.parkourCombat.adapter;

/**
 * Pure FALL state machine driven by vanilla physics snapshots.
 *
 * <p>FALL is not a ParCool action. It is derived from the player's motion:
 * jumping upwards must not enter FALLING; only a descending player that has
 * accumulated fall distance does.</p>
 */
public final class FallStateMachine {
    private static final int FALL_END_TTL_TICKS = 40;
    private static final double MIN_FALL_DISTANCE = 0.5D;

    private FallPhase phase = FallPhase.NOT_FALLING;
    private ParkourPosition startPosition;
    private double fallDistance;
    private double verticalVelocity;
    private int durationTicks;
    private boolean onGround;
    private FallEndReason endReason = FallEndReason.UNKNOWN;
    private long lastChangedGameTime;
    private int fallEndTicks;

    public void tick(FallMotionSample sample, long gameTime) {
        if (phase == FallPhase.FALL_END) {
            if (isEntry(sample)) {
                startFall(sample, gameTime);
                return;
            }
            fallEndTicks++;
            if (fallEndTicks >= FALL_END_TTL_TICKS) {
                resetToNotFalling(gameTime);
            }
            return;
        }

        if (phase == FallPhase.NOT_FALLING) {
            if (isEntry(sample)) {
                startFall(sample, gameTime);
            } else {
                onGround = sample.onGround();
            }
            return;
        }

        fallDistance = sample.fallDistance();
        verticalVelocity = sample.verticalVelocity();
        onGround = sample.onGround();
        durationTicks++;
        lastChangedGameTime = gameTime;

        FallEndReason reason = detectEndReason(sample);
        if (reason != null) {
            endFall(reason, sample, gameTime);
        }
    }

    public void forceEnd(FallEndReason reason, long gameTime) {
        if (phase == FallPhase.FALLING) {
            endFall(reason, null, gameTime);
        }
    }

    public FallSnapshot snapshot(ParkourStateSource source, ParkourTrust trust) {
        ParkourPosition start = startPosition == null
                ? new ParkourPosition(0.0D, 0.0D, 0.0D)
                : startPosition;
        return new FallSnapshot(
                phase,
                start,
                fallDistance,
                verticalVelocity,
                durationTicks,
                onGround,
                endReason,
                source,
                trust,
                lastChangedGameTime
        );
    }

    public FallPhase phase() {
        return phase;
    }

    public boolean isFalling() {
        return phase == FallPhase.FALLING;
    }

    public void clear() {
        phase = FallPhase.NOT_FALLING;
        startPosition = null;
        fallDistance = 0.0D;
        verticalVelocity = 0.0D;
        durationTicks = 0;
        onGround = false;
        endReason = FallEndReason.UNKNOWN;
        lastChangedGameTime = 0L;
        fallEndTicks = 0;
    }

    private void startFall(FallMotionSample sample, long gameTime) {
        phase = FallPhase.FALLING;
        startPosition = sample.position();
        fallDistance = sample.fallDistance();
        verticalVelocity = sample.verticalVelocity();
        durationTicks = 0;
        onGround = false;
        endReason = FallEndReason.UNKNOWN;
        lastChangedGameTime = gameTime;
        fallEndTicks = 0;
    }

    private void endFall(FallEndReason reason, FallMotionSample sample, long gameTime) {
        phase = FallPhase.FALL_END;
        endReason = reason;
        onGround = sample != null && sample.onGround();
        lastChangedGameTime = gameTime;
        fallEndTicks = 0;
    }

    private void resetToNotFalling(long gameTime) {
        phase = FallPhase.NOT_FALLING;
        startPosition = null;
        fallDistance = 0.0D;
        verticalVelocity = 0.0D;
        durationTicks = 0;
        onGround = false;
        endReason = FallEndReason.UNKNOWN;
        lastChangedGameTime = gameTime;
        fallEndTicks = 0;
    }

    private boolean isEntry(FallMotionSample sample) {
        return !sample.onGround()
                && !sample.inWater()
                && !sample.inLava()
                && !sample.fallFlying()
                && !sample.climbing()
                && !sample.inVehicle()
                && !sample.dead()
                && sample.verticalVelocity() < 0.0D
                && sample.fallDistance() > MIN_FALL_DISTANCE;
    }

    private FallEndReason detectEndReason(FallMotionSample sample) {
        if (sample.dead()) {
            return FallEndReason.DEATH;
        }
        if (sample.dimensionChanged()) {
            return FallEndReason.DIMENSION_CHANGE;
        }
        if (sample.teleported()) {
            return FallEndReason.TELEPORT;
        }
        if (sample.onGround()) {
            return FallEndReason.LANDED;
        }
        if (sample.inWater()) {
            return FallEndReason.WATER;
        }
        if (sample.inLava()) {
            return FallEndReason.LAVA;
        }
        if (sample.fallFlying()) {
            return FallEndReason.FLYING;
        }
        if (sample.climbing()) {
            return FallEndReason.CLIMBING;
        }
        if (sample.inVehicle()) {
            return FallEndReason.VEHICLE;
        }
        return null;
    }
}
