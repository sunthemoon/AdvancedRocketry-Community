package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class RollingSignatureStateTest {
    @Test void supportedActiveProgressWithInvalidJournalKeepsTypedRefusalAcrossMarkers() {
        String id = "advancedrocketrycommunity:rolling_iron_bars";
        for (int marker : new int[]{0, 1, 2, 3}) {
            for (int schema : new int[]{2, 1}) {
                CompoundTag parent = new CompoundTag();
                parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                        ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(id, 10, 400)),
                        Optional.of("a".repeat(64)), Optional.empty(), ProcessFailure.NONE)));
                CompoundTag blocked = new CompoundTag(); blocked.putInt("schema_version", schema);
                blocked.putString("retained", "original-journal"); parent.put(ProcessJournalPersistence.ROOT, blocked);
                if (marker != 0) {
                    new RecipeSignatureMigration().save(parent, id);
                    if (marker == 2) { parent.getCompound(RecipeSignatureMigration.ROOT).putInt("schema_version", 2); }
                    if (marker == 3) { parent.getCompound(RecipeSignatureMigration.ROOT).putString("format", "unknown"); }
                }
                ProcessMachineState expected = schema == 2 || marker >= 2
                        ? ProcessMachineState.UNSUPPORTED_DATA : ProcessMachineState.RECOVERY_REQUIRED;
                for (int reload = 0; reload < 2; reload++) {
                    java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
                    RollingMachineProcessController process = new RollingMachineProcessController(changes::incrementAndGet);
                    process.load(parent, UUID.randomUUID()); assertEquals(expected, process.state());
                    assertFalse(process.tick(null, null)); assertEquals(expected, process.state());
                    assertEquals(ProcessFailure.NONE, process.failure()); assertFalse(process.acceptsResourceAccess());
                    assertFalse(process.permitsExternalResourceOperations()); assertTrue(process.preservesRecipeInput());
                    process.recordExternalMutation(null, null); assertEquals(4, process.resourceRevision()); assertEquals(0, changes.get());
                    CompoundTag saved = new CompoundTag(); process.save(saved);
                    assertEquals(parent.get(ProcessJournalPersistence.ROOT), saved.get(ProcessJournalPersistence.ROOT));
                    assertEquals(parent.get(RecipeSignatureMigration.ROOT), saved.get(RecipeSignatureMigration.ROOT));
                    for (String field : java.util.List.of("resource_revision", "definition_id", "recipe_signature",
                            "progress_ticks", "consumed_energy", "last_applied_transaction", "failure_code", "failure_subject")) {
                        assertEquals(parent.getCompound(ProcessStatePersistence.ROOT).get(field),
                                saved.getCompound(ProcessStatePersistence.ROOT).get(field), field);
                    }
                    if (marker != 1) { assertEquals(parent, saved); }
                    parent = saved;
                }
            }
        }
    }

    @Test void futureProcessWithSupportedOrForeignJournalKeepsUnsupportedClassification() {
        String id = "advancedrocketrycommunity:rolling_iron_bars";
        for (boolean marked : new boolean[]{false, true}) {
            for (boolean foreign : new boolean[]{false, true}) {
                UUID owner = UUID.randomUUID(); CompoundTag parent = new CompoundTag();
                CompoundTag future = new CompoundTag(); future.putInt("schema_version", 2); future.putString("retained", "future");
                parent.put(ProcessStatePersistence.ROOT, future);
                ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
                ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
                parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                        UUID.randomUUID(), foreign ? UUID.randomUUID() : owner,
                        new ProcessPlan(id, 4, before.fingerprint(), before, after))));
                if (marked) { new RecipeSignatureMigration().save(parent, id); }
                for (int reload = 0; reload < 2; reload++) {
                    RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
                    process.load(parent, owner); assertEquals(ProcessMachineState.UNSUPPORTED_DATA, process.state());
                    assertFalse(process.tick(null, null)); assertEquals(ProcessMachineState.UNSUPPORTED_DATA, process.state());
                    assertFalse(process.acceptsResourceAccess()); assertTrue(process.preservesRecipeInput());
                    assertEquals(0, process.resourceRevision());
                    CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(parent, saved); parent = saved;
                }
            }
        }
    }

    @Test void currentOrphanJournalRequiresRecoveryBeforeAnyWorldOrRecipeLookup() {
        String id = "advancedrocketrycommunity:rolling_iron_bars"; UUID owner = UUID.randomUUID();
        CompoundTag parent = new CompoundTag();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.IDLE, 4, Optional.empty(), Optional.empty(), Optional.empty(), ProcessFailure.NONE)));
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
        parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                UUID.randomUUID(), owner, new ProcessPlan(id, 4, before.fingerprint(), before, after))));
        new RecipeSignatureMigration().save(parent, id);
        for (int reload = 0; reload < 2; reload++) {
            RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
            process.load(parent, owner);
            assertFalse(process.tick(null, null), "Current orphan journal must stop before world access");
            assertEquals(ProcessMachineState.RECOVERY_REQUIRED, process.state());
            assertEquals(ProcessFailureCode.RECOVERY_DIVERGED, process.failure().code());
            assertEquals("journal_progress_missing", process.failure().subject());
            assertEquals(4, process.resourceRevision()); assertTrue(process.progress().isEmpty());
            assertFalse(process.permitsExternalResourceOperations()); assertTrue(process.preservesRecipeInput());
            CompoundTag saved = new CompoundTag(); process.save(saved);
            assertEquals(parent.get(ProcessJournalPersistence.ROOT), saved.get(ProcessJournalPersistence.ROOT));
            assertEquals(parent.get(RecipeSignatureMigration.ROOT), saved.get(RecipeSignatureMigration.ROOT)); parent = saved;
        }
    }

    @Test void futureAndCorruptRootsKeepTypedRefusalsBeforeAnySignaturePause() {
        for (String root : new String[]{ProcessStatePersistence.ROOT, ProcessJournalPersistence.ROOT}) {
            for (int schema : new int[]{2, 1}) {
                CompoundTag parent = new CompoundTag(); CompoundTag raw = new CompoundTag();
                raw.putInt("schema_version", schema); raw.putString("retained", "repair-only"); parent.put(root, raw);
                ProcessMachineState expected = schema == 2 ? ProcessMachineState.UNSUPPORTED_DATA
                        : ProcessMachineState.RECOVERY_REQUIRED;
                for (int reload = 0; reload < 2; reload++) {
                    var changes = new java.util.concurrent.atomic.AtomicInteger();
                    RollingMachineProcessController process = new RollingMachineProcessController(changes::incrementAndGet);
                    process.load(parent, UUID.randomUUID());
                    assertEquals(expected, process.state()); ProcessFailure failure = process.failure();
                    assertFalse(process.tick(null, null), "Rejected roots must stop before world access");
                    assertEquals(expected, process.state()); assertEquals(failure, process.failure());
                    assertEquals(0, changes.get()); assertFalse(process.acceptsResourceAccess());
                    assertFalse(process.permitsExternalResourceOperations()); assertTrue(process.preservesRecipeInput());
                    assertEquals(0, process.resourceRevision()); assertTrue(process.progress().isEmpty());
                    CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(parent, saved);
                    assertFalse(saved.contains(RecipeSignatureMigration.ROOT)); parent = saved;
                }
            }
        }
    }

    @Test void unmarkedBuiltinActiveAndEveryJournalPhasePauseBeforeAnyWorldOrRecipeLookup() {
        String id = "advancedrocketrycommunity:rolling_iron_bars";
        for (String signature : new String[]{"575101496cbaf0038049fe2d373a662a05016d570cc54d0f7a6f2db026cb59cc",
                "0".repeat(64), "a".repeat(64)}) {
            for (ProcessJournalPhase phase : new ProcessJournalPhase[]{null, ProcessJournalPhase.PREPARED,
                    ProcessJournalPhase.APPLYING, ProcessJournalPhase.APPLIED}) {
                UUID owner = UUID.randomUUID(); CompoundTag old = new CompoundTag();
                int ticks = phase == null ? 40 : 100;
                old.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                        ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(id, ticks, ticks * 20L)),
                        Optional.of(signature), Optional.empty(), new ProcessFailure(ProcessFailureCode.OUTPUT_BLOCKED, "old_failure"))));
                if (phase != null) {
                    ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
                    ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
                    old.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(new ProcessTransactionJournal(
                            1, UUID.randomUUID(), owner, id, 4, before.fingerprint(), before, after, phase)));
                }
                for (int load = 0; load < 2; load++) {
                    RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
                    process.load(old, owner);
                    assertFalse(process.tick(null, null), "Unproved work must pause before dereferencing world/controller");
                    assertEquals(ProcessMachineState.RECOVERY_REQUIRED, process.state());
                    assertEquals("signature_migration_unproven", process.failure().subject());
                    assertEquals(ticks, process.progress().orElseThrow().progressTicks());
                    assertEquals(4, process.resourceRevision()); assertTrue(process.preservesRecipeInput());
                    assertFalse(process.permitsExternalResourceOperations());
                    assertEquals(phase != null, process.load().isPresent());
                    CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(old, saved);
                    assertFalse(saved.contains(RecipeSignatureMigration.ROOT)); old = saved;
                }
            }
        }
    }

    @Test void currentProgressAllowsOrdinaryRemovalButPreparedJournalAndRepairStateDoNot() {
        String id = "advancedrocketrycommunity:rolling_iron_bars";
        UUID owner = UUID.randomUUID();
        CompoundTag parent = new CompoundTag();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(id, 10, 200)),
                Optional.of("a".repeat(64)), Optional.empty(), ProcessFailure.NONE)));
        new RecipeSignatureMigration().save(parent, id);
        RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
        process.load(parent, owner);
        assertTrue(process.acceptsResourceAccess()); assertFalse(process.preservesRecipeInput());
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
        var pending = ProcessTransactionJournal.prepared(UUID.randomUUID(), owner,
                new ProcessPlan(id, 4, before.fingerprint(), before, after));
        process.save(pending);
        assertTrue(process.preservesRecipeInput()); assertEquals(pending, process.load().orElseThrow());
        process.clear(pending.transactionId());
        assertFalse(process.preservesRecipeInput());
        parent.getCompound(ProcessStatePersistence.ROOT).putString("state", "recovery_required");
        process.load(parent, owner);
        assertTrue(process.acceptsResourceAccess()); assertTrue(process.preservesRecipeInput());
        process.load(new CompoundTag(), owner);
        assertFalse(process.preservesRecipeInput());
    }

    @Test void markedProgressAndJournalMustBothMatchTheMarkerIdentity() {
        String id = "advancedrocketrycommunity:rolling_iron_bars";
        CompoundTag parent = new CompoundTag();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(id, 10, 200)),
                Optional.of("a".repeat(64)), Optional.empty(), ProcessFailure.NONE)));
        UUID owner = UUID.randomUUID();
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
        parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(
                ProcessTransactionJournal.prepared(UUID.randomUUID(), owner,
                        new ProcessPlan("arce_test:different", 4, before.fingerprint(), before, after))));
        new RecipeSignatureMigration().save(parent, id);
        RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
        process.load(parent, owner);
        assertFalse(process.acceptsResourceAccess()); assertEquals(ProcessMachineState.UNSUPPORTED_DATA, process.state());
        CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(parent, saved);
    }

    @Test void unboundedRootsAreGuardedBeforeExistingRecursiveDecodersAndRoundTripByReferenceOnly() {
        for (String root : new String[]{RecipeSignatureMigration.ROOT, "arce_process", "arce_process_journal", "arce_machine"}) {
            CompoundTag raw = new CompoundTag() {
                @Override public CompoundTag copy() { throw new AssertionError("unbounded input was copied"); }
            };
            CompoundTag node = raw;
            for (int depth = 0; depth < 5_000; depth++) {
                CompoundTag child = new CompoundTag(); node.put("next", child); node = child;
            }
            CompoundTag parent = new CompoundTag(); parent.put(root, raw);
            RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
            assertDoesNotThrow(() -> process.load(parent, UUID.randomUUID()));
            assertEquals(ProcessMachineState.UNSUPPORTED_DATA, process.state());
            assertFalse(process.acceptsResourceAccess()); assertFalse(process.permitsExternalResourceOperations());
            assertTrue(process.preservesRecipeInput());
            CompoundTag output = new CompoundTag(); assertDoesNotThrow(() -> process.save(output));
            assertSame(raw, output.get(root));
        }
    }

    @Test void actualControllerPreservesUnprovedLegacyProgressAndRestoresLifecycleAfterFutureMarker() {
        CompoundTag old = new CompoundTag();
        old.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(
                        "advancedrocketrycommunity:rolling_iron_bars", 40, 800)),
                Optional.of("575101496cbaf0038049fe2d373a662a05016d570cc54d0f7a6f2db026cb59cc"),
                Optional.empty(), ProcessFailure.NONE)));
        UUID owner = UUID.randomUUID(); RollingMachineProcessController process = new RollingMachineProcessController(() -> {});
        process.load(old, owner);
        assertEquals(40, process.progress().orElseThrow().progressTicks());
        assertFalse(process.permitsExternalResourceOperations());
        assertEquals(ProcessMachineState.RECOVERY_REQUIRED, process.state());
        CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(old, saved);
        CompoundTag future = old.copy(); CompoundTag marker = new CompoundTag(); marker.putInt("schema_version", 2);
        future.put(RecipeSignatureMigration.ROOT, marker); process.load(future, owner);
        assertFalse(process.acceptsResourceAccess()); saved = new CompoundTag(); process.save(saved); assertEquals(future, saved);
        process.load(new CompoundTag(), owner);
        assertTrue(process.acceptsResourceAccess()); assertTrue(process.permitsExternalResourceOperations());
    }
}
