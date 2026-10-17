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
    // Two captures only: up to 200 ticks of fixture preparation, two 20-tick force windows plus one
    // completion tick each, and the first product backoff (100). No third attempt / 400-tick backoff.
    private static final int PREPARATION_TICKS = 200;
    private static final int TIMEOUT_TICKS = PREPARATION_TICKS + 2 * (20 + 1) + 100;
    private EscrowProofRetryGameTests() { }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = TIMEOUT_TICKS)
    public static void supersededTerrainProofRetriesTheSameFrontierAfterBackoff(GameTestHelper helper) {
        new Scenario(helper, false).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = TIMEOUT_TICKS)
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
        private final EscrowGameTestProof<EscrowBarrier.Result> proof;
        private boolean injected, closed;
        private final long startedAt;
        private long refusedAt = -1;
        private int preparationTicks;

        Scenario(GameTestHelper helper, boolean entity) {
            this.helper = helper; this.entity = entity;
            var level = helper.getLevel(); var server = level.getServer();
            startedAt = server.getTickCount();
            position = new BlockPos(entity ? 548_001 : 546_001, 100, 546_001); chunk = new ChunkPos(position);
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z);
            fixture = EscrowTestEndpoints.create(level, position, UUID.randomUUID());
            var operation = fixture.operation(1); fixture.apply(operation);
            // Request this nonempty entity slot's disk load now, rather than waiting for the surrounding
            // terrain's entity-ticking promotion to request it much later. This is vanilla's normal save path.
            if (entity) { level.entityManager.storeChunkSections(chunk.toLong(), value -> { }); }
            var endpoint = entity ? operation.destination() : operation.source();
            var frontier = entity ? fixture.bankState() : fixture.sourceState();
            var validator = EscrowTestEndpoints.validator(endpoint, frontier);
            var store = EscrowRuntime.of(server).store(level);
            pacer = new GameTestTickPacer(server);
            proof = new EscrowGameTestProof<>(server::getTickCount,
                    (tick, completed) -> {
                        if (!entity && !EscrowGameTestProof.freshTerrainSave(level, chunk)) {
                            preparationTick(); return false;
                        }
                        checkpoint("capture");
                        return barrier.begin(store, endpoint, frontier, validator, tick, result -> {
                        if (!result.confirmed()) {
                            helper.assertTrue(result.reason() == EscrowReason.SUPERSEDING_SAVE, "Injected save produced another refusal: " + result.reason());
                            refusedAt = server.getTickCount();
                            checkpoint("superseded");
                        }
                        completed.accept(result);
                        });
                    }, EscrowBarrier.Result::confirmed, EscrowBarrier.Result::reason, result -> {
                        helper.assertTrue(result.confirmed(), "Retried proof was not confirmed: " + result.reason());
                        helper.assertTrue(injected && attempts() == 2 && barrier.supersedingSaves() == 1
                                && barrier.ioFailures() == 0 && barrier.refusals() == 0, "Retry did not follow exactly one real superseding save");
                        helper.assertTrue(refusedAt >= 0 && server.getTickCount() - refusedAt >= EscrowReason.backoff(1), "Retry skipped the product backoff");
                        helper.assertTrue(result.proof().frontier().equals(frontier) && result.proof().capture().stored().isDone()
                                && !result.proof().capture().stored().isCompletedExceptionally(), "Retry changed the frozen frontier or omitted its own store");
                        helper.assertTrue(EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                                && EscrowTestEndpoints.amount(fixture.bank().getPersistentData()) == 1_000, "Retry changed resource conservation");
                        checkpoint("confirmed"); result.proof().release(); helper.succeed();
                    }, 2);
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
            if (closed) { return; }
            if (entity && !level.areEntitiesLoaded(chunk.toLong())) { preparationTick(); return; }
            proof.tick();
            if (proof.attempts() == 1 && !injected) {
                injected = true; checkpoint("inject_save");
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
        private void preparationTick() {
            helper.assertTrue(++preparationTicks <= PREPARATION_TICKS, "Retry fixture preparation exceeded its explicit budget");
        }
        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(); }
        @Override public void testFailed(GameTestInfo info) { close(); }
        private void checkpoint(String stage) {
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_GAMETEST_PROOF_RETRY entities={} stage={} fixture_ticks={} attempts={}",
                    entity, stage, helper.getLevel().getServer().getTickCount() - startedAt, attempts());
        }
        private void close() {
            if (closed) { return; }
            closed = true; barrier.close(); fixture.bank().discard();
            helper.getLevel().getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
        }
    }
}
