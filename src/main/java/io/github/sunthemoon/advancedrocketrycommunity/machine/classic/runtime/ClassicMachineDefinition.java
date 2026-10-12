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
    public static final ClassicMachineDefinition CRYSTALLIZER = new ClassicMachineDefinition(
            ModIdentity.id("crystallizer"), ModIdentity.id("crystallizer"), () -> ModRecipes.CRYSTALLIZER_TYPE.get());
    public static final ClassicMachineDefinition CHEMICAL_REACTOR = new ClassicMachineDefinition(
            ModIdentity.id("chemical_reactor"), ModIdentity.id("chemical_reactor"), () -> ModRecipes.CHEMICAL_REACTOR_TYPE.get());
    public static final ClassicMachineDefinition PRECISION_LASER_ETCHER = new ClassicMachineDefinition(
            ModIdentity.id("precision_laser_etcher"), ModIdentity.id("precision_laser_etcher"), () -> ModRecipes.PRECISION_LASER_ETCHER_TYPE.get());
    public static final ClassicMachineDefinition CENTRIFUGE = new ClassicMachineDefinition(
            ModIdentity.id("centrifuge"), ModIdentity.id("centrifuge"), () -> ModRecipes.CENTRIFUGE_TYPE.get());
    public static boolean controllerId(String id) {
        return java.util.List.of(LATHE, ARC_FURNACE, CUTTING, CRYSTALLIZER, CHEMICAL_REACTOR,
                PRECISION_LASER_ETCHER, CENTRIFUGE).stream().anyMatch(value -> value.kind().toString().equals(id));
    }
}
