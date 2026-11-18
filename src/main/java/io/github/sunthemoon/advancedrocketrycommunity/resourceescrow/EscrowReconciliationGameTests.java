package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowFixtureAreas;
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
    /** The scenarios' remote fixtures, one per {@code missing} value; EscrowFixtureAreas holds their areas (W111). */
    public static final int FIXTURES = 6;
    private EscrowReconciliationGameTests() { }

    public static BlockPos fixture(int missing) {
        if (missing < 0 || missing >= FIXTURES) { throw new IllegalArgumentException("No reconciliation fixture " + missing); }
        return EscrowFixtureAreas.coreFixture(2 + missing);
    }

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
        FluidFuelLoaderEscrowFixtures.NativeFill fixture;
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
            helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.gametest.EscrowFixtureAreas.prepared(level, chunk),
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
                rocket = RocketSettlementIdleGameTests.createRocket(level, position, java.util.UUID.randomUUID());
                helper.assertTrue(level.addFreshEntity(rocket), "Native rocket publication failed");
                fixture = FluidFuelLoaderEscrowFixtures.prepare(helper, coordinator, rocket, position, result -> {
                    helper.assertTrue(result, "The checked L1 writer refused the fixture preparation"); acknowledged = true;
                });
                operation = fixture.operation();
                helper.assertTrue(data.ledger().records().isEmpty() && rocket.flightData().orElseThrow().fuel().totalAmount() == 0
                        && fixture.amount() == 5_000, "C0 changed an endpoint before L1");
                step = 1;
            }
            if (step == 1) { coordinator.commitPreparations(); if (!acknowledged) { return; } step = 2; }
            if (step == 2) {
                operation = data.ledger().records().get(0);
                if (missing == 1) { fixture.clear(); }
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                helper.assertTrue(replies.stream().noneMatch(EscrowReconciliationScheduler.Result::proved), "Apply or absence was reported as L2 proof");
                if (missing == 1) { assertMissing(0); finish(); return; }
                if (rocket.flightData().orElseThrow().fuel().totalAmount() != 1_000) { return; }
                helper.assertTrue(fixture.amount() == 4_000
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
                if (missing == 2) { fixture.clear(); }
                RocketEscrowAudit.attempt(coordinator, server, operation, data.ledger().records(), replies::add);
                if (missing == 4) {
                    helper.assertTrue(rocket.flightData().orElseThrow().fuel().totalAmount() == 1_000
                            && fixture.state(data.ledger().id()).epoch() == 1 && rocket.escrowSection().epoch() == 1
                            && fixture.amount() == 4_000, "C7/C8 changed resources or epochs");
                    if (data.ledger().records().isEmpty()) {
                        helper.assertTrue(replies.stream().filter(EscrowReconciliationScheduler.Result::proved).count() == 1
                                && rocket.escrowSection().markers().isEmpty() && fixture.state(data.ledger().id()).markers().isEmpty()
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
                        && fixture.amount() == 4_000
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
            // Materialize ForgeData before capture so removing the probe restores the exact envelope.
            var persistent = fixture.loader().getPersistentData();
            var view = EscrowReconciliationView.capture(helper.getLevel().getServer(), root, root.records());
            helper.assertTrue(view.current(coordinator) && view.unchanged(coordinator), "Fresh native binding is not current");
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
                    && fixture.state(data.ledger().id()).epoch() == 1 && fixture.state(data.ledger().id()).markers().size() == 1
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
            coordinator.close(); rocket.discard(); fixture.clear();
            helper.getLevel().getChunkSource().removeRegionTicket(TicketType.START, chunk, 2, Unit.INSTANCE);
            try {
                try (var files = Files.list(directory)) { for (var file : files.toList()) { Files.delete(file); } }
                Files.delete(directory);
            } catch (java.io.IOException failure) { throw new IllegalStateException("Fixture cleanup failed", failure); }
            step = 4; helper.succeed();
        }
    }
}
