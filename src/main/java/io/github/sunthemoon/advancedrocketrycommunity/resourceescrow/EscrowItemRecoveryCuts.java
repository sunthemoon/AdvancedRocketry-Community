package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** D-S2 durable damage, protected item root and latest-cargo disassembly/rebuild representatives. */
final class EscrowItemRecoveryCuts extends EscrowItemCutProbe {
    static final Set<String> CASES = Set.of("IR_CONTAINER", "IR_LOOT", "IR_DISASSEMBLY", "IR_REBUILD", "IR_ROOT");
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketItemCargoBlockEntity device;
    private RocketStructureSnapshot before;
    private RocketTransferRecord record;
    private long began, installed;
    private int step, recoveries;
    private boolean commanded;
    EscrowItemRecoveryCuts(MinecraftServer server) { super(server); }
    @Override void checkpoint(String label) { }
    private boolean damage() { return name.equals("IR_CONTAINER") || name.equals("IR_LOOT"); }
    @Override void beforeReconciliation() {
        if (!damage() || recovery || step != 1 || data == null || data.ledger().records().size() != 1 || !baseline(data, server)) { return; }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.ITEM_CARGO_UNLOAD, id(device), false);
        EscrowCargoSwitches.pauseReconciliationForTests(server, id(device), true);
        var candidate = candidate(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), name, rocket.snapshot().orElseThrow(), rocket.getUUID());
        var path = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("arce_w143_fixture/candidate.dat");
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            try (var output = java.nio.file.Files.newOutputStream(path, java.nio.file.StandardOpenOption.CREATE_NEW)) { net.minecraft.nbt.NbtIo.writeCompressed(candidate, output); }
        } catch (java.io.IOException failure) { throw new IllegalStateException("Item candidate export failed", failure); }
        save(); pause("BEFORE_OFFLINE_DAMAGE");
    }
    static CompoundTag candidate(boolean hooks, String name, RocketStructureSnapshot snapshot, UUID physical) {
        if (!hooks || !("IR_CONTAINER".equals(name) || "IR_LOOT".equals(name))) { return null; }
        EscrowTags.require(snapshot.blocks().size() == 1, "Item damage requires one container");
        var block = snapshot.blocks().get(0); var payload = block.blockEntityPayload().orElseThrow(); var contents = payload.data();
        var state = block.state();
        if (name.equals("IR_CONTAINER")) {
            state = new RocketBlockState(ResourceLocation.tryParse("minecraft:barrel"), Map.of("facing", "north", "open", "false"));
        } else {
            contents.remove("Items"); contents.putString("LootTable", "minecraft:chests/simple_dungeon"); contents.putLong("LootTableSeed", 143);
        }
        var changed = RocketStructureSnapshot.create(snapshot.snapshotId(), snapshot.sourceDimension(), snapshot.sourceOrigin(),
                List.of(new RocketBlock(block.position(), state, new RocketBlockEntityPayload(payload.adapterId(), contents))),
                snapshot.passengerAnchors(), snapshot.stats(), snapshot.createdAtGameTime(), snapshot.propulsion().orElseThrow());
        var result = new CompoundTag(); result.putUUID("physical", physical);
        result.put("before", RocketSnapshotNbtCodec.encode(snapshot)); result.put("after", RocketSnapshotNbtCodec.encode(changed)); return result;
    }
    @Override void tick() {
        if (done || stopped() || !loaded()) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); data.bind(server).autosave(); }
        }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !data.bind(server).operational()) { return; }
        EscrowTags.require(server.getTickCount() - began <= 6_000, "Item recovery fixture exceeded 6,000 ticks");
        if (recovery) { recover(journal); return; }
        if (step == 0) {
            if (!baseline(data, server)) { return; }
            rocket = cargoRocket(10); before = rocket.snapshot().orElseThrow(); device = device(DEVICE, rocket, RocketCargoRole.UNLOAD, 60);
            if (name.equals("IR_ROOT")) {
                var outer = device.saveWithFullMetadata(); var root = outer.getCompound(RocketItemCargoRoot.KEY);
                root.putInt("schema_version", 2); root.putString("extension", "W143-preserve"); device.load(outer); device.setChanged();
                save(); roots(); pause("ROOT_PRESERVED"); return;
            }
            save();
            if (damage()) { EscrowCargoSwitches.pauseReconciliationForTests(server, id(device), true); }
            EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.ITEM_CARGO_UNLOAD, id(device), true); step = 1;
        } else if (step == 1 && !damage()) {
            if (data.ledger().records().isEmpty()) { return; }
            EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.ITEM_CARGO_UNLOAD, id(device), false); step = 2;
        } else if (step == 2) {
            if (!data.ledger().records().isEmpty() || !rocket.escrowSection().markers().isEmpty() || !section(device).markers().isEmpty() || !baseline(data, server)) { return; }
            requireLatest(); save();
            record = name.equals("IR_DISASSEMBLY") ? EscrowCargoTransferFixtures.landedRecord(rocket, before) : stale();
            journal.put(record); journal.flushChecked(server); installed = server.getTickCount(); step = 3;
        } else if (step == 3 && journal.acknowledgedCurrent()) {
            if (name.equals("IR_DISASSEMBLY")) { disassemble(journal); }
            else if (server.getTickCount() - installed >= 300) { refused(journal); pause("REBUILD_REFUSED"); }
        }
    }
    private RocketTransferRecord stale() {
        var from = before.relocated(UUID.randomUUID(), level.dimension().location(), new RocketPosition(88, 100, 64), 0);
        var to = before.relocated(UUID.randomUUID(), level.dimension().location(), new RocketPosition(80, 100, 64), 0);
        var fuel = rocket.flightData().orElseThrow().fuel(); var transfer = UUID.randomUUID(); var logical = rocket.assemblyTransactionId().orElseThrow();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                level.dimension().location(), level.dimension().location(), 500, 0).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("W143 stale destination")));
        var flight = RocketFlightData.initial(logical, from.stats().fuelCapacity(), from.stats().seatCount(), plan.sourceBody(), from.sourceDimension(), from.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), to.sourceDimension(), to.sourceOrigin(), 160);
        return RocketTransferRecord.create(transfer, logical, rocket.ownerId().orElseThrow(), UUID.randomUUID(), from, to, flight, arrival, 500, 0)
                .destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }
    private void recover(RocketTransferSavedData journal) {
        if (server.getTickCount() - began < 200 || !data.bind(server).writerIdle()) { return; }
        device = existingDevice(DEVICE); var found = allRockets();
        if (device == null) { return; }
        if (name.equals("IR_DISASSEMBLY")) {
            if (!RocketTransactionSavedData.get(server).entries().isEmpty()) { return; }
            if (!found.isEmpty()) {
                EscrowTags.require(found.size() == 1 && level.getBlockState(ROCKET).isAir(), "Item disassembly has duplicate authority");
                rocket = found.get(0); requireLatest(); disassemble(journal); return;
            }
            if (!journal.entries().isEmpty() || !journal.actionAuthorityAvailable()) { return; }
            EscrowTags.require(level.getBlockEntity(ROCKET) instanceof net.minecraft.world.Container container
                    && ItemCargoReconciliationFixtures.amount(container) == 6 && tank(device) == 64, "Disassembly did not restore the latest typed items exactly once");
            result("RESTORED", "container=6 device=64 rockets=0"); return;
        }
        EscrowTags.require(found.size() == 1, "Item recovery lost or duplicated its rocket"); rocket = found.get(0);
        if (name.equals("IR_ROOT")) { roots(); result("ROOT_HELD", "issued=0"); return; }
        if (damage()) {
            EscrowTags.require(data.ledger().records().size() == 1, "Item damage lost its operation"); var op = data.ledger().records().get(0);
            if (op.phase() != EscrowOperation.Phase.REPAIR) { return; }
            EscrowTags.require(op.reason().manual() && !RocketEscrowGate.idle(rocket) && device.escrowLocked()
                    && tank(device) == 60 && rocket.escrowSection().epoch() == 0 && section(device).epoch() == 0,
                    "Item damage changed or released the frozen endpoints");
            result("REPAIR", "reason=" + op.reason()); return;
        }
        if (server.getTickCount() - began < 400) { return; }
        EscrowTags.require(journal.entries().size() == 1, "Rebuild restart lost retained journal"); record = journal.entries().get(0);
        refused(journal); result("REFUSED", "rocket=6 device=64 journal=1");
    }
    private void disassemble(RocketTransferSavedData journal) {
        if (commanded) { return; }
        if (!RocketRuntime.resourceMutationAllowed(rocket)) {
            var retained = journal.entries().stream().findFirst();
            if (retained.isPresent() && server.getTickCount() % 20 == 0 && recoveries++ < 20) { RocketRuntime.recoverTransfer(server, retained.get().transferId()); }
            return;
        }
        EscrowItemDisassemblyRequest.run(rocket, name); commanded = true;
    }
    private void requireLatest() {
        EscrowTags.require(cargo(rocket) == 6 && tank(device) == 64 && rocket.escrowSection().epoch() == 1 && section(device).epoch() == 1,
                "Real item unload did not preserve the latest typed remainder");
    }
    private void refused(RocketTransferSavedData journal) {
        var report = RocketRuntime.recoverTransfer(server, record.transferId()); requireLatest();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_ITEM_REBUILD_RESULT status={} rockets={} retained={}", report.status(), allRockets().size(), journal.find(record.transferId()).isPresent());
        EscrowTags.require(allRockets().size() == 1 && journal.find(record.transferId()).isPresent()
                && rocket.getX() == ROCKET.getX() + 0.5D && report.status() == RocketTransferRecoveryReport.Status.RETRY_LATER,
                "Rebuild reconstructed or resolved a historical item copy");
    }
    private void roots() {
        EscrowTags.require(device.unsupportedRoot() && device.protectedFromRemoval() && device.heldRoot().isPresent() && device.root().isEmpty()
                && data.ledger().records().isEmpty() && data.ledger().issuedThrough() == 0 && rocket.escrowSection().epoch() == 0,
                "Future item root was changed or admitted");
    }
}
