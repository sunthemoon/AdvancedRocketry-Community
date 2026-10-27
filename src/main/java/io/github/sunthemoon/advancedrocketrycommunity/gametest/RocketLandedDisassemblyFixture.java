package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearSnapshotBlocks;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.clearTransferJournal;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.placeLegalRocket;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.successfulSnapshot;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionQuoter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketLandedDisassemblyGameTestSupport.Requester;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/**
 * The step script and the landed arrival of {@link RocketLandedDisassemblyGameTests} (W85 section 7 A1), moved out of
 * that class unchanged so that it stays under 800 lines (W92F, AGENTS.md section 3.4).
 */
final class RocketLandedDisassemblyFixture {
    private static final TicketType<ChunkPos> TICKET = TicketType.create("arce_landed_disassembly_test",
            Comparator.comparingLong(ChunkPos::toLong), 200);
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;

    private RocketLandedDisassemblyFixture() { }

    /** One onEachTick registered at test start; steps advance through a mutable index (WORKER-RULES). */
    static final class Script {
        final GameTestHelper helper;
        private final List<BooleanSupplier> steps = new ArrayList<>();
        private final GameTestTickPacer pacer;
        private int index;

        Script(GameTestHelper helper) {
            this.helper = helper;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            then(() -> clearTransferJournal(helper.getLevel()));
        }

        Script then(Runnable action) {
            steps.add(() -> { action.run(); return true; });
            return this;
        }

        Script waitUntil(BooleanSupplier ready) {
            steps.add(() -> {
                if (ready.getAsBoolean()) { return true; }
                pacer.pace();
                return false;
            });
            return this;
        }

        Script pause(int ticks) {
            int[] left = {ticks};
            steps.add(() -> --left[0] <= 0);
            return this;
        }

        void check(boolean condition, String message) {
            helper.assertTrue(condition, message);
        }

        /** A landed arrival E with its COMMITTED record T, settled in a fresh session; then the scenario; then cleanup. */
        void landed(boolean fueled, boolean detached, java.util.function.BiConsumer<Script, Arrival> scenario) {
            Arrival arrival = new Arrival(helper);
            then(arrival::prepare);
            waitUntil(arrival::ready);
            then(() -> arrival.land(fueled, detached));
            scenario.accept(this, arrival);
            then(arrival::close);
        }

        void start() {
            helper.onEachTick(() -> {
                while (index < steps.size() && steps.get(index).getAsBoolean()) { index++; }
                if (index == steps.size()) { index++; helper.succeed(); }
            });
        }
    }

    /**
     * A landed arrival on the Moon, from the same scan and plan as the resource-recovery fixture. Scenario steps are
     * written before it exists; {@link #prepare} builds it when the script reaches it.
     */
    static final class Arrival {
        final GameTestHelper helper;
        final ServerLevel earth;
        final ServerLevel moon;
        final UUID owner = UUID.randomUUID();
        RocketTransferRecord record;
        MoonPadArea.Changes ground;
        RocketEntity rocket;
        RocketTransferRecord landed;
        RocketLandedDisassemblyGameTestSupport support;
        RocketTransferSavedData detached;
        Path directory;

        Arrival(GameTestHelper helper) {
            this.helper = helper;
            earth = helper.getLevel();
            moon = earth.getServer().getLevel(CelestialIds.MOON_LEVEL);
            helper.assertTrue(moon != null, "Moon Level unavailable");
        }

        void prepare() {
            placeLegalRocket(helper, ORIGIN);
            helper.setBlock(ORIGIN.west(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(ORIGIN.east(), ModBlocks.ROCKET_FUEL_TANK.get());
            helper.setBlock(ORIGIN.north(), ModBlocks.ROCKET_FUEL_TANK.get());
            var snapshot = successfulSnapshot(helper, ORIGIN);
            clearSnapshotBlocks(earth, snapshot);
            var sourceOrigin = snapshot.sourceOrigin();
            var destinationOrigin = new RocketPosition(sourceOrigin.x(),
                    Math.max(moon.getMinBuildHeight() + 16, sourceOrigin.y()), sourceOrigin.z());
            var destination = snapshot.relocated(UUID.randomUUID(), moon.dimension().location(), destinationOrigin,
                    earth.getGameTime());
            clearSnapshotBlocks(moon, destination);
            // ADR-075: a Moon destination below y 80 is rechecked for support, so it gets ground of its own.
            ground = new MoonPadArea.Changes(moon);
            ground.support(destination);
            var fuel = RocketFuelState.empty(snapshot.propulsion().orElseThrow()).withFluidBank(false,
                    ModIdentity.id("rocket_fuel"), null, 3_000);
            UUID logical = UUID.randomUUID();
            UUID transfer = UUID.randomUUID();
            long now = earth.getGameTime();
            var base = new RocketFlightPlan(3, transfer, CelestialIds.EARTH_ID, CelestialIds.MOON_ID,
                    earth.dimension().location(), moon.dimension().location(), 417, now);
            var catalog = new RocketFluidFuelCatalog(Map.of(
                    new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")),
                    new RocketFluidFuelCatalog.Efficiency(417, 1_000)));
            var plan = RocketPropulsionQuoter.quote(fuel, base, catalog).plan();
            var flight = RocketFlightData.initial(logical, 3_000, snapshot.stats().seatCount(), CelestialIds.EARTH_ID,
                    earth.dimension().location(), snapshot.sourceOrigin(), now).withFuel(fuel, now);
            var transit = flight.withPlan(plan).startCountdown(now).completeCountdown(now).beginTransit(transfer, now);
            var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.MOON_ID,
                    moon.dimension().location(), destination.sourceOrigin(), now);
            record = RocketTransferRecord.create(transfer, logical, owner, UUID.randomUUID(), snapshot, destination,
                    transit, arrival, plan.requiredFuel(), now);
        }

        boolean ready() {
            keepLoaded(earth, record.sourceSnapshot());
            keepLoaded(moon, record.destinationSnapshot());
            var origin = record.destinationSnapshot().sourceOrigin();
            long chunk = ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4);
            return moon.areEntitiesLoaded(chunk) && earth.areEntitiesLoaded(chunk);
        }

        private static void keepLoaded(ServerLevel level,
                                       io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
            var chunks = new java.util.HashSet<ChunkPos>();
            for (var block : snapshot.blocks()) {
                var absolute = snapshot.sourceOrigin().add(block.position());
                var at = new BlockPos(absolute.x(), absolute.y(), absolute.z());
                var chunk = new ChunkPos(at);
                if (chunks.add(chunk)) {
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 2, chunk);
                    level.getChunkAt(at);
                }
            }
        }

        /** Removes this fixture's Moon tickets, so that E's chunk and entity section can unload; {@link #ready} re-adds them. */
        void releaseMoonTickets() {
            var snapshot = record.destinationSnapshot();
            var chunks = new java.util.HashSet<ChunkPos>();
            for (var block : snapshot.blocks()) {
                var absolute = snapshot.sourceOrigin().add(block.position());
                var chunk = new ChunkPos(new BlockPos(absolute.x(), absolute.y(), absolute.z()));
                if (chunks.add(chunk)) { moon.getChunkSource().removeRegionTicket(TICKET, chunk, 2, chunk); }
            }
        }

        void land(boolean fueled, boolean isolated) {
            rocket = ModEntities.ROCKET.get().create(moon);
            rocket.initializeTransferred(record.destinationSnapshot(), record.logicalRocketId(), owner,
                    record.destinationFlightData().land(moon.getGameTime()));
            helper.assertTrue(moon.addFreshEntity(rocket), "Arrival entity admission failed");
            if (!fueled) {
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(RocketFuelState.empty(
                        record.destinationSnapshot().propulsion().orElseThrow()), moon.getGameTime()));
            }
            landed = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                    .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
            support = RocketLandedDisassemblyGameTestSupport.create(helper);
            if (isolated) {
                try { directory = Files.createTempDirectory("arce-w92-landed-"); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
                detached = new RocketTransferSavedData();
                detached.put(landed);
                RocketJournalGameTestSupport.confirmDetached(helper, detached, file());
                return;
            }
            journal().put(landed);
            RocketJournalGameTestSupport.acknowledgeFixture(helper);
            var installed = RocketJournalGameTestSupport.recoverFixture(helper,
                    () -> RocketRuntime.recoverTransfer(earth.getServer(), landed.transferId()));
            helper.assertTrue(installed.status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "Installed recovery did not settle the arrival: " + installed);
            helper.assertTrue(support.recover(landed.transferId()).status() == RocketTransferRecoveryReport.Status.RECOVERED,
                    "The session did not settle the arrival");
            helper.assertTrue(support.resourceMutationAllowed(rocket), "The settled arrival is not landed authority");
        }

        RocketTransferSavedData journal() { return RocketTransferSavedData.get(earth.getServer()); }
        UUID logical() { return record.logicalRocketId(); }
        RocketRegion region() { return RocketRegion.fromSnapshot(record.destinationSnapshot()); }
        Path file() { return directory.resolve(TYPE.fileName()); }

        Requester requester(UUID id) {
            var player = new Requester(moon, id);
            player.setPos(rocket.position());
            return player;
        }

        RocketValidationCode release(Requester requester) { return support.releaseDetached(rocket, requester, detached); }

        void write(CheckedSavedDataPolicy policy, boolean poll) {
            RocketJournalGameTestSupport.writeDetached(helper, detached, file(), policy, poll);
        }

        BlockPos footprint(int index) {
            var snapshot = record.destinationSnapshot();
            var at = snapshot.sourceOrigin().add(snapshot.blocks().get(index).position());
            return new BlockPos(at.x(), at.y(), at.z());
        }

        void obstruct(int index) { moon.setBlockAndUpdate(footprint(index), Blocks.STONE.defaultBlockState()); }
        void clearObstruction(int index) { moon.setBlockAndUpdate(footprint(index), Blocks.AIR.defaultBlockState()); }

        boolean onlyObstruction(int index) {
            for (int i = 0; i < record.destinationSnapshot().blocks().size(); i++) {
                var state = moon.getBlockState(footprint(i));
                if (i == index ? !state.is(Blocks.STONE) : !state.isAir()) { return false; }
            }
            return true;
        }

        boolean anyRestored() {
            for (int i = 0; i < record.destinationSnapshot().blocks().size(); i++) {
                if (!moon.getBlockState(footprint(i)).isAir() && !moon.getBlockState(footprint(i)).is(Blocks.STONE)) {
                    return true;
                }
            }
            return false;
        }

        boolean allRestored() {
            var snapshot = record.destinationSnapshot();
            for (int i = 0; i < snapshot.blocks().size(); i++) {
                var id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(moon.getBlockState(footprint(i)).getBlock());
                if (!snapshot.blocks().get(i).state().blockId().equals(id)) { return false; }
            }
            return true;
        }

        /** T's encoded record in the world journal's checked file, or in the isolated file. */
        CompoundTag diskRecord() {
            Path path = detached != null ? file() : earth.getServer().getWorldPath(LevelResource.ROOT)
                    .resolve("data").resolve(TYPE.fileName());
            var payload = CheckedSavedDataFile.readPayload(path, TYPE).orElseThrow();
            for (var raw : payload.getList("transfers", 10)) {
                var tag = (CompoundTag) raw;
                if (tag.getUUID("transfer_id").equals(landed.transferId())) { return tag.copy(); }
            }
            return new CompoundTag();
        }

        List<UUID> diskTransfers() {
            var payload = CheckedSavedDataFile.readPayload(file(), TYPE).orElseThrow();
            return RocketTransferSavedData.load(payload).entries().stream().map(RocketTransferRecord::transferId).toList();
        }

        void close() {
            if (rocket != null && !rocket.isRemoved()) { rocket.discard(); }
            clearSnapshotBlocks(moon, record.destinationSnapshot());
            ground.restore();
            clearSnapshotBlocks(earth, record.sourceSnapshot());
            if (detached != null) {
                detached.stop();
                try {
                    Files.deleteIfExists(file());
                    try (var stale = Files.list(directory)) {
                        for (var path : stale.toList()) { Files.deleteIfExists(path); }
                    }
                    Files.delete(directory);
                } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                }
            } else {
                clearTransferJournal(earth);
                RocketJournalGameTestSupport.acknowledgeFixture(helper);
            }
        }
    }
}
