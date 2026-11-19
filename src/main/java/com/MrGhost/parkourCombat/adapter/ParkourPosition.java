package com.MrGhost.parkourCombat.adapter;

public record ParkourPosition(double x, double y, double z) {
    public double distanceSquared(ParkourPosition other) {
        double dx = this.x - other.x;
        double dy = this.y - other.y;
        double dz = this.z - other.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
