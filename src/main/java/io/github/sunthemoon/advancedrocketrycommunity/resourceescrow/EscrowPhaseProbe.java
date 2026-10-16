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

/** Gated F5 journal/effect cuts, including source absence with an unsaved destination. */
final class EscrowPhaseProbe {
    private static final List<BlockPos> POSITIONS = List.of(new BlockPos(64, 100, 64), new BlockPos(80, 100, 64));
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private boolean started;
    private boolean discarded;
    private boolean pending;
    private boolean done;
    private boolean holdingArrival;
    private boolean sourceStored;

    EscrowPhaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server;
        cut = System.getProperty("advancedrocketrycommunity.escrowCase");
        EscrowTags.require(List.of("FP_PASSENGERS_ACK", "FP_SOURCE_ACK", "FP_SOURCE_REMOVED", "FP_COMMITTED", "FP_ABSENT_SOURCE",
                "FD_DESCENT", "FD_LANDED").contains(cut),
                "Unknown F5 phase cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        for (var position : POSITIONS) {
            var chunk = new ChunkPos(position); server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
    }

    void checkpoint(String point) {
        if (recovery || !started) { return; }
        String expected = switch (cut) {
            case "FP_PASSENGERS_ACK" -> "F5_PASSENGERS_TRANSFERRED_ACK";
            case "FP_SOURCE_ACK" -> "F5_SOURCE_REMOVED_ACK";
            case "FP_SOURCE_REMOVED" -> "F5_SOURCE_REMOVAL_AFTER";
            case "FP_COMMITTED" -> "F5_COMMITTED_ACK";
            default -> "";
        };
        if (point.equals(expected)) { pause(); }
    }

    boolean holdsArrival() { return cut.startsWith("FP_") || holdingArrival; }

    void tick() {
        if (done || pending || !loaded()) { return; }
        var level = server.overworld(); var data = ResourceEscrowSavedData.forTests(server); data.bind(server);
        var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
        if (!recovery && !started) {
            var rocket = EscrowRocketAuditProbe.createRocket(level, UUID.randomUUID(), true);
            var parent = rocket.saveWithoutId(new CompoundTag());
            parent.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
            rocket.load(parent); EscrowTags.require(level.addFreshEntity(rocket), "F5 source publication refused");
            var base = rocket.flightData().orElseThrow(); long now = level.getGameTime();
            var fuel = base.fuel().withFluidBank(false, net.minecraft.resources.ResourceLocation.tryParse("minecraft:water"), null, 1_000);
            rocket.updateFlightData(base.withFuel(fuel, now).withPassengers(base.passengers().assign(UUID.randomUUID()).orElseThrow()));
            var from = rocket.snapshot().orElseThrow();
            var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), new RocketPosition(80, 100, 64), now);
            var plan = new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                    from.sourceDimension(), from.sourceDimension(), 500, now).withPropulsionDebit(new RocketPropulsionDebit(500, 0,
                    fuel.propulsion().orElseThrow().revision(), fuel.resourceSignature(), RocketNativeResources.hash("F5 phase fixture")));
            started = EscrowGovernedPreparation.begin(rocket, rocket.flightData().orElseThrow().withPlan(plan).startCountdown(now),
                    to, record -> loaded(), record -> { }, () -> discarded = true);
            EscrowTags.require(started, "F5 preparation refused"); return;
        }
        EscrowTags.require(!discarded, "F5 preparation callback discarded");
        if (journal.entries().size() != 1) { return; }
        var record = journal.entries().get(0);
        if (record.phase() != RocketTransferPhase.COMMITTED || !journal.actionAuthorityAvailable()) { return; }
        if (level.getEntity(record.sourceEntityId()) != null
                || !(level.getEntity(record.destinationEntityId().orElseThrow()) instanceof RocketEntity arrival)) { return; }
        if (cut.startsWith("FD_")) { arrival(record, arrival, data); return; }
        EscrowTags.require(record.nativeTransfer().orElseThrow().destinationEnvelope(record).equals(EscrowNativeEnvelope.rocket(arrival))
                && arrival.flightData().orElseThrow().fuel().totalAmount() == 500 && arrival.escrowSection().epoch() == 0
                && data.ledger().armedFlights().equals(List.of(record.nativeTransfer().orElseThrow().armed()))
                && !RocketEscrowGate.idle(arrival) && arrival.flightData().orElseThrow().passengers().assignments().size() == 1,
                "Native F5 lost its fixed authority, one debit or unresolved offline assignment");
        if (recovery) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PHASE_RESULT cut={} phase=COMMITTED source_absent=true destination_fuel=500 armed=true", cut);
            done = true; return;
        }
        if (!cut.equals("FP_ABSENT_SOURCE")) { return; }
        var slot = EscrowGovernedDestinationDisk.endpoint(record, false); var runtime = EscrowRuntime.of(server);
        pending = runtime.barrier().begin(runtime.store(level), slot,
                new EscrowPrefix.State(data.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()),
                EscrowGovernedDestinationDisk.validator(record, slot, null), server.getTickCount(), result -> {
                    EscrowTags.require(result.confirmed(), "F5 absence checkpoint failed: " + result.reason());
                    result.proof().release(); pause();
                });
    }

    private void arrival(RocketTransferRecord record, RocketEntity arrival, ResourceEscrowSavedData data) {
        EscrowTags.require(EscrowGovernedPhaseAction.destinationMatches(record, EscrowNativeEnvelope.rocket(arrival))
                && arrival.flightData().orElseThrow().fuel().totalAmount() == 500
                && arrival.flightData().orElseThrow().fuel().committedDebits().equals(List.of(record.transferId()))
                && arrival.escrowSection().epoch() == 0 && !RocketEscrowGate.idle(arrival)
                && data.ledger().armedFlights().equals(List.of(record.nativeTransfer().orElseThrow().armed()))
                && arrival.flightData().orElseThrow().passengers().assignments().size() == 1,
                "Native descent changed resources, assignments or ARMED authority");
        boolean landed = arrival.flightData().orElseThrow().state() == RocketFlightState.LANDED;
        if (recovery) {
            if (!landed || arrival.getY() != 100) { return; }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PHASE_RESULT cut={} phase=COMMITTED landed=true source_absent=true armed=true", cut);
            done = true; return;
        }
        if (!holdingArrival && (cut.equals("FD_LANDED") ? !landed : landed || arrival.getY() >= 136 || arrival.getY() <= 112)) { return; }
        holdingArrival = true;
        var runtime = EscrowRuntime.of(server); var slot = EscrowGovernedDestinationDisk.endpoint(record, sourceStored);
        var frozen = new EscrowFlightSource(arrival);
        var validator = sourceStored ? frozen.validator() : EscrowGovernedDestinationDisk.validator(record, slot, null);
        pending = runtime.barrier().begin(runtime.store(server.overworld()), slot,
                new EscrowPrefix.State(data.ledger().id(), slot.beforeEpoch(), slot.beforeSignature(), List.of()), validator,
                server.getTickCount(), result -> {
                    EscrowTags.require(result.confirmed() && frozen.observe(), "Descent cut store failed: " + result.reason());
                    result.proof().release(); pending = false;
                    if (sourceStored) { pause(); } else { sourceStored = true; }
                });
    }

    private boolean loaded() {
        var level = server.overworld();
        return POSITIONS.stream().allMatch(pos -> level.areEntitiesLoaded(new ChunkPos(pos).toLong()) && level.isPositionEntityTicking(pos));
    }
    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_PHASE_PAUSED cut={}", cut);
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Native F5 kill did not arrive within 30 seconds");
    }
}
