package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/** W27d3's bounded recognition. Reading the loaded index is not an absence proof. */
final class BayCopyCensus {
    record Copy(RocketEntity rocket, boolean destination) { }
    record Scan(List<Copy> copies, @Nullable String refusal) {
        Scan { copies = List.copyOf(copies); }
        static Scan refused(String reason) { return new Scan(List.of(), reason); }
    }

    private BayCopyCensus() { }

    static Scan inspect(MinecraftServer server, RocketTransferRecord record, BayGroup group) {
        return inspect(server, record, group, rocket -> RocketEscrowGate.bayNeutral(rocket, record.transferId()));
    }

    /** The predicate is the adapter's escrow-neutrality check; classification precedes those reads. */
    static Scan inspect(MinecraftServer server, RocketTransferRecord record, BayGroup group, Predicate<RocketEntity> neutral) {
        End source = End.of(server, record.sourceSnapshot(), false);
        End destination = End.of(server, record.destinationSnapshot(), true);
        var named = new IdentityHashMap<RocketEntity, List<End>>();
        // Exact UUID first, even if it has moved out of the bounded query or no longer names L.
        for (End end : new End[] {source, destination}) {
            if (end == null) { continue; }
            var exact = end.level().getEntity(record.sourceEntityId());
            if (exact != null && !(exact instanceof RocketEntity)) { return Scan.refused("SOURCE_AUTHORITY_CONFLICT"); }
            if (exact instanceof RocketEntity rocket) { named.computeIfAbsent(rocket, ignored -> new ArrayList<>()); }
        }
        for (End end : new End[] {source, destination}) {
            if (end != null && end.collect(record.logicalRocketId(), named)) { return Scan.refused("DUPLICATE_LIMIT"); }
        }
        var copies = new ArrayList<Copy>();
        for (var entry : named.entrySet()) {
            var rocket = entry.getKey();
            if (rocket.getUUID().equals(record.sourceEntityId()) && source != null && rocket.level() == source.level()) {
                if (!source.recognizes(rocket, record, group)) { return Scan.refused("SOURCE_AUTHORITY_CONFLICT"); }
                continue;
            }
            if (rocket.getUUID().equals(record.sourceEntityId())) { return Scan.refused("UNEXPECTED_COPY"); }
            var end = entry.getValue().stream().filter(value -> value.recognizes(rocket, record, group)).findFirst().orElse(null);
            if (end == null) { return Scan.refused("UNEXPECTED_COPY"); }
            copies.add(new Copy(rocket, end.destination()));
        }
        if (copies.stream().anyMatch(copy -> !neutral.test(copy.rocket()))) { return Scan.refused("PROTECTED_DUPLICATE"); }
        copies.sort(java.util.Comparator.comparing(copy -> copy.rocket().getUUID()));
        return new Scan(copies, null);
    }

    /** A known joining/moving identity must stay inside one recorded end; no scan outside those ends is performed. */
    static boolean within(MinecraftServer server, RocketTransferRecord record, RocketEntity rocket) {
        End source = End.of(server, record.sourceSnapshot(), false), destination = End.of(server, record.destinationSnapshot(), true);
        return source != null && source.contains(rocket) || destination != null && destination.contains(rocket);
    }

    /** Recheck after callbacks such as passenger movement, before consuming or discarding this particular copy. */
    static boolean recognizes(MinecraftServer server, RocketTransferRecord record, BayGroup group, Copy copy) {
        var end = End.of(server, copy.destination() ? record.destinationSnapshot() : record.sourceSnapshot(), copy.destination());
        return end != null && end.recognizes(copy.rocket(), record, group);
    }

    static boolean names(RocketEntity rocket, UUID logical) {
        return rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                || rocket.flightData().filter(flight -> logical.equals(flight.logicalRocketId())).isPresent()
                || rocket.preservedAssemblyTransactionId().filter(logical::equals).isPresent();
    }

    private record End(ServerLevel level, RocketStructureSnapshot snapshot, AABB box, @Nullable AABB originBox, boolean destination) {
        @Nullable static End of(MinecraftServer server, RocketStructureSnapshot snapshot, boolean destination) {
            // Join/movement notifications precede R1 readiness; never form an overflowing or over-budget region there.
            var columns = io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(snapshot);
            if (!columns.valid()) { return null; }
            var level = RocketTransferEntities.level(server, snapshot.sourceDimension());
            if (level == null) { return null; }
            var region = RocketRegion.fromSnapshot(snapshot);
            var origin = columns.origin();
            AABB originBox = columns.containsOrigin() ? null : new AABB(origin.x * 16D, level.getMinBuildHeight(), origin.z * 16D,
                    origin.x * 16D + 16, level.getMaxBuildHeight(), origin.z * 16D + 16);
            return new End(level, snapshot, new AABB(region.minimum().x() - 1D, level.getMinBuildHeight(), region.minimum().z() - 1D,
                    region.maximum().x() + 2D, level.getMaxBuildHeight(), region.maximum().z() + 2D), originBox, destination);
        }
        boolean contains(RocketEntity rocket) { return rocket.level() == level && (box.intersects(rocket.getBoundingBox()) || originContains(rocket)); }
        /** The extra origin slot proves home-column membership, not adjacent columns whose entity boxes overlap it. */
        boolean originContains(RocketEntity rocket) {
            return originBox != null && rocket.getX() >= originBox.minX && rocket.getX() < originBox.maxX
                    && rocket.getZ() >= originBox.minZ && rocket.getZ() < originBox.maxZ && originBox.intersects(rocket.getBoundingBox());
        }
        boolean collect(UUID logical, IdentityHashMap<RocketEntity, List<End>> named) {
            if (collect(box, logical, named, false)) { return true; }
            return originBox != null && collect(originBox, logical, named, true);
        }
        private boolean collect(AABB region, UUID logical, IdentityHashMap<RocketEntity, List<End>> named, boolean originOnly) {
            level.getEntities().get(EntityTypeTest.forClass(RocketEntity.class), region, rocket -> {
                if ((!originOnly || originContains(rocket)) && names(rocket, logical)) {
                    named.computeIfAbsent(rocket, ignored -> new ArrayList<>()).add(this);
                }
                return named.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES
                        ? AbortableIterationConsumer.Continuation.ABORT : AbortableIterationConsumer.Continuation.CONTINUE;
            });
            return named.size() >= RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES;
        }
        boolean recognizes(RocketEntity rocket, RocketTransferRecord record, BayGroup group) {
            var origin = snapshot.sourceOrigin();
            return contains(rocket) && rocket.operational() && !rocket.isPassenger()
                    && rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isPresent()
                    && rocket.ownerId().filter(record.ownerId()::equals).isPresent()
                    && rocket.flightData().filter(flight -> flight.logicalRocketId().equals(record.logicalRocketId())).isPresent()
                    && rocket.getX() == origin.x() + .5D && rocket.getZ() == origin.z() + .5D
                    && rocket.getY() >= origin.y() && rocket.getY() <= origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS
                    && rocket.snapshot().filter(value -> value.snapshotId().equals(snapshot.snapshotId())
                            && (destination ? group.destinationHalf(value.contentHash()) : group.sourceHalf(value.contentHash()))
                            != BayGroup.Half.OUTSIDE).isPresent();
        }
    }
}
