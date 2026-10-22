package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** C-S3 disposable fixtures and observations; recovery, rejection and locks are production-owned. */
final class EscrowCargoRecoveryProbe {
    static final String PREFIX = "FC_LOAD_";
    private static final Set<String> CASES = Set.of("R1_255", "R1_256", "R2_DUPLICATE", "R3_CURSOR", "R3_OTHER", "R3_RELOCATED", "G1_FUTURE", "G1_INVALID");
    private final MinecraftServer server;
    private final String name, cut;
    private final boolean recovery;
    private final Consumer<String> pause;
    private RocketStructureSnapshot before;
    private boolean injected, retired, opened;
    private FloorBaseline floorBaseline;
    private boolean floorReady;
    private String floorWriteFailure;
    private long created;

    static boolean selected(boolean hooks, String name) {
        return hooks && name != null && name.startsWith(PREFIX) && CASES.contains(name.substring(PREFIX.length()));
    }
    static BlockPos devicePosition(String name, boolean recovery) {
        return new BlockPos(recovery && (PREFIX + "R3_RELOCATED").equals(name) ? 72 : 64, 100, 64);
    }
    EscrowCargoRecoveryProbe(MinecraftServer server, String name, boolean recovery, Consumer<String> pause) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; this.name = name;
        cut = name.substring(PREFIX.length()); this.recovery = recovery; this.pause = pause;
    }
    boolean admit() { return !cut.startsWith("G1_"); }
    boolean duplicate() { return cut.equals("R2_DUPLICATE"); }

    void place(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device) {
        before = rocket.snapshot().orElseThrow(); created = server.getTickCount();
        if (cut.startsWith("R1_")) {
            int count = cut.equals("R1_255") ? 255 : 256;
            var floors = EscrowLoaderFloorProbe.floors(count);
            floors.forEach(floor -> floor.putString("signature_kind", EscrowSignatureKind.ROCKET_COMPOSITE_V1.name()));
            data.acknowledged(new EscrowLedger(data.ledger().id(), count, data.ledger().writerGeneration(), List.of(), floors, List.of()), true);
            if (count == 256) { floorBaseline = new FloorBaseline(data.ledger()); }
            data.bind(server).autosave();
            var record = EscrowCargoTransferFixtures.record(rocket, before, false);
            rocket.updateFlightData(record.destinationFlightData().land(200));
            var journal = RocketTransferSavedData.get(server); journal.put(record); journal.flushChecked(server);
        } else if (duplicate()) {
            var flight = rocket.flightData().orElseThrow();
            rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false,
                    net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000), 25));
        } else if (cut.startsWith("G1_")) {
            var outer = device.fluidEntity().saveWithFullMetadata(); var root = outer.getCompound(RocketFluidCargoRoot.KEY);
            if (cut.equals("G1_FUTURE")) { root.putInt("schema_version", 2); root.putString("extension", "W138-preserve"); }
            else { root.getCompound("tank").putInt("Amount", -1); }
            device.fluidEntity().load(outer); device.fluidEntity().setChanged();
        }
    }
    void checkpoint(String label) {
        if (recovery) { return; }
        if (floorBaseline != null && !floorReady && (label.equals("WRITE_FAILED_BEFORE_REPLACE") || label.equals("WRITE_UNKNOWN"))) {
            floorWriteFailure = label;
        }
        if (cut.equals("R1_255") && label.equals("K2")) { pause.accept("R1_L1_REPLACED"); }
        if (duplicate() && label.equals("C8_RELEASED")) { retired = true; }
        if (cut.equals("R3_RELOCATED") && label.equals("C6_JOINT_READBACK")) { pause.accept("R3_BEFORE_OFFLINE_MOVE"); }
    }
    void beforeReconciliation(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device) {
        if (recovery || injected || !(cut.equals("R3_CURSOR") || cut.equals("R3_OTHER")) || data == null
                || data.ledger().records().size() != 1 || !data.bind(server).writerIdle()) { return; }
        injected = true;
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, device.id(), false);
        if (cut.equals("R3_CURSOR")) {
            var candidate = cursorCandidate(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), rocket.snapshot().orElseThrow(), rocket.getUUID());
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("arce_w138_fixture/r3-cursor.dat");
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                try (var output = java.nio.file.Files.newOutputStream(file, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                    net.minecraft.nbt.NbtIo.writeCompressed(candidate, output);
                }
            } catch (java.io.IOException failure) { throw new IllegalStateException("R3 cursor candidate export failed", failure); }
        }
        // A live held endpoint correctly rejects mutation. The harness damages only the stopped world copy.
        EscrowTags.require(server.saveEverything(false, true, true), "R3 baseline save refused");
        pause.accept("R3_BEFORE_OFFLINE_EDIT");
    }
    static CompoundTag cursorCandidate(boolean hooks, RocketStructureSnapshot snapshot, java.util.UUID physical) {
        if (!hooks) { return null; }
        var cargo = snapshot.blocks().stream().filter(block -> block.blockEntityPayload().isPresent()).toList();
        EscrowTags.require(cargo.size() == 1 && cargo.get(0).position().equals(new RocketPosition(0, 0, 0)), "R3 cursor fixture differs");
        var blocks = snapshot.blocks().stream().map(block -> block.blockEntityPayload().isPresent()
                ? new RocketBlock(new RocketPosition(0, 1, 0), block.state(), block.blockEntityPayload().orElseThrow()) : block).toList();
        var changed = RocketStructureSnapshot.create(snapshot.snapshotId(), snapshot.sourceDimension(), snapshot.sourceOrigin(), blocks,
                snapshot.passengerAnchors(), snapshot.stats(), snapshot.createdAtGameTime(), snapshot.propulsion().orElseThrow());
        var root = new CompoundTag(); root.putUUID("physical", physical);
        root.put("before", RocketSnapshotNbtCodec.encode(snapshot)); root.put("after", RocketSnapshotNbtCodec.encode(changed));
        return root;
    }
    void tick(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device) {
        if (recovery) { return; }
        if (cut.startsWith("G1_")) { roots(data, rocket, device); pause.accept("G1_PRESERVED"); }
        if (cut.equals("R1_256") && !floorReady) {
            EscrowTags.require(floorWriteFailure == null, "R1 floor baseline writer outcome: " + floorWriteFailure);
            if (!floorBaseline.advance(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), data, data.bind(server), ResourceEscrowSavedData.file(server))) { return; }
            floorReady = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_FLOOR_BASELINE outcome=COMMITTED generation={} readback_floors=256",
                    data.ledger().writerGeneration());
        }
        if (cut.equals("R1_256") && data.bind(server).writerIdle() && RocketTransferSavedData.get(server).clean()
                && device.fluidEntity().displayStatus() == RocketCargoStatus.ESCROW_FULL) {
            floorBaseline.confirm(data, ResourceEscrowSavedData.file(server));
            floors(data, rocket, device, 256); pause.accept("R1_REFUSED");
        }
        if (duplicate() && retired) {
            if (!injected) {
                injected = true; created = server.getTickCount();
                var record = EscrowCargoTransferFixtures.record(rocket, before, true);
                var other = ModEntities.ROCKET.get().create(server.overworld());
                EscrowTags.require(other != null, "R2 duplicate creation failed");
                other.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(), record.destinationFlightData().land(200));
                EscrowTags.require(server.overworld().addFreshEntity(other), "R2 duplicate publication failed");
                var journal = RocketTransferSavedData.get(server); journal.put(record); journal.flushChecked(server);
                EscrowTags.require(server.saveEverything(false, true, true), "R2 baseline save refused");
            }
            if (server.getTickCount() - created >= 100 && RocketTransferSavedData.get(server).clean()) {
                duplicateState(data, rocket, device); pause.accept("R2_FENCED");
            }
        }
    }
    boolean recover(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device, long elapsed) {
        if (elapsed < 100 || !data.bind(server).writerIdle()) { return false; }
        if (cut.startsWith("G1_")) { roots(data, rocket, device); }
        else if (cut.startsWith("R1_")) {
            int count = cut.equals("R1_255") ? 255 : 256;
            if (count == 256) {
                if (!opened) { opened = true; EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, device.id(), true); }
                if (device.fluidEntity().displayStatus() != RocketCargoStatus.ESCROW_FULL) { return false; }
                EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_LOAD, device.id(), false);
            }
            floors(data, rocket, device, count);
        } else if (duplicate()) { duplicateState(data, rocket, device); }
        else {
            EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().issuedThrough() == 1, "R3 lost/repeated its operation");
            var op = data.ledger().records().get(0);
            if (op.phase() != EscrowOperation.Phase.REPAIR) { return false; }
            var coordinator = data.bind(server);
            EscrowTags.require(op.reason().manual() && coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                    && coordinator.admissionAuthority().pendingCount() == 1 && !coordinator.reconciliationCheckpointProved(op.id()),
                    "R3 released or overwrote a held endpoint");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_RECOVERY_HELD cut={} reason={} phase=REPAIR pending=1", name, op.reason());
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_RESULT cut={} records={} issued={}", name, data.ledger().records().size(), data.ledger().issuedThrough());
        return true;
    }
    private void roots(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device) {
        var nativeDevice = device.fluidEntity();
        EscrowTags.require(nativeDevice.unsupportedRoot() && nativeDevice.protectedFromRemoval() && nativeDevice.heldRoot().isPresent()
                && nativeDevice.root().isEmpty() && data.ledger().records().isEmpty() && data.ledger().issuedThrough() == 0
                && rocket.escrowSection().epoch() == 0, "G1 changed an unsupported root or admitted cargo");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_ROOT_HELD cut={} status={} protected=true", name, nativeDevice.dataStatus());
    }
    private void floors(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device, int count) {
        var coordinator = data.bind(server); var usage = coordinator.floorUsage();
        int pending = count == 255 ? 1 : 0;
        EscrowTags.require(data.ledger().floors().size() == count && data.ledger().floors().stream()
                .allMatch(f -> f.getString("signature_kind").equals(EscrowSignatureKind.ROCKET_COMPOSITE_V1.name()))
                && data.ledger().records().size() == pending && data.ledger().issuedThrough() == count + pending
                && usage.used() == count && usage.reserved() == pending && usage.total() == 256
                && device.amount() == 5_000 && device.section().epoch() == 0 && rocket.escrowSection().epoch() == 0,
                "R1 exceeded composite floor capacity or mutated a refused/held transfer");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_FLOOR_STATE cut={} used={} reserved={} total=256 records={}", name, count, pending, pending);
    }
    private void duplicateState(ResourceEscrowSavedData data, RocketEntity rocket, EscrowCargoProbeDevice device) {
        var journal = RocketTransferSavedData.get(server);
        EscrowTags.require(journal.entries().size() == 1 && journal.clean(), "R2 changed its retained journal");
        var record = journal.entries().iterator().next();
        var report = RocketRuntime.recoverTransfer(server, record.transferId());
        String diagnostic = String.join(" ", io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime
                .service().orElseThrow().diagnostics(server, java.util.Optional.empty()));
        EscrowTags.require(report.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport.Status.RETRY_LATER
                && floorConflict(diagnostic, record.transferId()) && device.amount() == 4_000 && rocket.escrowSection().epoch() == 1
                && data.ledger().records().isEmpty() && data.ledger().issuedThrough() == 1
                && server.overworld().getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(68, 100, 64)).inflate(12)).size() == 2,
                "R2 overwrote, retired or discarded a cargo-mutated authority/duplicate");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_DUPLICATE_HELD cut={} status={} diagnostic={}", name, report.status(), diagnostic);
    }
    static boolean floorConflict(String diagnostic, java.util.UUID transfer) {
        return diagnostic.contains("fenced_transfer=" + transfer + " reason=FLOOR_CONFLICT ");
    }
    /** Fixture prerequisite: an idle writer can still have a dirty, never-requested root after autosave throttling. */
    static final class FloorBaseline {
        private final EscrowLedger seeded;
        FloorBaseline(EscrowLedger seeded) {
            EscrowTags.require(seeded.floors().size() == 256 && seeded.records().isEmpty(), "Invalid R1 saturated fixture seed");
            this.seeded = seeded;
        }
        boolean advance(boolean hooks, ResourceEscrowSavedData data, EscrowCoordinator coordinator, java.nio.file.Path file) {
            if (!hooks) { return false; }
            EscrowTags.require(coordinator.operational() && !coordinator.writerUnknown(), "R1 floor baseline writer unavailable");
            if (!coordinator.writerIdle()) { return false; }
            if (data.ledger().writerGeneration() == seeded.writerGeneration()) {
                coordinator.autosave();
                if (!coordinator.writerIdle()) {
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_FLOOR_BASELINE requested=true previous_generation={} floors=256",
                            seeded.writerGeneration());
                }
                return false;
            }
            // Only the coordinator's COMMITTED callback advances this ledger generation. Disk replacement alone is insufficient.
            confirm(data, file);
            return true;
        }
        void confirm(ResourceEscrowSavedData data, java.nio.file.Path file) {
            var acknowledged = data.ledger();
            EscrowTags.require(!data.isDirty() && acknowledged.writerGeneration() == seeded.writerGeneration() + 1
                    && acknowledged.equals(seeded.generation(acknowledged.writerGeneration())), "R1 floor seed lacks its exact COMMITTED acknowledgement");
            EscrowLedgerBaseline.require(file, acknowledged); // Two checked saved-file reads, including all 256 exact composite floors.
        }
    }
}
