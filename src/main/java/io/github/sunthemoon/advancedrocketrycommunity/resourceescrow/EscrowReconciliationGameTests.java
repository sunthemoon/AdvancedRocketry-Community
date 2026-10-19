package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
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

/** Real native endpoints with a detached checked ledger and separate C2, C6 and exact C7/C8 boundaries. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowReconciliationGameTests {
    /** The scenarios' remote fixtures, one per {@code missing} value; EscrowCoreFixtureAreas holds their areas (W111). */
    public static final int FIXTURES = 6;
    private EscrowReconciliationGameTests() { }

    public static BlockPos fixture(int missing) { return new BlockPos(681_008 + missing * 32, 100, 681_008); }

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

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void jointNativeReadbackKeepsBothFrontiersLockedWithoutReportingL2(GameTestHelper helper) throws Exception {
        new Scenario(helper, 3).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void exactNativeL2PrunesOnlyAfterAcknowledgementAndKeepsResourcesAndEpochs(GameTestHelper helper) throws Exception {
        new Scenario(helper, 4).start();
    }

    @GameTest(template = "empty", batch = "escrow_core", timeoutTicks = 400)
    public static void cachedNativeViewStillChecksIdentityAtTheFinalBoundary(GameTestHelper helper) throws Exception {
        new Scenario(helper, 5).start();
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
            position = fixture(missing); chunk = new ChunkPos(position);
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            directory = Files.createTempDirectory("arce-c35-reconcile-");
            coordinator = new EscrowCoordinator(data, new EscrowLedgerWriter(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0));
        }
        void start() {
            var level = helper.getLevel();
            level.getChunkSource().addRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            level.getChunk(chunk.x, chunk.z); // Fixed test setup, not reconciliation loading.
            // W111: the escrow_core batch hook generated, saved and flushed this area before the timed ticks started.
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowCoreFixtureAreas.prepared(level, chunk),
                    "Reconciliation fixture area was not prepared before the batch");
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
                coordinator.prepareNativeTest(operation, rocket, fixture.source(), result -> {
                    helper.assertTrue(result, "The checked L1 writer refused the fixture preparation"); acknowledged = true;
                });
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
                if (missing == 5) { assertCachedBoundary(); finish(); return; }
                if (missing == 3 && coordinator.reconciliationCheckpointProved(operation.id())) {
                    assertC6(); finish(); return; // Stop this boundary test before requesting C7.
                }
                if (missing == 2) { level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()); }
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                if (missing == 4) {
                    helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000
                            && fixture.sourceState().epoch() == 1 && rocket.escrowSection().epoch() == 1
                            && EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000, "C7/C8 changed resources or epochs");
                    if (data.ledger().records().isEmpty()) {
                        helper.assertTrue(replies.stream().filter(EscrowReconciliationScheduler.Result::proved).count() == 1
                                && rocket.escrowSection().markers().isEmpty() && fixture.sourceState().markers().isEmpty()
                                && !coordinator.locked(operation.source().key()) && !coordinator.locked(operation.destination().key())
                                && !coordinator.reconciliationCheckpointProved(operation.id()), "Exact L2 did not prune/release once");
                        finish(); return;
                    }
                    helper.assertTrue(replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved)
                            && coordinator.locked(operation.source().key()) && coordinator.locked(operation.destination().key()),
                            "Pending C7 reported success or released an endpoint");
                    return;
                }
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000
                        && EscrowTestEndpoints.amount(fixture.source().getPersistentData()) == 4_000
                        && replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "C2 replayed or fabricated proof");
                if (missing == 2) { assertMissing(1_000); }
                if (missing == 3) {
                    assertC6();
                    if (!coordinator.reconciliationCheckpointProved(operation.id())) { return; }
                }
                finish();
            }
        }
        void assertCachedBoundary() {
            var root = coordinator.ledger();
            var view = EscrowReconciliationView.capture(helper.getLevel().getServer(), root, root.records());
            helper.assertTrue(view.current(coordinator) && view.unchanged(coordinator), "Fresh native binding is not current");
            var persistent = fixture.source().getPersistentData();
            persistent.putBoolean("c39_changed", true);
            helper.assertTrue(!view.current(coordinator), "Fixture mutation did not invalidate the cached binding");
            persistent.remove("c39_changed");
            double x = rocket.getX(); rocket.setPos(x + 1, rocket.getY(), rocket.getZ());
            helper.assertTrue(!view.current(coordinator), "Native placement did not invalidate the cached binding");
            rocket.setPos(x, rocket.getY(), rocket.getZ());
            var duplicate = RocketSettlementIdleGameTests.createRocket(helper.getLevel(), position, fixture.logical());
            helper.assertTrue(helper.getLevel().addFreshEntity(duplicate), "Duplicate fixture publication failed");
            try {
                boolean refused;
                try { refused = !view.unchanged(coordinator); }
                catch (IllegalArgumentException invalid) { refused = true; }
                helper.assertTrue(refused, "Scheduling cache bypassed the final native identity census");
            } finally { duplicate.discard(); }
            helper.assertTrue(view.unchanged(coordinator), "Removing the duplicate did not restore the actual observation");
        }
        void assertC6() {
            helper.assertTrue(coordinator.frozen(operation.source().key()) && coordinator.frozen(operation.destination().key())
                    && coordinator.locked(operation.source().key()) && coordinator.locked(operation.destination().key())
                    && data.ledger().records().size() == 1, "C3-C6 released an L1 record or an endpoint frontier");
            helper.assertTrue(rocket.escrowSection().epoch() == 1 && rocket.escrowSection().markers().size() == 1
                    && fixture.sourceState().epoch() == 1 && fixture.sourceState().markers().size() == 1
                    && replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "Native readback changed an epoch/marker or reported L2");
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
