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
import net.minecraft.world.phys.AABB;

/** Empty-server-only native F3 cuts; no source clock or resource is edited on recovery. */
final class EscrowSourceClockProbe {
    private static final BlockPos SOURCE = new BlockPos(64, 100, 64);
    private static final BlockPos DESTINATION = new BlockPos(80, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private boolean waiting;
    private boolean done;
    private boolean observedRollback;
    private boolean accepted;
    private boolean discarded;
    private int step;

    EscrowSourceClockProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(List.of("SC_COUNTDOWN", "SC_ASCENT", "SC_TRANSIT").contains(cut), "Unknown source-clock cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        for (var position : List.of(SOURCE, DESTINATION)) {
            var chunk = new ChunkPos(position);
            server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z); // Fixed release fixture, not a caller's endpoint.
        }
    }

    void tick() {
        if (done || waiting || !loaded()) { return; }
        var level = server.overworld();
        if (data == null) { data = ResourceEscrowSavedData.forTests(server); data.bind(server); }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (recovery) { recover(journal); return; }
        if (step == 0) {
            if (data.ledger().writerGeneration() == 0 || !data.bind(server).writerIdle()) {
                data.setDirty(); data.bind(server).autosave(); return;
            }
            // Keep this source-only cut scoped to F3 now that the dispatcher also implements F4.
            // The source must still reach DESTINATION_DUE; a blocked pad cannot publish a destination.
            level.setBlockAndUpdate(DESTINATION, net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState());
            EscrowTags.require(server.saveEverything(false, true, true), "Source-clock metadata baseline refused");
            // Deliberately create an older metadata/newer entity-store order without rewriting disk files.
            // This occurs before F1; no autosave is inserted after its complete source checkpoint.
            server.getWorldData().overworldData().setGameTime(Math.addExact(level.getGameTime(), 10_000));
            rocket = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
            rocket.load(parent); EscrowTags.require(level.addFreshEntity(rocket), "Native source publication refused");
            var flight = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = flight.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
            rocket.updateFlightData(flight.withFuel(fuel, now).withPassengers(flight.passengers().assign(UUID.randomUUID()).orElseThrow()));
            step = 1;
        }
        if (step == 1) {
            var from = rocket.snapshot().orElseThrow(); var fuel = rocket.flightData().orElseThrow().fuel(); long now = level.getGameTime();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), now);
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("source-clock fixture")));
            boolean started = EscrowGovernedPreparation.begin(rocket, rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, record -> onlySource(), record -> accepted = true, () -> discarded = true);
            EscrowTags.require(started, "Native F1 preparation refused"); step = 2; return;
        }
        EscrowTags.require(!discarded, "Native F1/F2 callback was discarded");
        if (!accepted || !rocket.flightData().orElseThrow().state().name().equals(cut.substring(3))) { return; }
        if (cut.equals("SC_ASCENT") && rocket.getY() < SOURCE.getY() + 12) { return; }
        var frozen = new EscrowFlightSource(rocket); var runtime = EscrowRuntime.of(server);
        waiting = runtime.barrier().begin(runtime.store(level), frozen.endpoint, frozen.frontier, frozen.validator(), server.getTickCount(), result -> {
            EscrowTags.require(result.confirmed(), "Native source phase store/force/readback failed: " + result.reason());
            result.proof().release();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SOURCE_CLOCK_PAUSED cut={}", cut);
            try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
            throw new IllegalStateException("Native source-clock kill did not arrive within 30 seconds");
        });
    }

    private void recover(RocketTransferSavedData journal) {
        EscrowTags.require(journal.entries().size() == 1, "Source-clock cut lost its checked journal");
        var record = journal.entries().get(0);
        var entity = server.overworld().getEntity(record.sourceEntityId());
        if (!(entity instanceof RocketEntity source)) { return; }
        rocket = source;
        observedRollback |= source.flightData().orElseThrow().stateStartedGameTime() > source.level().getGameTime();
        var flight = source.flightData().orElseThrow();
        EscrowTags.require(record.phase() == RocketTransferPhase.PREPARED && data.ledger().armedFlights().size() == 1
                && data.ledger().armedFlights().get(0).equals(record.nativeTransfer().orElseThrow().armed())
                && flight.fuel().equals(record.sourceFlightData().fuel()) && flight.fuel().committedDebits().isEmpty()
                && flight.passengers().equals(record.sourceFlightData().passengers())
                && source.escrowSection().epoch() == 1 && source.escrowSection().markers().isEmpty()
                && !RocketEscrowGate.idle(source) && onlySource(), "Restart changed native source resources or authority");
        if (flight.state() != RocketFlightState.TRANSIT) { return; }
        // Check actual production readiness, not merely the persisted TRANSIT enum on the second restart.
        var result = EscrowRuntime.of(server).sourceFlights().tick(server, journal, record, source, this::onlySource);
        if (result != EscrowGovernedSourceFlight.Result.DESTINATION_DUE) { return; }
        EscrowTags.require(observedRollback && source.getY() == 148, "Source-clock recovery did not exercise a rollback");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SOURCE_CLOCK_RESULT cut={} state={} rollback=true fuel=1000 epoch=1 manifest=1 debits=0",
                cut, result); done = true;
    }

    private boolean loaded() {
        var level = server.overworld();
        return List.of(SOURCE, DESTINATION).stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong())
                && level.isPositionEntityTicking(pos));
    }
    private boolean onlySource() {
        if (!loaded() || rocket == null || !rocket.isAlive() || !rocket.isAddedToWorld()) { return false; }
        var candidates = server.overworld().getEntitiesOfClass(RocketEntity.class, new AABB(63, 90, 63, 96, 160, 80));
        return candidates.size() == 1 && candidates.get(0) == rocket;
    }
}
