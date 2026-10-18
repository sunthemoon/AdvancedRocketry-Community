package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.AnvilRecordReader;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Detached ordinary-flight fixtures for the W98 source-retirement GameTests (P1-N3 leaf N3-A1). */
final class RocketSourceRetirementFixtures {
    static final long FUEL = 1_000, REQUIRED = 500;

    private RocketSourceRetirementFixtures() { }

    /** One ordinary PREPARED flight: the entity's COUNTDOWN data and the record as prepareLaunch writes it. */
    record Flight(RocketTransferRecord record, RocketFlightData countdown) {
        RocketEntity spawnCountdown(GameTestHelper helper) {
            return spawn(helper, helper.getLevel(), countdown, false);
        }

        /** A source image that is still in flight, as a save taken during ASCENT or TRANSIT holds it. */
        RocketEntity spawnInFlight(GameTestHelper helper, ServerLevel level) {
            return spawn(helper, level, record.sourceFlightData(), true);
        }

        RocketEntity spawn(GameTestHelper helper, ServerLevel level, RocketFlightData flight, boolean atAltitude) {
            RocketEntity rocket = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(rocket != null, "No rocket entity type");
            rocket.setUUID(record.sourceEntityId());
            rocket.initializeTransferred(record.sourceSnapshot(), record.logicalRocketId(), record.ownerId(), flight);
            if (atAltitude) { RocketTransferEntities.positionAtAltitude(rocket, record.sourceSnapshot().sourceOrigin()); }
            else { RocketTransferEntities.positionAtOrigin(rocket, record.sourceSnapshot().sourceOrigin()); }
            helper.assertTrue(level.addFreshEntity(rocket), "Rocket was not published");
            return rocket;
        }

        /** A destination copy still descending at its arrival origin, as recovery may find it. */
        RocketEntity spawnDestination(GameTestHelper helper, ServerLevel level) {
            RocketEntity rocket = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(rocket != null, "No rocket entity type");
            rocket.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), record.ownerId(),
                    record.destinationFlightData());
            RocketTransferEntities.positionAtAltitude(rocket, record.destinationSnapshot().sourceOrigin());
            helper.assertTrue(level.addFreshEntity(rocket), "Destination copy was not published");
            return rocket;
        }

        ChunkPos originSlot() { return slot(record.sourceSnapshot()); }
        ChunkPos destinationSlot() { return slot(record.destinationSnapshot()); }
    }

    static ChunkPos slot(RocketStructureSnapshot snapshot) {
        return new ChunkPos(snapshot.sourceOrigin().x() >> 4, snapshot.sourceOrigin().z() >> 4);
    }

    /** A same-Level ordinary flight whose countdown began {@code countdownAge} ticks ago, with {@code crew} assigned. */
    static Flight countdown(GameTestHelper helper, BlockPos origin, BlockPos target, List<UUID> crew, long countdownAge) {
        return countdown(helper.getLevel(), origin, target, crew, countdownAge);
    }

    static Flight countdown(ServerLevel level, BlockPos origin, BlockPos target, List<UUID> crew, long countdownAge) {
        long now = level.getGameTime(), started = now - countdownAge;
        UUID logical = UUID.randomUUID(), transfer = UUID.randomUUID(), owner = UUID.randomUUID();
        int seats = Math.max(1, crew.size());
        var propulsion = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, (int) FUEL, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var anchors = new ArrayList<RocketPosition>();
        for (int seat = 0; seat < seats; seat++) { anchors.add(new RocketPosition(0, seat, 0)); }
        var blocks = new ArrayList<RocketBlock>();
        for (int y = 0; y < seats; y++) {
            blocks.add(new RocketBlock(new RocketPosition(0, y, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of())));
        }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(), position(origin), blocks,
                anchors, new RocketStats(seats, 100L * seats, FUEL, FUEL, 1, seats, 1, 0), started, propulsion);
        var arrivalSnapshot = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), position(target), started);
        var plan = new RocketFlightPlan(3, transfer, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                level.dimension().location(), level.dimension().location(), REQUIRED, started);
        var fuel = RocketFuelState.empty(propulsion).fill(FUEL).state();
        var flight = RocketFlightData.initial(logical, FUEL, seats, plan.sourceBody(), plan.sourceDimension(), position(origin), started)
                .withFuel(fuel, started);
        var manifest = flight.passengers();
        for (UUID passenger : crew) { manifest = manifest.assign(passenger).orElseThrow(); }
        var countdown = flight.withPassengers(manifest).withPlan(plan).startCountdown(started);
        long ascent = started + RocketFlightLimits.COUNTDOWN_TICKS, transit = ascent + RocketFlightLimits.ASCENT_TICKS;
        var scheduled = countdown.completeCountdown(ascent).beginTransit(transfer, transit);
        var arrival = scheduled.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(), plan.destinationDimension(),
                position(target), transit + RocketFlightLimits.TRANSIT_TICKS);
        var record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), snapshot, arrivalSnapshot,
                scheduled, arrival, REQUIRED, started);
        return new Flight(record, countdown);
    }

    static RocketPosition position(BlockPos pos) { return new RocketPosition(pos.getX(), pos.getY(), pos.getZ()); }

    /** The saved entity slot's tags, read from the region file without loading or saving anything; empty if unwritten. */
    static List<CompoundTag> disk(ServerLevel level, ChunkPos slot) {
        var directory = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT)).resolve("entities");
        try {
            var tags = new ArrayList<CompoundTag>();
            for (Tag raw : AnvilRecordReader.read(directory, slot).root().getList("Entities", Tag.TAG_COMPOUND)) { tags.add((CompoundTag) raw); }
            return tags;
        } catch (IOException unwritten) { return List.of(); }
    }

    /** The saved image of {@code entity} in {@code slot}, or null. */
    static CompoundTag diskImage(ServerLevel level, ChunkPos slot, UUID entity) {
        return disk(level, slot).stream().filter(tag -> tag.hasUUID("UUID") && tag.getUUID("UUID").equals(entity)).findFirst().orElse(null);
    }

    /** Saved images naming logical rocket L in {@code slot}. */
    static long diskCopies(ServerLevel level, ChunkPos slot, UUID logical) {
        return disk(level, slot).stream().filter(tag -> RocketRetirementProof.names(tag, logical, new UUID(0L, 0L))).count();
    }

    /** A saved image is the returned survivor: stationary, no plan, at its snapshot origin, with its full source fuel. */
    static boolean stationaryImage(CompoundTag image, RocketTransferRecord record) {
        if (image == null) { return false; }
        var flight = image.getCompound(RocketRetirementProof.DATA).getCompound("flight_data");
        var pos = image.getList("Pos", Tag.TAG_DOUBLE); var origin = record.sourceSnapshot().sourceOrigin();
        String state = flight.getString("state");
        return (state.equals("FUELED") || state.equals("ASSEMBLED")) && !flight.contains("plan") && !flight.contains("active_transfer_id")
                && pos.getDouble(0) == origin.x() + 0.5D && pos.getDouble(1) == origin.y() && pos.getDouble(2) == origin.z() + 0.5D
                && flight.getCompound("fuel").getLong("amount") == FUEL;
    }

    @SuppressWarnings("unchecked")
    static <T> T field(Object owner, String name) {
        try {
            var field = owner.getClass().getDeclaredField(name); field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    static Set<UUID> live(RocketTransferService service) { return field(service, "liveTransfers"); }
    static Set<UUID> settled(RocketTransferService service) { return field(service, "settledTransfers"); }
    static RocketTransferRecoveryService recovery(RocketTransferService service) { return field(service, "recovery"); }
}
