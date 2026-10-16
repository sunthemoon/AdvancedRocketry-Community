package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real native endpoints with a detached checked ledger; these C0-C2 tests claim no native L2 proof. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowReconciliationGameTests {
    private EscrowReconciliationGameTests() { }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void nativeL1AppliesEachEndpointOnceWithoutReportingProof(GameTestHelper helper) throws Exception {
        new Scenario(helper, 0).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void missingCreditedSourceStaysRepairWithoutReconstruction(GameTestHelper helper) throws Exception {
        new Scenario(helper, 2).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void missingBeforeApplyCannotCreditDestination(GameTestHelper helper) throws Exception {
        new Scenario(helper, 1).start();
    }

    private static final class Scenario {
        final GameTestHelper helper;
        final int missing;
        final BlockPos position;
        final ChunkPos chunk;
        final GameTestTickPacer pacer;
        final ResourceEscrowSavedData data = ResourceEscrowSavedData.create();
        final EscrowCoordinator coordinator;
        final Path directory;
        final ArrayList<EscrowReconciliationScheduler.Result> replies = new ArrayList<>();
        EscrowTestEndpoints.Fixture fixture;
        RocketEntity rocket;
        EscrowOperation operation;
        boolean acknowledged;
        int step;

        Scenario(GameTestHelper helper, int missing) throws Exception {
            this.helper = helper; this.missing = missing;
            position = new BlockPos(681_008 + missing * 32, 100, 681_008); chunk = new ChunkPos(position);
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            directory = Files.createTempDirectory("arce-c35-reconcile-");
            coordinator = new EscrowCoordinator(data, new EscrowLedgerWriter(directory.resolve("ledger.dat"), 0));
        }
        void start() {
            var level = helper.getLevel();
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z); // Fixed test setup, not reconciliation loading.
            helper.onEachTick(this::tick);
        }
        void tick() {
            if (step == 4) { return; }
            pacer.pace(); var level = helper.getLevel(); var server = level.getServer();
            coordinator.tick(server.getTickCount());
            if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(position)) { return; }
            if (step == 0) {
                var journal = RocketTransferSavedData.get(server); if (!journal.retryChecked(server)) { return; }
                fixture = EscrowTestEndpoints.create(level, position, data.ledger().id());
                rocket = RocketSettlementIdleGameTests.createRocket(level, position, fixture.logical());
                helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
                var sample = fixture.operation(coordinator.nextSequence()); var fuel = rocket.flightData().orElseThrow().fuel();
                var next = fuel.withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 1_000);
                var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, level.dimension().location().toString(), rocket.getUUID(), fixture.logical(),
                        EscrowAdapterIds.ROCKET, chunk.x, 0, chunk.z, EscrowEndpoint.Unit.PRIMARY, 0, fuel.resourceSignature(), next.resourceSignature());
                var guard = journal.acknowledgedGuard(fixture.logical()).orElseThrow();
                var payload = sample.payload(); payload.putString("FluidName", ModIdentity.id("rocket_fuel").toString());
                operation = new EscrowOperation(sample.id(), sample.sequence(), rocket.ownerId().orElseThrow(), sample.createdTick(), sample.phase(),
                        sample.reason(), payload, RocketNativeResources.signature(payload), sample.source(), destination,
                        new EscrowGuard(fixture.logical(), null, Math.max(1, guard.sequence()), guard.rootDigest()));
                coordinator.prepareNativeTest(operation, rocket, fixture.source(), result -> acknowledged = result);
                helper.assertTrue(data.ledger().records().isEmpty() && rocket.flightData().orElseThrow().fuel().totalAmount() == 0
                        && EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 5_000, "C0 changed an endpoint before L1");
                step = 1;
            }
            if (step == 1) { coordinator.commitPreparations(); if (!acknowledged) { return; } step = 2; }
            if (step == 2) {
                operation = data.ledger().records().get(0);
                if (missing == 1) { level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                helper.assertTrue(replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "Apply or absence was reported as L2 proof");
                if (missing == 1) { assertMissing(0); finish(); return; }
                if (rocket.flightData().orElseThrow().fuel().totalAmount() != 1_000) { return; }
                helper.assertTrue(EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                        && rocket.escrowSection().epoch() == 1 && rocket.escrowSection().markers().size() == 1
                        && data.ledger().records().size() == 1 && coordinator.locked(operation.destination().key()),
                        "C2 lost conservation, epoch, marker or the pending lock");
                step = 3; return;
            }
            if (step == 3) {
                if (missing == 2) { level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000
                        && EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                        && replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "C2 replayed or fabricated proof");
                if (missing == 2) { assertMissing(1_000); }
                finish();
            }
        }
        void assertMissing(long expectedFuel) {
            helper.assertTrue(replies.get(replies.size() - 1).reason() == EscrowReason.MISSING_ENDPOINT
                    && rocket.flightData().orElseThrow().fuel().totalAmount() == expectedFuel
                    && rocket.escrowSection().markers().size() == (expectedFuel == 0 ? 0 : 1)
                    && helper.getLevel().getBlockEntity(position) == null, "Missing source was reconstructed or changed credited resources");
            coordinator.transition(operation.id(), EscrowReason.MISSING_ENDPOINT);
            helper.assertTrue(coordinator.currentReason(operation) == EscrowReason.MISSING_ENDPOINT
                    && coordinator.locked(operation.destination().key()), "Missing source lost sticky repair");
        }
        void finish() {
            coordinator.close(); rocket.discard(); fixture.bank().discard();
            helper.getLevel().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
            helper.getLevel().getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            try {
                try (var files = Files.list(directory)) { for (var file : files.toList()) { Files.delete(file); } }
                Files.delete(directory);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Fixture cleanup failed", failure); }
            step = 4; helper.succeed();
        }
    }
}
