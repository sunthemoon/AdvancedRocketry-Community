package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;

/** Current machine-specific identities; the controller does not switch on a machine kind. */
public record ClassicMachineDefinition(ResourceLocation kind, ResourceLocation pattern,
                                       Supplier<RecipeType<ClassicMachineRecipe>> recipes) {
    public static final ClassicMachineDefinition LATHE = new ClassicMachineDefinition(
            ModIdentity.id("lathe"), ModIdentity.id("lathe"), () -> ModRecipes.LATHE_TYPE.get());
    public static final ClassicMachineDefinition ARC_FURNACE = new ClassicMachineDefinition(
            ModIdentity.id("arc_furnace"), ModIdentity.id("arc_furnace"), () -> ModRecipes.ARC_FURNACE_TYPE.get());
    public static final ClassicMachineDefinition CUTTING = new ClassicMachineDefinition(
            ModIdentity.id("cutting_machine"), ModIdentity.id("cutting_machine"), () -> ModRecipes.CUTTING_TYPE.get());
    public static boolean controllerId(String id) {
        return java.util.List.of(LATHE, ARC_FURNACE, CUTTING).stream().anyMatch(value -> value.kind().toString().equals(id));
    }
}
