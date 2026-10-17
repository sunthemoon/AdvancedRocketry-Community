package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Negative observations only. A cache hit keeps the hold; it never supplies action authority. */
final class EscrowGovernedEnvelopeHolds {
    private final Map<UUID, Rejected> rejected = new HashMap<>();

    <T> T capture(UUID logical, Object root, RocketEntity first, RocketEntity second, Supplier<T> capture) {
        return capture(logical, root, first, first == null ? -1 : first.governedObservation(),
                second, second == null ? -1 : second.governedObservation(), capture);
    }

    <T> T capture(UUID logical, Object root, Object first, long firstObservation,
                  Object second, long secondObservation, Supplier<T> capture) {
        var previous = rejected.get(logical);
        if (previous != null && previous.root == root && previous.first == first && previous.second == second
                && previous.firstObservation == firstObservation && previous.secondObservation == secondObservation) { return null; }
        EscrowTags.require(previous != null || rejected.size() < RocketFlightLimits.MAX_ACTIVE_TRANSFERS,
                "Governed negative observation capacity exceeded");
        T value;
        try { value = capture.get(); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) {
            rejected.put(logical, new Rejected(root, first, firstObservation, second, secondObservation));
            throw invalid; // Preserve the adapter's first-failure cleanup; later identical polls stay held.
        }
        if (value == null) { rejected.put(logical, new Rejected(root, first, firstObservation, second, secondObservation)); }
        else { rejected.remove(logical); }
        return value;
    }

    void retain(Predicate<UUID> present) { rejected.keySet().removeIf(logical -> !present.test(logical)); }
    boolean rejected(UUID logical) { return rejected.containsKey(logical); }
    void clear() { rejected.clear(); }
    private record Rejected(Object root, Object first, long firstObservation, Object second, long secondObservation) { }
}
