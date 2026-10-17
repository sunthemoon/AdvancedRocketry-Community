package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;

/** Pure native envelope fixture; no entity publication, checked acknowledgement or proof is inferred. */
public final class GovernedTransferFixture {
    public record Fixture(RocketTransferRecord record, CompoundTag source, CompoundTag destination) { }
    private GovernedTransferFixture() { }
    public static UUID id(long value) { return new UUID(18014, value); }

    public static Fixture create() { return create(1, 1, false); }

    public static Fixture create(long serial, int passengers, boolean previousPlan) {
        return create(serial, passengers, previousPlan, false);
    }

    public static Fixture maximal() { return create(Long.MAX_VALUE, 16, true, true); }

    private static Fixture create(long serial, int passengers, boolean previousPlan, boolean maximal) {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000,
                1, 1, RocketPropulsion.Settings.DEFAULTS);
        var origin = new RocketPosition(8, 80, 8); var arrival = new RocketPosition(72, 80, 8);
        var blocks = java.util.stream.IntStream.range(0, passengers).mapToObj(i -> new RocketBlock(new RocketPosition(i, 1, 0),
                new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))).toList();
        var seats = java.util.stream.IntStream.range(0, passengers).mapToObj(i -> new RocketPosition(i, 1, 0)).toList();
        // The descriptor and manifest are native; geometry only needs a deterministic bounded snapshot.
        var sourceBody = maximal ? location('a', 128) : RocketFlightPlanner.EARTH.bodyId();
        var sourceLevel = maximal ? location('b', 255) : RocketFlightPlanner.EARTH.dimensionId();
        var destinationBody = maximal ? location('c', 128) : RocketFlightPlanner.MOON.bodyId();
        var destinationLevel = maximal ? location('d', 255) : RocketFlightPlanner.MOON.dimensionId();
        var source = RocketStructureSnapshot.create(id(1), sourceLevel, origin,
                blocks, seats, new RocketStats(passengers, 200, 1_000, 1_000, 1, passengers, 1, 0), 0, descriptor);
        var destination = source.relocated(id(2), destinationLevel, arrival, 160);
        var tag = new CompoundTag(); tag.putString("batch", "source-credit");
        if (maximal) {
            tag.putByteArray("padding", new byte[0]);
            tag.putByteArray("padding", new byte[8_192 - RocketNbtSize.uncompressedBytes(tag)]);
        }
        var fuel = RocketFuelState.empty(descriptor)
                .withFluidBank(false, ResourceLocation.tryParse("advancedrocketrycommunity:hydrogen"), tag, 1_000)
                .withFluidBank(true, ResourceLocation.tryParse("advancedrocketrycommunity:oxygen"), maximal ? tag : null, 1_000);
        if (maximal) {
            fuel = RocketFuelState.restorePropulsion(fuel.propulsion().orElseThrow(),
                    java.util.stream.IntStream.range(0, 63).mapToObj(i -> id(1_000 + i)).toList());
        }
        var manifest = RocketPassengerManifest.empty(passengers);
        for (int i = 0; i < passengers; i++) { manifest = manifest.assign(id(100 + i)).orElseThrow(); }
        var idle = RocketFlightData.initial(id(3), 1_000, passengers, sourceBody,
                sourceLevel, origin, 0).withFuel(fuel, 0).withPassengers(manifest);
        var calculated = RocketFlightPlanner.plan(source.stats(), fuel, RocketFlightPlanner.EARTH,
                RocketFlightPlanner.MOON, id(4), 1).plan();
        var plan = new RocketFlightPlan(4, id(4), sourceBody, destinationBody, sourceLevel, destinationLevel, null,
                calculated.requiredFuel(), 1, new io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget.BodySurface(destinationBody),
                calculated.propulsionDebit());
        if (previousPlan) {
            idle = idle.withPlan(new RocketFlightPlan(4, id(99), sourceBody, destinationBody, sourceLevel, destinationLevel, null,
                    plan.requiredFuel(), 0, plan.destinationTarget(), plan.propulsionDebit()));
        }
        var transit = idle.withPlan(plan).startCountdown(1).completeCountdown(61).beginTransit(id(4), 141);
        var descent = transit.arriveAtDestination(fuel.debit(plan).state(), destinationBody, destinationLevel, arrival, 161);
        var sourceEnvelope = envelope(id(5), source, idle, 7, true);
        var destinationEnvelope = envelope(id(6), destination, descent, 0, false);
        var arm = new EscrowFlightTables.Armed(id(7), id(3), id(8), id(5), 7, id(4), serial, 10,
                "1".repeat(64), "2".repeat(64), EscrowGovernedEnvelope.digest(sourceEnvelope),
                EscrowGovernedEnvelope.digest(destinationEnvelope), id(6), new EscrowGuard(id(3), null, 1, "3".repeat(64)),
                maximal ? id(9) : null, maximal ? 9 : 0, EscrowFlightTables.Outcome.ARMED);
        var nativeTransfer = EscrowTransferEnvelope.capture(arm, sourceEnvelope, destinationEnvelope, source,
                destination, transit, descent);
        return new Fixture(RocketTransferRecord.createGoverned(id(4), id(3), id(8), id(5), source, destination,
                transit, descent, plan.requiredFuel(), 1, nativeTransfer), sourceEnvelope, destinationEnvelope);
    }

    private static CompoundTag envelope(UUID physical, RocketStructureSnapshot snapshot, RocketFlightData flight,
                                        long epoch, boolean mounted) {
        var t = new CompoundTag(); t.putInt("schema_version", 1); t.putUUID("physical", physical);
        t.putString("type", "advancedrocketrycommunity:rocket"); t.putUUID("logical", id(3)); t.putUUID("owner", id(8));
        t.putString("level", snapshot.sourceDimension().toString());
        t.putIntArray("chunk", new int[] {snapshot.sourceOrigin().x() >> 4, snapshot.sourceOrigin().z() >> 4});
        t.put("pos", vector(snapshot.sourceOrigin().x() + 0.5, snapshot.sourceOrigin().y(), snapshot.sourceOrigin().z() + 0.5));
        t.putUUID("assembly_snapshot", snapshot.snapshotId()); t.putString("content_hash", snapshot.contentHash());
        t.put("snapshot", RocketSnapshotNbtCodec.encode(snapshot)); t.put("flight", RocketFlightNbtCodec.encode(flight));
        t.put("wrapper", new RocketEscrowSection(id(7), epoch, List.of()).encode()); t.put("motion", vector(0, 0, 0));
        t.putBoolean("no_gravity", true); t.putBoolean("no_physics", true); t.putBoolean("on_ground", false);
        t.putBoolean("invulnerable", false); t.putFloat("yaw", 0); t.putFloat("pitch", 0);
        var passengers = new ListTag();
        if (mounted) {
            for (var seat : flight.passengers().assignments()) { var p = new CompoundTag(); p.putUUID("physical", seat.passengerId()); passengers.add(p); }
        }
        t.put("passengers", passengers); return t;
    }

    public static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); list.add(DoubleTag.valueOf(x)); list.add(DoubleTag.valueOf(y)); list.add(DoubleTag.valueOf(z)); return list;
    }

    private static ResourceLocation location(char value, int length) {
        return ResourceLocation.tryParse("fixture:" + String.valueOf(value).repeat(length - 8));
    }
}
