package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedJournalCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W27c3 real checked files, readback and refusal; no deployment protocol or fault log expectations. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketTransactionProofGameTests {
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSACTIONS;
    private RocketTransactionProofGameTests() { }

    @GameTest(template = "empty", batch = "rocket_transaction_proof", timeoutTicks = 100)
    public static void serverCheckpointAcknowledgesTheOverworldTransactionFile(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = RocketTransactionSavedData.get(server);
        helper.assertTrue(data.checkpoint(server) == CheckedJournalCheckpoint.Outcome.COMMITTED,
                "Server checkpoint did not commit");
        var file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(TYPE.fileName());
        helper.assertTrue(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow().equals(data.save(new CompoundTag())),
                "Overworld transaction file differs from the captured root");
        helper.assertTrue(data.acknowledgedCurrent(), "Server checkpoint is not current");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "rocket_transaction_proof", timeoutTicks = 100)
    public static void replacementExceptionReadbackAcknowledgesButRestartRequiresNewProof(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory("arce-w123-readback-");
        Path file = directory.resolve(TYPE.fileName());
        try {
            var data = new RocketTransactionSavedData();
            var policy = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                throw new IOException("W123 injected after replacement");
            });
            helper.assertTrue(data.saveChecked(file, policy) == CheckedJournalCheckpoint.Outcome.COMMITTED,
                    "Exact readback did not resolve replacement exception");
            var loaded = RocketTransactionSavedData.load(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
            helper.assertTrue(loaded.operational() && loaded.entries().isEmpty() && !loaded.acknowledgedCurrent(),
                    "Reload invented session proof or changed saved fields");
            helper.assertTrue(data.acknowledgement().orElseThrow().records().isEmpty(), "Empty root gained records");
            helper.succeed();
        } finally { cleanup(file, directory); }
    }

    @GameTest(template = "empty", batch = "rocket_transaction_proof", timeoutTicks = 100)
    public static void failedUnknownAndBlockedWritesRefuseAbsence(GameTestHelper helper) throws IOException {
        Path directory = Files.createTempDirectory("arce-w123-refusal-");
        Path file = directory.resolve(TYPE.fileName());
        try {
            var data = new RocketTransactionSavedData();
            var failure = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                throw new IOException("W123 injected before replacement");
            });
            helper.assertTrue(data.saveChecked(file, failure) == CheckedJournalCheckpoint.Outcome.NOT_COMMITTED,
                    "Definite failure was not refused");
            assertUnproved(helper, data);
            var unknown = new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                throw new IOException("W123 injected ambiguous replacement");
            }, (target, type) -> { throw new IllegalStateException("W123 unreadable readback"); });
            helper.assertTrue(data.saveChecked(file, unknown) == CheckedJournalCheckpoint.Outcome.UNKNOWN,
                    "Ambiguous outcome was acknowledged");
            assertUnproved(helper, data);
            var future = data.save(new CompoundTag()); future.putInt("schema_version", 999);
            CheckedSavedDataFile.replace(file, TYPE, () -> future);
            byte[] before = Files.readAllBytes(file);
            var blocked = RocketTransactionSavedData.load(future); blocked.setDirty(); blocked.save(file.toFile());
            helper.assertTrue(java.util.Arrays.equals(before, Files.readAllBytes(file)), "Blocked save overwrote authority");
            assertUnproved(helper, blocked);
            helper.succeed();
        } finally { cleanup(file, directory); }
    }

    private static void assertUnproved(GameTestHelper helper, RocketTransactionSavedData data) {
        helper.assertTrue(data.isDirty() && data.acknowledgement().isEmpty()
                && data.absenceStatus(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
                == RocketTransactionSavedData.AbsenceStatus.ASSEMBLY_JOURNAL_UNPROVED,
                "Refused journal exposed absence proof");
    }

    private static void cleanup(Path file, Path directory) throws IOException {
        Files.deleteIfExists(file.resolveSibling(TYPE.fileName() + ".arce-pending"));
        Files.deleteIfExists(file);
        Files.delete(directory);
    }
}
