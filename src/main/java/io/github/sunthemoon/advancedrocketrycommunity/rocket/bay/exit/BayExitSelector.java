package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;

/** Server-lifetime arbitration model, not an authority/permit or fall-protection service. Owner-thread only. */
public final class BayExitSelector {
    public static final int LIVE_LIMIT = 1024;
    public enum Status { SAFE, NO_SAFE_EXIT, EXIT_BUDGET_EXHAUSTED, REENTRANT_REFUSED, CAPACITY_REFUSED, STALE, PENDING }
    public record Request(Position rocket, Position fallback, Optional<Position> hint, Optional<Anchor> anchor,
                          double width, double height) {
        public Request {
            Objects.requireNonNull(rocket); Objects.requireNonNull(fallback);
            Objects.requireNonNull(hint); Objects.requireNonNull(anchor);
            if (!fallback.finite() || !Objects.equals(rocket.level(), fallback.level())) {
                throw new IllegalArgumentException("Fallback must be finite and in the rocket's Level");
            }
        }
    }
    /** Ordinal is one-based, zero for a fallback; sequence zero means no selection attempt was started. */
    public record Result(Status status, Position position, int ordinal, long attemptSequence, long captureSequence,
                         BayExitBudget.Usage work) { }

    /** One handle per actual detach. S2 owns player-instance/identity checks and must reuse this handle. */
    public final class Context {
        private final BayExitSelector source = BayExitSelector.this;
        private final Request request;
        private final BooleanSupplier current;
        private final Port safetyPort;
        private final long capture;
        private Result result;
        private Box reserved;
        private Context(Request request, BooleanSupplier current, Port safetyPort, long capture) {
            this.request = request; this.current = current; this.safetyPort = safetyPort; this.capture = capture;
        }
        public Optional<Result> result() { budget.checkThread(); return Optional.ofNullable(result); }
        public long captureSequence() { return capture; }
    }

    private final BayExitBudget budget;
    private final Port safety;
    private final Set<Context> live = Collections.newSetFromMap(new IdentityHashMap<>());
    private final ArrayDeque<Context> delayed = new ArrayDeque<>();
    // Width <= 1 permits a bounded x-range visitor, with every visited reservation charged (even z misses).
    private final TreeMap<Double, Set<Context>> reservations = new TreeMap<>();
    private Context active;
    private boolean draining;
    private long captures;
    private long attempts;

    public BayExitSelector(BayExitBudget budget, Port safety) {
        this.budget = Objects.requireNonNull(budget); this.safety = Objects.requireNonNull(safety);
    }

    /** Capture allocates no attempt, candidate, block-read or entity-visit work. */
    public Context capture(Request request, BooleanSupplier current) {
        return capture(request, current, safety);
    }

    /** Bind the exact passenger/Level read view without creating another arbitrator or budget. */
    public Context capture(Request request, BooleanSupplier current, Port safetyPort) {
        budget.checkThread();
        var context = new Context(Objects.requireNonNull(request), Objects.requireNonNull(current),
                Objects.requireNonNull(safetyPort), ++captures);
        if (live.size() == LIVE_LIMIT || draining) { context.result = refusal(context, Status.CAPACITY_REFUSED); }
        else { live.add(context); delayed.addLast(context); }
        return context;
    }

    public Result select(Context context) {
        budget.checkThread();
        requireOwn(context);
        budget.enterWork();
        try { return selectOwned(context); }
        finally { budget.leaveWork(); }
    }

    private Result selectOwned(Context context) {
        if (context.result != null) { return context.result; }
        if (active != null) {
            // The same in-flight decision has no position yet. PENDING is not a placement/fallback instruction.
            if (active == context) { return refusal(context, Status.PENDING); }
            context.result = refusal(context, Status.REENTRANT_REFUSED);
            return context.result;
        }
        // Validity is external code: guard even the preflight that allocates no attempt or probe.
        active = context;
        long sequence = 0;
        var before = budget.usage();
        try {
            if (!live.contains(context) || !context.current.getAsBoolean()) {
                context.result = refusal(context, Status.STALE);
                return context.result;
            }
            sequence = ++attempts;
            Request r = context.request;
            List<Position> candidates = BayExitCandidates.ordered(r.rocket, r.hint, r.anchor);
            boolean exhausted = false;
            int ordinal = 0;
            for (Position position : candidates) {
                if (++ordinal > BayExitBudget.CANDIDATES_PER_EXIT) { exhausted = true; break; }
                var allowance = budget.openProbe();
                if (allowance.isEmpty()) { exhausted = true; break; }
                try (var probe = allowance.orElseThrow()) {
                    Box box = new Box(position, r.width, r.height);
                    boolean[] checked = {false};
                    boolean[] clear = {false};
                    Verdict verdict = box.valid() ? context.safetyPort.inspect(box, probe, (query, allowanceProbe) -> {
                        if (!box.equals(query) || allowanceProbe != probe) { return false; }
                        if (!checked[0]) { clear[0] = clearReservations(box, probe); checked[0] = true; }
                        return clear[0];
                    }) : Verdict.UNSAFE;
                    exhausted |= probe.incomplete();
                    if (!context.current.getAsBoolean()) {
                        context.result = new Result(Status.STALE, r.fallback, 0, sequence, context.capture,
                                budget.usage().minus(before));
                        return context.result;
                    }
                    if (verdict == Verdict.SAFE && checked[0] && clear[0] && !probe.incomplete()) {
                        context.reserved = box;
                        reservations.computeIfAbsent(box.minX(), key -> new LinkedHashSet<>())
                                .add(context);
                        context.result = new Result(Status.SAFE, position, ordinal, sequence, context.capture,
                                budget.usage().minus(before));
                        return context.result;
                    }
                }
            }
            context.result = new Result(exhausted ? Status.EXIT_BUDGET_EXHAUSTED : Status.NO_SAFE_EXIT,
                    r.fallback, 0, sequence, context.capture, budget.usage().minus(before));
            return context.result;
        } finally {
            if (context.result == null) {
                context.result = new Result(Status.NO_SAFE_EXIT, context.request.fallback, 0, sequence,
                        context.capture, budget.usage().minus(before));
            }
            active = null;
        }
    }

    private boolean clearReservations(Box box, BayExitBudget.Probe probe) {
        for (Set<Context> bucket : reservations.subMap(box.minX() - 1.0, false, box.maxX(), false).values()) {
            for (Context other : bucket) {
                if (!probe.visit() || other.reserved.overlaps(box)) { return false; }
            }
        }
        return true;
    }

    /** Application must already be observed in entity occupancy before removing its reservation. */
    public void applied(Context context) {
        budget.checkThread();
        requireOwn(context);
        if (active != null) { throw new IllegalStateException("Cannot apply during an exit decision"); }
        if (context.reserved != null && live.contains(context)) {
            Set<Context> bucket = reservations.get(context.reserved.minX());
            bucket.remove(context);
            if (bucket.isEmpty()) { reservations.remove(context.reserved.minX()); }
            context.reserved = null;
        }
    }

    /** A stale/failed context only releases its own reservation. Cached outcomes remain readable on the handle. */
    public void release(Context context) {
        budget.checkThread();
        requireOwn(context);
        if (active != null) { throw new IllegalStateException("Cannot release during an exit decision"); }
        applied(context); live.remove(context); delayed.remove(context);
        if (context.result == null) { context.result = refusal(context, Status.STALE); }
    }

    /**
     * Next server-tick END: finish captured bypasses FIFO using this tick's remaining allowance. The consumer
     * applies each decision synchronously before the next one runs. Stale completions never invoke it.
     * Previously selected contexts expire here too. Captures during the drain refuse, rather than extending it.
     */
    public void drain(Consumer<Context> completion) {
        budget.checkThread(); Objects.requireNonNull(completion);
        if (active != null || draining) { throw new IllegalStateException("Cannot nest exit END drains"); }
        draining = true;
        var batch = new ArrayList<>(delayed);
        budget.enterWork();
        try {
            for (Context context : batch) {
                boolean current;
                // The END preflight has the same callback boundary as synchronous selection.
                active = context;
                try { current = context.current.getAsBoolean(); }
                finally { active = null; }
                if (!current) { release(context); }
                else if (context.result == null && select(context).status != Status.STALE) { completion.accept(context); }
            }
        } finally {
            // Keep not-yet-applied reservations through the entire drain, independent of capture order.
            try { for (Context context : batch) { release(context); } }
            finally { draining = false; budget.leaveWork(); }
        }
    }

    private void requireOwn(Context context) {
        if (context.source != this) { throw new IllegalArgumentException("Foreign exit context"); }
    }

    private Result refusal(Context context, Status status) {
        return new Result(status, context.request.fallback, 0, 0, context.capture, new BayExitBudget.Usage(0, 0, 0));
    }
}
