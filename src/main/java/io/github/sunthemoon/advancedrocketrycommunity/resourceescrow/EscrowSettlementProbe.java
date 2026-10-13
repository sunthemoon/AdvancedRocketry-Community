package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/** O1-only real-rocket cuts. No proof, tombstone, journal removal or release is claimed. */
final class EscrowSettlementProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String cut;
    private final boolean recovery;
    private final boolean chain;
    private final boolean abandon;
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private RocketEntity rocket;
    private EscrowTestEndpoints.Fixture source;
    private EscrowSettlementIntent preview;
    private boolean waiting;
    private boolean done;
    private volatile boolean writingIntent;
    private int step;

    EscrowSettlementProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        cut = System.getProperty("advancedrocketrycommunity.escrowCase", "");
        EscrowTags.require(List.of("SO_ACCEPT_BEFORE", "SO_ACCEPT_AFTER", "SO_ABANDON_BEFORE", "SO_ABANDON_AFTER",
                "SO_CHAIN_BEFORE", "SO_CHAIN_AFTER").contains(cut), "Unknown settlement intent cut");
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        chain = cut.startsWith("SO_CHAIN_"); abandon = cut.startsWith("SO_ABANDON_");
    }
    void checkpoint(String name) {
        if (!recovery && writingIntent && cut.endsWith("_BEFORE") && name.equals("K1")) { pause(); }
    }
    private void pause() {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SETTLEMENT_PAUSED cut={} boundary={} scope=O1_ONLY", cut,
                cut.endsWith("_BEFORE") ? "STAGED_BEFORE_REPLACE" : "OWNER_ACKNOWLEDGED");
        try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException failure) { throw new IllegalStateException(failure); }
        throw new IllegalStateException("Settlement intent hard stop did not arrive within 30 seconds");
    }
    void tick() {
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong())
                || !level.isPositionEntityTicking(POSITION)) { return; }
        if (data == null) { data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server); }
        if (recovery) { recovered(); return; }
        switch (step) {
            case 0 -> {
                var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
                source = EscrowTestEndpoints.create(level, POSITION, data.ledger().id());
                rocket = EscrowRocketAuditProbe.createRocket(level, source.logical(), true);
                EscrowTags.require(level.addFreshEntity(rocket), "Rocket publication failed"); source.bank().discard();
                var operations = new ArrayList<EscrowOperation>(); operations.add(operation(source, coordinator.nextSequence(), 0));
                if (chain) { operations.add(operation(EscrowTestEndpoints.chainSource(source, POSITION.offset(1, 0, 0)), coordinator.nextSequence() + 1, 1)); }
                for (var op : operations) { coordinator.prepareTest(op, committed -> EscrowTags.require(committed, "Settlement fixture L1 failed")); }
                step = 1;
            }
            case 1 -> { if (coordinator.commitPreparations()) { step = 2; } }
            case 2 -> {
                if (!coordinator.writerIdle() || data.ledger().records().size() != (chain ? 2 : 1)) { return; }
                coordinator.transition(data.ledger().records().get(0).id(), EscrowReason.OTHER_STATE); coordinator.persistTransitions(); step = 3;
            }
            case 3 -> {
                if (!coordinator.writerIdle() || data.ledger().records().get(0).reason() != EscrowReason.OTHER_STATE) { return; }
                if (abandon) { level.setBlockAndUpdate(POSITION, Blocks.AIR.defaultBlockState()); }
                EscrowTags.require(server.saveEverything(false, true, true), "Settlement fixture save refused");
                level.entityManager.permanentStorage.flush(true); level.getChunkSource().chunkMap.worker.synchronize(true).join();
                var selected = data.ledger().records().get(0).id();
                preview = EscrowRuntime.of(server).settlement().preview(server, selected,
                        abandon ? EscrowSettlementIntent.Choice.ABANDON : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "Server");
                step = 4;
            }
            case 4 -> {
                if (abandon && server.getTickCount() - preview.previewTick() < 200) { return; }
                writingIntent = true;
                waiting = EscrowRuntime.of(server).settlement().confirm(server, data.ledger().records().get(0).id(), preview.choice(), ACTOR, preview.digest(), committed -> {
                    EscrowTags.require(committed, "Settlement O1 was not acknowledged");
                    EscrowTags.require(!RocketEscrowGate.idle(rocket) && data.ledger().tombstones().isEmpty(), "O1 incorrectly released resources");
                    if (cut.endsWith("_AFTER")) { pause(); }
                });
            }
            default -> throw new IllegalStateException("Unknown settlement fixture stage");
        }
    }
    private EscrowOperation operation(EscrowTestEndpoints.Fixture fixture, long sequence, long epoch) {
        var sample = fixture.operation(sequence); var fuel = rocket.flightData().orElseThrow().fuel();
        var before = epoch == 0 ? fuel : fuel.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 1_000);
        var after = fuel.withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, (epoch + 1) * 1_000);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), source.logical(),
                ModIdentity.id("rocket").toString(), 4, 0, 4, EscrowEndpoint.Unit.PRIMARY, epoch, before.resourceSignature(), after.resourceSignature());
        var guard = RocketTransferSavedData.get(server).acknowledgedGuard(source.logical()).orElseThrow();
        return new EscrowOperation(sample.id(), sequence, sample.owner(), sample.createdTick(), sample.phase(), sample.reason(), sample.payload(), sample.payloadHash(),
                sample.source(), destination, new EscrowGuard(source.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
    }
    private void recovered() {
        EscrowTags.require(data.ledger().records().size() == (chain ? 2 : 1), "Settlement lost connected records");
        var first = data.ledger().records().get(0);
        if (!(level.getEntity(first.destination().identity()) instanceof RocketEntity found)) { return; }
        rocket = found;
        EscrowTags.require(rocket.operational() && !RocketEscrowGate.idle(rocket)
                && rocket.flightData().orElseThrow().fuel().totalAmount() == 0
                && data.ledger().tombstones().isEmpty() && data.ledger().floors().isEmpty(), "O1 restart changed resources or released authority");
        boolean frozen = cut.endsWith("_AFTER");
        for (var op : data.ledger().records()) {
            EscrowTags.require(!op.settlement().isEmpty() == frozen, "Wrong durable intent boundary");
            if (frozen) { EscrowSettlementIntent.decode(op.settlement()).requireConfirmed(); }
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_SETTLEMENT_RESULT cut={} records={} intent={} fuel=0 locked=true scope=O1_ONLY", cut,
                data.ledger().records().size(), frozen ? "frozen" : "absent"); done = true;
    }
}
