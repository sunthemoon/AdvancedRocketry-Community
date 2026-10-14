package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ADR-066 section 3.3: the resumable, fair and bounded nearest-authorized-station query of the space safety return.
 * One job per player and at most {@link #MAX_JOBS}; per server tick at most {@link #INSPECTIONS_PER_JOB} candidate
 * inspections for one job, {@link #INSPECTIONS_PER_TICK} in total and {@link #PROBES_PER_TICK} loaded-cell probes.
 *
 * <p>A job keeps a stable cursor (the last station ID inspected, in UUID order) and its best candidate until it has
 * examined the whole candidate set; a candidate is probed only when it is nearer than the best, so the first safe
 * prefix is never the answer. Nearest is the squared horizontal distance from the captured start to the arrival
 * centre, ties by the lower station UUID. A complete pass re-inspects and re-probes the best within one tick (the
 * commit); a failure there, or a change of the index generation or of the player's access class, starts a fresh pass.
 * Candidate probes are interruptible: a job remembers the candidate and the next probe step. An unavailable
 * authority ends the job with a refusal, never with the Overworld. Jobs are served round robin, and a job the budget
 * stopped keeps its place. Pure and single-threaded; the server owns one instance.
 */
public final class SafetyReturnQuery {
    public static final int MAX_JOBS = 64;
    public static final int INSPECTIONS_PER_JOB = 32;
    public static final int INSPECTIONS_PER_TICK = 128;
    public static final int PROBES_PER_TICK = 256;
    /** Floor, feet and head of one arrival. */
    public static final int PROBE_STEPS = 3;

    /** What the query reads; the server adapter never loads a chunk for it. */
    public interface World {
        /** False while the station registry is blocked or unknown, or the catalog is missing: nothing is authorized. */
        boolean authorityAvailable();

        long generation();

        /** The player's current access class; operators use the all-station index. */
        boolean operator(UUID player);

        Optional<UUID> next(UUID player, boolean operator, UUID after);

        /** The station's arrival, if it still exists, the player may VISIT it and its body and profile resolve. */
        Optional<Target> inspect(UUID player, boolean operator, UUID station);

        /** One loaded-cell probe, step 0 to {@link #PROBE_STEPS} - 1; false for an unloaded or unsafe cell. */
        boolean probe(Target target, int step);
    }

    /** A station's arrival: the feet cell above the landing pad. */
    public record Target(UUID stationId, int x, int y, int z) {
        public Target {
            Objects.requireNonNull(stationId, "stationId");
        }
    }

    public sealed interface Result permits Station, Overworld, Refused {
        UUID player();
    }

    /** The nearest authorized station whose arrival was loaded and safe at the commit. */
    public record Station(UUID player, Target target, int passes) implements Result {
    }

    /** A complete pass found no authorized, loaded and safe arrival. */
    public record Overworld(UUID player, int passes) implements Result {
    }

    /** The station authority or catalog is unavailable: no station is authorized, and this is not an empty pass. */
    public record Refused(UUID player) implements Result {
    }

    /** What one tick used, for budgets and tests. */
    public record TickUse(int inspections, int probes, int maxJobInspections, int jobs) {
    }

    private final Map<UUID, Job> jobs = new HashMap<>();
    private ArrayDeque<UUID> order = new ArrayDeque<>();
    private int inspections;
    private int probes;
    private TickUse lastTick = new TickUse(0, 0, 0, 0);

    /** Starts a job from the captured position; false when the player already has one or the queue is full. */
    public boolean admit(UUID player, double startX, double startZ) {
        Objects.requireNonNull(player, "player");
        if (!Double.isFinite(startX) || !Double.isFinite(startZ)) {
            throw new IllegalArgumentException("The start position is not finite");
        }
        if (jobs.containsKey(player) || jobs.size() >= MAX_JOBS) {
            return false;
        }
        jobs.put(player, new Job(player, startX, startZ));
        order.addLast(player);
        return true;
    }

    public boolean pending(UUID player) {
        return jobs.containsKey(player);
    }

    public void cancel(UUID player) {
        if (jobs.remove(player) != null) {
            order.remove(player);
        }
    }

    public int size() {
        return jobs.size();
    }

    public TickUse lastTick() {
        return lastTick;
    }

    public void clear() {
        jobs.clear();
        order.clear();
        lastTick = new TickUse(0, 0, 0, 0);
    }

    /** One server tick: serves the jobs in turn within the budgets and returns the finished ones. */
    public List<Result> tick(World world) {
        Objects.requireNonNull(world, "world");
        inspections = 0;
        probes = 0;
        int maxJob = 0;
        int served = jobs.size();
        List<Result> results = new ArrayList<>();
        ArrayDeque<UUID> waiting = new ArrayDeque<>();
        ArrayDeque<UUID> progressed = new ArrayDeque<>();
        for (UUID player : order) {
            Job job = jobs.get(player);
            int before = inspections;
            Optional<Result> result = serve(world, job);
            maxJob = Math.max(maxJob, inspections - before);
            if (result.isPresent()) {
                jobs.remove(player);
                results.add(result.get());
            } else if (job.progressed) {
                progressed.addLast(player);
            } else {
                waiting.addLast(player);
            }
        }
        // A job the budget stopped keeps its place ahead of the jobs that were served.
        waiting.addAll(progressed);
        order = waiting;
        lastTick = new TickUse(inspections, probes, maxJob, served);
        return results;
    }

    private Optional<Result> serve(World world, Job job) {
        job.progressed = false;
        if (!world.authorityAvailable()) {
            return Optional.of(new Refused(job.player));
        }
        long generation = world.generation();
        boolean operator = world.operator(job.player);
        if (!job.started || generation != job.generation || operator != job.operator) {
            job.restart(generation, operator);
        }
        int turn = 0;
        while (true) {
            if (job.committing) {
                // The commit re-inspects and re-probes the best in one tick, so its safety is current when it moves.
                if (turn >= INSPECTIONS_PER_JOB || inspections >= INSPECTIONS_PER_TICK
                        || probes > PROBES_PER_TICK - PROBE_STEPS) {
                    return Optional.empty();
                }
                turn++;
                inspections++;
                job.progressed = true;
                Optional<Target> current = world.inspect(job.player, operator, job.best.stationId());
                boolean safe = current.isPresent() && current.get().equals(job.best);
                for (int step = 0; safe && step < PROBE_STEPS; step++) {
                    probes++;
                    safe = world.probe(job.best, step);
                }
                if (safe) {
                    return Optional.of(new Station(job.player, job.best, job.passes));
                }
                // The best arrival changed or unloaded: a fresh bounded pass.
                job.restart(generation, operator);
                continue;
            }
            if (job.probing != null) {
                while (job.probeStep < PROBE_STEPS) {
                    if (probes >= PROBES_PER_TICK) {
                        return Optional.empty();
                    }
                    probes++;
                    job.progressed = true;
                    if (!world.probe(job.probing, job.probeStep)) {
                        break;
                    }
                    job.probeStep++;
                }
                if (job.probeStep >= PROBE_STEPS) {
                    job.best = job.probing;
                    job.bestDistance = job.distance(job.probing);
                }
                job.probing = null;
                continue;
            }
            if (turn >= INSPECTIONS_PER_JOB || inspections >= INSPECTIONS_PER_TICK) {
                return Optional.empty();
            }
            turn++;
            inspections++;
            job.progressed = true;
            Optional<UUID> next = world.next(job.player, operator, job.cursor);
            if (next.isEmpty()) {
                job.passes++;
                if (job.best == null) {
                    return Optional.of(new Overworld(job.player, job.passes));
                }
                job.committing = true;
                continue;
            }
            job.cursor = next.get();
            Optional<Target> target = world.inspect(job.player, operator, job.cursor);
            if (target.isPresent() && job.better(target.get())) {
                job.probing = target.get();
                job.probeStep = 0;
            }
        }
    }

    private static final class Job {
        private final UUID player;
        private final double startX;
        private final double startZ;
        private boolean started;
        private long generation;
        private boolean operator;
        private UUID cursor;
        private Target best;
        private double bestDistance;
        private Target probing;
        private int probeStep;
        private boolean committing;
        private int passes;
        private boolean progressed;

        private Job(UUID player, double startX, double startZ) {
            this.player = player;
            this.startX = startX;
            this.startZ = startZ;
        }

        private void restart(long generation, boolean operator) {
            started = true;
            this.generation = generation;
            this.operator = operator;
            cursor = null;
            best = null;
            bestDistance = Double.POSITIVE_INFINITY;
            probing = null;
            probeStep = 0;
            committing = false;
        }

        private double distance(Target target) {
            double dx = target.x() + 0.5D - startX;
            double dz = target.z() + 0.5D - startZ;
            return dx * dx + dz * dz;
        }

        private boolean better(Target target) {
            double distance = distance(target);
            return best == null || distance < bestDistance
                    || (distance == bestDistance && target.stationId().compareTo(best.stationId()) < 0);
        }
    }
}
