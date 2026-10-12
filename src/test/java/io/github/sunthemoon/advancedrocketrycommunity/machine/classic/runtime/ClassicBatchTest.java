package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicBatchTest {
    private static final ClassicBankKey INPUT = new ClassicBankKey(ClassicBankKind.ITEM_INPUT, 1, 2, 3);
    private static final ClassicBankKey OUTPUT = new ClassicBankKey(ClassicBankKind.ITEM_OUTPUT, 1, 1, 3);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private ClassicMachineRecipe recipe() {
        return new ClassicMachineRecipe(ClassicRecipeCodec.decode(ResourceLocation.tryParse("test:lathe"), """
                {"type":"advancedrocketrycommunity:lathe","schema_version":1,
                "item_inputs":[{"ingredient":{"item":"minecraft:iron_ingot"},"count":1}],
                "item_outputs":[{"item":"minecraft:iron_nugget","count":2}],
                "fluid_inputs":[],"fluid_outputs":[],"processing_time":300,"energy_per_tick":20}
                """));
    }
    private ClassicResources banks(boolean full) {
        return ClassicResources.empty(UUID.randomUUID()).replace(List.of(
                ClassicResourceBank.items(INPUT, List.of(new ItemStack(Items.IRON_INGOT, 2), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY)),
                ClassicResourceBank.items(OUTPUT, full ? List.of(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64),
                        new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIRT, 64))
                        : List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY))), false);
    }
    @Test void nativeCompletionIsDetachedAndKernelCommitAgrees() {
        var before = banks(false); var recipe = recipe();
        var plan = ClassicBatch.prepare(before, Set.of(INPUT, OUTPUT), recipe, recipe.definition()).plan().orElseThrow();
        assertEquals(2, before.bank(INPUT).orElseThrow().item(0).getCount());
        assertEquals(1, plan.after().bank(INPUT).orElseThrow().item(0).getCount());
        assertEquals(2, plan.after().bank(OUTPUT).orElseThrow().item(0).getCount());
        assertEquals(before.revision() + 1, plan.after().revision());
        assertTrue(ClassicBatch.verifyCommit(plan, before.machineId(), UUID.randomUUID()));
    }
    @Test void unknownRegistryOutputIsAnInvalidRecipeRatherThanAnUncaughtLookupFailure() {
        var unknown = new ClassicMachineRecipe(ClassicRecipeCodec.decode(ResourceLocation.tryParse("test:unknown"),
                recipe().rows().canonicalJson().replace("minecraft:iron_nugget", "test:absent_item")));
        assertThrows(IllegalArgumentException.class, unknown::definition);
    }
    @Test void fullOutputAndInactiveInputsKeepTheOriginalSnapshot() {
        var before = banks(true); var recipe = recipe();
        assertEquals(ClassicWait.OUTPUT, ClassicBatch.prepare(before, Set.of(INPUT, OUTPUT), recipe, recipe.definition()).reason());
        assertEquals(2, before.bank(INPUT).orElseThrow().item(0).getCount());
        assertEquals(ClassicWait.INPUT, ClassicBatch.prepare(before, Set.of(OUTPUT), recipe, recipe.definition()).reason());
    }
    @Test void removingOneBankIsIdempotentAndPreservesTheOther() {
        var before = banks(false); var after = before.remove(INPUT);
        assertTrue(after.bank(INPUT).isEmpty()); assertTrue(after.bank(OUTPUT).isPresent());
        assertSame(after, after.remove(INPUT)); assertTrue(before.bank(INPUT).isPresent());
        assertEquals(before.revision() + 1, after.revision());
    }
}
