package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLoaderS6PadJournalProbeTest {
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void failedGatesReturnTheIdenticalPolicyWithoutCallbacks() {
        var base = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        for (String cut : List.of("LF_S6_PAD_BEFORE", "LF_S6_PAD_MOVED")) {
            assertSame(base, EscrowLoaderS6PadJournalProbe.policy(base, false, "start", cut,
                    () -> { fail(); return true; }, () -> fail(), ignored -> fail()));
            assertSame(base, EscrowLoaderS6PadJournalProbe.policy(base, true, "recover", cut,
                    () -> { fail(); return true; }, () -> fail(), ignored -> fail()));
            assertSame(base, EscrowLoaderS6PadJournalProbe.policy(base, true, "start", cut,
                    () -> false, () -> fail(), ignored -> fail()));
        }
        for (String cut : List.of("", "LF_S6_PAD_UNKNOWN", "LF_S6_PAD_BEFORE_OTHER", "LF_S6_F8_JOURNAL_BEFORE")) {
            assertSame(base, EscrowLoaderS6PadJournalProbe.policy(base, true, "start", cut,
                    () -> { fail(); return true; }, () -> fail(), ignored -> fail()));
        }
    }

    @Test void observesOnlyTheSelectedSideAndPreservesBytesAndReader() throws Exception {
        for (String cut : List.of("LF_S6_PAD_BEFORE", "LF_S6_PAD_MOVED")) {
            var base = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
            var target = java.nio.file.Files.createDirectory(directory.resolve(cut)).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var staged = java.nio.file.Files.createDirectory(directory.resolve(cut + "-stage")).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var previous = root(true, true); var candidate = root(false, true);
            base.replace(target, () -> previous); base.replace(staged, () -> candidate);
            var calls = new ArrayList<String>();
            var policy = base.withCommitter((from, to) -> { calls.add("commit"); base.committer().commit(from, to); });
            var selected = EscrowLoaderS6PadJournalProbe.policy(policy, true, "start", cut,
                    () -> true, () -> calls.add("gate"), calls::add);
            assertSame(policy.reader(), selected.reader());
            selected.committer().commit(staged, target);
            assertEquals(cut.endsWith("BEFORE") ? List.of("gate", cut, "commit") : List.of("gate", "commit", cut), calls);
            assertEquals(candidate, base.read(target).orElseThrow());
        }
    }

    @Test void ignoresOrdinaryAndUnrelatedWrites() throws Exception {
        var base = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        var target = java.nio.file.Files.createDirectory(directory.resolve("target")).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        var staged = java.nio.file.Files.createDirectory(directory.resolve("stage")).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
        for (var previous : List.of(root(true, false), root(false, true))) {
            base.replace(target, () -> previous); base.replace(staged, () -> root(false, true));
            EscrowLoaderS6PadJournalProbe.policy(base, true, "start", "LF_S6_PAD_MOVED",
                    () -> true, () -> { }, ignored -> fail()).committer().commit(staged, target);
            assertEquals(root(false, true), base.read(target).orElseThrow());
        }
    }

    private static CompoundTag root(boolean present, boolean governed) {
        var root = new CompoundTag(); root.putInt("schema_version", 3); var rows = new ListTag();
        if (present) {
            var row = new CompoundTag(); row.putInt("schema_version", governed ? 3 : 2);
            row.putString("phase", "COMMITTED"); row.putUUID("transfer_id", UUID.randomUUID());
            if (governed) { row.put("native_transfer", new CompoundTag()); }
            rows.add(row);
        }
        root.put("transfers", rows); return root;
    }
}
