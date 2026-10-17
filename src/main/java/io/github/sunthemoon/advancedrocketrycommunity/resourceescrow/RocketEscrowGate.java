package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/** Read-only guards: neither an ordinary rocket nor a policy query creates the lazy ledger. */
public final class RocketEscrowGate {
    private RocketEscrowGate() { }

    public static boolean idle(RocketEntity rocket) {
        return escrowIdle(rocket);
    }

    public static boolean resourcesIdle(RocketEntity rocket) {
        return escrowIdle(rocket) || terminalSuccessor(rocket);
    }

    public static boolean terminalSuccessor(RocketEntity rocket) { return EscrowTerminalSuccessor.released(rocket); }

    public static boolean terminalFuelUpdate(RocketEntity rocket,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData candidate) {
        return EscrowTerminalSuccessor.fuelUpdate(rocket, candidate);
    }

    /** Frozen flight authority also protects non-resource state and the boarded manifest. */
    public static boolean flightFrozen(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) { return true; }
        if (rocket.assemblyTransactionId().filter(EscrowRuntime.of(level.getServer()).flightCheckpoint()::locked).isPresent()) { return true; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        return data != null && data.ledger().armedFlights().stream()
                .anyMatch(arm -> rocket.assemblyTransactionId().filter(arm.logical()::equals).isPresent());
    }

    /** Bay-scoped permits may bypass only the bay hold, never these existing escrow protections. */
    public static boolean escrowIdle(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) {
            return false;
        }
        var section = rocket.escrowSection();
        UUID logical = rocket.assemblyTransactionId().orElse(null);
        if (logical != null && EscrowRuntime.of(level.getServer()).flightCheckpoint().locked(logical)) { return false; }
        if (!section.markers().isEmpty()) { return false; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return section.ledger() == null; }
        var ledger = data.ledger();
        if (logical != null && ledger.armedFlights().stream().anyMatch(arm -> arm.logical().equals(logical))) { return false; }
        if (section.ledger() != null && !section.ledger().equals(ledger.id())) { return false; }
        if (logical != null && data.bind(level.getServer()).logicalLocked(logical)) { return false; }
        if (ledger.records().stream().anyMatch(record -> rocket.getUUID().equals(record.source().identity())
                || rocket.getUUID().equals(record.destination().identity()))) { return false; }
        if (logical == null) { return false; }
        // An unrelated ledger/receipt must not require a legacy rocket to have a native fuel vector.
        if (!EscrowSettlementTombstones.history(ledger, logical)) { return true; }
        if (rocket.flightData().isEmpty() || rocket.snapshot().isEmpty()
                || rocket.flightData().orElseThrow().fuel().propulsion().isEmpty()) { return false; }
        String signature = rocket.flightData().orElseThrow().fuel().resourceSignature();
        var head = ledger.rocketAuthorities().stream().filter(value -> value.logical().equals(logical)).findFirst().orElse(null);
        if (head != null && (head.kind() != EscrowSignatureKind.FUEL_VECTOR_V1 || !head.owner().equals(rocket.ownerId().orElseThrow())
                || !head.invariant().equals(EscrowInvariant.rocket(rocket)) || section.epoch() < head.epoch()
                || section.epoch() == head.epoch() && !signature.equals(head.signature()))) { return false; }
        return !EscrowSettlementTombstones.stateLocked(ledger, logical, rocket.getUUID(), section, signature,
                rocket.flightData().orElseThrow().fuel().totalAmount());
    }

    public static boolean logicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        return escrowLogicalLocked(server, logical);
    }

    public static boolean escrowLogicalLocked(net.minecraft.server.MinecraftServer server, UUID logical) {
        if (EscrowRuntime.of(server).flightCheckpoint().locked(logical)) { return true; }
        var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return false; }
        return data.bind(server).logicalLocked(logical) || EscrowSettlementTombstones.logicalLocked(data.ledger(), logical);
    }
}
