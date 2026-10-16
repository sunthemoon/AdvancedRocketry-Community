package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;

/** Gated native F4 cuts. Recovery observes the ordinary dispatcher, without rewriting any saved source. */
final class EscrowDestinationProbe {
    private static final BlockPos SOURCE = new BlockPos(64, 100, 64);
    private static final BlockPos DESTINATION = new BlockPos(80, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private RocketEntity rocket;
    private boolean started;
    private boolean discarded;
    private boolean pending;
    private boolean done;

    EscrowDestinationProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(List.of("DP_ACK", "DP_PUBLICATION", "DP_STORED").contains(cut), "Unknown destination cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        for (var position : List.of(SOURCE, DESTINATION)) {
            var chunk = new ChunkPos(position); server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String point) {
        if (!recovery && started && (cut.equals("DP_ACK") && point.equals("F4_DESTINATION_ACK")
                || cut.equals("DP_PUBLICATION") && point.equals("F4_PUBLICATION_AFTER"))) { pause(); }
    }

    void tick() {
        if (done || pending || !loaded()) { return; }
        var level = server.overworld(); var data = ResourceEscrowSavedData.forTests(server); data.bind(server);
        var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
        if (!recovery && !started) {
            rocket = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
            rocket.load(parent); EscrowTags.require(level.addFreshEntity(rocket), "Destination-cut source publication refused");
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), now);
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F4 destination fixture")));
            started = EscrowGovernedPreparation.begin(rocket, rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, record -> loaded(), record -> { }, () -> discarded = true);
            EscrowTags.require(started, "Destination-cut F1 preparation refused"); return;
        }
        EscrowTags.require(!discarded, "Destination-cut F1 callback discarded");
        if (journal.entries().size() != 1) { return; }
        var record = journal.entries().get(0);
        if (record.phase() != RocketTransferPhase.DESTINATION_SPAWNED) { return; }
        if (!(level.getEntity(record.sourceEntityId()) instanceof RocketEntity source)
                || !(level.getEntity(record.destinationEntityId().orElseThrow()) instanceof RocketEntity arrival)) { return; }
        EscrowTags.require(record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(arrival))
                && source.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())
                && source.escrowSection().epoch() == 1 && arrival.escrowSection().epoch() == 0
                && data.ledger().armedFlights().equals(List.of(record.nativeTransfer().orElseThrow().armed()))
                && !RocketEscrowGate.idle(source) && !RocketEscrowGate.idle(arrival), "Native F4 changed its source or destination authority");
        if (recovery) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_DESTINATION_RESULT cut={} phase=DESTINATION_SPAWNED source_fuel=1000 destination_fuel=500", cut);
            done = true; return;
        }
        if (!cut.equals("DP_STORED")) { return; }
        var frozen = new EscrowFlightSource(arrival); var runtime = EscrowRuntime.of(server);
        pending = runtime.barrier().begin(runtime.store(level), frozen.endpoint, frozen.frontier, frozen.validator(), server.getTickCount(), result -> {
            EscrowTags.require(result.confirmed(), "Native destination checkpoint failed: " + result.reason());
            result.proof().release(); pause();
        });
    }

    private boolean loaded() {
        var level = server.overworld();
        return List.of(SOURCE, DESTINATION).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                && level.isPositionEntityTicking(pos));
    }
    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_DESTINATION_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Native F4 kill did not arrive within 30 seconds");
    }
}
