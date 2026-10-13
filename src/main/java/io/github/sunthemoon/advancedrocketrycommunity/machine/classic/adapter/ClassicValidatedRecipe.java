package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeJsonSignature;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

/** Owned strict rows. Native binding is private, guarded, and epoch-specific. */
public final class ClassicValidatedRecipe {
    private final ResourceLocation id;
    private final String type;
    private final Boolean sawmill;
    private final String canonicalJson;
    private final String signature;
    private final List<ClassicItemInput> itemInputs;
    private final List<ClassicItemOutput> itemOutputs;
    private final List<ClassicFluidRow> fluidInputs;
    private final List<ClassicFluidRow> fluidOutputs;
    private final int processingTicks;
    private final int energyPerTick;
    private final List<ClassicChanceOutput> chanceOutputs;
    private final boolean hasChanceList;
    private final String special;
    private volatile Resolved resolved;

    ClassicValidatedRecipe(ResourceLocation id, String canonicalJson, List<ClassicItemInput> itemInputs,
            List<ClassicItemOutput> itemOutputs, List<ClassicFluidRow> fluidInputs, List<ClassicFluidRow> fluidOutputs,
            int processingTicks, int energyPerTick) {
        this(id, "advancedrocketrycommunity:lathe", null, canonicalJson, itemInputs, itemOutputs,
                fluidInputs, fluidOutputs, processingTicks, energyPerTick);
    }

    ClassicValidatedRecipe(ResourceLocation id, String type, Boolean sawmill, String canonicalJson,
            List<ClassicItemInput> itemInputs, List<ClassicItemOutput> itemOutputs,
            List<ClassicFluidRow> fluidInputs, List<ClassicFluidRow> fluidOutputs, int processingTicks, int energyPerTick) {
        this(id, type, sawmill, canonicalJson, itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                processingTicks, energyPerTick, null);
    }

    ClassicValidatedRecipe(ResourceLocation id, String type, Boolean sawmill, String canonicalJson,
            List<ClassicItemInput> itemInputs, List<ClassicItemOutput> itemOutputs,
            List<ClassicFluidRow> fluidInputs, List<ClassicFluidRow> fluidOutputs, int processingTicks, int energyPerTick,
            List<ClassicChanceOutput> chanceOutputs) {
        this(id, type, sawmill, canonicalJson, itemInputs, itemOutputs, fluidInputs, fluidOutputs,
                processingTicks, energyPerTick, chanceOutputs, null);
    }

    ClassicValidatedRecipe(ResourceLocation id, String type, Boolean sawmill, String canonicalJson,
            List<ClassicItemInput> itemInputs, List<ClassicItemOutput> itemOutputs,
            List<ClassicFluidRow> fluidInputs, List<ClassicFluidRow> fluidOutputs, int processingTicks, int energyPerTick,
            List<ClassicChanceOutput> chanceOutputs, String special) {
        this.id = ClassicValueChecks.id(id);
        ClassicValueChecks.require(ClassicRecipeCodec.TYPES.contains(type)
                && (sawmill == null || type.equals("advancedrocketrycommunity:cutting")), "Recipe type/flags");
        this.type = type; this.sawmill = sawmill;
        ClassicValueChecks.require(chanceOutputs == null || type.equals("advancedrocketrycommunity:centrifuging")
                && chanceOutputs.size() <= 16, "Chance list type/bound");
        this.hasChanceList = chanceOutputs != null;
        this.chanceOutputs = chanceOutputs == null ? List.of() : List.copyOf(chanceOutputs);
        ClassicValueChecks.require(canonicalJson != null && canonicalJson.length() <= RecipeJsonSignature.MAX_BYTES
                && canonicalJson.getBytes(StandardCharsets.UTF_8).length <= RecipeJsonSignature.MAX_BYTES,
                "Recipe text bound");
        ClassicValueChecks.require(itemInputs != null && itemOutputs != null && fluidInputs != null && fluidOutputs != null
                && itemInputs.size() <= 4 && itemOutputs.size() <= 4
                && fluidInputs.size() <= 2 && fluidOutputs.size() <= 2
                && processingTicks >= 1 && processingTicks <= 72_000
                && energyPerTick >= 1 && energyPerTick <= 10_000, "Recipe row/tick limits");
        this.itemInputs = List.copyOf(itemInputs); this.itemOutputs = List.copyOf(itemOutputs);
        this.fluidInputs = List.copyOf(fluidInputs); this.fluidOutputs = List.copyOf(fluidOutputs);
        this.processingTicks = processingTicks; this.energyPerTick = energyPerTick;
        // The subject is one unit and its transformed stack takes the fourth output slot.
        ClassicValueChecks.require(special == null || type.equals("advancedrocketrycommunity:chemical_reacting")
                && ClassicRecipeCodec.SPECIALS.contains(special) && !itemInputs.isEmpty()
                && itemInputs.get(0).count() == 1 && itemOutputs.size() <= 3, "Special recipe type/subject");
        this.special = special;
        ClassicValueChecks.require(canonicalRows().equals(canonicalJson), "Recipe JSON/row mismatch");
        this.canonicalJson = canonicalJson;
        this.signature = RecipeJsonSignature.sha256(canonicalJson);
    }

    public ResourceLocation id() { return id; }
    public String type() { return type; }
    public boolean sawmill() { return Boolean.TRUE.equals(sawmill); }
    public boolean enabled(boolean sawmillEnabled) { return !sawmill() || sawmillEnabled; }
    public String canonicalJson() { return canonicalJson; }
    public String jsonSignature() { return signature; }
    public List<ClassicItemInput> itemInputs() { return itemInputs; }
    public List<ClassicItemOutput> itemOutputs() { return itemOutputs; }
    public List<ClassicFluidRow> fluidInputs() { return fluidInputs; }
    public List<ClassicFluidRow> fluidOutputs() { return fluidOutputs; }
    public int processingTicks() { return processingTicks; }
    public int energyPerTick() { return energyPerTick; }
    public List<ClassicChanceOutput> chanceOutputs() { return chanceOutputs; }
    /** The closed special-recipe name, or null for an ordinary recipe. */
    public String special() { return special; }

    private String canonicalRows() {
        JsonObject root = new JsonObject();
        root.addProperty("type", type); root.addProperty("schema_version", 1);
        if (sawmill != null) { root.addProperty("sawmill", sawmill); }
        JsonArray inputs = new JsonArray();
        for (ClassicItemInput input : itemInputs) {
            JsonObject row = new JsonObject(); row.add("ingredient", ClassicRecipeCodec.parse(input.canonicalIngredientJson()));
            row.addProperty("count", input.count()); inputs.add(row);
        }
        JsonArray outputs = new JsonArray();
        for (ClassicItemOutput output : itemOutputs) {
            JsonObject row = new JsonObject(); row.addProperty("item", output.itemId().toString());
            row.addProperty("count", output.count()); outputs.add(row);
        }
        root.add("item_inputs", inputs); root.add("item_outputs", outputs);
        root.add("fluid_inputs", fluidRows(fluidInputs)); root.add("fluid_outputs", fluidRows(fluidOutputs));
        root.addProperty("processing_time", processingTicks); root.addProperty("energy_per_tick", energyPerTick);
        if (special != null) { root.addProperty("special", special); }
        if (hasChanceList) {
            JsonArray chances = new JsonArray();
            for (ClassicChanceOutput candidate : chanceOutputs) {
                JsonObject row = new JsonObject(); row.addProperty("item", candidate.itemId().toString());
                row.addProperty("count", candidate.count()); row.addProperty("weight", candidate.weight()); chances.add(row);
            }
            root.add("chance_outputs", chances);
        }
        return RecipeJsonSignature.canonical(root);
    }

    private static JsonArray fluidRows(List<ClassicFluidRow> fluids) {
        JsonArray result = new JsonArray();
        for (ClassicFluidRow fluid : fluids) {
            JsonObject row = new JsonObject(); row.addProperty("fluid", fluid.fluidId().toString());
            row.addProperty("amount", fluid.amount()); result.add(row);
        }
        return result;
    }

    public ProcessDefinition logicalDefinition() {
        Resolved current = resolved;
        if (current == null) { throw new IllegalStateException("Recipe tags/registries have not been bound"); }
        return current.definition();
    }

    List<String> resolvedItemAlternatives(int inputIndex, GuardTicket ticket) {
        if (inputIndex < 0 || inputIndex >= itemInputs.size()) { throw new IndexOutOfBoundsException(inputIndex); }
        return bind(ticket).alternatives().get(inputIndex);
    }

    ProcessDefinition boundDefinition(GuardTicket ticket) { return bind(ticket).definition(); }

    private Resolved bind(GuardTicket ticket) {
        ticket.requireValid(); Object epoch = ticket.recipeEpoch();
        Resolved current = resolved;
        if (current != null && current.epoch() == epoch) { ticket.requireValid(); return current; }
        var alternatives = new ArrayList<List<String>>(); var inputs = new ArrayList<ProcessInput>();
        for (int index = 0; index < itemInputs.size(); index++) {
            ClassicItemInput row = itemInputs.get(index);
            List<String> values = resolve(row, ticket); alternatives.add(values);
            inputs.add(new ProcessInput(ProcessResourceKind.ITEM, "item_input." + index, values, row.count()));
        }
        for (int index = 0; index < fluidInputs.size(); index++) {
            ClassicFluidRow row = fluidInputs.get(index); requireFluid(row.fluidId(), ticket);
            inputs.add(new ProcessInput(ProcessResourceKind.FLUID, "fluid_input." + index,
                    List.of(row.fluidId().toString()), row.amount()));
        }
        var outputs = new ArrayList<ProcessOutput>();
        for (int index = 0; index < itemOutputs.size(); index++) {
            ClassicItemOutput row = itemOutputs.get(index); requireItem(row.itemId(), ticket);
            outputs.add(new ProcessOutput(new ProcessResourceKey(ProcessResourceKind.ITEM,
                    "item_output." + index, row.itemId().toString()), row.count()));
        }
        for (int index = 0; index < fluidOutputs.size(); index++) {
            ClassicFluidRow row = fluidOutputs.get(index); requireFluid(row.fluidId(), ticket);
            outputs.add(new ProcessOutput(new ProcessResourceKey(ProcessResourceKind.FLUID,
                    "fluid_output." + index, row.fluidId().toString()), row.amount()));
        }
        ProcessDefinition definition = new ProcessDefinition(id.toString(), 1,
                canonicalJson.getBytes(StandardCharsets.UTF_8).length, processingTicks, energyPerTick, inputs, outputs);
        ticket.requireValid();
        Resolved candidate = new Resolved(epoch, List.copyOf(alternatives), definition);
        resolved = candidate;
        return candidate;
    }

    private static List<String> resolve(ClassicItemInput row, GuardTicket ticket) {
        JsonElement raw = ClassicRecipeCodec.parse(row.canonicalIngredientJson());
        List<JsonElement> entries = raw.isJsonArray() ? raw.getAsJsonArray().asList() : List.of(raw);
        TreeSet<String> alternatives = new TreeSet<>();
        for (JsonElement value : entries) {
            JsonObject entry = value.getAsJsonObject();
            if (entry.has("item")) {
                ResourceLocation id = ResourceLocation.tryParse(entry.get("item").getAsString());
                requireItem(id, ticket); alternatives.add(id.toString());
            } else {
                ResourceLocation tagId = ResourceLocation.tryParse(entry.get("tag").getAsString());
                ticket.requireValid();
                var holders = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId));
                ticket.requireValid();
                if (holders.isPresent()) {
                    var iterator = holders.orElseThrow().iterator();
                    ticket.requireValid();
                    while (true) {
                        ticket.requireValid(); boolean more = iterator.hasNext(); ticket.requireValid();
                        if (!more) { break; }
                        ticket.requireValid(); Holder<Item> holder = iterator.next(); ticket.requireValid();
                        Item item = holder.value(); ticket.requireValid();
                        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item); ticket.requireValid();
                        ClassicValueChecks.id(id); requireItem(id, ticket); alternatives.add(id.toString());
                        ClassicValueChecks.require(alternatives.size() <= 32, "Recipe tag alternatives bound");
                    }
                }
            }
            ClassicValueChecks.require(alternatives.size() <= 32, "Recipe alternatives bound");
        }
        ClassicValueChecks.require(!alternatives.isEmpty(), "Recipe tag/item unavailable");
        ticket.requireValid(); return List.copyOf(alternatives);
    }

    private static void requireItem(ResourceLocation id, GuardTicket ticket) {
        ticket.requireValid(); Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null); ticket.requireValid();
        ClassicValueChecks.require(item != null, "Unknown recipe Item");
        var stack = item.getDefaultInstance(); ticket.requireValid();
        boolean empty = stack.isEmpty(); ticket.requireValid();
        ClassicValueChecks.require(!empty, "Empty recipe Item");
    }

    private static void requireFluid(ResourceLocation id, GuardTicket ticket) {
        ticket.requireValid(); boolean exists = ForgeRegistries.FLUIDS.containsKey(id); ticket.requireValid();
        var fluid = ForgeRegistries.FLUIDS.getValue(id); ticket.requireValid();
        ClassicValueChecks.require(exists && fluid != null && fluid != Fluids.EMPTY, "Unknown/empty recipe Fluid");
    }

    private record Resolved(Object epoch, List<List<String>> alternatives, ProcessDefinition definition) { }
}
