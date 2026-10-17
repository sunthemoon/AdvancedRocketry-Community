package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** F6 slot predicates only. Storage equality is not passenger authority, F7 or cleanup permission. */
final class EscrowGovernedTerminalDisk {
    private EscrowGovernedTerminalDisk() { }

    static List<EscrowEndpoint> slots(RocketTransferRecord record, EscrowFlightTables.Outcome outcome) {
        EscrowTags.require(outcome == EscrowFlightTables.Outcome.HANDED_OFF || outcome == EscrowFlightTables.Outcome.CANCELLED,
                "Terminal slot needs a terminal outcome");
        var source = EscrowGovernedDestinationDisk.endpoint(record, false);
        var destination = EscrowGovernedDestinationDisk.endpoint(record, true);
        // A shared slot's frontier belongs to its survivor, not the disappeared predecessor.
        return EscrowBarrier.sameSlot(source, destination)
                ? List.of(outcome == EscrowFlightTables.Outcome.HANDED_OFF ? destination : source) : List.of(source, destination);
    }

    static boolean survivor(RocketTransferRecord record, EscrowFlightTables.Outcome outcome, CompoundTag envelope) {
        try {
            if (outcome == EscrowFlightTables.Outcome.CANCELLED) {
                return EscrowGovernedReturnAction.returned(record, envelope);
            }
            if (outcome != EscrowFlightTables.Outcome.HANDED_OFF || envelope == null) { return false; }
            var flight = EscrowGovernedArrivalState.flight(envelope);
            return flight.state() == RocketFlightState.LANDED
                    && EscrowGovernedArrivalState.matches(record, envelope, flight.stateStartedGameTime());
        } catch (IllegalArgumentException | IllegalStateException | ArithmeticException invalid) { return false; }
    }

    static Predicate<CompoundTag> validator(RocketTransferRecord record, EscrowFlightTables.Outcome outcome,
                                           EscrowEndpoint slot, CompoundTag survivor) {
        EscrowTags.require(survivor(record, outcome, survivor), "Terminal checkpoint needs its complete stationary successor");
        EscrowTags.require(slots(record, outcome).stream().anyMatch(endpoint -> endpoint.equals(slot)), "Unrecorded terminal slot");
        var presentEnd = EscrowGovernedDestinationDisk.endpoint(record, outcome == EscrowFlightTables.Outcome.HANDED_OFF);
        var absentEnd = EscrowGovernedDestinationDisk.endpoint(record, outcome != EscrowFlightTables.Outcome.HANDED_OFF);
        boolean presentSlot = EscrowBarrier.sameSlot(slot, presentEnd);
        var present = EscrowFlightSource.validator(presentEnd, survivor);
        return root -> {
            try {
                int[] position = root.getIntArray("Position");
                if (position.length != 2 || position[0] != slot.x() || position[1] != slot.z()) { return false; }
                if (presentSlot && !present.test(root)) { return false; }
                return absent(EscrowTags.list(root, "Entities", EscrowSettlementObserver.ENTITIES_PER_SLOT),
                        record, presentEnd, absentEnd, presentSlot, 0, new int[] {0});
            } catch (RuntimeException invalid) { return false; }
        };
    }

    private static boolean absent(List<net.minecraft.nbt.Tag> entries, RocketTransferRecord record,
            EscrowEndpoint present, EscrowEndpoint absent, boolean presentSlot, int depth, int[] visits) {
        EscrowTags.require(depth <= 16, "Terminal slot passenger depth exceeded");
        for (var raw : entries) {
            EscrowTags.require(++visits[0] <= EscrowSettlementObserver.ENTITIES_PER_SLOT, "Terminal slot census exceeded");
            var object = (CompoundTag) raw;
            if (object.hasUUID("UUID") && absent.identity().equals(object.getUUID("UUID"))) { return false; }
            var data = object.getCompound("RocketEntityData");
            if ((object.hasUUID("UUID") && present.identity().equals(object.getUUID("UUID"))
                    || data.hasUUID("assembly_transaction_id") && record.logicalRocketId().equals(data.getUUID("assembly_transaction_id")))
                    && !presentSlot) { return false; }
            if (object.contains("Passengers") && !absent(EscrowTags.list(object, "Passengers", EscrowSettlementObserver.ENTITIES_PER_SLOT),
                    record, present, absent, presentSlot, depth + 1, visits)) { return false; }
        }
        return true;
    }
}
