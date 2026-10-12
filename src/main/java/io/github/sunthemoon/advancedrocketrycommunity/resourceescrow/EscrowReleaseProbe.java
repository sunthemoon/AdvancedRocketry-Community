package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;

/** Packaged, property-gated native fixture. No gameplay command or registry ID exposes admission. */
final class EscrowReleaseProbe {
    private static final BlockPos POSITION = new BlockPos(64, 100, 64);
    private final MinecraftServer server;
    private final ServerLevel level;
    private final String cut;
    private final boolean recovery;
    private volatile boolean pausing;
    private boolean selected;
    private boolean injected;
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private EscrowTestEndpoints.Fixture fixture;
    private EscrowOperation op;
    private final Map<EscrowEndpoint.Key, EscrowBarrier.Proof> proofs = new HashMap<>();
    private int step;
    private boolean waiting;
    private boolean done;

    EscrowReleaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        cut = System.getProperty("advancedrocketrycommunity.escrowCheckpoint", "none");
        recovery = System.getProperty("advancedrocketrycommunity.escrowScenario").equals("recover");
        EscrowTags.require(cut.equals("none") || cut.equals("G3") || cut.equals("G3L2") || cut.equals("E2") || cut.equals("E2_CAPTURE")
                || cut.matches("E4L[12]_(?:FAIL|UNKNOWN)")
                || cut.matches("K(?:[0-9]|10)"), "Unknown escrow checkpoint");
        ChunkPos chunk = new ChunkPos(POSITION);
        level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, net.minecraft.util.Unit.INSTANCE);
        level.getChunk(chunk.x, chunk.z); // Explicit fixed test-fixture loading, not endpoint input.
    }
    void checkpoint(String name) {
        if (!injected && (cut.equals("E4L1_FAIL") && name.equals("K1")
                || cut.equals("E4L2_FAIL") && name.equals("K8"))) {
            injected = true; throw new IllegalStateException("Release-test definite pre-replacement failure");
        }
        if (cut.endsWith("_UNKNOWN") && name.equals("WRITE_UNKNOWN")) {
            EscrowTags.require(!coordinator.writerIdle() && data.ledger().records().size() == (cut.startsWith("E4L1") ? 0 : 1),
                    "Unknown writer outcome released its candidate or altered acknowledged authority");
            checkpoint(cut); return;
        }
        if ((cut.equals("G3") && name.equals("K1") || cut.equals("G3L2") && name.equals("K8")) && !selected) {
            selected = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_DRAIN_READY cut={}", cut);
            try { TimeUnit.MILLISECONDS.sleep(1_500); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            return;
        }
        if (!name.equals(cut) || selected) { return; }
        selected = true; pausing = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_PAUSED cut={} thread={}", name, Thread.currentThread().getName());
        pause();
    }
    private static void pause() {
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        throw new IllegalStateException("Escrow checkpoint kill did not arrive within 30 seconds");
    }
    void tick() {
        if (pausing) { pause(); }
        if (done || waiting || !level.areEntitiesLoaded(new ChunkPos(POSITION).toLong())) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server);
            if (recovery) { restoreFixture(); } else { fixture = EscrowTestEndpoints.create(level, POSITION, data.ledger().id()); }
        }
        if (recovery && op == null) { finish(); return; }
        switch (step) {
            case 0 -> {
                if (recovery) { step = 2; return; }
                if (!server.saveEverything(false, true, true)) { throw new IllegalStateException("Fixture baseline save refused"); }
                data.setDirty(); coordinator.autosave(); step = 1;
            }
            case 1 -> {
                if (!coordinator.writerIdle() || data.ledger().writerGeneration() == 0) { return; }
                if (op == null) {
                    checkpoint("K0"); op = fixture.operation(coordinator.nextSequence());
                    fixture.prepare(coordinator, op, committed -> {
                        if (!committed && cut.equals("E4L1_FAIL")) {
                            EscrowTags.require(data.ledger().records().isEmpty() && EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 5_000
                                    && EscrowTestEndpoints.amount(fixture.bank().getPersistentData()) == 0, "Failed L1 applied resources");
                            checkpoint(cut); return;
                        }
                        if (!committed) { throw new IllegalStateException("Native fixture L1 failed"); } step = 2; waiting = false;
                    });
                }
                waiting = coordinator.commitPreparations();
            }
            case 2 -> {
                fixture.applySource(op); checkpoint("K3"); fixture.applyBank(op);
                coordinator.freeze(op.source().key(), fixture.sourceState()); coordinator.freeze(op.destination().key(), fixture.bankState()); step = 3;
            }
            case 3 -> begin(cut.equals("K5"));
            case 4 -> begin(!cut.equals("K5"));
            case 5 -> {
                boolean started = coordinator.retire(Set.of(op.id()), proofs, committed -> {
                    if (!committed && cut.equals("E4L2_FAIL")) {
                        EscrowTags.require(data.ledger().records().size() == 1 && coordinator.locked(op.destination().key()), "Failed L2 released resources");
                        checkpoint(cut); return;
                    }
                    if (!committed && cut.equals("E4L2_UNKNOWN")) { return; }
                    if (!committed) { throw new IllegalStateException("Native fixture L2 failed"); }
                    checkpoint("K10"); EscrowTestEndpoints.clearRetired(fixture); waiting = false; step = 6;
                });
                waiting = started;
            }
            case 6 -> finish();
            default -> throw new IllegalStateException("Unknown native fixture stage");
        }
    }
    private void begin(boolean destination) {
        EscrowRuntime runtime = EscrowRuntime.of(server); EscrowEndpoint endpoint = destination ? op.destination() : op.source();
        EscrowPrefix.State frontier = destination ? fixture.bankState() : fixture.sourceState();
        boolean duringCapture = cut.equals("E2_CAPTURE") && !recovery && step == 3;
        var injectedSave = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<net.minecraftforge.event.level.ChunkDataEvent.Save> listener = event -> {
            var chunk = level.getChunkAt(POSITION);
            if (event.getChunk() == chunk && injectedSave.compareAndSet(false, true)) { newerSave(); }
        };
        if (duringCapture) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener); }
        boolean accepted;
        try { accepted = runtime.barrier().begin(runtime.store(level), endpoint, frontier,
                EscrowTestEndpoints.validator(endpoint, frontier), server.getTickCount(), result -> {
                    if ((cut.equals("E2") || cut.equals("E2_CAPTURE")) && !recovery && step == 3) {
                        EscrowTags.require(!result.confirmed() && result.reason() == EscrowReason.SUPERSEDING_SAVE
                                && data.ledger().records().size() == 1 && coordinator.locked(endpoint.key()),
                                "Newer save released an unconfirmed operation");
                        if (duringCapture) {
                            EscrowTags.require(injectedSave.get() && runtime.barrier().supersedingSaves() == 1 && runtime.barrier().ioFailures() == 0,
                                    "Capture supersession was counted as IO failure");
                            // Stabilize the newer VANILLA store for this cut; this does not produce an own-store proof.
                            level.getChunkSource().chunkMap.worker.synchronize(true).join();
                        }
                        checkpoint(cut); return;
                    }
                    if (!result.confirmed()) { throw new IllegalStateException("Native fixture barrier failed: " + result.reason()); }
                    proofs.put(endpoint.key(), result.proof()); waiting = false;
                    if (step == 3) { checkpoint(destination ? "K5" : "K4"); step = 4; }
                    else { checkpoint("K7"); step = 5; }
                });
        } finally { if (duringCapture) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); } }
        if (accepted) {
            waiting = true;
            if (cut.equals("E2") && !recovery && step == 3) {
                // Normal post-capture Save and submission on the same vanilla worker, before batch force/readback.
                newerSave();
            }
        }
    }
    private void newerSave() {
        var chunk = level.getChunkAt(POSITION);
        var newer = net.minecraft.world.level.chunk.storage.ChunkSerializer.write(level, chunk);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.ChunkDataEvent.Save(chunk, level, newer));
        level.getChunkSource().chunkMap.write(chunk.getPos(), newer);
    }
    private void restoreFixture() {
        var source = level.getBlockEntity(POSITION);
        var entities = level.getEntitiesOfClass(ArmorStand.class, new net.minecraft.world.phys.AABB(POSITION).inflate(8), entity -> true);
        EscrowTags.require(source != null && entities.size() == 1, "Missing native restart fixtures");
        UUID logical = entities.get(0).getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity");
        fixture = new EscrowTestEndpoints.Fixture(level, source, entities.get(0), data.ledger().id(), logical);
        if (!data.ledger().records().isEmpty()) {
            EscrowTags.require(data.ledger().records().size() == 1, "Unexpected native fixture ledger"); op = data.ledger().records().get(0);
        } else { EscrowTestEndpoints.clearRetired(fixture); }
    }
    private void finish() {
        int source = EscrowTestEndpoints.amount(fixture.source().getPersistentData());
        int bank = EscrowTestEndpoints.amount(fixture.bank().getPersistentData());
        EscrowTags.require(source + bank == 5_000 && data.ledger().records().isEmpty(), "Native fixture conservation or retirement failed");
        EscrowTags.require((source == 5_000 && bank == 0) || (source == 4_000 && bank == 1_000), "Unexpected native resources");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_RESULT source={} bank={} records={} source_epoch={} bank_epoch={} ledger={}",
                source, bank, data.ledger().records().size(), fixture.sourceState().epoch(), fixture.bankState().epoch(), data.ledger().id());
        done = true;
    }
}
