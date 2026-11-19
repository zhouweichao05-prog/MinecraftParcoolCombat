package com.MrGhost.parkourCombat.adapter;

/**
 * Side-effect free per-tick input for {@link MotionAccumulator}.
 *
 * <p>The caller reads the player once per tick and fills this frame. It avoids
 * coupling the pure motion calculations to Minecraft classes.</p>
 */
public record MotionFrame(
        long gameTime,
        ParkourPosition position,
        double velocityX,
        double velocityY,
        double velocityZ,
        boolean onGround,
        boolean inWater,
        boolean inLava,
        boolean fallFlying,
        boolean climbing,
        boolean inVehicle,
        boolean dead,
        boolean dimensionChanged,
        boolean teleported
) {
    public boolean isFinite() {
        return position != null
                && Double.isFinite(position.x())
                && Double.isFinite(position.y())
                && Double.isFinite(position.z())
                && Double.isFinite(velocityX)
                && Double.isFinite(velocityY)
                && Double.isFinite(velocityZ);
    }
}
