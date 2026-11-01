package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaimBook;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;

/** W231 test-only extension of W170's bay snapshots and the existing unstarted-server harness. */
public final class W231BayCapacityFixture {
    private W231BayCapacityFixture() { }
    public static UUID id(long n) { return new UUID(231, n); }

    /** Eight bays and 2,040 structural blocks; no arbitrary ballast or maximum-sized payload claim. */
    public static RocketTransferRecord record(int rocket, UUID owner) {
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 2048; i++) {
            var pos = new RocketPosition(i % 16, i / 256, (i / 16) % 16);
            var payload = i < 8 ? new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID,
                    SatelliteBayRoot.encode(new SatelliteBayRoot.Contents(owner, BayConsumptionFixtures.pkg(i), null))) : null;
            var block = i < 8 ? ModIdentity.id("rocket_satellite_bay") : ResourceLocation.tryParse("minecraft:iron_block");
            blocks.add(new RocketBlock(pos, new RocketBlockState(block, Map.of()), payload));
        }
        UUID transfer = id(10_000 + rocket), logical = id(20_000 + rocket);
        var origin = new RocketPosition(rocket * 64, 80, 0);
        var target = new RocketPosition(rocket * 64, 80, 128);
        var stats = new RocketStats(2048, 200, 1000, 1000, 1, 1, 1, 8);
        var source = RocketStructureSnapshot.create(id(30_000 + rocket), RocketFlightPlanner.EARTH.dimensionId(), origin,
                blocks, List.of(new RocketPosition(1, 1, 0)), stats, 0);
        var destination = source.relocated(id(40_000 + rocket), RocketFlightPlanner.MOON.dimensionId(), target, 160);
        var fuel = RocketFuelState.empty(1000).fill(1000).state();
        var plan = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, transfer, 0).plan();
        var transit = RocketFlightData.initial(logical, 1000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), origin, 0).withFuel(fuel, 0)
                .withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var arrival = transit.arriveAtDestination(transit.fuel().debit(plan).state(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.MOON.dimensionId(), target, RocketFlightLimits.COUNTDOWN_TICKS
                        + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(transfer, logical, owner, id(50_000 + rocket), source, destination,
                transit, arrival, plan.requiredFuel(), 0);
    }

    public static BayReceipt receipt(RocketTransferRecord record, int bay, long sequence) {
        return new BayReceipt(id(sequence), sequence, record.logicalRocketId(), new RocketPosition(bay, 0, 0),
                id(60_000 + sequence), record.ownerId(), ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA,
                ModIdentity.id("earth").toString(), RocketNativeResources.signature(BayConsumptionFixtures.pkg(bay)),
                record.transferId(), record.sourceSnapshot().contentHash(), null, record.destinationSnapshot().contentHash(),
                null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 1, 1, 0);
    }

    public static MinecraftServer server(Path directory, RocketTransferSavedData journal) {
        var server = UnstartedServer.owned();
        UnstartedServer.set(MinecraftServer.class, server, "running", true);
        var access = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "resources", new HashMap<>());
        UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "levelDirectory",
                new LevelStorageSource.LevelDirectory(directory));
        UnstartedServer.set(MinecraftServer.class, server, "storageSource", access);
        var level = UnstartedServer.allocate(StorageLevel.class);
        level.storage = TestDataStorage.over(directory);
        level.storage.set(RocketTransferSavedData.DATA_NAME, journal);
        UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
        return server;
    }

    /** The real coordinator tick, in a sustained REGISTRY_BLOCKED restart wait for T's startup acknowledgement. */
    public static TickProbe coordinator(MinecraftServer server, BayDeploymentSession session, List<BayReceipt> receipts)
            throws ReflectiveOperationException {
        var constructor = BayTransferRecovery.class.getDeclaredConstructor(MinecraftServer.class, BayDeploymentSession.class);
        constructor.setAccessible(true);
        var coordinator = constructor.newInstance(server, session);
        UnstartedServer.set(BayTransferRecovery.class, coordinator, "ticked", true); // no release-test first-tick hook
        BayClaimBook book = UnstartedServer.get(BayTransferRecovery.class, coordinator, "claims");
        BayStepQueue queue = UnstartedServer.get(BayTransferRecovery.class, coordinator, "queue");
        for (var claim : BayRestartRows.claims(receipts)) {
            claim.members().forEach(member -> member.hold("REGISTRY_BLOCKED"));
            claim.repair("REGISTRY_BLOCKED");
            if (!book.begin(claim)) { throw new IllegalStateException("Fixture claim refused"); }
            queue.offer(claim.logical());
        }
        return new TickProbe(coordinator, book, queue);
    }

    public static final class TickProbe implements AutoCloseable {
        private final BayTransferRecovery coordinator;
        private final BayClaimBook book;
        private final BayStepQueue queue;
        TickProbe(BayTransferRecovery coordinator, BayClaimBook book, BayStepQueue queue) {
            this.coordinator = coordinator; this.book = book; this.queue = queue;
        }
        public void tick() { coordinator.tick(); }
        public int claims() { return book.size(); }
        public int queued() { return queue.size(); }
        public boolean held() { return book.claims().stream().allMatch(c -> c.hold() == BayClaim.Hold.REPAIR); }
        @Override public void close() { coordinator.stop(); }
    }

    private static final class StorageLevel extends ServerLevel {
        DimensionDataStorage storage;
        StorageLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
    }
}
