package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

class RetainedRecipePlanTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void retainedAlternativesReconcileAValidatedPlanWithoutBindingCurrentTags() {
        RollingMachineRecipe source = new RollingMachineRecipe.Serializer().fromJson(
                new ResourceLocation("advancedrocketrycommunity", "rolling_iron_bars"), JsonParser.parseString("""
                {"type":"advancedrocketrycommunity:rolling","schema_version":1,
                 "ingredient":{"tag":"arce_test:unbound"},"input_count":2,
                 "fluid":{"fluid":"minecraft:water","amount":100},
                 "result":{"item":"minecraft:iron_bars","count":8},"processing_time":100,"energy_per_tick":20}
                """).getAsJsonObject());
        assertFalse(source.available());
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(3, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot"), new ProcessResourceBalance(2, 64),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(100, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", "minecraft:iron_bars"), new ProcessResourceBalance(0, 64)));
        RollingMachineRecipe retained = source.retainedPlan(before);
        assertFalse(retained.hasTagIngredients());
        assertEquals(List.of("minecraft:iron_ingot"), retained.ingredientAlternatives());
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(retained.processDefinition(), before);
        assertTrue(simulation.successful());
        assertEquals(4, simulation.plan().orElseThrow().after().revision());
        assertEquals(0, simulation.plan().orElseThrow().after().balances()
                .get(key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot")).amount());
        assertFalse(source.available(), "recovery did not re-resolve the current tag");
        assertThrows(IllegalArgumentException.class,
                () -> RetainedRecipePlan.ingredient(before, "unknown_channel"));
        ProcessResourceSnapshot unknownItem = new ProcessResourceSnapshot(3, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", "arce_test:missing"), new ProcessResourceBalance(2, 64)));
        assertThrows(com.google.gson.JsonParseException.class, () -> source.retainedPlan(unknownItem));
    }

    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String resource) {
        return new ProcessResourceKey(kind, channel, resource);
    }
}
