package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** Real ordinary arrival adapter against a retired pad whose occupying entity is initially unloaded. */
public final class RocketRetiredPadGameTestProbe implements AutoCloseable {
    private static final TicketType<UUID> AREA = TicketType.create("arce_r52_pad_source", UUID::compareTo);
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final RocketTransferService service = new RocketTransferService();
    private final RocketTransferSavedData journal = new RocketTransferSavedData();
    private final UUID ticket = UUID.randomUUID();
    private final ChunkPos chunk;
    private final RocketEntity occupant;
    private RocketEntity source;
    private RocketTransferRecord record;
    private Path directory;
    private boolean closed;

    public RocketRetiredPadGameTestProbe(GameTestHelper helper, RocketEntity occupant) {
        EscrowTestEndpoints.requireEnabled(helper.getLevel().getServer());
        this.helper = helper; this.occupant = occupant; level = helper.getLevel();
        chunk = new ChunkPos(occupant.chunkPosition().x + 100, occupant.chunkPosition().z);
        level.getChunkSource().addRegionTicket(AREA, chunk, 2, ticket); level.getChunk(chunk.x, chunk.z);
    }

    public boolean prepare() {
        var at = new BlockPos(chunk.getMinBlockX() + 8, 100, chunk.getMinBlockZ() + 8);
        if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(at)) { return false; }
        if (record == null) {
            var snapshot = occupant.snapshot().orElseThrow(); long now = level.getGameTime();
            var origin = new RocketPosition(at.getX(), at.getY(), at.getZ());
            var from = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), origin, now);
            var to = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), snapshot.sourceOrigin(), now);
            UUID transfer = UUID.randomUUID(), logical = UUID.randomUUID();
            var fuel = occupant.flightData().orElseThrow().fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 500);
            var plan = new RocketFlightPlan(3, transfer, CelestialIds.MOON_ID, CelestialIds.EARTH_ID,
                    level.dimension().location(), level.dimension().location(), 500, now)
                    .withPropulsionDebit(new RocketPropulsionDebit(500, 0, fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(),
                            RocketNativeResources.hash("R52 occupied pad fixture")));
            var transit = RocketFlightData.initial(logical, from.stats().fuelCapacity(), 1, CelestialIds.MOON_ID,
                    level.dimension().location(), origin, now).withFuel(fuel, now).withPlan(plan).startCountdown(now)
                    .completeCountdown(now + 60).beginTransit(transfer, now + 140);
            var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), CelestialIds.EARTH_ID,
                    level.dimension().location(), to.sourceOrigin(), now + 160);
            source = ModEntities.ROCKET.get().create(level);
            helper.assertTrue(source != null, "Competing flight source creation failed");
            source.initializeTransferred(from, logical, UUID.randomUUID(), transit);
            helper.assertTrue(level.addFreshEntity(source), "Competing flight source publication failed");
            record = RocketTransferRecord.create(transfer, logical, source.ownerId().orElseThrow(), source.getUUID(), from, to, transit, arrival, 500, now);
            journal.put(record);
            try { directory = Files.createTempDirectory("arce-r52-pad-"); journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile()); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
        journal.pollWriter(level.getServer().getTickCount());
        return journal.acknowledgedCurrent();
    }

    public void cold() {
        helper.assertTrue(!level.areEntitiesLoaded(occupant.chunkPosition().toLong()), "Retired occupant did not unload");
        service.spawnDestination(level.getServer(), journal, record, source);
        helper.assertTrue(journal.find(record.transferId()).orElseThrow() == record && !source.isRemoved()
                && source.flightData().orElseThrow().equals(record.sourceFlightData()), "Cold occupied arrival changed authority before entity readiness");
    }

    public boolean blocked() {
        service.spawnDestination(level.getServer(), journal, record, source);
        if (source.flightData().orElseThrow().activeTransferId().isPresent()) { return false; }
        helper.assertTrue(!source.isRemoved() && source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())
                        && journal.find(record.transferId()).flatMap(RocketTransferRecord::destinationEntityId).isEmpty(),
                "Competing flight landed on the retired occupied pad or lost source fuel");
        return true;
    }

    @Override public void close() {
        if (closed) { return; } closed = true;
        if (source != null) { source.discard(); }
        RocketSourceRetirementFixtures.recovery(service).retirements().clear(); journal.stop();
        level.getChunkSource().removeRegionTicket(AREA, chunk, 2, ticket);
        if (record != null) { RocketTransferEntities.releaseFlightTickets(level.getServer(), record); }
        if (directory != null) {
            try { Files.deleteIfExists(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat")); Files.deleteIfExists(directory); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }
    }
}
