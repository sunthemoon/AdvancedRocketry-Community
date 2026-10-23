package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.HashMap;
import java.util.Map;

/** Detached owner-thread observations around an admission call, outside its measured cost. */
final class CargoNativeAdmissionWitness {
    private CargoNativeAdmissionWitness() { }
    static Map<String, Object> capture(ResourceEscrowSavedData data, EscrowCoordinator coordinator) {
        var state = new HashMap<String, Object>();
        state.put("ledger", EscrowCodec.encode(data.ledger())); state.put("dirty", data.isDirty());
        state.put("sequence", coordinator.nextSequence());
        state.put("pending", coordinator.admissionAuthority().pendingCount());
        for (var field : new String[] {"queued", "requests", "frontiers", "transitions"}) {
            state.put(field, new HashMap<>((Map<?, ?>) CargoNativeBenchmark.field(coordinator, field)));
        }
        state.put("reservations", new HashMap<>((Map<?, ?>) CargoNativeBenchmark.field(
                coordinator.admissionAuthority(), "queuedReservations")));
        var writer = (EscrowLedgerWriter) CargoNativeBenchmark.field(coordinator, "writer");
        state.put("captured", java.util.List.copyOf(writer.capturedOperations()));
        state.put("generation", writer.lastGeneration()); state.put("idle", writer.idle());
        state.put("active", CargoNativeBenchmark.field(writer, "active"));
        state.put("successor", CargoNativeBenchmark.field(writer, "successor"));
        return state;
    }
}
