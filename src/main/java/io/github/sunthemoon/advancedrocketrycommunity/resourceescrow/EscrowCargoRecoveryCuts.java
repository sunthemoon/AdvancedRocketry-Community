package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * C-S5 recovery and disassembly cuts (FF_ cases) after a real fluid cargo unload of 1,000 mB from a 5,000 mB tank:
 * P1-F8's three windows through the existing gated {@code LANDED_DISASSEMBLY} boundaries (the runner sets them), and
 * gated stale legacy records over the unloaded rocket's pre-unload snapshot that the production recovery service then
 * decides: KEEP_SOURCE, REBUILD_DESTINATION, REBUILD_SOURCE, a stale PREPARED with both physical entities and a
 * disembarked passenger's reconnect. Each record names the real rocket's identity; the fixture never acts for recovery.
 */
final class EscrowCargoRecoveryCuts extends EscrowCargoCutProbe {
    static final Map<String, String> CASES = Map.of(
            "FF_DISASSEMBLY_REMOVAL", "K1",
            "FF_DISASSEMBLY_RESTORATION", "K2",
            "FF_DISASSEMBLY_RELEASE", "K5",
            "FF_REBUILD_DESTINATION", "REBUILD_DESTINATION",
            "FF_KEEP_SOURCE", "KEEP_SOURCE",
            "FF_REBUILD_SOURCE", "REBUILD_SOURCE",
            "FF_STALE_PREPARED", "BOTH_ENTITIES",
            "FF_RECONNECT", "RECONNECT");
    static final UUID PASSENGER = UUID.nameUUIDFromBytes("arce:w142:disembarked-passenger".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    /** The relocated stale ends: the pad, and a far cell in the same chunk as the pad. */
    static final BlockPos ELSEWHERE = PAD.offset(8, 0, 0);
    private final String boundary;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketFluidCargoBlockEntity device;
    private RocketStructureSnapshot before;
    private RocketTransferRecord record;
    private UUID duplicate;
    private long began, since;
    private int step, recoveries;
    private boolean commanded, placed;
    private Vec3 location;

    EscrowCargoRecoveryCuts(MinecraftServer server) {
        super(server);
        boundary = CASES.get(name);
        EscrowTags.require(boundary != null, "Unknown W142 recovery cut");
    }

    /** P1-F8's own probe pauses at K1, K2 and K5; this probe never pauses those cases itself. */
    static boolean disassembly(String boundary) { return boundary.equals("K1") || boundary.equals("K2") || boundary.equals("K5"); }

    @Override void checkpoint(String label) { }

    @Override void tick() {
        if (done || stopped() || !loaded()) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); began = server.getTickCount();
            if (!recovery) { data.setDirty(); data.bind(server).autosave(); }
        }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server) || !data.bind(server).operational()) { return; }
        EscrowTags.require(server.getTickCount() - began <= 6_000, "W142 recovery fixture exceeded its bounded 6,000 ticks");
        if (placed) { ReleaseTestPlayers.pump(server); }
        if (recovery) { recover(journal); return; }
        switch (step) {
            case 0 -> create();
            case 1 -> admitted();
            case 2 -> retired(journal);
            case 3 -> installed(journal);
            default -> { }
        }
    }

    private void create() {
        if (data.ledger().writerGeneration() == 0 || !data.bind(server).writerIdle()) { data.bind(server).autosave(); return; }
        rocket = cargoRocket(5_000); before = rocket.snapshot().orElseThrow();
        device = device(DEVICE, rocket, RocketCargoRole.UNLOAD, 0);
        save(); EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, id(device), true); step = 1;
    }

    private void admitted() {
        var op = data.ledger().records().stream().filter(value -> value.device().identity().equals(id(device))).findFirst().orElse(null);
        if (op == null) { return; }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, id(device), false);
        EscrowTags.require(op.phase() == EscrowOperation.Phase.PREPARED && op.guard().transfer() == null, "The unload was not an unguarded PREPARED");
        step = 2;
    }

    /** After the ordinary unload retired, the gated fixture installs its record; nothing else acts for recovery. */
    private void retired(RocketTransferSavedData journal) {
        if (!data.ledger().records().isEmpty() || !section(device).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()
                || !data.bind(server).writerIdle()) { return; }
        EscrowTags.require(cargo(rocket) == 4_000 && tank(device) == 1_000 && rocket.escrowSection().epoch() == 1
                && !before.contentHash().equals(rocket.snapshot().orElseThrow().contentHash()), "The real unload did not retire with its latest cargo");
        record = stale();
        if (boundary.equals("BOTH_ENTITIES")) {
            var copy = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
            copy.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(), record.destinationFlightData().land(200));
            EscrowTags.require(level.addFreshEntity(copy), "W142 both-entity fixture publication failed"); duplicate = copy.getUUID();
        }
        journal.put(record); journal.flushChecked(server);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_CUT_RECORD cut={} transfer={} phase={} rocket={} duplicate={}",
                name, record.transferId(), record.phase(), rocket.getUUID(), duplicate);
        since = server.getTickCount(); step = 3;
    }

    private void installed(RocketTransferSavedData journal) {
        if (!journal.acknowledgedCurrent()) { return; }
        if (disassembly(boundary)) { disassemble(journal); return; }
        if (duplicate != null && server.getTickCount() - since < 20) { return; }
        if (duplicate != null && !commanded) { save(); commanded = true; } // The fixture's duplicate is durable before the cut.
        if (server.getTickCount() - since < 300) { return; }
        if (boundary.equals("RECONNECT") && !placed) { join(); since = server.getTickCount() - 200; return; }
        requireRefused(journal);
        pause(boundary);
    }

    /** The stale legacy record over the pre-unload snapshot, shaped so the production decision is this case's action. */
    private RocketTransferRecord stale() {
        var dimension = before.sourceDimension(); var at = before.sourceOrigin();
        RocketStructureSnapshot from, to; UUID sourceEntity; UUID destinationEntity = null;
        switch (boundary) {
            case "K1", "K2", "K5" -> { return EscrowCargoTransferFixtures.landedRecord(rocket, before); }
            case "REBUILD_DESTINATION" -> {
                // Neither recorded end is the loaded rocket's snapshot; the record's arrival identity is the rocket's UUID.
                from = before.relocated(UUID.randomUUID(), dimension, position(ELSEWHERE), 0);
                to = before.relocated(UUID.randomUUID(), dimension, position(PAD), 0);
                sourceEntity = UUID.randomUUID(); destinationEntity = rocket.getUUID();
            }
            case "REBUILD_SOURCE" -> {
                from = before.relocated(UUID.randomUUID(), dimension, position(PAD), 0);
                to = before.relocated(UUID.randomUUID(), dimension, position(ELSEWHERE), 0);
                sourceEntity = rocket.getUUID();
            }
            default -> {
                // KEEP_SOURCE, both entities and reconnect: the source end is the rocket's own pre-unload snapshot.
                from = before; to = before.relocated(UUID.randomUUID(), dimension, new RocketPosition(at.x() + 8, at.y(), at.z()), 0);
                sourceEntity = rocket.getUUID();
            }
        }
        var fuel = rocket.flightData().orElseThrow().fuel(); var transfer = UUID.randomUUID(); var logical = rocket.assemblyTransactionId().orElseThrow();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(), dimension, dimension, 500, 0)
                .withPropulsionDebit(new RocketPropulsionDebit(500, 0, fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(),
                        RocketNativeResources.hash("W142 stale cargo journal fixture")));
        var flight = RocketFlightData.initial(logical, from.stats().fuelCapacity(), from.stats().seatCount(), plan.sourceBody(), dimension, from.sourceOrigin(), 0)
                .withFuel(fuel, 0).withPlan(plan);
        if (boundary.equals("RECONNECT")) { flight = flight.withPassengers(flight.passengers().assign(PASSENGER).orElseThrow()); }
        flight = flight.startCountdown(0).completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), dimension, to.sourceOrigin(), 160);
        var prepared = RocketTransferRecord.create(transfer, logical, rocket.ownerId().orElseThrow(), sourceEntity, from, to, flight, arrival, 500, 0);
        return destinationEntity == null ? prepared : prepared.destinationSpawned(destinationEntity)
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    /** P1-F8 starts from the checked legacy authority, then the release-test disassembly command. */
    private void disassemble(RocketTransferSavedData journal) {
        if (commanded) { return; }
        if (journal.find(record.transferId()).isPresent() && !RocketRuntime.resourceMutationAllowed(rocket)) {
            if (server.getTickCount() % 20 == 0 && recoveries++ < 20) { RocketRuntime.recoverTransfer(server, record.transferId()); }
            return;
        }
        command(); commanded = true;
    }

    private void command() {
        int code = server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "arce rocket release-test disassemble " + rocket.getStringUUID() + " discard-fuel 1000");
        EscrowTags.require(code == 1, "W142 release-test disassembly was refused");
    }

    private void recover(RocketTransferSavedData journal) {
        if (server.getTickCount() - began < 100 || !data.bind(server).writerIdle()) { return; }
        var found = allRockets(); device = existingDevice(DEVICE);
        if (device == null) { return; }
        if (disassembly(boundary)) { restored(journal, found); return; }
        if (server.getTickCount() - began < 400) { return; }
        rocket = found.stream().filter(value -> value.getX() == ROCKET.getX() + 0.5D).findFirst().orElse(null);
        if (rocket == null) { return; }
        record = journal.entries().size() == 1 ? journal.entries().get(0) : record;
        if (boundary.equals("BOTH_ENTITIES") && duplicate == null) {
            duplicate = found.stream().filter(value -> !value.getUUID().equals(rocket.getUUID()))
                    .map(RocketEntity::getUUID).findFirst().orElse(null);
            EscrowTags.require(duplicate != null, "The protected stale copy disappeared after restart");
        }
        if (boundary.equals("RECONNECT") && !placed) { join(); since = server.getTickCount(); return; }
        if (boundary.equals("RECONNECT") && server.getTickCount() - since < 100) { return; }
        requireRefused(journal);
        result("REFUSED", "action=" + boundary + " rocket=4000 device=1000 epoch=1 journal=" + journal.entries().size()
                + " rockets=" + found.size() + " passenger=" + (placed ? "unmounted" : "none"));
    }

    /** The latest cargo exists exactly once: the unloaded rocket, or after its disassembly the restored tank. */
    private void restored(RocketTransferSavedData journal, java.util.List<RocketEntity> found) {
        if (!RocketTransactionSavedData.get(server).entries().isEmpty() || server.getTickCount() - began < 200) { return; }
        if (!found.isEmpty()) {
            EscrowTags.require(found.size() == 1 && cargo(found.get(0)) == 4_000 && level.getBlockState(ROCKET).isAir(),
                    "The restart kept a rocket and restored blocks, or changed the latest cargo");
            rocket = found.get(0);
            if (!commanded) {
                var retained = journal.entries().stream().filter(value -> value.logicalRocketId().equals(rocket.assemblyTransactionId().orElseThrow())).findFirst();
                if (retained.isPresent() && !RocketRuntime.resourceMutationAllowed(rocket)) {
                    if (server.getTickCount() % 20 == 0 && recoveries++ < 20) { RocketRuntime.recoverTransfer(server, retained.get().transferId()); }
                    return;
                }
                command(); commanded = true;
            }
            return;
        }
        if (!journal.entries().isEmpty() || !journal.actionAuthorityAvailable()) { return; }
        EscrowTags.require(level.getBlockEntity(ROCKET) instanceof PressurizedTankBlockEntity tank && tank.fluidState().getFluid() == Fluids.WATER
                && tank.fluidState().getAmount() == 4_000 && level.getBlockState(ROCKET.above()).is(Blocks.IRON_BLOCK) && tank(device) == 1_000,
                "Restoration duplicated cargo or used the recorded 5,000 mB instead of the latest 4,000 mB");
        result("RESTORED", "tank=4000 device=1000 rockets=0 journal=idle attempt=" + (commanded ? "after_restart" : "durable"));
    }

    /** No stale copy won: the rocket keeps its latest cargo, no other rocket appeared and the record was not resolved for it. */
    private void requireRefused(RocketTransferSavedData journal) {
        var found = allRockets();
        var current = found.stream().filter(value -> value.getUUID().equals(rocket.getUUID())).findFirst().orElse(null);
        long others = found.stream().filter(value -> !value.getUUID().equals(rocket.getUUID()) && !value.getUUID().equals(duplicate)).count();
        EscrowTags.require(current != null && current.getX() == ROCKET.getX() + 0.5D && cargo(current) == 4_000 && current.escrowSection().epoch() == 1
                && tank(device) == 1_000 && others == 0 && (duplicate == null || found.stream().anyMatch(value -> value.getUUID().equals(duplicate)))
                && journal.find(record.transferId()).isPresent(), "A stale copy won, a protected copy was discarded or the stale record was resolved: rockets="
                + found.size() + " journal=" + journal.entries().size());
        if (placed) {
            var player = server.getPlayerList().getPlayer(PASSENGER);
            EscrowTags.require(player != null && player.getVehicle() == null && player.position().distanceTo(location) < 0.01D,
                    "The disembarked passenger was remounted or moved by a stale record");
        }
    }

    private void join() {
        location = new Vec3(ROCKET.getX() + 2.5D, ROCKET.getY(), ROCKET.getZ() + 2.5D);
        var player = ReleaseTestPlayers.place(server, PASSENGER, "W142Passenger", level, location);
        location = player.position(); placed = true;
    }

    private static RocketPosition position(BlockPos at) { return new RocketPosition(at.getX(), at.getY(), at.getZ()); }
}
