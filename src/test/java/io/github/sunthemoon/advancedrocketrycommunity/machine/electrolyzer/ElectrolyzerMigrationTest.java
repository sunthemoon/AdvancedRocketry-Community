package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStateData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElectrolyzerMigrationTest {
    private static final String RECIPE_ID = "advancedrocketrycommunity:electrolyzer_water";

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void v120Mig001LegacySchemaMigratesOnceWithoutResolvingOrExecutingARecipe() {
        CompoundTag parent = parentWithLegacy(40, RECIPE_ID, 1_200);
        Tag legacyBefore = parent.get(ElectrolyzerPersistence.DATA_KEY).copy();
        ElectrolyzerPersistence.DecodeResult legacy = ElectrolyzerPersistence.decode(parent);
        AtomicInteger changed = new AtomicInteger();
        ElectrolyzerProcessController first = new ElectrolyzerProcessController(changed::incrementAndGet);

        first.load(parent, legacy);
        first.save(parent);

        assertTrue(first.migrationPerformed());
        assertTrue(first.acceptsResourceAccess());
        assertEquals(40, first.progressTicks());
        assertEquals(0, first.recipeLookupCount());
        assertEquals(0, first.resourceRevision());
        assertEquals(1_200, legacy.energy());
        assertTrue(legacy.water().isEmpty());
        assertEquals(legacyBefore, parent.get(ElectrolyzerPersistence.DATA_KEY));
        assertFalse(parent.contains(ProcessJournalPersistence.ROOT));

        ProcessStateData migrated = ProcessStatePersistence.decode(parent).value().orElseThrow();
        assertEquals(ProcessMachineState.RUNNING, migrated.state());
        assertEquals(RECIPE_ID, migrated.progress().orElseThrow().definitionId());
        assertEquals(40, migrated.progress().orElseThrow().progressTicks());
        assertEquals(0, migrated.progress().orElseThrow().consumedEnergy());
        assertEquals(
                ElectrolyzerProcessController.LEGACY_UNVERIFIED_SIGNATURE,
                migrated.recipeSignature().orElseThrow()
        );
        assertTrue(migrated.lastAppliedTransactionId().isEmpty());
        assertEquals(0, changed.get(), "Migration must not masquerade as a resource mutation");

        CompoundTag processBefore = parent.getCompound(ProcessStatePersistence.ROOT).copy();
        ElectrolyzerProcessController second = new ElectrolyzerProcessController(() -> { });
        second.load(parent, ElectrolyzerPersistence.decode(parent));
        CompoundTag secondSave = parent.copy();
        second.save(secondSave);

        assertFalse(second.migrationPerformed());
        assertEquals(40, second.progressTicks());
        assertEquals(0, second.recipeLookupCount());
        assertEquals(processBefore, secondSave.getCompound(ProcessStatePersistence.ROOT));
        assertEquals(legacyBefore, secondSave.get(ElectrolyzerPersistence.DATA_KEY));
    }

    @Test
    void v120Mig002FutureAndInvalidLegacyRootsAreCopiedAndBlockAllResourceAccess() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION + 1);
        future.putString("future_marker", "preserve-exactly");
        assertBlockedAndPreserved(future, ElectrolyzerStatus.UNSUPPORTED_DATA);

        assertBlockedAndPreserved(IntTag.valueOf(17), ElectrolyzerStatus.INVALID_RECIPE);
    }

    @Test
    void futureAndInvalidSharedProcessRootsAreCopiedAndBlockTheCompatibleLegacyState() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", ProcessStatePersistence.SCHEMA_VERSION + 1);
        future.putString("future_marker", "preserve-shared-root");
        assertSharedRootBlockedAndPreserved(future, ElectrolyzerStatus.UNSUPPORTED_DATA);

        assertSharedRootBlockedAndPreserved(IntTag.valueOf(23), ElectrolyzerStatus.INVALID_RECIPE);
    }

    @Test
    void futureAndInvalidSharedJournalRootsAreCopiedAndBlockTheCompatibleLegacyState() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", ProcessTransactionJournal.SCHEMA_VERSION + 1);
        future.putString("future_marker", "preserve-shared-journal");
        assertJournalRootBlockedAndPreserved(future, ElectrolyzerStatus.UNSUPPORTED_DATA);

        assertJournalRootBlockedAndPreserved(IntTag.valueOf(29), ElectrolyzerStatus.INVALID_RECIPE);
    }

    private static void assertBlockedAndPreserved(Tag legacyRoot, ElectrolyzerStatus expectedStatus) {
        CompoundTag parent = new CompoundTag();
        parent.put(ElectrolyzerPersistence.DATA_KEY, legacyRoot.copy());
        ElectrolyzerPersistence.DecodeResult decoded = ElectrolyzerPersistence.decode(parent);
        if (legacyRoot instanceof CompoundTag) {
            assertNotSame(legacyRoot, decoded.preservedData());
        }
        ElectrolyzerProcessController controller = new ElectrolyzerProcessController(() -> { });

        controller.load(parent, decoded);
        controller.save(parent);

        assertFalse(controller.acceptsResourceAccess());
        assertEquals(expectedStatus, controller.displayStatus());
        assertEquals(0, controller.recipeLookupCount());
        assertEquals(legacyRoot, parent.get(ElectrolyzerPersistence.DATA_KEY));
        assertFalse(parent.contains(ProcessStatePersistence.ROOT));
    }

    private static void assertSharedRootBlockedAndPreserved(Tag processRoot, ElectrolyzerStatus expectedStatus) {
        CompoundTag parent = parentWithLegacy(0, null, 0);
        parent.put(ProcessStatePersistence.ROOT, processRoot.copy());
        ElectrolyzerProcessController controller = new ElectrolyzerProcessController(() -> { });

        controller.load(parent, ElectrolyzerPersistence.decode(parent));
        controller.save(parent);

        assertFalse(controller.acceptsResourceAccess());
        assertEquals(expectedStatus, controller.displayStatus());
        assertEquals(processRoot, parent.get(ProcessStatePersistence.ROOT));
        if (processRoot instanceof CompoundTag) {
            assertNotSame(processRoot, parent.get(ProcessStatePersistence.ROOT));
        }
    }

    private static void assertJournalRootBlockedAndPreserved(Tag journalRoot, ElectrolyzerStatus expectedStatus) {
        CompoundTag parent = parentWithLegacy(0, null, 0);
        parent.put(ProcessJournalPersistence.ROOT, journalRoot.copy());
        ElectrolyzerProcessController controller = new ElectrolyzerProcessController(() -> { });

        controller.load(parent, ElectrolyzerPersistence.decode(parent));
        controller.save(parent);

        assertFalse(controller.acceptsResourceAccess());
        assertFalse(controller.permitsExternalResourceOperations());
        assertEquals(expectedStatus, controller.displayStatus());
        assertEquals(journalRoot, parent.get(ProcessJournalPersistence.ROOT));
        if (journalRoot instanceof CompoundTag) {
            assertNotSame(journalRoot, parent.get(ProcessJournalPersistence.ROOT));
        }
    }

    private static CompoundTag parentWithLegacy(int progress, String activeRecipe, int energy) {
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", ElectrolyzerBlockEntity.SLOT_COUNT);
        inventory.put("Items", new ListTag());

        CompoundTag machine = new CompoundTag();
        machine.putInt("schema_version", ElectrolyzerRecipeSpec.CURRENT_SCHEMA_VERSION);
        machine.put("inventory", inventory);
        machine.put("fluid", new CompoundTag());
        machine.putInt("energy", energy);
        machine.putInt("progress", progress);
        if (activeRecipe != null) {
            machine.putString("active_recipe", activeRecipe);
        }

        CompoundTag parent = new CompoundTag();
        parent.put(ElectrolyzerPersistence.DATA_KEY, machine);
        return parent;
    }
}
