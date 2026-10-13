package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;

/** E5 uses a real capture veto, an absent registration plus unloaded chunk, and a manual conflict. */
final class EscrowRecoveryReleaseProbe {
    private static final List<BlockPos> POSITIONS = List.of(new BlockPos(64, 100, 64), new BlockPos(96, 100, 64),
            new BlockPos(2048, 100, 2048), new BlockPos(128, 100, 64));
    private final MinecraftServer server;
    private final ServerLevel level;
    private final boolean recovery;
    private final List<EscrowTestEndpoints.Fixture> fixtures = new ArrayList<>();
    private final List<EscrowOperation> operations = new ArrayList<>();
    private ResourceEscrowSavedData data;
    private EscrowCoordinator coordinator;
    private int step, acknowledged;
    private boolean done;

    EscrowRecoveryReleaseProbe(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server); this.server = server; level = server.overworld();
        recovery = "recover".equals(System.getProperty("advancedrocketrycommunity.escrowScenario"));
        for (var pos : POSITIONS) {
            var chunk = new ChunkPos(pos); level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z); // Fixed harness fixture coordinates, never admission input.
        }
    }
    void tick() {
        if (done || data == null && POSITIONS.stream().anyMatch(pos -> !level.areEntitiesLoaded(new ChunkPos(pos).toLong()))) { return; }
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); coordinator = data.bind(server);
            for (var pos : POSITIONS) {
                if (!recovery) { fixtures.add(EscrowTestEndpoints.create(level, pos, data.ledger().id())); }
                else {
                    var banks = level.getEntitiesOfClass(ArmorStand.class, new AABB(pos).inflate(8), entity -> true);
                    EscrowTags.require(banks.size() == 1 && level.getBlockEntity(pos) != null, "Missing E5 endpoint");
                    var bank = banks.get(0); UUID logical = bank.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity");
                    fixtures.add(new EscrowTestEndpoints.Fixture(level, level.getBlockEntity(pos), bank, data.ledger().id(), logical));
                }
            }
            if (recovery) {
                operations.addAll(data.ledger().records());
                EscrowRuntime.of(server).reconcileTests(coordinator, fixtures, endpoint -> false); step = 4;
            }
        }
        switch (step) {
            case 0 -> { server.saveEverything(false, true, true); data.setDirty(); coordinator.autosave(); step = 1; }
            case 1 -> {
                if (!coordinator.writerIdle() || data.ledger().writerGeneration() == 0) { return; }
                for (var fixture : fixtures) {
                    var op = fixture.operation(coordinator.nextSequence()); operations.add(op);
                    fixture.prepare(coordinator, op, result -> { EscrowTags.require(result, "E5 L1 failed"); acknowledged++; });
                }
                step = 2;
            }
            case 2 -> { if (acknowledged == 4) { step = 3; } else { coordinator.commitPreparations(); } }
            case 3 -> {
                fixtures.get(1).source().getPersistentData().getCompound("arce_escrow_fixture").putInt("amount", 4999);
                fixtures.get(1).source().setChanged();
                EscrowRuntime.of(server).reconcileTests(coordinator, List.of(fixtures.get(0), fixtures.get(1), fixtures.get(3)),
                        endpoint -> endpoint.key().equals(operations.get(0).source().key()));
                var far = new ChunkPos(POSITIONS.get(2)); level.getChunkSource().removeRegionTicket(TicketType.START, far, 2, Unit.INSTANCE);
                step = 4;
            }
            case 4 -> {
                long stalled = data.ledger().records().stream().filter(op -> op.phase() == EscrowOperation.Phase.STALLED).count();
                long manual = data.ledger().records().stream().filter(op -> op.phase() == EscrowOperation.Phase.REPAIR).count();
                if (recovery) {
                    if (data.ledger().records().size() != 1 || manual != 1 || !coordinator.writerIdle()) { return; }
                    for (int i : new int[] {0, 2, 3}) {
                        EscrowTags.require(EscrowTestEndpoints.amount(fixtures.get(i).source().getPersistentData()) == 4000
                                && EscrowTestEndpoints.amount(fixtures.get(i).bank().getPersistentData()) == 1000, "E5 recovery did not conserve resources");
                    }
                    EscrowTags.require(coordinator.locked(data.ledger().records().get(0).source().key()), "Manual E5 record unlocked automatically");
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_E5_RESULT records=1 manual=1 stalled=0 unloaded=0 healthy=3"); done = true;
                } else {
                    var far = new ChunkPos(POSITIONS.get(2));
                    if (stalled != 1 || manual != 1 || data.ledger().records().size() != 3 || !coordinator.writerIdle()
                            || level.getChunkSource().getChunkNow(far.x, far.z) != null) { return; }
                    EscrowTags.require(data.ledger().records().stream().anyMatch(op -> op.id().equals(operations.get(0).id())
                            && op.reason() == EscrowReason.BARRIER_VETO), "E5 stall reason was not persisted");
                    server.saveEverything(false, true, true);
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_E5_BOUNDARY records=3 manual=1 stalled=1 unloaded=1 healthy=1");
                    AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_ESCROW_PAUSED cut=E5 thread={}", Thread.currentThread().getName());
                    try { TimeUnit.SECONDS.sleep(30); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                    throw new IllegalStateException("E5 checkpoint kill did not arrive within 30 seconds");
                }
            }
            default -> throw new IllegalStateException("Unknown E5 stage");
        }
    }
}
