package com.MrGhost.parkourCombat.adapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Bounded, fixed-size history of motion snapshots. Insertion does not allocate;
 * a list is only created when a caller explicitly asks for a view.
 */
public final class MotionHistory {
    public static final int DEFAULT_CAPACITY = 20;

    private final MotionSnapshot[] samples;
    private int size;
    private int next;

    public MotionHistory() {
        this(DEFAULT_CAPACITY);
    }

    public MotionHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.samples = new MotionSnapshot[capacity];
    }

    public void add(MotionSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        samples[next] = snapshot;
        next = (next + 1) % samples.length;
        if (size < samples.length) {
            size++;
        }
    }

    public MotionSnapshot latest() {
        return size == 0 ? null : samples[(next - 1 + samples.length) % samples.length];
    }

    /**
     * @param age 0 = latest, 1 = previous, ...
     * @return snapshot or null when the age is outside the stored range
     */
    public MotionSnapshot get(int age) {
        if (age < 0 || age >= size) {
            return null;
        }
        int index = (next - 1 - age) % samples.length;
        if (index < 0) {
            index += samples.length;
        }
        return samples[index];
    }

    public int size() {
        return size;
    }

    /**
     * Actual capacity of this instance. Read-only; does not affect eviction.
     */
    public int capacity() {
        return samples.length;
    }

    public void clear() {
        Arrays.fill(samples, null);
        size = 0;
        next = 0;
    }

    /**
     * Newest-first view, allocated only on explicit query.
     */
    public List<MotionSnapshot> recent(int maxSamples) {
        if (maxSamples <= 0 || size == 0) {
            return Collections.emptyList();
        }
        int count = Math.min(maxSamples, size);
        List<MotionSnapshot> result = new ArrayList<>(count);
        for (int age = 0; age < count; age++) {
            result.add(get(age));
        }
        return Collections.unmodifiableList(result);
    }
}
