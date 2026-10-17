package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** Competing real-rocket fixture fills: ordinary C0-C8 and a separate operator O1-O4. */
final class EscrowFlightBenchmarkWork {
    private final MinecraftServer server;
    private final ResourceEscrowSavedData data;
    private final EscrowCoordinator coordinator;
    private final EscrowFlightBenchmarkMetrics metrics;
    private EscrowTestEndpoints.Fixture ordinary, settlement;
    private RocketEntity ordinaryRocket, settlementRocket;
    private UUID ordinaryId, settlementId;
    private long ordinaryStart;
    private boolean ordinaryDone, ordinaryPrepared, settlementPrepared, confirming, settlementDone;
    private EscrowSettlementIntent preview;

    EscrowFlightBenchmarkWork(MinecraftServer server, ResourceEscrowSavedData data, EscrowFlightBenchmarkMetrics metrics) {
        this.server = server; this.data = data; this.metrics = metrics; coordinator = data.bind(server);
    }
    void create() {
        ordinary = fixture(new BlockPos(64, 100, 160)); settlement = fixture(new BlockPos(96, 100, 160));
        ordinaryRocket = rocket(ordinary); settlementRocket = rocket(settlement);
        EscrowTags.require(server.overworld().addFreshEntity(ordinaryRocket), "Ordinary benchmark rocket publication refused");
        // A complete credited BEFORE-choice fixture, held for an operator choice. It never uses the model-bank scheduler.
        var admission = operation(settlement, settlementRocket, coordinator.nextSequence());
        var op = admission.operation(); settlementId = op.id();
        var tag = settlementRocket.saveWithoutId(new CompoundTag()); var wrapper = tag.getCompound("RocketEntityData");
        var flight = settlementRocket.flightData().orElseThrow();
        wrapper.put("flight_data", RocketFlightNbtCodec.encode(flight.withFuel(flight.fuel().withFluidBank(false,
                ResourceLocation.tryParse("minecraft:water"), null, 1000), server.overworld().getGameTime())));
        wrapper.put("resource_escrow", new RocketEscrowSection(data.ledger().id(), 1,
                List.of(new EscrowPrefix.Marker(op.id(), op.sequence(), "DESTINATION", 1))).encode());
        settlementRocket.load(tag);
        coordinator.prepareTest(op, admission.before(), committed -> {
            EscrowTags.require(committed, "Settlement baseline not acknowledged");
            settlement.applySource(op);
            coordinator.transition(settlementId, EscrowReason.OTHER_STATE); coordinator.persistTransitions();
            EscrowTags.require(server.overworld().addFreshEntity(settlementRocket), "Settlement rocket publication refused");
            settlementPrepared = true;
        });
    }
    private EscrowTestEndpoints.Fixture fixture(BlockPos pos) {
        var fixture = EscrowTestEndpoints.create(server.overworld(), pos, data.ledger().id()); fixture.bank().discard(); return fixture;
    }
    private RocketEntity rocket(EscrowTestEndpoints.Fixture fixture) {
        var pos = fixture.source().getBlockPos();
        return EscrowRocketAuditProbe.createRocket(server.overworld(), fixture.logical(), true,
                new RocketPosition(pos.getX(), pos.getY(), pos.getZ() + 4));
    }
    private EscrowNativeBinding.Admission operation(EscrowTestEndpoints.Fixture fixture, RocketEntity rocket, long sequence) {
        var sample = fixture.operation(sequence); var fuel = rocket.flightData().orElseThrow().fuel();
        var after = fuel.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1000);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, rocket.level().dimension().location().toString(),
                rocket.getUUID(), fixture.logical(), EscrowAdapterIds.ROCKET, rocket.chunkPosition().x, 0, rocket.chunkPosition().z,
                EscrowEndpoint.Unit.PRIMARY, 0, fuel.resourceSignature(), after.resourceSignature());
        var guard = RocketTransferSavedData.get(server).acknowledgedGuard(fixture.logical()).orElseThrow();
        var op = new EscrowOperation(sample.id(), sequence, rocket.ownerId().orElseThrow(), sample.createdTick(), sample.phase(), sample.reason(),
                sample.payload(), sample.payloadHash(), sample.source(), destination,
                new EscrowGuard(fixture.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
        return EscrowNativeBinding.captureTest(op, rocket, fixture.source(), fuel, after, data.ledger());
    }
    boolean ready() { return settlementPrepared && coordinator.writerIdle(); }
    void start() {
        ordinaryStart = System.nanoTime();
        var admission = operation(ordinary, ordinaryRocket, coordinator.nextSequence()); ordinaryId = admission.operation().id();
        coordinator.prepareNativeTest(admission.operation(), ordinaryRocket, ordinary.source(), committed -> {
            EscrowTags.require(committed, "Ordinary benchmark fill not acknowledged"); ordinaryPrepared = true;
        });
    }
    void tick() {
        coordinator.commitPreparations();
        if (ordinaryPrepared && !ordinaryDone && data.ledger().records().stream().noneMatch(op -> op.id().equals(ordinaryId))
                && ordinaryRocket.escrowSection().markers().isEmpty() && ordinary.sourceState().markers().isEmpty()) {
            EscrowTags.require(ordinaryRocket.flightData().orElseThrow().fuel().totalAmount() == 1000
                    && EscrowTestEndpoints.amount(ordinary.source().getPersistentData()) == 4000, "Ordinary retirement conservation failed");
            metrics.sample("ordinary_start_to_c8", System.nanoTime() - ordinaryStart); ordinaryDone = true;
        }
        if (ordinaryStart == 0 || !settlementPrepared || settlementDone) { return; }
        if (!confirming && coordinator.writerIdle()) {
            var actor = EscrowFlightBenchmarkLoad.id("operator");
            if (preview == null) {
                preview = EscrowRuntime.of(server).settlement().preview(server, settlementId,
                        EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "Benchmark");
            }
            confirming = EscrowRuntime.of(server).settlement().confirm(server, settlementId,
                    EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, preview.digest(), committed ->
                            EscrowTags.require(committed, "Benchmark O1 not acknowledged"));
        }
        if (confirming && data.ledger().tombstones().stream().anyMatch(tag -> tag.getUUID("op_id").equals(settlementId)
                && tag.getBoolean("released"))) {
            EscrowTags.require(data.ledger().records().stream().noneMatch(op -> op.id().equals(settlementId))
                    && settlementRocket.flightData().orElseThrow().fuel().totalAmount() == 1000
                    && settlementRocket.escrowSection().markers().isEmpty()
                    && settlement.sourceState().markers().isEmpty(), "Settlement completed without exact chosen resources/pruning");
            settlementDone = true;
        }
    }
    boolean done() { return ordinaryDone && settlementDone; }
}
