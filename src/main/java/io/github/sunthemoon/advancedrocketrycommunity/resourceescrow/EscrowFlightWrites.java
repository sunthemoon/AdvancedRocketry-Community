package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.BiFunction;

/**
 * Bounded owner-thread batching; one exact generation acknowledges all independent requests. A request whose guard
 * refuses at capture or immediately before replacement alone receives false; the others are captured again.
 */
final class EscrowFlightWrites {
    private record Request(UUID logical, BiFunction<EscrowLedger, Long, EscrowLedger> change, BooleanSupplier valid, Consumer<Boolean> callback) { }
    private final Map<UUID, Request> queued = new LinkedHashMap<>();
    private List<Request> active = List.of();
    private boolean closed;

    /** valid must use only retained atomic proof tokens/leases: the writer also runs it off-thread. */
    boolean request(UUID logical, BiFunction<EscrowLedger, Long, EscrowLedger> change, BooleanSupplier valid, Consumer<Boolean> callback) {
        java.util.Objects.requireNonNull(logical); java.util.Objects.requireNonNull(change);
        java.util.Objects.requireNonNull(valid); java.util.Objects.requireNonNull(callback);
        if (closed || queued.size() + active.size() >= 64 || locked(logical)) { return false; }
        queued.put(logical, new Request(logical, change, valid, callback)); return true;
    }

    boolean pending() { return !queued.isEmpty(); }
    boolean locked(UUID logical) { return queued.containsKey(logical) || active.stream().anyMatch(value -> value.logical().equals(logical)); }

    /** Called only when the shared writer and coordinator cadence permit another immutable capture. */
    boolean start(Supplier<EscrowLedger> ledger, EscrowLedgerWriter writer, Consumer<EscrowLedgerWriter.Completion> acknowledge) {
        if (closed || queued.isEmpty() || !active.isEmpty() || !writer.idle() || writer.unavailable()) { return false; }
        var selected = List.copyOf(queued.values()); queued.clear();
        var accepted = new ArrayList<Request>(); var refused = new ArrayList<Request>();
        EscrowLedger candidate = ledger.get(); long generation = Math.addExact(writer.lastGeneration(), 1);
        for (var request : selected) {
            try {
                if (!request.valid().getAsBoolean()) { refused.add(request); continue; }
                var next = request.change().apply(candidate, generation);
                requireScope(candidate, next, request.logical());
                EscrowSettlementCapacity.preflight(next); candidate = next; accepted.add(request);
            } catch (IllegalArgumentException | ArithmeticException invalid) { refused.add(request); }
        }
        // Do not call gameplay continuations while the immutable candidate is still being assembled.
        active = List.copyOf(accepted);
        if (active.isEmpty()) { refused.forEach(value -> value.callback().accept(false)); return false; }
        var capturedRequests = active;
        var snapshot = EscrowLedgerWriter.Snapshot.of(candidate.generation(generation), Set.of());
        // Written by the writer's guard on its IO thread; the completion queue publishes it to the owner thread.
        var refusedAtReplace = new java.util.concurrent.atomic.AtomicReference<Request>();
        boolean started = writer.request(() -> snapshot, result -> {
            acknowledge.accept(result);
            if (result.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN) { return; }
            active = List.of();
            if (!closed) {
                boolean committed = result.outcome() == EscrowLedgerWriter.Outcome.COMMITTED
                        && result.generation() == generation && result.digest().equals(snapshot.digest());
                var guardRefused = committed ? null : refusedAtReplace.get();
                if (guardRefused != null) {
                    // R17 item 3: only the request whose guard refused receives false; the others are captured again.
                    var again = new LinkedHashMap<UUID, Request>();
                    capturedRequests.stream().filter(value -> value != guardRefused).forEach(value -> again.put(value.logical(), value));
                    again.putAll(queued); queued.clear(); queued.putAll(again);
                    guardRefused.callback().accept(false);
                    return;
                }
                capturedRequests.forEach(value -> value.callback().accept(committed));
            }
        }, () -> {
            refusedAtReplace.set(null);
            for (var value : capturedRequests) {
                if (!value.valid().getAsBoolean()) { refusedAtReplace.set(value); return false; }
            }
            return true;
        });
        if (!started) {
            active = List.of();
            capturedRequests.forEach(value -> queued.put(value.logical(), value));
        }
        refused.forEach(value -> value.callback().accept(false));
        return started;
    }

    private static void requireScope(EscrowLedger before, EscrowLedger after, UUID logical) {
        EscrowTags.require(after.id().equals(before.id()) && after.writerGeneration() == before.writerGeneration()
                && after.issuedThrough() == before.issuedThrough() && after.records().equals(before.records())
                && after.floors().equals(before.floors()) && after.tombstones().equals(before.tombstones()), "Flight change altered unrelated escrow authority");
        EscrowTags.require(after.issuedLaunchSerial() == before.issuedLaunchSerial()
                || before.issuedLaunchSerial() < Long.MAX_VALUE && after.issuedLaunchSerial() == before.issuedLaunchSerial() + 1,
                "Flight serial did not advance exactly once");
        EscrowTags.require(before.rocketAuthorities().stream().filter(value -> !value.logical().equals(logical)).toList()
                .equals(after.rocketAuthorities().stream().filter(value -> !value.logical().equals(logical)).toList())
                && before.armedFlights().stream().filter(value -> !value.logical().equals(logical)).toList()
                .equals(after.armedFlights().stream().filter(value -> !value.logical().equals(logical)).toList()), "Flight change crossed logical scope");
    }

    void close() { closed = true; queued.clear(); active = List.of(); }
}
