package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;

/** R203's explicitly historical second stage; it never converts a governed record. */
final class EscrowLoaderS6OrdinaryProbe {
    static final String F9 = "LF_S6_F9_RESPAWN";
    private final MinecraftServer server;
    private final boolean recovery;
    private final EscrowLoaderS6FlightProbe setup;
    private RocketEntity source;
    private RocketTransferRecord record;
    private String originalSource, checkedDigest;
    private boolean seeded, acknowledged, done;
    private long settledAt, sequence, began = -1;
    private int rejected;

    static boolean selected(boolean hooks, String cut) { return hooks && F9.equals(cut); }
    static RocketTransferRecord historical(RocketStructureSnapshot from, RocketFlightData base,
            RocketEscrowSection section, UUID owner, UUID source, UUID transfer, UUID destination, long start) {
        EscrowTags.require(base.state() == RocketFlightState.LANDED && base.launchReady()
                && base.fuel().amount() == 500 && base.fuel().committedDebits().size() == 1
                && base.fuel().propulsion().orElseThrow().revision() == 2 && start >= base.stateStartedGameTime()
                && section.ledger() != null && section.epoch() == 0 && section.markers().isEmpty(),
                "Historical setup requires the unchanged, settled loader-funded arrival");
        var target = new RocketPosition(96, 100, 64);
        var to = from.relocated(UUID.randomUUID(), from.sourceDimension(), target, start);
        var fuel = base.fuel();
        var plan = new RocketFlightPlan(3, transfer, base.currentBody(), RocketFlightPlanner.EARTH.bodyId(),
                base.currentDimension(), base.currentDimension(), 250, start)
                .withPropulsionDebit(new RocketPropulsionDebit(250, 0, fuel.propulsion().orElseThrow().revision(),
                        fuel.resourceSignature(), RocketNativeResources.hash("W211 R203 ordinary historical flight")));
        var transit = EscrowLoaderS6FlightProbe.launchCountdown(base, plan, start).completeCountdown(start + 60)
                .beginTransit(transfer, start + 140);
        var arrival = transit.arriveAtDestination(fuel.debit(plan).state(), plan.destinationBody(),
                plan.destinationDimension(), target, start + 160);
        return RocketTransferRecord.create(transfer, base.logicalRocketId(), owner, source, from, to, transit, arrival, 250, start)
                .destinationSpawned(destination).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
    }

    EscrowLoaderS6OrdinaryProbe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowCase")), "Ordinary setup requires its exact test gate");
        this.server = server; this.recovery = recovery;
        setup = recovery ? null : new EscrowLoaderS6FlightProbe(server, F9, this::settled);
        for (int x : new int[] {64, 80, 96}) {
            var chunk = new ChunkPos(new BlockPos(x, 100, 64));
            server.overworld().getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            server.overworld().getChunk(chunk.x, chunk.z);
        }
        if (!recovery) {
            Consumer<EntityJoinLevelEvent> veto = event -> {
                if (record == null || !(event.getEntity() instanceof RocketEntity rocket)
                        || rocket.assemblyTransactionId().filter(record.logicalRocketId()::equals).isEmpty()) { return; }
                EscrowTags.require(acknowledged && rocket.getUUID().equals(record.destinationEntityId().orElseThrow()),
                        "Ordinary respawn did not reuse its checked destination UUID");
                rejected++; event.setCanceled(true);
            };
            // This process is forcibly stopped at three rejections; no global or next-session state survives.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, EntityJoinLevelEvent.class, veto);
        }
    }
    private void settled(RocketEntity arrival) {
        source = arrival; settledAt = server.overworld().getGameTime();
        originalSource = EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(source));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ORDINARY_SETTLED cut={} nbt={}", F9,
                EscrowLoaderS6TerminalProbe.encode(EscrowCodec.encode(ResourceEscrowSavedData.existing(server).ledger())));
    }
    void checkpoint(String point) { if (setup != null && source == null) { setup.checkpoint(point); } }
    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Ordinary stage exceeded the existing fixture bound");
        if (!recovery && source == null) { setup.tick(); return; }
        var journal = RocketTransferSavedData.get(server);
        if (!journal.retryChecked(server)) { return; }
        if (recovery) { recover(journal); return; }
        if (server.overworld().getGameTime() < settledAt + 160) { return; }
        if (!seeded) {
            record = historical(source.snapshot().orElseThrow(), source.flightData().orElseThrow(), source.escrowSection(),
                    source.ownerId().orElseThrow(), source.getUUID(), UUID.randomUUID(), UUID.randomUUID(), settledAt);
            journal.put(record); seeded = true;
            EscrowTags.require(journal.afterAcknowledged(server, record.logicalRocketId(), () -> {
                sequence = journal.mutationSequence(); acknowledged = true;
                checkedDigest = checked(journal);
                AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ORDINARY_HISTORICAL cut={} digest={} schema=2 source_bank=500 destination_bank=250", F9, checkedDigest);
            }), "Ordinary historical T could not reserve its checked acknowledgement");
            return;
        }
        if (!acknowledged) { return; }
        EscrowTags.require(journal.mutationSequence() == sequence && journal.acknowledgedCurrent()
                && checked(journal).equals(checkedDigest) && !source.isRemoved()
                && EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(source)).equals(originalSource)
                && server.overworld().getEntity(record.destinationEntityId().orElseThrow()) == null,
                "Rejected ordinary respawn changed authority or source");
        // The installed recovery service supplies one automatic attempt per tick; no fixture retry loop.
        if (rejected < 3) { return; }
        EscrowTags.require(rejected == 3, "Ordinary observation missed its third actual rejection");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ORDINARY_PAUSED cut={} rejected=3 sequence={} source_unchanged=true", F9, sequence);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Ordinary forced stop did not arrive within 30 seconds");
    }
    private String checked(RocketTransferSavedData journal) {
        var path = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var payload = CheckedSavedDataFile.readPayload(path, ManagedSavedDataType.ROCKET_TRANSFERS).orElseThrow();
        var rows = payload.getList("transfers", 10);
        EscrowTags.require(rows.size() == 1 && journal.find(record.transferId()).orElse(null) == record
                && rows.getCompound(0).getInt("schema_version") == 2 && !rows.getCompound(0).contains("native_transfer"),
                "Ordinary historical T changed its checked schema or identity");
        return EscrowNativeEnvelope.digest(rows.getCompound(0));
    }
    private void recover(RocketTransferSavedData journal) {
        var level = server.overworld();
        if (!journal.actionAuthorityAvailable() || server.getTickCount() - began < 100) { return; }
        EscrowTags.require(journal.entries().size() == 1, "Ordinary restart lost its retained T");
        record = journal.entries().get(0);
        if (!(level.getEntity(record.destinationEntityId().orElseThrow()) instanceof RocketEntity survivor)
                || survivor.flightData().orElseThrow().state() != RocketFlightState.LANDED) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        var loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(new BlockPos(66, 100, 64));
        EscrowTags.require(data != null && data.ledger().writerGeneration() == 6 && data.ledger().armedFlights().isEmpty()
                && data.ledger().records().isEmpty() && record.nativeTransfer().isEmpty() && record.phase() == RocketTransferPhase.COMMITTED
                && level.getEntity(record.sourceEntityId()) == null && amount(loader) == 4_000
                && bank(survivor, false) == 250 && survivor.flightData().orElseThrow().fuel().committedDebits().size() == 2
                && survivor.escrowSection().equals(RocketEscrowSection.neutral()) && RocketEscrowGate.idle(survivor),
                "Ordinary restart changed its exact second-flight survivor");
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_ORDINARY_RESULT cut={} landed_time={} digest={} bank=250 debits=2 journal=1", F9,
                survivor.flightData().orElseThrow().stateStartedGameTime(), EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(survivor)));
    }
}
