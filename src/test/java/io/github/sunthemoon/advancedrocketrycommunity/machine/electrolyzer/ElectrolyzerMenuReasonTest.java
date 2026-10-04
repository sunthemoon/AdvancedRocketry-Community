package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ElectrolyzerMenuReasonTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test
    void displayAccessorLeavesSaveAndRuntimeObservationsUnchanged() {
        for (String subject : new String[] {"recipe_missing", "recipe_changed", "recipe_tags_invalid",
                "signature_migration_pending", "signature_migration_unproven", "retained_plan_invalid", "unknown"}) {
            AtomicInteger changes = new AtomicInteger();
            ElectrolyzerProcessController process = new ElectrolyzerProcessController(changes::incrementAndGet);
            CompoundTag parent = new CompoundTag();
            ProcessFailure failure = new ProcessFailure(ProcessFailureCode.INVALID_RECIPE, subject);
            parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                    ProcessMachineState.INVALID_RECIPE, 3, Optional.empty(), Optional.empty(), Optional.empty(), failure)));
            new RecipeSignatureMigration().save(parent, null);
            var legacy = new ElectrolyzerPersistence.DecodeResult(true, false, false, false, null,
                    new ItemStack[] {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY}, FluidStack.EMPTY, 0, 0, null);
            process.load(parent, legacy);
            CompoundTag before = new CompoundTag(); process.save(before);
            for (int count = 0; count < 10; count++) {
                assertEquals(RecipeMenuReason.fromFailure(failure), process.recipeMenuReason());
            }
            CompoundTag after = new CompoundTag(); process.save(after);
            assertEquals(before, after);
            assertEquals(3, process.resourceRevision());
            assertEquals(ElectrolyzerStatus.INVALID_RECIPE, process.displayStatus());
            assertEquals(0, process.recipeLookupCount());
            assertEquals(0, changes.get());
        }
    }

    @Test
    void unsupportedRootTakesPrecedenceOverRecipeDetail() {
        ElectrolyzerProcessController process = new ElectrolyzerProcessController(() -> fail("Display/load mutated"));
        CompoundTag parent = new CompoundTag();
        CompoundTag future = new CompoundTag(); future.putInt("schema_version", 2);
        future.putString("failure_subject", "recipe_missing");
        parent.put(ProcessStatePersistence.ROOT, future);
        process.load(parent, ElectrolyzerPersistence.decode(new CompoundTag()));
        assertEquals(ElectrolyzerStatus.UNSUPPORTED_DATA, process.displayStatus());
        assertEquals(RecipeMenuReason.NONE, process.recipeMenuReason());
        CompoundTag saved = new CompoundTag(); process.save(saved);
        assertEquals(future, saved.get(ProcessStatePersistence.ROOT));
    }
}
