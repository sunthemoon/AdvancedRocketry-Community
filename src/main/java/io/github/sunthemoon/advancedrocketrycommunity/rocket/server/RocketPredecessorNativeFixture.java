package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadEntry;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.ChunkEntities;
import net.minecraft.world.level.entity.EntityPersistentStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Empty-server-only native fixture. The harness saves S before asking it to unload, never at an I7 cut. */
public final class RocketPredecessorNativeFixture {
    private final MinecraftServer server;
    private final ServerLevel source, destination;
    private final RocketEntity original;
    private final String mode;
    private final long started;
    private RocketEntity landed;
    private RocketTransferRecord old;
    private StationState station;
    private LandingPadEntry pad;
    private EntityPersistentStorage<Entity> storage;
    private boolean ready, launched, closed, coldSpent;

    public RocketPredecessorNativeFixture(MinecraftServer server, RocketEntity original, String mode) {
        EscrowTestEndpoints.requireEnabled(server);
        if (!java.util.Set.of("UNSAVED", "DESCENT", "SAME_SLOT", "NAMED", "STOLEN", "REFUSED",
                "SAVE_OFF", "STORAGE", "SATURATED", "COLD_SPENT", "CREW").contains(mode)
                || !RocketRuntime.resourceMutationAllowed(original)
                || RocketTransferSavedData.get(server).findByLogicalRocket(original.assemblyTransactionId().orElseThrow()).isPresent()) {
            throw new IllegalArgumentException("Invalid predecessor fixture input");
        }
        this.server = server; this.original = original; this.mode = mode; source = (ServerLevel) original.level();
        destination = mode.equals("SAME_SLOT") ? source : server.getLevel(CelestialIds.MOON_LEVEL);
        started = server.getTickCount();
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || event.getServer() != server || closed) { return; }
        if (coldSpent && old != null) {
            // A fixture-controlled unavailable entity column, not a storage decorator or a production ticket change.
            source.entityManager.updateChunkStatus(RocketSourceRetirementFixtures.slot(old.sourceSnapshot()),
                    net.minecraft.world.level.entity.Visibility.HIDDEN);
        }
        if (ready) { return; }
        if (server.getTickCount() - started > 1200) { close(); throw new IllegalStateException("Predecessor fixture did not become ready"); }
        var journal = RocketTransferSavedData.get(server);
        if (old == null) {
            if (source.areEntitiesLoaded(original.chunkPosition().toLong())) { return; }
            seed(journal);
        }
        if (!journal.logicalAcknowledged(old.logicalRocketId())
                || !destination.areEntitiesLoaded(landed.chunkPosition().toLong())) { return; }
        ready = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_I7_FIXTURE_READY mode={} transfer={} logical={} source={} destination={} source_loaded={} dimension={}",
                mode, old.transferId(), old.logicalRocketId(), old.sourceEntityId(), landed.getUUID(),
                source.areEntitiesLoaded(RocketSourceRetirementFixtures.slot(old.sourceSnapshot()).toLong()), destination.dimension().location());
    }

    private void seed(RocketTransferSavedData journal) {
        long now = source.getGameTime(); UUID transfer = UUID.randomUUID();
        var snapshot = original.snapshot().orElseThrow(); var fuel = original.flightData().orElseThrow().fuel();
        var body = destination == source ? CelestialIds.EARTH_ID : CelestialIds.MOON_ID;
        var fromBody = destination == source ? CelestialIds.MOON_ID : CelestialIds.EARTH_ID;
        var at = destination == source ? snapshot.sourceOrigin() : new RocketPosition(8, 100, 8);
        var arrival = snapshot.relocated(UUID.randomUUID(), destination.dimension().location(), at, now);
        var initial = RocketFlightData.initial(original.assemblyTransactionId().orElseThrow(), fuel.capacity(),
                original.flightData().orElseThrow().passengers().seatCapacity(), fromBody, source.dimension().location(), snapshot.sourceOrigin(), now)
                .withFuel(fuel, now);
        if (mode.equals("CREW")) { initial = initial.withPassengers(initial.passengers().assign(UUID.randomUUID()).orElseThrow()); }
        var plan = new RocketFlightPlan(3, transfer, fromBody, body, source.dimension().location(), destination.dimension().location(), 500, now);
        var countdown = initial.withPlan(plan).startCountdown(now);
        var transit = RocketTransferService.scheduledTransit(countdown);
        var descent = transit.arriveAtDestination(fuel.debit(plan).state(), body, destination.dimension().location(), at,
                RocketTransferService.scheduledArrival(countdown));
        RocketTransferEntities.keepLoaded(destination, arrival, transfer);
        landed = ModEntities.ROCKET.get().create(destination);
        if (landed == null) { throw new IllegalStateException("No fixture rocket type"); }
        var state = mode.equals("DESCENT") ? descent : descent.land(now);
        if (mode.equals("CREW")) { state = state.withPassengers(RocketPassengerManifest.empty(initial.passengers().seatCapacity())); }
        landed.initializeTransferred(arrival, initial.logicalRocketId(), original.ownerId().orElseThrow(), state);
        RocketTransferEntities.positionAtOrigin(landed, at);
        if (!destination.addFreshEntity(landed)) { throw new IllegalStateException("Fixture destination refused"); }
        old = RocketTransferRecord.create(transfer, initial.logicalRocketId(), original.ownerId().orElseThrow(), original.getUUID(),
                snapshot, arrival, transit, descent, 500, now).destinationSpawned(landed.getUUID())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        journal.put(old);
        RocketSourceRetirementFixtures.settled(RocketRuntime.installedTransfers()).add(transfer);
        journal.flushChecked(server);
    }

    /** Called only after the harness has saved a requested DESCENT preimage, before any retirement proof starts. */
    public void land() {
        requireReady();
        var flight = landed.flightData().orElseThrow();
        if (flight.state() == RocketFlightState.DESCENT) { landed.updateFlightData(flight.land(destination.getGameTime())); }
        if (mode.equals("NAMED") || mode.equals("STOLEN")) { namedPad(); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_I7_FIXTURE_LANDED mode={} entity={}", mode, landed.getUUID());
    }

    private void namedPad() {
        station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                .create(server, old.ownerId(), "I7 native dock", CelestialIds.EARTH_ID, false).station().orElseThrow();
        var space = server.getLevel(CelestialIds.SPACE_LEVEL);
        var at = new BlockPos(station.landingPad().x() + 32, station.landingPad().y() - 1, station.landingPad().z());
        for (int x = -3; x <= 3; x++) { for (int z = -3; z <= 3; z++) {
            var pos = at.offset(x, 0, z); space.getChunkAt(pos);
            space.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            space.setBlockAndUpdate(pos, Blocks.GRAY_CONCRETE.defaultBlockState());
        } }
        space.setBlockAndUpdate(at, ModBlocks.LANDING_PAD.get().defaultBlockState());
        pad = LandingPadService.register(space, at, old.ownerId(), false, "I7 native dock");
    }

    public void launch() {
        requireReady();
        if (launched) { throw new IllegalStateException("Fixture launch already requested"); }
        launched = true;
        if (mode.equals("SAVE_OFF")) { destination.noSave = true; }
        if (mode.equals("STORAGE")) {
            storage = destination.entityManager.permanentStorage;
            destination.entityManager.permanentStorage = new ForeignStorage(storage);
        }
        coldSpent = mode.equals("COLD_SPENT");
        var result = pad == null ? RocketRuntime.requestAdminFlight(landed,
                destination == source ? RocketDestination.MOON : RocketDestination.EARTH, UUID.randomUUID())
                : RocketRuntime.requestAdminPadFlight(landed, station.stationId(), pad.reference(), UUID.randomUUID());
        if (result.code() != RocketFlightRequestCode.JOURNAL_PENDING) { throw new IllegalStateException("Fixture launch: " + result.code()); }
        if (mode.equals("REFUSED")) {
            var flight = landed.flightData().orElseThrow();
            landed.updateFlightData(flight.withPassengers(flight.passengers().assign(UUID.randomUUID()).orElseThrow()));
        }
        if (mode.equals("STOLEN")) {
            LandingPadService.remove(server.getLevel(CelestialIds.SPACE_LEVEL), pad.position(station), old.ownerId(), false);
        }
        if (mode.equals("SATURATED")) {
            for (int i = 0; i < RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES; i++) {
                var copy = ModEntities.ROCKET.get().create(destination);
                copy.initializeTransferred(landed.snapshot().orElseThrow(), old.logicalRocketId(), old.ownerId(), landed.flightData().orElseThrow());
                RocketTransferEntities.positionAtOrigin(copy, landed.snapshot().orElseThrow().sourceOrigin());
                if (!destination.addFreshEntity(copy)) { throw new IllegalStateException("Fixture saturation refused"); }
            }
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_I7_FIXTURE_LAUNCH mode={} code={} old={} entity={}", mode, result.code(), old.transferId(), landed.getUUID());
    }

    private void requireReady() {
        EscrowTestEndpoints.requireEnabled(server);
        if (!ready || closed) { throw new IllegalStateException("Predecessor fixture is not ready"); }
    }

    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (event.getServer() == server) { close(); } }
    private void close() {
        if (closed) { return; } closed = true;
        if (storage != null) { destination.entityManager.permanentStorage = storage; }
        destination.noSave = false;
        MinecraftForge.EVENT_BUS.unregister(this);
    }

    private record ForeignStorage(EntityPersistentStorage<Entity> delegate) implements EntityPersistentStorage<Entity> {
        public CompletableFuture<ChunkEntities<Entity>> loadEntities(ChunkPos pos) { return delegate.loadEntities(pos); }
        public void storeEntities(ChunkEntities<Entity> entities) { delegate.storeEntities(entities); }
        public void flush(boolean sync) { delegate.flush(sync); }
        public void close() throws IOException { delegate.close(); }
    }
}
