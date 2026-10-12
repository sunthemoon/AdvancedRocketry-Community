package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicRecipeCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicValidatedRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.BoundedItemIngredientCodec;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModRecipes;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;

/** Authored bounded JSON is synced unchanged; server tag expansion is deferred until lookup. */
public final class ClassicMachineRecipe implements Recipe<Container> {
    private final ClassicValidatedRecipe rows;
    public ClassicMachineRecipe(ClassicValidatedRecipe rows) { this.rows = rows; }
    public ClassicValidatedRecipe rows() { return rows; }
    public List<List<String>> resolveInputs() {
        return rows.itemInputs().stream().map(row -> BoundedItemIngredientCodec.resolveAlternatives(
                JsonParser.parseString(row.canonicalIngredientJson()))).toList();
    }
    public ProcessDefinition definition() {
        var inputs = new ArrayList<ProcessInput>(); var outputs = new ArrayList<ProcessOutput>();
        List<List<String>> alternatives = resolveInputs();
        for (int i = 0; i < rows.itemInputs().size(); i++) {
            inputs.add(new ProcessInput(ProcessResourceKind.ITEM, "item_input." + i,
                    alternatives.get(i), rows.itemInputs().get(i).count()));
        }
        for (int i = 0; i < rows.fluidInputs().size(); i++) {
            var row = rows.fluidInputs().get(i); requireFluid(row.fluidId());
            inputs.add(new ProcessInput(ProcessResourceKind.FLUID, "fluid_input." + i,
                    List.of(row.fluidId().toString()), row.amount()));
        }
        for (int i = 0; i < rows.itemOutputs().size(); i++) {
            var row = rows.itemOutputs().get(i); output(i);
            outputs.add(new ProcessOutput(new ProcessResourceKey(ProcessResourceKind.ITEM,
                    "item_output." + i, row.itemId().toString()), row.count()));
        }
        for (int i = 0; i < rows.fluidOutputs().size(); i++) {
            var row = rows.fluidOutputs().get(i); requireFluid(row.fluidId());
            outputs.add(new ProcessOutput(new ProcessResourceKey(ProcessResourceKind.FLUID,
                    "fluid_output." + i, row.fluidId().toString()), row.amount()));
        }
        return new ProcessDefinition(getId().toString(), 1, rows.canonicalJson().getBytes(StandardCharsets.UTF_8).length,
                rows.processingTicks(), rows.energyPerTick(), inputs, retainedOutputs(inputs, outputs));
    }
    private List<ProcessOutput> retainedOutputs(List<ProcessInput> inputs, List<ProcessOutput> outputs) {
        rows.chanceOutputs().forEach(row -> stackLimit(row.itemId(), row.count()));
        if (retainsLens()) {
            for (ProcessInput input : inputs) {
                if (input.alternatives().equals(List.of("advancedrocketrycommunity:laser_lens"))) {
                    outputs.add(new ProcessOutput(input.key(input.alternatives().get(0)), input.amount()));
                }
            }
        }
        return outputs;
    }
    boolean retainsLens() { return rows.type().equals("advancedrocketrycommunity:laser_etching"); }
    static int stackLimit(ResourceLocation id, int count) {
        var item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new IllegalArgumentException("Unknown chance Item"));
        var stack = new ItemStack(item, count);
        if (stack.isEmpty() || count > stack.getMaxStackSize()) { throw new IllegalArgumentException("Chance stack limit"); }
        return stack.getMaxStackSize();
    }
    public ItemStack output(int index) {
        var row = rows.itemOutputs().get(index);
        var item = BuiltInRegistries.ITEM.getOptional(row.itemId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown recipe Item: " + row.itemId()));
        ItemStack stack = new ItemStack(item, row.count());
        if (stack.isEmpty() || stack.getCount() > stack.getMaxStackSize()) {
            throw new IllegalArgumentException("Invalid output Item");
        }
        return stack;
    }
    private static void requireFluid(ResourceLocation id) {
        if (BuiltInRegistries.FLUID.getOptional(id).filter(fluid -> fluid != Fluids.EMPTY).isEmpty()) {
            throw new IllegalArgumentException("Unknown recipe Fluid");
        }
    }
    @Override public ResourceLocation getId() { return rows.id(); }
    public boolean enabled() { return rows.enabled(io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig.sawmillVanillaWood()); }
    @Override public RecipeSerializer<?> getSerializer() {
        return switch (rows.type()) {
            case "advancedrocketrycommunity:arc_furnace" -> ModRecipes.ARC_FURNACE_SERIALIZER.get();
            case "advancedrocketrycommunity:cutting" -> ModRecipes.CUTTING_SERIALIZER.get();
            case "advancedrocketrycommunity:crystallizing" -> ModRecipes.CRYSTALLIZER_SERIALIZER.get();
            case "advancedrocketrycommunity:chemical_reacting" -> ModRecipes.CHEMICAL_REACTOR_SERIALIZER.get();
            case "advancedrocketrycommunity:laser_etching" -> ModRecipes.PRECISION_LASER_ETCHER_SERIALIZER.get();
            case "advancedrocketrycommunity:centrifuging" -> ModRecipes.CENTRIFUGE_SERIALIZER.get();
            default -> ModRecipes.LATHE_SERIALIZER.get();
        };
    }
    @Override public RecipeType<?> getType() {
        return switch (rows.type()) {
            case "advancedrocketrycommunity:arc_furnace" -> ModRecipes.ARC_FURNACE_TYPE.get();
            case "advancedrocketrycommunity:cutting" -> ModRecipes.CUTTING_TYPE.get();
            case "advancedrocketrycommunity:crystallizing" -> ModRecipes.CRYSTALLIZER_TYPE.get();
            case "advancedrocketrycommunity:chemical_reacting" -> ModRecipes.CHEMICAL_REACTOR_TYPE.get();
            case "advancedrocketrycommunity:laser_etching" -> ModRecipes.PRECISION_LASER_ETCHER_TYPE.get();
            case "advancedrocketrycommunity:centrifuging" -> ModRecipes.CENTRIFUGE_TYPE.get();
            default -> ModRecipes.LATHE_TYPE.get();
        };
    }
    @Override public boolean matches(Container container, Level level) { return false; }
    @Override public ItemStack assemble(Container container, RegistryAccess access) { return getResultItem(access); }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public boolean isSpecial() { return true; }
    @Override public ItemStack getResultItem(RegistryAccess access) {
        return rows.itemOutputs().isEmpty() ? ItemStack.EMPTY : output(0);
    }
    @Override public NonNullList<Ingredient> getIngredients() {
        var result = NonNullList.<Ingredient>create();
        rows.itemInputs().forEach(row -> result.add(BoundedItemIngredientCodec.decode(
                JsonParser.parseString(row.canonicalIngredientJson()))));
        return result;
    }
    public static final class Serializer implements RecipeSerializer<ClassicMachineRecipe> {
        private final String type;
        public Serializer() { this("advancedrocketrycommunity:lathe"); }
        public Serializer(String type) { this.type = type; }
        private ClassicMachineRecipe decode(ResourceLocation id, String json) {
            var rows = ClassicRecipeCodec.decode(id, json);
            if (!rows.type().equals(type)) { throw new IllegalArgumentException("Classic serializer type mismatch"); }
            return new ClassicMachineRecipe(rows);
        }
        @Override public ClassicMachineRecipe fromJson(ResourceLocation id, JsonObject json) {
            return decode(id, json.toString());
        }
        @Override public ClassicMachineRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return decode(id, buffer.readUtf(65_536));
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, ClassicMachineRecipe recipe) {
            if (!recipe.rows.type().equals(type)) { throw new IllegalArgumentException("Classic serializer type mismatch"); }
            buffer.writeUtf(recipe.rows.canonicalJson(), 65_536);
        }
    }
}
