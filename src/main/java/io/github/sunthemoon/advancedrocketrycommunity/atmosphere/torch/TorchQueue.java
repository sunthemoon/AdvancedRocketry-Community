package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.PrimitiveIterator;

/**
 * One Level's fair pending torch positions and volume sweeps (ADR-066 section 3.2). Positions are packed block
 * positions; nothing here reads the world. FIFO order, at most 8,192 distinct positions and 128 sweeps, and at most
 * 256 checks per tick shared one-for-one between queued positions and sweep cells.
 */
public final class TorchQueue {
    public static final int MAX_POSITIONS = 8_192;
    public static final int CHECKS_PER_TICK = 256;
    public static final int MAX_SWEEPS = 128;

    /** What one check of a queued position decided. */
    public enum Outcome { DONE, DEFER }

    /** Result of {@link #offer}. */
    public enum Offer { ADDED, PRESENT, FULL }

    private final LinkedHashSet<Long> positions = new LinkedHashSet<>();
    private final ArrayDeque<PrimitiveIterator.OfLong> sweeps = new ArrayDeque<>();
    private boolean overloaded;
    private long overflows;
    private long droppedSweeps;
    private long checks;
    private long deferrals;
    private long sweepCells;

    /** Appends a position unless it is already queued or the queue is full. */
    public Offer offer(long position) {
        if (positions.contains(position)) {
            return Offer.PRESENT;
        }
        if (positions.size() >= MAX_POSITIONS) {
            overflows++;
            return Offer.FULL;
        }
        positions.add(position);
        return Offer.ADDED;
    }

    public boolean full() {
        return positions.size() >= MAX_POSITIONS;
    }

    /**
     * Records a refusal caused by a full queue; true only for the first refusal of an overload episode, which ends
     * when the queue drains to half.
     */
    public boolean refuseOverloaded() {
        overflows++;
        boolean first = !overloaded;
        overloaded = true;
        return first;
    }

    /** Queues a sweep of one lost volume's cells; a sweep beyond 128 is dropped and counted. */
    public boolean addSweep(PrimitiveIterator.OfLong cells) {
        Objects.requireNonNull(cells, "cells");
        if (sweeps.size() >= MAX_SWEEPS) {
            droppedSweeps++;
            return false;
        }
        if (cells.hasNext()) {
            sweeps.addLast(cells);
        }
        return true;
    }

    /**
     * Spends at most 256 checks. Queued positions are taken in FIFO order and visited at most once per tick; a
     * deferred one returns to the tail. Sweep cells are read only while the queue has room for what they find.
     */
    public void tick(Checker checker, SweepReader reader) {
        int budget = CHECKS_PER_TICK;
        int unvisited = positions.size();
        List<Long> deferred = new ArrayList<>();
        while (budget > 0 && (unvisited > 0 || !sweeps.isEmpty())) {
            boolean progressed = false;
            if (unvisited > 0) {
                Iterator<Long> head = positions.iterator();
                long position = head.next();
                head.remove();
                unvisited--;
                budget--;
                checks++;
                progressed = true;
                if (checker.check(position) == Outcome.DEFER) {
                    deferred.add(position);
                    deferrals++;
                }
            }
            if (budget > 0 && !sweeps.isEmpty() && positions.size() + deferred.size() < MAX_POSITIONS) {
                PrimitiveIterator.OfLong sweep = sweeps.removeFirst();
                long cell = sweep.nextLong();
                budget--;
                sweepCells++;
                progressed = true;
                if (reader.candidate(cell) && !deferred.contains(cell)) {
                    positions.add(cell);
                }
                if (sweep.hasNext()) {
                    sweeps.addLast(sweep);
                }
            }
            if (!progressed) {
                break;
            }
        }
        positions.addAll(deferred);
        if (positions.size() <= MAX_POSITIONS / 2) {
            overloaded = false;
        }
    }

    public Metrics metrics() {
        return new Metrics(positions.size(), sweeps.size(), overflows, droppedSweeps, checks, deferrals, sweepCells);
    }

    public void clear() {
        positions.clear();
        sweeps.clear();
        overloaded = false;
    }

    /** Decides one queued position. */
    @FunctionalInterface
    public interface Checker {
        Outcome check(long position);
    }

    /** Whether a swept cell holds a torch that must be queued. */
    @FunctionalInterface
    public interface SweepReader {
        boolean candidate(long position);
    }

    /** Observable counts; the counters are cumulative for the Level's lifetime. */
    public record Metrics(int queued, int sweeps, long overflows, long droppedSweeps, long checks, long deferrals,
                          long sweepCells) {
    }
}
