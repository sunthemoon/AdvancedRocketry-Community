package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElectrolyzerSignatureBoundsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void supportedActiveProgressWithInvalidJournalKeepsTypedRefusalAcrossMarkers() {
        String id = "advancedrocketrycommunity:electrolyzer_water";
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
                ElectrolyzerStatus expected = schema == 2 || marker >= 2
                        ? ElectrolyzerStatus.UNSUPPORTED_DATA : ElectrolyzerStatus.INVALID_RECIPE;
                var legacy = new ElectrolyzerPersistence.DecodeResult(true, false, false, false, null,
                        new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, FluidStack.EMPTY,
                        0, 10, new ResourceLocation(id));
                for (int reload = 0; reload < 2; reload++) {
                    java.util.concurrent.atomic.AtomicInteger changes = new java.util.concurrent.atomic.AtomicInteger();
                    ElectrolyzerProcessController process = new ElectrolyzerProcessController(changes::incrementAndGet);
                    process.load(parent, legacy); assertEquals(expected, process.displayStatus());
                    assertDoesNotThrow(() -> process.tick(null, null, true)); assertEquals(expected, process.displayStatus());
                    assertFalse(process.acceptsResourceAccess());
                    assertFalse(process.permitsExternalResourceOperations()); assertTrue(process.preservesRecipeInput());
                    process.recordExternalMutation(); assertEquals(4, process.resourceRevision()); assertEquals(0, changes.get());
                    assertEquals(0, process.recipeLookupCount());
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
        String id = "advancedrocketrycommunity:electrolyzer_water";
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
                    ElectrolyzerProcessController process = new ElectrolyzerProcessController(() -> {});
                    process.load(parent, ElectrolyzerPersistence.decode(new CompoundTag()));
                    assertEquals(ElectrolyzerStatus.UNSUPPORTED_DATA, process.displayStatus());
                    assertDoesNotThrow(() -> process.tick(null, null, true));
                    assertEquals(ElectrolyzerStatus.UNSUPPORTED_DATA, process.displayStatus());
                    assertFalse(process.acceptsResourceAccess()); assertTrue(process.preservesRecipeInput());
                    assertEquals(0, process.resourceRevision()); assertEquals(0, process.recipeLookupCount());
                    CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(parent, saved); parent = saved;
                }
            }
        }
    }

    @Test void currentOrphanJournalRequiresRecoveryBeforeAnyWorldOrRecipeLookup() {
        String id = "advancedrocketrycommunity:electrolyzer_water";
        CompoundTag parent = new CompoundTag();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.IDLE, 4, Optional.empty(), Optional.empty(), Optional.empty(), ProcessFailure.NONE)));
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
        parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                UUID.randomUUID(), UUID.randomUUID(), new ProcessPlan(id, 4, before.fingerprint(), before, after))));
        new RecipeSignatureMigration().save(parent, id);
        for (int reload = 0; reload < 2; reload++) {
            ElectrolyzerProcessController process = new ElectrolyzerProcessController(() -> {});
            var compatibleIdle = new ElectrolyzerPersistence.DecodeResult(true, false, false, false, null,
                    new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, FluidStack.EMPTY, 0, 0, null);
            process.load(parent, compatibleIdle);
            assertDoesNotThrow(() -> process.tick(null, null, true), "Current orphan journal must stop before world access");
            assertEquals(ElectrolyzerStatus.INVALID_RECIPE, process.displayStatus());
            assertEquals(4, process.resourceRevision()); assertEquals(0, process.recipeLookupCount());
            assertFalse(process.permitsExternalResourceOperations()); assertTrue(process.preservesRecipeInput());
            CompoundTag saved = new CompoundTag(); process.save(saved);
            CompoundTag state = saved.getCompound(ProcessStatePersistence.ROOT);
            assertEquals("recovery_required", state.getString("state"));
            assertEquals("recovery_diverged", state.getString("failure_code"));
            assertEquals("journal_progress_missing", state.getString("failure_subject"));
            assertEquals(parent.get(ProcessJournalPersistence.ROOT), saved.get(ProcessJournalPersistence.ROOT));
            assertEquals(parent.get(RecipeSignatureMigration.ROOT), saved.get(RecipeSignatureMigration.ROOT)); parent = saved;
        }
    }

    @Test void futureAndCorruptRootsKeepTypedRefusalsBeforeAnySignaturePause() {
        for (String root : new String[]{ProcessStatePersistence.ROOT, ProcessJournalPersistence.ROOT}) {
            for (int schema : new int[]{2, 1}) {
                CompoundTag parent = new CompoundTag(); CompoundTag raw = new CompoundTag();
                raw.putInt("schema_version", schema); raw.putString("retained", "repair-only"); parent.put(root, raw);
                ElectrolyzerStatus expected = schema == 2 ? ElectrolyzerStatus.UNSUPPORTED_DATA
                        : ElectrolyzerStatus.INVALID_RECIPE;
                for (int reload = 0; reload < 2; reload++) {
                    var changes = new java.util.concurrent.atomic.AtomicInteger();
                    ElectrolyzerProcessController process = new ElectrolyzerProcessController(changes::incrementAndGet);
                    process.load(parent, ElectrolyzerPersistence.decode(new CompoundTag()));
                    assertEquals(expected, process.displayStatus());
                    assertDoesNotThrow(() -> process.tick(null, null, true), "Rejected roots must stop before world access");
                    assertEquals(expected, process.displayStatus()); assertEquals(0, changes.get());
                    assertFalse(process.acceptsResourceAccess()); assertFalse(process.permitsExternalResourceOperations());
                    assertTrue(process.preservesRecipeInput()); assertEquals(0, process.resourceRevision());
                    assertEquals(0, process.recipeLookupCount());
                    CompoundTag saved = new CompoundTag(); process.save(saved); assertEquals(parent, saved);
                    assertFalse(saved.contains(RecipeSignatureMigration.ROOT)); parent = saved;
                }
            }
        }
    }

    @Test void currentProgressAllowsOrdinaryRemovalButPreparedJournalAndRepairStateDoNot() {
        String id = "advancedrocketrycommunity:electrolyzer_water";
        CompoundTag parent = new CompoundTag();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, 4, Optional.of(new ProcessProgress(id, 10, 400)),
                Optional.of("a".repeat(64)), Optional.empty(), ProcessFailure.NONE)));
        new RecipeSignatureMigration().save(parent, id);
        var legacy = new ElectrolyzerPersistence.DecodeResult(true, false, false, false, null,
                new ItemStack[]{ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, FluidStack.EMPTY,
                0, 10, new ResourceLocation(id));
        ElectrolyzerProcessController process = new ElectrolyzerProcessController(() -> {});
        process.load(parent, legacy);
        assertTrue(process.acceptsResourceAccess()); assertFalse(process.preservesRecipeInput());
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(4, java.util.Map.of());
        ProcessResourceSnapshot after = new ProcessResourceSnapshot(5, java.util.Map.of());
        var pending = ProcessTransactionJournal.prepared(UUID.randomUUID(), UUID.randomUUID(),
                new ProcessPlan(id, 4, before.fingerprint(), before, after));
        process.save(pending);
        assertTrue(process.preservesRecipeInput()); assertEquals(pending, process.load().orElseThrow());
        process.clear(pending.transactionId());
        assertFalse(process.preservesRecipeInput());
        parent.getCompound(ProcessStatePersistence.ROOT).putString("state", "recovery_required");
        process.load(parent, legacy);
        assertTrue(process.acceptsResourceAccess()); assertTrue(process.preservesRecipeInput());
        process.load(new CompoundTag(), ElectrolyzerPersistence.decode(new CompoundTag()));
        assertFalse(process.preservesRecipeInput());
    }

    @Test void unboundedRootsAreGuardedBeforeRecursiveDecodersAndDoNotBecomeEmptySavedResources() {
        for (String root : new String[]{RecipeSignatureMigration.ROOT, "arce_process", "arce_process_journal", "arce_machine"}) {
            CompoundTag raw = new CompoundTag() {
                @Override public CompoundTag copy() { throw new AssertionError("unbounded input was copied"); }
            };
            CompoundTag node = raw;
            for (int depth = 0; depth < 5_000; depth++) {
                CompoundTag child = new CompoundTag(); node.put("next", child); node = child;
            }
            CompoundTag parent = new CompoundTag(); parent.put(root, raw);
            ElectrolyzerProcessController process = new ElectrolyzerProcessController(() -> {});
            assertDoesNotThrow(() -> process.load(parent, ElectrolyzerPersistence.decode(new CompoundTag())));
            assertEquals(ElectrolyzerStatus.UNSUPPORTED_DATA, process.displayStatus());
            assertFalse(process.acceptsResourceAccess()); assertFalse(process.permitsExternalResourceOperations());
            assertTrue(process.preservesRecipeInput());
            CompoundTag output = new CompoundTag(); assertDoesNotThrow(() -> process.save(output));
            assertSame(raw, output.get(root));
        }
    }
}
