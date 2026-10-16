package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** F7's exact resource-only release. It is never general idle, launch, mount or destruction permission. */
final class EscrowTerminalSuccessor {
    private EscrowTerminalSuccessor() { }

    static boolean released(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.operational() || !rocket.isAlive() || !rocket.isAddedToWorld()) { return false; }
        var server = level.getServer(); var data = ResourceEscrowSavedData.existing(server);
        if (data == null) { return false; }
        var logical = rocket.assemblyTransactionId().orElseThrow();
        var journal = RocketTransferSavedData.get(server);
        var record = journal.findByLogicalRocket(logical).orElse(null);
        if (record == null || record.nativeTransfer().isEmpty() || !journal.actionAuthorityAvailable()
                || !data.bind(server).governedAvailable(logical)
                || EscrowRuntime.of(server).flightCheckpoint().locked(logical)) { return false; }
        try { return matches(data.ledger(), record, EscrowNativeEnvelope.rocket(rocket)); }
        catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
    }

    static boolean matches(EscrowLedger root, RocketTransferRecord record, CompoundTag observed) {
        if (EscrowGovernedDispatch.decide(root, record).route() != EscrowGovernedDispatch.Route.CLEANUP) { return false; }
        var arm = root.armedFlights().stream().filter(value -> value.transfer().equals(record.transferId())).findFirst().orElseThrow();
        boolean destination = arm.outcome() == EscrowFlightTables.Outcome.HANDED_OFF;
        var expected = destination ? record.nativeTransfer().orElseThrow().destinationEnvelope(record)
                : record.nativeTransfer().orElseThrow().sourceEnvelope(record);
        var wrapper = RocketEscrowSection.decode(observed.getCompound("wrapper"));
        var flight = EscrowGovernedArrivalState.flight(observed);
        var original = destination ? record.destinationFlightData() : record.sourceFlightData();
        long epoch = destination ? 0 : arm.epoch();
        // Resource changes after release advance the epoch. The saved head retains its proved floor.
        return observed.getUUID("physical").equals(destination ? arm.destination() : arm.source())
                && observed.getUUID("logical").equals(arm.logical()) && observed.getUUID("owner").equals(arm.owner())
                && observed.getString("type").equals(expected.getString("type"))
                && observed.getString("level").equals(expected.getString("level"))
                && observed.getCompound("snapshot").equals(expected.getCompound("snapshot"))
                && arm.ledger().equals(wrapper.ledger()) && wrapper.markers().isEmpty() && wrapper.epoch() >= epoch
                && (wrapper.epoch() != epoch || flight.fuel().equals(original.fuel()))
                && flight.fuel().committedDebits().equals(original.fuel().committedDebits())
                && flight.state().acceptsFuel() && flight.activeTransferId().isEmpty()
                && flight.currentBody().equals(original.currentBody()) && flight.currentDimension().equals(original.currentDimension())
                && flight.currentOrigin().equals(original.currentOrigin()) && flight.currentTarget().equals(original.currentTarget());
    }

    static boolean fuelUpdate(RocketEntity rocket, RocketFlightData candidate) {
        if (!released(rocket)) { return false; }
        var before = rocket.flightData().orElseThrow();
        return !before.fuel().equals(candidate.fuel()) && before.state().acceptsFuel()
                && before.withFuel(candidate.fuel(), candidate.stateStartedGameTime()).equals(candidate);
    }
}
