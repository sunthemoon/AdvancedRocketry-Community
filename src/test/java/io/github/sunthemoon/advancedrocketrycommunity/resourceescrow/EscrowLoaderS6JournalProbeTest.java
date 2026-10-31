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

class EscrowLoaderS6JournalProbeTest {
    private static final List<String> CUTS = List.of("LF_S6_F2_BEFORE", "LF_S6_F2_REPLACED",
            "LF_S6_F8_JOURNAL_BEFORE", "LF_S6_F8_JOURNAL_REPLACED");
    @TempDir Path directory;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyMissingGateAndEveryOtherSelectorReturnsIdenticalPolicy() {
        var base = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
        for (String cut : CUTS) {
            assertSame(base, EscrowLoaderS6JournalProbe.policy(base, false, "start", cut,
                    () -> { fail("Evaluated release gate without hooks"); return true; }, () -> fail(), ignored -> fail()));
            for (String scenario : List.of("", "recover", "START", "start_typo")) {
                assertSame(base, EscrowLoaderS6JournalProbe.policy(base, true, scenario, cut,
                        () -> { fail("Evaluated release gate outside start"); return true; }, () -> fail(), ignored -> fail()));
            }
            assertSame(base, EscrowLoaderS6JournalProbe.policy(base, true, "start", cut,
                    () -> false, () -> fail("Required refused release gate"), ignored -> fail()));
            assertSame(base, EscrowLoaderS6JournalProbe.policy(base, true, "start", cut + "_OTHER",
                    () -> { fail("Evaluated unknown selector"); return true; }, () -> fail(), ignored -> fail()));
        }
        for (String cut : List.of("", "LF_S6_", "LF_S6_F2_ACK", "LF_S6_F8_JOURNAL_ACK", "FT_JOURNAL_BEFORE")) {
            assertSame(base, EscrowLoaderS6JournalProbe.policy(base, true, "start", cut,
                    () -> { fail("Evaluated unknown selector"); return true; }, () -> fail(), ignored -> fail()));
        }
        assertSame(base, EscrowLoaderS6JournalProbe.journalPolicy(null, base));
    }

    @Test void selectedPoliciesOnlyPauseOnTheirSideOfTheUnchangedCommit() throws Exception {
        for (String cut : CUTS) {
            boolean removal = cut.contains("F8_");
            var previous = root(removal ? "COMMITTED" : null, true);
            var candidate = root(removal ? null : "PREPARED", true);
            var target = java.nio.file.Files.createDirectory(directory.resolve(cut)).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var staged = java.nio.file.Files.createDirectory(directory.resolve(cut + "-stage")).resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
            var atomic = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
            atomic.replace(target, () -> previous);
            atomic.replace(staged, () -> candidate);
            var calls = new ArrayList<String>();
            var base = atomic.withCommitter((from, to) -> {
                assertEquals(staged, from); assertEquals(target, to);
                assertEquals(candidate, atomic.read(from).orElseThrow());
                assertEquals(previous, atomic.read(to).orElseThrow());
                calls.add("commit"); atomic.committer().commit(from, to);
            });
            var selected = EscrowLoaderS6JournalProbe.policy(base, true, "start", cut,
                    () -> true, () -> calls.add("gate"), calls::add);
            assertNotSame(base, selected);
            assertSame(base.reader(), selected.reader());
            selected.committer().commit(staged, target);
            assertEquals(cut.endsWith("BEFORE") ? List.of("gate", cut, "commit") : List.of("gate", "commit", cut), calls);
            assertEquals(candidate, atomic.read(target).orElseThrow());
        }
    }

    @Test void unrelatedAndMalformedTransitionsAreNotSelectedOrRewritten() throws Exception {
        for (String cut : CUTS) {
            for (var roots : List.of(List.of(root(null, true), root(null, true)),
                    List.of(root(null, true), root("COMMITTED", true)),
                    List.of(root(null, true), root("PREPARED", false)),
                    List.of(root("PREPARED", true), root(null, true)),
                    List.of(root("COMMITTED", false), root(null, true)))) {
                var atomic = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.ROCKET_TRANSFERS);
                var target = directory.resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
                var staged = java.nio.file.Files.createTempDirectory(directory, "stage-").resolve(ManagedSavedDataType.ROCKET_TRANSFERS.fileName());
                atomic.replace(target, () -> roots.get(0)); atomic.replace(staged, () -> roots.get(1));
                var selected = EscrowLoaderS6JournalProbe.policy(atomic, true, "start", cut,
                        () -> true, () -> { }, ignored -> fail("Paused unrelated transition"));
                selected.committer().commit(staged, target);
                assertEquals(roots.get(1), atomic.read(target).orElseThrow());
            }
        }
    }

    private static CompoundTag root(String phase, boolean governed) {
        var root = new CompoundTag(); root.putInt("schema_version", 3);
        var transfers = new ListTag();
        if (phase != null) {
            var row = new CompoundTag(); row.putInt("schema_version", governed ? 3 : 2);
            row.putString("phase", phase); row.putUUID("transfer_id", UUID.randomUUID());
            if (governed) { row.put("native_transfer", new CompoundTag()); }
            transfers.add(row);
        }
        root.put("transfers", transfers); return root;
    }
}
