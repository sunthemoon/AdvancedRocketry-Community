package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Competing real-rocket fixture fills: ordinary C0-C8 and a separate operator O1-O4. */
final class EscrowFlightBenchmarkWork {
    private final MinecraftServer server;
    private final ResourceEscrowSavedData data;
    private final EscrowCoordinator coordinator;
    private final EscrowFlightBenchmarkMetrics metrics;
    private RocketEntity ordinaryRocket, settlementRocket;
    private UUID ordinaryId, settlementId;
    private long ordinaryStart;
    private boolean ordinaryDone, ordinaryPrepared, settlementPrepared, confirming, settlementDone;
    private EscrowSettlementIntent preview;
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity ordinaryLoader;
    private FluidFuelLoaderEscrowFixtures.NativeFill ordinaryNative, settlementNative;

    EscrowFlightBenchmarkWork(MinecraftServer server, ResourceEscrowSavedData data, EscrowFlightBenchmarkMetrics metrics) {
        this.server = server; this.data = data; this.metrics = metrics; coordinator = data.bind(server);
    }
    void create() {
        createAccounted();
    }
    boolean ready() { return settlementPrepared && coordinator.writerIdle(); }
    void start() {
        ordinaryStart = System.nanoTime();
        ordinaryNative = admitNative(ordinaryLoader, ordinaryRocket, committed -> {
            EscrowTags.require(committed, "Ordinary benchmark fill not acknowledged"); ordinaryPrepared = true;
        });
        ordinaryId = ordinaryNative.operation().id();
    }
    void tick() {
        coordinator.commitPreparations();
        if (ordinaryPrepared && !ordinaryDone && data.ledger().records().stream().noneMatch(op -> op.id().equals(ordinaryId))
                && ordinaryRocket.escrowSection().markers().isEmpty()
                && ordinaryNative.state(data.ledger().id()).markers().isEmpty()) {
            EscrowTags.require(ordinaryRocket.flightData().orElseThrow().fuel().totalAmount() == 1000
                    && ordinaryNative.amount() == 4000,
                    "Ordinary retirement conservation failed");
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
            settlementDone = settlementPruned(
                    data.ledger().records().stream().anyMatch(op -> op.id().equals(settlementId)),
                    settlementRocket.flightData().orElseThrow().fuel().totalAmount(),
                    settlementNative.amount(),
                    settlementRocket.escrowSection().markers().size(),
                    settlementNative.state(data.ledger().id()).markers().size());
        }
    }
    /** Every mode uses the accounted native-loader fixture required by the schema-3 ledger. */
    private void createAccounted() {
        var level = server.overworld(); var owner = EscrowFlightBenchmarkLoad.id("operator");
        ordinaryRocket = FluidFuelLoaderEscrowFixtures.rocket(level, new BlockPos(64, 100, 164), owner,
                FluidFuelLoaderEscrowFixtures.mono(2000));
        settlementRocket = FluidFuelLoaderEscrowFixtures.rocket(level, new BlockPos(96, 100, 164), owner,
                FluidFuelLoaderEscrowFixtures.mono(2000));
        EscrowTags.require(level.addFreshEntity(ordinaryRocket) && level.addFreshEntity(settlementRocket),
                "Accounted benchmark rocket publication refused");
        ordinaryLoader = nativeLoader(new BlockPos(64, 100, 160), ordinaryRocket);
        var settlementLoader = nativeLoader(new BlockPos(96, 100, 160), settlementRocket);
        settlementNative = admitNative(settlementLoader, settlementRocket, committed -> {
            EscrowTags.require(committed, "Settlement baseline not acknowledged");
            var op = settlementNative.operation();
            settlementNative.applySource(coordinator);
            settlementRocket.applyEscrowFill(EscrowReconciliationAction.issue(data.ledger(), op,
                    EscrowNativeEnvelope.rocket(settlementRocket), true, () -> true));
            coordinator.transition(op.id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions();
            settlementPrepared = true;
        });
        settlementId = settlementNative.operation().id();
    }
    private io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity nativeLoader(
            BlockPos position, RocketEntity rocket) {
        return FluidFuelLoaderEscrowFixtures.loader(server.overworld(), position, rocket.ownerId().orElseThrow(),
                FluidFuelLoaderEscrowFixtures.tank(FluidFuelLoaderEscrowFixtures.ROCKET_FUEL, 5000),
                FluidFuelLoaderEscrowFixtures.binding(rocket));
    }
    private FluidFuelLoaderEscrowFixtures.NativeFill admitNative(
            io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity loader,
            RocketEntity rocket, java.util.function.Consumer<Boolean> completion) {
        var result = EscrowLoaderAdmission.admitDetachedForTests(loader, rocket, coordinator, completion);
        EscrowTags.require(result == EscrowCoordinator.Fill.QUEUED, "Accounted benchmark admission refused: " + result);
        var op = coordinator.pendingAdmissions().stream()
                .filter(member -> member.source().identity().equals(FluidFuelLoaderEscrowFixtures.device(loader))).findFirst().orElseThrow();
        return new FluidFuelLoaderEscrowFixtures.NativeFill(loader, rocket, op);
    }
    static boolean settlementPruned(boolean recordPresent, long fuel, long sourceAmount, int rocketMarkers, int sourceMarkers) {
        EscrowTags.require(!recordPresent && fuel == 1000 && sourceAmount == 4000,
                "Settlement released without exact chosen resources/retirement");
        // O4 acknowledges release and enqueues bounded pruning; it does not complete both endpoints inline.
        // Observe the product cursor without pruning here or extending the probe's fixed deadline.
        return rocketMarkers == 0 && sourceMarkers == 0;
    }
    boolean done() { return ordinaryDone && settlementDone; }
}
