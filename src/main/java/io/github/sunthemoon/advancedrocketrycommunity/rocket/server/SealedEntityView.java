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
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Frozen standalone D identity. No tickets, synthetic transfer record, hash-only adoption or native reinitialization. */
final class SealedEntityView {
    private final ServerLevel level;
    private final RocketPersistedTransaction entry;
    private final List<ChunkPos> columns;
    private record Binding(RocketEntity entity, RocketFlightData flight, RocketStructureSnapshot snapshot) { }
    private final io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget budget;
    private final Runnable changed;
    private Map<UUID, Binding> bindings = Map.of();
    private List<RocketEntity> copies = List.of();
    private long observedTick = Long.MIN_VALUE, epoch;
    private SealedDisassembly.Entity observation = SealedDisassembly.Entity.UNLOADED;
    private CompoundTag frozen;
    private UUID logical;

    SealedEntityView(ServerLevel level, RocketPersistedTransaction entry, int terrainSlots,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedWorkBudget budget, Runnable changed) {
        this.level = level; this.entry = entry; this.budget = budget; this.changed = changed;
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
    /** The original live request's exact D binding, not a new search for copies. The gate still owns that census. */
    boolean requestCurrent() {
        var binding = bindings.get(entry.record().rocketEntityId());
        return binding != null && !binding.entity.isRemoved() && eligible(binding.entity)
                && binding.flight == binding.entity.flightData().orElse(null)
                && binding.snapshot == binding.entity.snapshot().orElse(null);
    }
    boolean loaded() { return columns.stream().allMatch(column -> level.areEntitiesLoaded(column.toLong())); }
    boolean names(ServerLevel candidateLevel, net.minecraft.world.entity.Entity entity) {
        if (level != candidateLevel) { return false; }
        return entity.getUUID().equals(entry.record().rocketEntityId()) || entity instanceof RocketEntity rocket
                && (rocket.snapshot().filter(value -> value.snapshotId().equals(entry.snapshot().snapshotId())).isPresent()
                || logical != null && rocket.assemblyTransactionId().filter(logical::equals).isPresent());
    }
    void invalidate() { observedTick = Long.MIN_VALUE; epoch++; }
    SealedDisassembly.Entity observe() {
        long now = level.getServer().getTickCount();
        if (observedTick == now) { return observation; }
        observedTick = now; observation = SealedDisassembly.Entity.UNLOADED;
        if (!loaded()) { return SealedDisassembly.Entity.UNLOADED; }
        long generation = epoch;
        var found = new java.util.ArrayList<RocketEntity>();
        if (!budget.take()) { return observation; }
        var exact = level.getEntity(entry.record().rocketEntityId());
        if (exact != null) {
            if (!(exact instanceof RocketEntity rocket)) { return observation = SealedDisassembly.Entity.OTHER; }
            found.add(rocket);
        }
        var region = entry.record().region();
        var box = new net.minecraft.world.phys.AABB(region.minimum().x() - 1.0D, level.getMinBuildHeight(),
                region.minimum().z() - 1.0D, region.maximum().x() + 2.0D, level.getMaxBuildHeight(), region.maximum().z() + 2.0D);
        var exhausted = new boolean[1];
        level.getEntities().get(net.minecraft.world.level.entity.EntityTypeTest.forClass(RocketEntity.class), box, rocket -> {
            if (rocket == exact) { return net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE; }
            if (!budget.take()) { exhausted[0] = true; return net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT; }
            if (names(level, rocket)) { found.add(rocket); }
            return found.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                    ? net.minecraft.util.AbortableIterationConsumer.Continuation.ABORT
                    : net.minecraft.util.AbortableIterationConsumer.Continuation.CONTINUE;
        });
        if (exhausted[0] || generation != epoch) { return observation; }
        if (found.size() >= io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES) {
            return observation = SealedDisassembly.Entity.OTHER;
        }
        found.sort(java.util.Comparator.comparing(RocketEntity::getUUID,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferAuthorityOrder.preferred(entry.record().rocketEntityId())));
        var checked = new java.util.LinkedHashMap<UUID, Binding>();
        for (var rocket : found) {
            if (!eligible(rocket)) { return observation = SealedDisassembly.Entity.OTHER; }
            var prior = bindings.get(rocket.getUUID());
            if (prior != null && prior.entity == rocket) {
                if (prior.flight != rocket.flightData().orElse(null) || prior.snapshot != rocket.snapshot().orElse(null)) {
                    return observation = SealedDisassembly.Entity.OTHER;
                }
            } else if (!bindImage(rocket)) { return observation = SealedDisassembly.Entity.OTHER; }
            checked.put(rocket.getUUID(), new Binding(rocket, rocket.flightData().orElseThrow(), rocket.snapshot().orElseThrow()));
        }
        if (generation != epoch) { return observation; }
        if (!copies.equals(found)) { changed.run(); }
        copies = List.copyOf(found); bindings = Map.copyOf(checked);
        return observation = copies.isEmpty() ? SealedDisassembly.Entity.ABSENT : SealedDisassembly.Entity.MATCHING;
    }
    private boolean bindImage(RocketEntity rocket) {
        // Serialize only a newly observed object, not every position inspection. All saved resources must match.
        var image = new CompoundTag();
        if (!rocket.save(image) || image.contains("Passengers")) { return false; }
        if (frozen == null) {
            if (!RocketSnapshotNbtCodec.encode(entry.snapshot()).equals(RocketSnapshotNbtCodec.encode(rocket.snapshot().orElseThrow()))) { return false; }
            frozen = image; logical = rocket.assemblyTransactionId().orElseThrow(); return true;
        }
        return image.getString("id").equals(frozen.getString("id")) && java.util.Objects.equals(image.get("Pos"), frozen.get("Pos"))
                && java.util.Objects.equals(image.get(RocketRetirementProof.DATA), frozen.get(RocketRetirementProof.DATA));
    }
    private boolean eligible(RocketEntity rocket) {
        var origin = entry.snapshot().sourceOrigin();
        return RocketTransferEntities.loadedEntity(rocket) && rocket.operational()
                && level.dimension().location().equals(entry.snapshot().sourceDimension())
                && rocket.ownerId().filter(entry.ownerId()::equals).isPresent()
                && rocket.snapshot().filter(snapshot -> snapshot.snapshotId().equals(entry.snapshot().snapshotId())
                && snapshot.contentHash().equals(entry.snapshot().contentHash())).isPresent()
                && rocket.assemblyTransactionId().isPresent() && !rocket.isPassenger() && rocket.getPassengers().isEmpty()
                && rocket.flightData().filter(data -> RocketFlightStateMachine.isLegal(data.state(), RocketFlightEvent.DISASSEMBLE)
                && data.passengers().assignments().isEmpty() && data.activeTransferId().isEmpty()).isPresent()
                && rocket.getX() == origin.x() + 0.5D && rocket.getY() == origin.y() && rocket.getZ() == origin.z() + 0.5D
                && RocketEscrowGate.retirementUnprotected(rocket);
    }
    String diagnostic() {
        return "loaded=" + loaded() + ",census=" + observation + ",copies=" + copies.size() + ",epoch=" + epoch;
    }
    boolean discard() {
        if (observe() != SealedDisassembly.Entity.MATCHING) { return false; }
        return discard(copies.get(copies.size() - 1));
    }
    boolean soleSurvivor() { return observe() == SealedDisassembly.Entity.MATCHING && copies.size() == 1; }
    boolean discardDuplicate() {
        return observe() == SealedDisassembly.Entity.MATCHING && copies.size() > 1 && discard(copies.get(copies.size() - 1));
    }
    private boolean discard(RocketEntity target) {
        if (!eligible(target)) { return false; }
        target.discard(); invalidate(); changed.run();
        return level.getEntity(target.getUUID()) == null;
    }
    java.util.function.Predicate<CompoundTag> validator(ChunkPos column, boolean requirePresent, java.util.function.Consumer<String> diagnostic) {
        boolean origin = column.equals(RocketRetirementProof.Slot.origin(entry.snapshot()).chunk());
        var logical = this.logical; var frozen = this.frozen == null ? null : this.frozen.copy();
        var ids = copies.stream().map(RocketEntity::getUUID).toList();
        if (frozen != null && !ids.isEmpty()) { frozen.putUUID("UUID", ids.get(0)); }
        var snapshot = entry.snapshot().snapshotId(); var entity = entry.record().rocketEntityId();
        return root -> {
            boolean valid = requirePresent && origin ? frozen != null && SealedEntityImages.presentCopies(root, logical, snapshot, entity, frozen, ids)
                    : SealedEntityImages.absent(root, logical, snapshot, entity);
            diagnostic.accept("entity,present=" + requirePresent + ",origin=" + origin + ",valid=" + valid);
            return valid;
        };
    }
}
