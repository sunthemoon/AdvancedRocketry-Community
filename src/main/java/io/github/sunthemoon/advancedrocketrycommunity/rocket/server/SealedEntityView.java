package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightEvent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightStateMachine;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Frozen standalone D identity. No tickets, synthetic transfer record, hash-only adoption or native reinitialization. */
final class SealedEntityView {
    private final ServerLevel level;
    private final RocketPersistedTransaction entry;
    private final List<ChunkPos> columns;
    private RocketEntity frozenEntity;
    private RocketFlightData flight;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot frozenSnapshot;
    private CompoundTag frozen;
    private UUID logical;

    SealedEntityView(ServerLevel level, RocketPersistedTransaction entry, int terrainSlots) {
        this.level = level; this.entry = entry;
        columns = columns(entry.snapshot(), terrainSlots);
        if (columns.isEmpty()) { throw new IllegalArgumentException("Sealed entity discovery exceeds existing proof bounds"); }
    }
    /** Ordinary no-T snapshots keep their existing snapshot limits, not the governed flight's sixteen-column admission. */
    static List<ChunkPos> columns(RocketStructureSnapshot snapshot, int terrainSlots) {
        if (terrainSlots < 1 || terrainSlots >= RocketOrdinaryRetirement.MAX_SLOTS) { return List.of(); }
        var region = io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion.fromSnapshot(snapshot);
        try {
            int minX = Math.subtractExact(region.minimum().x(), 1) >> 4, minZ = Math.subtractExact(region.minimum().z(), 1) >> 4;
            int maxX = Math.addExact(region.maximum().x(), 2) >> 4, maxZ = Math.addExact(region.maximum().z(), 2) >> 4;
            long count = ((long) maxX - minX + 1) * ((long) maxZ - minZ + 1);
            var origin = RocketRetirementProof.Slot.origin(snapshot).chunk();
            if (count < 1 || count + terrainSlots > RocketOrdinaryRetirement.MAX_SLOTS
                    || origin.x < minX || origin.x > maxX || origin.z < minZ || origin.z > maxZ) { return List.of(); }
            var columns = new java.util.ArrayList<ChunkPos>();
            for (int x = minX; x <= maxX; x++) { for (int z = minZ; z <= maxZ; z++) { columns.add(new ChunkPos(x, z)); } }
            return List.copyOf(columns);
        } catch (ArithmeticException overflow) { return List.of(); }
    }
    List<ChunkPos> columns() { return columns; }
    UUID logical() { return logical; }
    boolean loaded() { return columns.stream().allMatch(column -> level.areEntitiesLoaded(column.toLong())); }
    SealedDisassembly.Entity observe() {
        if (!loaded()) { return SealedDisassembly.Entity.UNLOADED; }
        var candidate = level.getEntity(entry.record().rocketEntityId());
        if (candidate == null) { return SealedDisassembly.Entity.ABSENT; }
        if (!(candidate instanceof RocketEntity rocket) || !eligible(rocket)) { return SealedDisassembly.Entity.OTHER; }
        if (frozenEntity == null) {
            // A restart may rebind only the full U snapshot and owner, not a similarly shaped rocket.
            if (!RocketSnapshotNbtCodec.encode(entry.snapshot()).equals(RocketSnapshotNbtCodec.encode(rocket.snapshot().orElseThrow()))) {
                return SealedDisassembly.Entity.OTHER;
            }
            var image = new CompoundTag();
            if (!rocket.save(image) || image.contains("Passengers")) { return SealedDisassembly.Entity.OTHER; }
            frozenEntity = rocket; flight = rocket.flightData().orElseThrow(); frozen = image;
            frozenSnapshot = rocket.snapshot().orElseThrow();
            logical = rocket.assemblyTransactionId().orElseThrow();
        }
        return rocket == frozenEntity && rocket.flightData().orElse(null) == flight
                && rocket.snapshot().orElse(null) == frozenSnapshot
                ? SealedDisassembly.Entity.MATCHING : SealedDisassembly.Entity.OTHER;
    }
    private boolean eligible(RocketEntity rocket) {
        var origin = entry.snapshot().sourceOrigin();
        return RocketTransferEntities.loadedEntity(rocket) && rocket.operational()
                && rocket.ownerId().filter(entry.ownerId()::equals).isPresent()
                && rocket.snapshot().filter(snapshot -> snapshot.snapshotId().equals(entry.snapshot().snapshotId())
                && snapshot.contentHash().equals(entry.snapshot().contentHash())).isPresent()
                && rocket.assemblyTransactionId().isPresent() && !rocket.isPassenger() && rocket.getPassengers().isEmpty()
                && rocket.flightData().filter(data -> RocketFlightStateMachine.isLegal(data.state(), RocketFlightEvent.DISASSEMBLE)
                && data.passengers().assignments().isEmpty() && data.activeTransferId().isEmpty()).isPresent()
                && rocket.getX() == origin.x() + 0.5D && rocket.getY() == origin.y() && rocket.getZ() == origin.z() + 0.5D
                && RocketEscrowGate.retirementUnprotected(rocket);
    }
    boolean discard() {
        if (observe() != SealedDisassembly.Entity.MATCHING) { return false; }
        frozenEntity.discard();
        return level.getEntity(entry.record().rocketEntityId()) == null;
    }
    boolean image(ChunkPos column, CompoundTag root, boolean requirePresent) {
        boolean origin = column.equals(RocketRetirementProof.Slot.origin(entry.snapshot()).chunk());
        return requirePresent && origin ? frozen != null && SealedEntityImages.present(root, logical,
                entry.snapshot().snapshotId(), entry.record().rocketEntityId(), frozen)
                : SealedEntityImages.absent(root, logical, entry.snapshot().snapshotId(), entry.record().rocketEntityId());
    }
}
