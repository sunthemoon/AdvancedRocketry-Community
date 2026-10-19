package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFlightTables;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedEnvelope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTransferEnvelope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

/**
 * W11c leaf C-I fixtures (W11CD-DESIGN section 6): explicit before and after snapshots and COMMITTED Moon-to-overworld
 * records whose recorded destination holds one chest payload. No cargo device, mutation or ledger write is involved.
 */
final class RocketTransferRecognitionFixtures {
    /** The one difference between a recorded destination snapshot and its same-ID twin. */
    enum Change { CARGO, GEOMETRY, STATE, ADAPTER, PROPULSION }

    static final ResourceLocation TANK_ADAPTER = ResourceLocation.tryParse("advancedrocketrycommunity:pressurized_tank_v1");
    private static final RocketPosition SOURCE = new RocketPosition(12_000_256, 80, 12_000_256);
    private static final RocketPosition SEAT = new RocketPosition(0, 1, 0);
    private static final RocketPosition HULL = new RocketPosition(1, 0, 0);

    private RocketTransferRecognitionFixtures() { }

    /** An ordinary (record 2) COMMITTED transfer with a bound destination entity ID. */
    static RocketTransferRecord legacy(RocketPosition destinationOrigin) {
        UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID();
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(), SOURCE,
                blocks(1), List.of(SEAT), stats(), 0);
        var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), destinationOrigin, 160);
        var fuel = RocketFuelState.empty(1_000).fill(1_000).state();
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), RocketFlightPlanner.EARTH.dimensionId(), 100, 0);
        var flight = RocketFlightData.initial(logical, 1_000, 1, RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), SOURCE, 0).withFuel(fuel, 0).withPlan(plan).startCountdown(0)
                .completeCountdown(60).beginTransit(transfer, 140);
        var arrival = flight.arriveAtDestination(fuel.debit(transfer, 100).state(), RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), destinationOrigin, 160);
        return committed(RocketTransferRecord.create(transfer, logical, UUID.randomUUID(), UUID.randomUUID(), source,
                destination, flight, arrival, 100, 0), UUID.randomUUID());
    }

    /** A governed (record 3) COMMITTED transfer whose ARMED entry exists only inside its native envelope. */
    static RocketTransferRecord governed(RocketPosition destinationOrigin) {
        UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID(), owner = UUID.randomUUID();
        UUID sourcePhysical = UUID.randomUUID(), destinationPhysical = UUID.randomUUID(), ledger = UUID.randomUUID();
        var descriptor = propulsion(1_000);
        var source = RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.MOON.dimensionId(), SOURCE,
                blocks(1), List.of(SEAT), stats(), 0, descriptor);
        var destination = source.relocated(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), destinationOrigin, 160);
        var fuel = RocketFuelState.empty(descriptor)
                .withFluidBank(false, ResourceLocation.tryParse("advancedrocketrycommunity:hydrogen"), null, 1_000)
                .withFluidBank(true, ResourceLocation.tryParse("advancedrocketrycommunity:oxygen"), null, 1_000);
        var manifest = RocketPassengerManifest.empty(1);
        var idle = RocketFlightData.initial(logical, 1_000, 1, RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), SOURCE, 0).withFuel(fuel, 0).withPassengers(manifest);
        var calculated = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.MOON, RocketFlightPlanner.EARTH,
                transfer, 1).plan();
        var plan = new RocketFlightPlan(4, transfer, RocketFlightPlanner.MOON.bodyId(), RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), RocketFlightPlanner.EARTH.dimensionId(), null, calculated.requiredFuel(), 1,
                new TravelTarget.BodySurface(RocketFlightPlanner.EARTH.bodyId()), calculated.propulsionDebit());
        var transit = idle.withPlan(plan).startCountdown(1).completeCountdown(61).beginTransit(transfer, 141);
        var descent = transit.arriveAtDestination(fuel.debit(plan).state(), RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), destinationOrigin, 161);
        var sourceEnvelope = envelope(sourcePhysical, logical, owner, ledger, source, idle, 7);
        var destinationEnvelope = envelope(destinationPhysical, logical, owner, ledger, destination, descent, 0);
        var arm = new EscrowFlightTables.Armed(ledger, logical, owner, sourcePhysical, 7, transfer, 1, 10, "1".repeat(64),
                "2".repeat(64), EscrowGovernedEnvelope.digest(sourceEnvelope), EscrowGovernedEnvelope.digest(destinationEnvelope),
                destinationPhysical, new EscrowGuard(logical, null, 1, "3".repeat(64)), null, 0, EscrowFlightTables.Outcome.ARMED);
        var nativeTransfer = EscrowTransferEnvelope.capture(arm, sourceEnvelope, destinationEnvelope, source, destination,
                transit, descent);
        return committed(RocketTransferRecord.createGoverned(transfer, logical, owner, sourcePhysical, source, destination,
                transit, descent, plan.requiredFuel(), 1, nativeTransfer), destinationPhysical);
    }

    /** The recorded snapshot's twin: the same snapshot ID, dimension, origin and creation time, with one change. */
    static RocketStructureSnapshot changed(RocketStructureSnapshot recorded, Change change) {
        var blocks = new ArrayList<RocketBlock>();
        for (var block : recorded.blocks()) {
            var payload = block.blockEntityPayload().orElse(null);
            if (payload == null) {
                blocks.add(change == Change.GEOMETRY && block.position().equals(HULL)
                        ? new RocketBlock(new RocketPosition(-1, 0, 0), block.state()) : block);
                continue;
            }
            var state = change == Change.STATE ? new RocketBlockState(block.state().blockId(),
                    Map.of("facing", "south", "type", "single", "waterlogged", "false")) : block.state();
            var adapter = change == Change.ADAPTER ? TANK_ADAPTER : payload.adapterId();
            var data = change == Change.CARGO ? items(payload.data().getList("Items", 10).getCompound(0).getByte("Count") + 1)
                    : payload.data();
            blocks.add(new RocketBlock(block.position(), state, new RocketBlockEntityPayload(adapter, data)));
        }
        var propulsion = recorded.propulsion().orElse(null);
        if (change == Change.PROPULSION) {
            propulsion = propulsion == null ? propulsion(recorded.stats().fuelCapacity())
                    : new RocketPropulsion(propulsion.mode(), propulsion.primaryCapacity(), propulsion.oxidizerCapacity() + 1_000,
                            1, 1, RocketPropulsion.Settings.DEFAULTS);
        }
        return RocketStructureSnapshot.create(recorded.snapshotId(), recorded.sourceDimension(), recorded.sourceOrigin(),
                blocks, recorded.passengerAnchors(), recorded.stats(), recorded.createdAtGameTime(), propulsion);
    }

    /** The same blocks and resources under another snapshot ID, which no rule may recognize. */
    static RocketStructureSnapshot reissued(RocketStructureSnapshot snapshot) {
        return RocketStructureSnapshot.create(UUID.randomUUID(), snapshot.sourceDimension(), snapshot.sourceOrigin(),
                snapshot.blocks(), snapshot.passengerAnchors(), snapshot.stats(), snapshot.createdAtGameTime(),
                snapshot.propulsion().orElse(null));
    }

    private static RocketTransferRecord committed(RocketTransferRecord prepared, UUID destination) {
        return prepared.destinationSpawned(destination).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    private static List<RocketBlock> blocks(int stone) {
        var chest = new RocketBlockState(ResourceLocation.tryParse("minecraft:chest"),
                Map.of("facing", "north", "type", "single", "waterlogged", "false"));
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        return List.of(new RocketBlock(new RocketPosition(0, 0, 0), chest,
                        new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, items(stone))),
                new RocketBlock(SEAT, iron), new RocketBlock(HULL, iron));
    }

    private static CompoundTag items(int stone) {
        var item = new CompoundTag(); item.putByte("Slot", (byte) 0); item.putString("id", "minecraft:stone");
        item.putByte("Count", (byte) stone);
        var list = new ListTag(); list.add(item);
        var data = new CompoundTag(); data.put("Items", list); return data;
    }

    private static RocketStats stats() { return new RocketStats(3, 200, 1_000, 1_000, 1, 1, 1, 1); }

    private static RocketPropulsion propulsion(long capacity) {
        return new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, capacity, 1_000, 1, 1, RocketPropulsion.Settings.DEFAULTS);
    }

    private static CompoundTag envelope(UUID physical, UUID logical, UUID owner, UUID ledger,
                                        RocketStructureSnapshot snapshot, RocketFlightData flight, long epoch) {
        var t = new CompoundTag(); t.putInt("schema_version", 1); t.putUUID("physical", physical);
        t.putString("type", "advancedrocketrycommunity:rocket"); t.putUUID("logical", logical); t.putUUID("owner", owner);
        t.putString("level", snapshot.sourceDimension().toString());
        t.putIntArray("chunk", new int[] {snapshot.sourceOrigin().x() >> 4, snapshot.sourceOrigin().z() >> 4});
        t.put("pos", vector(snapshot.sourceOrigin().x() + 0.5, snapshot.sourceOrigin().y(), snapshot.sourceOrigin().z() + 0.5));
        t.putUUID("assembly_snapshot", snapshot.snapshotId()); t.putString("content_hash", snapshot.contentHash());
        t.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); t.put("flight", RocketFlightNbtCodec.encode(flight));
        t.put("wrapper", new RocketEscrowSection(ledger, epoch, List.of()).encode()); t.put("motion", vector(0, 0, 0));
        t.putBoolean("no_gravity", true); t.putBoolean("no_physics", true); t.putBoolean("on_ground", false);
        t.putBoolean("invulnerable", false); t.putFloat("yaw", 0); t.putFloat("pitch", 0);
        t.put("passengers", new ListTag()); return t;
    }

    private static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); list.add(DoubleTag.valueOf(x)); list.add(DoubleTag.valueOf(y)); list.add(DoubleTag.valueOf(z));
        return list;
    }
}
