package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.nbt.CompoundTag;

/** F4 absence in each recorded slot, including a source/destination sharing one entity slot. */
final class EscrowGovernedDestinationDisk {
    private EscrowGovernedDestinationDisk() { }

    static EscrowEndpoint endpoint(RocketTransferRecord record, boolean destination) {
        var arm = record.nativeTransfer().orElseThrow().armed();
        var snapshot = destination ? record.destinationSnapshot() : record.sourceSnapshot();
        var signature = (destination ? record.destinationFlightData() : record.sourceFlightData()).fuel().resourceSignature();
        return new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, snapshot.sourceDimension().toString(),
                destination ? arm.destination() : arm.source(), arm.logical(), EscrowAdapterIds.ROCKET,
                snapshot.sourceOrigin().x() >> 4, 0, snapshot.sourceOrigin().z() >> 4,
                EscrowEndpoint.Unit.PRIMARY, destination ? 0 : arm.epoch(), signature, signature);
    }

    static Predicate<CompoundTag> validator(RocketTransferRecord record, EscrowEndpoint slot, CompoundTag source) {
        var sourceEndpoint = endpoint(record, false);
        boolean sourceSlot = EscrowBarrier.sameSlot(slot, sourceEndpoint);
        var present = source == null ? null : EscrowFlightSource.validator(sourceEndpoint, source);
        return root -> {
            try {
                int[] position = root.getIntArray("Position");
                if (position.length != 2 || position[0] != slot.x() || position[1] != slot.z()) { return false; }
                // The source validator rejects all other copies of L, and checks complete source E.
                if (sourceSlot && present != null && !present.test(root)) { return false; }
                return absent(EscrowTags.list(root, "Entities", EscrowSettlementObserver.ENTITIES_PER_SLOT),
                        record, sourceSlot && source != null, 0, new int[] {0});
            } catch (RuntimeException invalid) { return false; }
        };
    }

    private static boolean absent(List<net.minecraft.nbt.Tag> entries, RocketTransferRecord record,
                                  boolean sourceSlot, int depth, int[] visits) {
        EscrowTags.require(depth <= 16, "Publication slot passenger depth exceeded");
        var destination = record.nativeTransfer().orElseThrow().armed().destination();
        for (var raw : entries) {
            EscrowTags.require(++visits[0] <= EscrowSettlementObserver.ENTITIES_PER_SLOT, "Publication slot census exceeded");
            var object = (CompoundTag) raw;
            if (object.hasUUID("UUID") && destination.equals(object.getUUID("UUID"))) { return false; }
            var data = object.getCompound("RocketEntityData");
            if ((object.hasUUID("UUID") && record.sourceEntityId().equals(object.getUUID("UUID"))
                    || data.hasUUID("assembly_transaction_id") && record.logicalRocketId().equals(data.getUUID("assembly_transaction_id")))
                    && !sourceSlot) { return false; }
            if (object.contains("Passengers") && !absent(EscrowTags.list(object, "Passengers", EscrowSettlementObserver.ENTITIES_PER_SLOT),
                    record, sourceSlot, depth + 1, visits)) { return false; }
        }
        return true;
    }
}
