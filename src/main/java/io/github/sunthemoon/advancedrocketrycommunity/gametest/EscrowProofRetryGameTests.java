package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real slot saves refuse the first capture; only a new own-store proof after backoff can pass. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowProofRetryGameTests {
    private EscrowProofRetryGameTests() { }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 200)
    public static void supersededTerrainProofRetriesTheSameFrontierAfterBackoff(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 200)
    public static void supersededEntityProofRetriesTheSameFrontierAfterBackoff(GameTestHelper helper) {
        new Scenario(helper, true).start();
    }

    private static final class Scenario implements GameTestListener {
        private final GameTestHelper helper;
        private final boolean entity;
        private final BlockPos position;
        private final ChunkPos chunk;
        private final EscrowBarrier barrier = new EscrowBarrier();
        private final GameTestTickPacer pacer;
        private final EscrowTestEndpoints.Fixture fixture;
        private final ChunkSaveWatcher saves;
        private final EscrowGameTestProof<EscrowBarrier.Result> proof;
        private boolean saved, injected, closed;
        private long refusedAt = -1;

        Scenario(GameTestHelper helper, boolean entity) {
            this.helper = helper; this.entity = entity;
            var level = helper.getLevel(); var server = level.getServer();
            position = new BlockPos(entity ? 548_001 : 546_001, 100, 546_001); chunk = new ChunkPos(position);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
            fixture = EscrowTestEndpoints.create(level, position, UUID.randomUUID());
            var operation = fixture.operation(1); fixture.apply(operation);
            var endpoint = entity ? operation.destination() : operation.source();
            var frontier = entity ? fixture.bankState() : fixture.sourceState();
            var validator = EscrowTestEndpoints.validator(endpoint, frontier);
            var store = EscrowRuntime.of(server).store(level);
            pacer = new GameTestTickPacer(server); saves = ChunkSaveWatcher.start(helper, position);
            proof = new EscrowGameTestProof<>(server::getTickCount,
                    (tick, completed) -> barrier.begin(store, endpoint, frontier, validator, tick, result -> {
                        if (!result.confirmed()) {
                            helper.assertTrue(result.reason() == EscrowReason.SUPERSEDING_SAVE, "Injected save produced another refusal: " + result.reason());
                            refusedAt = server.getTickCount();
                        }
                        completed.accept(result);
                    }), EscrowBarrier.Result::confirmed, EscrowBarrier.Result::reason, result -> {
                        helper.assertTrue(result.confirmed(), "Retried proof was not confirmed: " + result.reason());
                        helper.assertTrue(injected && attempts() == 2 && barrier.supersedingSaves() == 1
                                && barrier.ioFailures() == 0 && barrier.refusals() == 0, "Retry did not follow exactly one real superseding save");
                        helper.assertTrue(refusedAt >= 0 && server.getTickCount() - refusedAt >= EscrowReason.backoff(1), "Retry skipped the product backoff");
                        helper.assertTrue(result.proof().frontier().equals(frontier) && result.proof().capture().stored().isDone()
                                && !result.proof().capture().stored().isCompletedExceptionally(), "Retry changed the frozen frontier or omitted its own store");
                        helper.assertTrue(EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                                && EscrowTestEndpoints.amount(fixture.bank().getPersistentData()) == 1_000, "Retry changed resource conservation");
                        result.proof().release(); helper.succeed();
                    });
            try {
                var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(this);
            } catch (ReflectiveOperationException failure) { close(); throw new IllegalStateException(failure); }
        }

        private int attempts() { return proof.attempts(); }
        void start() { helper.onEachTick(this::tick); }
        private void tick() {
            if (closed) { return; }
            pacer.pace(); var level = helper.getLevel();
            barrier.tick(level.getServer().getTickCount());
            if (closed || !level.areEntitiesLoaded(chunk.toLong())) { return; }
            if (!saved) {
                if (!saves.any(position) && !VanillaChunkSave.saveIfNeeded(level, chunk)) { return; }
                saves.stop(); saved = true;
            }
            proof.tick();
            if (proof.attempts() == 1 && !injected) {
                injected = true;
                if (entity) {
                    // The same slot-specific store called by PersistentEntitySectionManager.autoSave, without saving other tests.
                    helper.assertTrue(level.entityManager.storeChunkSections(chunk.toLong(), value -> { }), "Loaded entity save was refused");
                } else {
                    var terrain = level.getChunk(chunk.x, chunk.z);
                    var newer = ChunkSerializer.write(level, terrain);
                    MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(terrain, level, newer));
                    level.getChunkSource().chunkMap.write(chunk, newer);
                }
            }
        }
        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(); }
        @Override public void testFailed(GameTestInfo info) { close(); }
        private void close() {
            if (closed) { return; }
            closed = true; barrier.close(); saves.stop(); fixture.bank().discard();
            helper.getLevel().getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
        }
    }
}
