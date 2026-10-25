package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;

/** Native journal pressure only: no governed authority is published or released by this fixture. */
final class RocketRejoinNativeJournal {
    private RocketRejoinNativeJournal() { }
    static RocketTransferRecord governed(RocketTransferRecord legacy) {
        var ledger = UUID.randomUUID(); var destination = UUID.randomUUID();
        var sourceFlight = RocketTransferEntities.stationarySource(legacy, legacy.createdAtGameTime());
        var source = envelope(legacy, ledger, legacy.sourceEntityId(), false, sourceFlight, 7);
        var arrival = envelope(legacy, ledger, destination, true, legacy.destinationFlightData(), 0);
        var arm = new EscrowFlightTables.Armed(ledger, legacy.logicalRocketId(), legacy.ownerId(), legacy.sourceEntityId(), 7,
                legacy.transferId(), 1, 10, "1".repeat(64), "2".repeat(64), EscrowGovernedEnvelope.digest(source),
                EscrowGovernedEnvelope.digest(arrival), destination, new EscrowGuard(legacy.logicalRocketId(), null, 1,
                "3".repeat(64)), null, 0, EscrowFlightTables.Outcome.ARMED);
        var nativeTransfer = EscrowTransferEnvelope.capture(arm, source, arrival, legacy.sourceSnapshot(), legacy.destinationSnapshot(),
                legacy.sourceFlightData(), legacy.destinationFlightData());
        return RocketTransferRecord.createGoverned(legacy.transferId(), legacy.logicalRocketId(), legacy.ownerId(), legacy.sourceEntityId(),
                legacy.sourceSnapshot(), legacy.destinationSnapshot(), legacy.sourceFlightData(), legacy.destinationFlightData(),
                legacy.requiredFuel(), legacy.createdAtGameTime(), nativeTransfer);
    }
    private static CompoundTag envelope(RocketTransferRecord t, UUID ledger, UUID physical, boolean destination, RocketFlightData flight, long epoch) {
        var snapshot = destination ? t.destinationSnapshot() : t.sourceSnapshot(); var origin = snapshot.sourceOrigin();
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putUUID("physical", physical);
        tag.putString("type", "advancedrocketrycommunity:rocket"); tag.putUUID("logical", t.logicalRocketId()); tag.putUUID("owner", t.ownerId());
        tag.putString("level", snapshot.sourceDimension().toString()); tag.putIntArray("chunk", new int[] {origin.x() >> 4, origin.z() >> 4});
        tag.put("pos", vector(origin.x() + 0.5, origin.y(), origin.z() + 0.5));
        tag.putUUID("assembly_snapshot", snapshot.snapshotId()); tag.putString("content_hash", snapshot.contentHash());
        tag.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); tag.put("flight", RocketFlightNbtCodec.encode(flight));
        tag.put("wrapper", new RocketEscrowSection(ledger, epoch, List.of()).encode()); tag.put("motion", vector(0, 0, 0));
        tag.putBoolean("no_gravity", true); tag.putBoolean("no_physics", true); tag.putBoolean("on_ground", false);
        tag.putBoolean("invulnerable", false); tag.putFloat("yaw", 0); tag.putFloat("pitch", 0); tag.put("passengers", new ListTag());
        return tag;
    }
    private static ListTag vector(double x, double y, double z) {
        var tag = new ListTag(); tag.add(DoubleTag.valueOf(x)); tag.add(DoubleTag.valueOf(y)); tag.add(DoubleTag.valueOf(z)); return tag;
    }
}
