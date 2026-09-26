package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketAdapterFailureFixture.Fault;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketTransactionRecoveryService;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailurePoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketAdapterFailureGameTests {
    private static final String TEMPLATE = "rocket_test";

    private RocketAdapterFailureGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void supportsExceptionIsRejectedBeforeMutation(GameTestHelper helper) {
        assertCaptureRejected(helper, Fault.SUPPORTS_THROW);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void captureExceptionIsRejectedBeforeMutation(GameTestHelper helper) {
        assertCaptureRejected(helper, Fault.CAPTURE_THROW);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void captureCannotSubstituteAnotherAdapterIdentity(GameTestHelper helper) {
        assertCaptureRejected(helper, Fault.FOREIGN_PAYLOAD_ID);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void restoreExceptionAfterMutationLeavesNoPartialPlacement(GameTestHelper helper) {
        assertPlacementRejected(helper, Fault.RESTORE_THROW);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void restoreFalseAfterMutationLeavesNoPartialPlacement(GameTestHelper helper) {
        assertPlacementRejected(helper, Fault.RESTORE_FALSE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void restoreTrueRequiresAnEquivalentContainerPayload(GameTestHelper helper) {
        assertPlacementRejected(helper, Fault.RESTORE_INCORRECT_TRUE);
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void disassemblyRestoreExceptionPreservesTheOnlyRocketAuthority(GameTestHelper helper) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            RocketEntity rocket = fixture.assemble();
            UUID operation = UUID.randomUUID();
            fixture.adapter.fault = Fault.RESTORE_THROW;
            RocketTransactionResult result = new RocketDisassemblyTransaction(
                    fixture.world(), fixture.locks, fixture.ledger, fixture.journal(operation))
                    .execute(operation, rocket.getUUID(), fixture.snapshot);

            helper.assertTrue(!result.success(), "Throwing restoration was committed");
            fixture.assertRocketRetained(rocket);
            fixture.assertSourceEmpty();
            if (fixture.hasEntry(operation)) {
                fixture.assertSnapshotRetained(operation);
            }
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void failedRollbackRetainsJournalUntilThePayloadCanBeVerified(GameTestHelper helper) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            RocketEntity rocket = fixture.assemble();
            UUID operation = UUID.randomUUID();
            RocketTransactionResult result = new RocketDisassemblyTransaction(
                    fixture.world(), fixture.locks, fixture.ledger, fixture.journal(operation),
                    (point, progress) -> {
                        if (point == RocketFailurePoint.DURING_RESTORATION
                                && progress == fixture.snapshot.blocks().size()) {
                            fixture.adapter.fault = Fault.CAPTURE_THROW;
                            throw new IllegalStateException("injected failure before container cleanup");
                        }
                    }).execute(operation, rocket.getUUID(), fixture.snapshot);

            helper.assertTrue(!result.success() && result.code() == RocketValidationCode.ROLLBACK_FAILED,
                    "Incomplete cleanup was reported as a completed rollback: " + result.code());
            helper.assertTrue(fixture.hasEntry(operation), "Incomplete cleanup discarded the recovery journal");
            helper.assertTrue(fixture.entry(operation).record().phase() != RocketTransactionPhase.ROLLED_BACK,
                    "Incomplete cleanup persisted a terminal rolled-back record");
            fixture.assertSnapshotRetained(operation);
            fixture.assertRocketRetained(rocket);

            fixture.adapter.fault = Fault.NONE;
            RocketTransactionRecoveryService recovery = new RocketTransactionRecoveryService(fixture.adapters);
            recovery.recoverOne(fixture.level.getServer());
            helper.assertTrue(!fixture.hasEntry(operation), "Healthy retry did not finish rollback cleanup");
            fixture.assertSourceEmpty();
            fixture.assertRocketRetained(rocket);
            recovery.recoverOne(fixture.level.getServer());
            fixture.assertSourceEmpty();
            fixture.assertRocketRetained(rocket);
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void assemblyRollbackExceptionRetainsSnapshotForLaterRestoration(GameTestHelper helper) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            UUID operation = UUID.randomUUID();
            RocketTransactionResult result = new RocketAssemblyTransaction(
                    fixture.world(), fixture.locks, fixture.ledger, fixture.journal(operation),
                    (point, progress) -> {
                        if (point == RocketFailurePoint.DURING_EXTRACTION
                                && progress == fixture.snapshot.blocks().size()) {
                            fixture.adapter.fault = Fault.RESTORE_THROW;
                            throw new IllegalStateException("injected extraction failure before rollback");
                        }
                    }).execute(operation, fixture.snapshot);

            helper.assertTrue(!result.success() && result.code() == RocketValidationCode.ROLLBACK_FAILED,
                    "Failed restoration did not report an incomplete rollback: " + result.code());
            helper.assertTrue(fixture.hasEntry(operation), "Failed rollback discarded its saved snapshot");
            fixture.assertSnapshotRetained(operation);
            fixture.assertNoDrops();

            fixture.adapter.fault = Fault.NONE;
            RocketTransactionRecoveryService recovery = new RocketTransactionRecoveryService(fixture.adapters);
            recovery.recoverOne(fixture.level.getServer());
            helper.assertTrue(!fixture.hasEntry(operation), "Healthy recovery did not retire the journal");
            fixture.assertSourceRestored();
            recovery.recoverOne(fixture.level.getServer());
            fixture.assertSourceRestored();
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void missingProviderPreservesEntityPayloadAndJournalUntilItReturns(GameTestHelper helper) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            RocketEntity rocket = fixture.assemble();
            UUID operation = new UUID(Long.MIN_VALUE, UUID.randomUUID().getLeastSignificantBits());
            fixture.writeRecovery(operation, rocket);
            RocketTransactionRecord before = fixture.entry(operation).record();

            new RocketTransactionRecoveryService(new RocketBlockEntityAdapters(List.of()))
                    .recoverOne(fixture.level.getServer());
            fixture.assertRocketRetained(rocket);
            fixture.assertSourceEmpty();
            helper.assertTrue(fixture.hasEntry(operation), "Unavailable provider discarded its journal");
            helper.assertTrue(before.equals(fixture.entry(operation).record()),
                    "Unavailable provider changed recovery authority");
            fixture.assertSnapshotRetained(operation);

            RocketTransactionRecoveryService restored = new RocketTransactionRecoveryService(fixture.adapters);
            restored.recoverOne(fixture.level.getServer());
            helper.assertTrue(!fixture.hasEntry(operation), "Provider return did not retire recovery work");
            helper.assertTrue(!rocket.isAlive(), "Block recovery retained a duplicate rocket");
            fixture.assertSourceRestored();
            int successfulRestores = fixture.adapter.restoreCalls;
            restored.recoverOne(fixture.level.getServer());
            fixture.assertSourceRestored();
            helper.assertTrue(fixture.adapter.restoreCalls == successfulRestores,
                    "Completed recovery restored the same container twice");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void unavailableProviderDoesNotStarveALaterRecoverableTransaction(GameTestHelper helper) {
        try (RocketAdapterFailureFixture unavailable = new RocketAdapterFailureFixture(helper);
                RocketAdapterFailureFixture valid = new RocketAdapterFailureFixture(helper,
                        RocketAdapterFailureFixture.SECOND_ORIGIN, RocketAdapterFailureFixture.OTHER_ID)) {
            RocketEntity waitingRocket = unavailable.assemble();
            RocketEntity readyRocket = valid.assemble();
            UUID waitingId = new UUID(Long.MIN_VALUE, UUID.randomUUID().getLeastSignificantBits());
            UUID readyId = new UUID(Long.MIN_VALUE + 1L, UUID.randomUUID().getLeastSignificantBits());
            unavailable.writeRecovery(waitingId, waitingRocket);
            valid.writeRecovery(readyId, readyRocket);
            RocketTransactionRecoveryService recovery = new RocketTransactionRecoveryService(valid.adapters);

            for (int attempt = 0; attempt < 2 && valid.hasEntry(readyId); attempt++) {
                recovery.recoverOne(valid.level.getServer());
            }
            helper.assertTrue(!valid.hasEntry(readyId), "Unavailable first provider starved later recovery");
            helper.assertTrue(!readyRocket.isAlive(), "Completed block recovery retained the second entity");
            valid.assertSourceRestored();
            unavailable.assertRocketRetained(waitingRocket);
            unavailable.assertSourceEmpty();
            unavailable.assertSnapshotRetained(waitingId);

            new RocketTransactionRecoveryService(unavailable.adapters).recoverOne(unavailable.level.getServer());
            helper.assertTrue(!unavailable.hasEntry(waitingId), "Returned provider did not complete the first record");
            helper.assertTrue(!waitingRocket.isAlive(), "Returned provider left duplicate entity authority");
            unavailable.assertSourceRestored();
            valid.assertSourceRestored();
        }
        helper.succeed();
    }

    private static void assertCaptureRejected(GameTestHelper helper, Fault fault) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            fixture.adapter.fault = fault;
            RocketBlockEntityAdapters.CaptureResult result = fixture.adapters.capture(fixture.chest());
            helper.assertTrue(!result.supported(), "Invalid capture dispatch was accepted: " + fault);
            fixture.adapter.fault = Fault.NONE;
            fixture.assertSourceRestored();
        }
        helper.succeed();
    }

    private static void assertPlacementRejected(GameTestHelper helper, Fault fault) {
        try (RocketAdapterFailureFixture fixture = new RocketAdapterFailureFixture(helper)) {
            RocketBlock block = fixture.chestBlock();
            fixture.clearChest();
            fixture.adapter.fault = fault;
            boolean placed = fixture.world().placeBlockIfEmpty(
                    RocketAdapterFailureFixture.position(fixture.chestPosition),
                    RocketWorldBlock.fromSnapshotBlock(block));
            helper.assertTrue(!placed, "Invalid restored payload was accepted: " + fault);
            helper.assertTrue(fixture.level.getBlockState(fixture.chestPosition).isAir()
                            && fixture.level.getBlockEntity(fixture.chestPosition) == null,
                    "Rejected restore left a partial container: " + fault);
            fixture.assertNoDrops();
        }
        helper.succeed();
    }
}
