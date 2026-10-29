package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.material.press.SmallPlatePressRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.common.crafting.conditions.ICondition;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

/** W193 (W179-F4): the mod's recipe serializers refuse into one bounded summary per data-pack reload. */
class RecipeRefusalLedgerTest {
    private static final List<String> CLASSIC_TYPES = List.of("lathe", "arc_furnace", "cutting", "crystallizing",
            "chemical_reacting", "laser_etching", "centrifuging");
    private static final String VALID_LATHE = "{\"type\":\"advancedrocketrycommunity:lathe\",\"schema_version\":1,"
            + "\"item_inputs\":[{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"count\":1}],\"item_outputs\":"
            + "[{\"item\":\"minecraft:iron_nugget\",\"count\":1}],\"fluid_inputs\":[],\"fluid_outputs\":[],"
            + "\"processing_time\":100,\"energy_per_tick\":10}";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void aSummaryCountsTheReasonsAndNamesTheSmallestIds() {
        RecipeRefusalLedger ledger = new RecipeRefusalLedger();
        ledger.record(id("c"), new IllegalArgumentException("Recipe text bound"));
        ledger.record(id("a"), new IllegalArgumentException("Recipe row type/count"));
        ledger.record(id("b"), new JsonSyntaxException("Invalid Electrolyzer recipe arce_test:b: bad",
                new IllegalArgumentException("Recipe row type/count")));
        assertEquals(3, ledger.count());
        assertEquals("Refused 3 data-pack recipes of this mod; none is registered. Reasons: Recipe row type/count=2, "
                + "Recipe text bound=1. First 3 by id: arce_test:a (Recipe row type/count), arce_test:b (Recipe row "
                + "type/count), arce_test:c (Recipe text bound)", ledger.summary().orElseThrow());
        assertTrue(new RecipeRefusalLedger().summary().isEmpty(), "a reload without a refusal logs nothing");
    }

    @Test void reasonsIdsAndTheSummaryStayWithinTheirBoundsForTenThousandRefusals() {
        RecipeRefusalLedger ledger = new RecipeRefusalLedger();
        for (int index = 0; index < 10_000; index++) {
            ledger.record(new ResourceLocation("arce_test", String.format("%05d_", index) + "x".repeat(300)),
                    new IllegalArgumentException("reason " + (index % 40) + " " + "y".repeat(500)));
        }
        String summary = ledger.summary().orElseThrow();
        assertTrue(summary.length() <= RecipeRefusalLedger.MAX_SUMMARY_CHARS, summary.length() + " characters");
        assertTrue(summary.startsWith("Refused 10000 data-pack recipes of this mod"), summary);
        assertTrue(summary.endsWith(" ..."), "parts that do not fit end with an ellipsis: " + summary);
        assertEquals(10_000, ledger.count());

        // Sixteen reasons by name and the rest as "other"; ids cut to 96 characters and only the smallest eight.
        RecipeRefusalLedger few = new RecipeRefusalLedger();
        for (int index = 0; index < 40; index++) {
            few.record(new ResourceLocation("arce_test", String.format("%02d_", index) + "x".repeat(300)),
                    new IllegalArgumentException("r" + String.format("%02d", index)));
        }
        String capped = summary(few);
        for (int index = 0; index < RecipeRefusalLedger.MAX_REASONS; index++) {
            assertTrue(capped.contains("r" + String.format("%02d", index) + "=1"), capped);
        }
        assertFalse(capped.contains("r16=1"), capped);
        assertTrue(capped.contains(RecipeRefusalLedger.OTHER + "=24"), capped);
        RecipeRefusalLedger idsOnly = new RecipeRefusalLedger();
        for (int index = 9; index >= 0; index--) {
            idsOnly.record(id("id" + index), new IllegalArgumentException("same"));
        }
        assertEquals("Refused 10 data-pack recipes of this mod; none is registered. Reasons: same=10. First 8 by id: "
                + "arce_test:id0 (same), arce_test:id1 (same), arce_test:id2 (same), arce_test:id3 (same), "
                + "arce_test:id4 (same), arce_test:id5 (same), arce_test:id6 (same), arce_test:id7 (same)",
                idsOnly.summary().orElseThrow());
        RecipeRefusalLedger cut = new RecipeRefusalLedger();
        cut.record(new ResourceLocation("arce_test", "x".repeat(300)), new IllegalArgumentException("z".repeat(300)));
        String one = cut.summary().orElseThrow();
        assertTrue(one.contains("arce_test:" + "x".repeat(RecipeRefusalLedger.MAX_ID_CHARS - 10) + " ("), one);
        assertFalse(one.contains("x".repeat(RecipeRefusalLedger.MAX_ID_CHARS - 9)), one);
        assertTrue(one.contains("z".repeat(RecipeRefusalLedger.MAX_REASON_CHARS) + "=1"), one);
        assertFalse(one.contains("z".repeat(RecipeRefusalLedger.MAX_REASON_CHARS + 1)), one);
    }

    @Test void theReasonIsTheCodecsOwnMessageWithoutTheRecipeId() {
        ResourceLocation id = id("probe");
        assertEquals("recipe has missing or unexpected top-level fields", RecipeRefusalLedger.reason(id,
                new JsonSyntaxException("Invalid Electrolyzer recipe arce_test:probe: recipe has missing or "
                        + "unexpected top-level fields", new IllegalArgumentException(
                                "recipe has missing or unexpected top-level fields"))));
        assertEquals("Recipe number is not an exact int", RecipeRefusalLedger.reason(id, new IllegalArgumentException(
                "Recipe number is not an exact int", new ArithmeticException("Rounding necessary"))));
        assertEquals("Invalid Precision Assembler recipe <id>", RecipeRefusalLedger.reason(id,
                new JsonSyntaxException("Invalid Precision Assembler recipe arce_test:probe",
                        new NullPointerException())));
        assertEquals("IllegalArgumentException", RecipeRefusalLedger.reason(id, new IllegalArgumentException()));
    }

    @Test void everySerializerRecordsAndSkipsOnlyUnderAnOpenLedger() {
        Map<String, RecipeSerializer<?>> serializers = serializers();
        ICondition.IContext context = context();
        for (Map.Entry<String, RecipeSerializer<?>> serializer : serializers.entrySet()) {
            JsonObject invalid = JsonParser.parseString("{\"type\":\"" + serializer.getKey() + "\",\"schema_version\":1}")
                    .getAsJsonObject();
            // Without a ledger for the context (data generation, direct calls, IContext.EMPTY) it throws as before.
            assertThrows(RuntimeException.class, () -> serializer.getValue().fromJson(id("w193"), invalid, context),
                    serializer.getKey());
            assertThrows(RuntimeException.class,
                    () -> serializer.getValue().fromJson(id("w193"), invalid, ICondition.IContext.EMPTY));
        }
        RecipeRefusalLedger ledger = RecipeRefusalLedger.open(context);
        try {
            int index = 0;
            for (Map.Entry<String, RecipeSerializer<?>> serializer : serializers.entrySet()) {
                JsonObject invalid = JsonParser.parseString("{\"type\":\"" + serializer.getKey()
                        + "\",\"schema_version\":1}").getAsJsonObject();
                ResourceLocation id = id(String.format("w193_%02d", index++));
                assertNull(serializer.getValue().fromJson(id, invalid, context), serializer.getKey());
                assertEquals(index, ledger.count(), serializer.getKey());
                // Another reload's context, or none, is not this ledger's.
                assertThrows(RuntimeException.class, () -> serializer.getValue().fromJson(id, invalid, context()));
            }
        } finally {
            List<String> errors = new ArrayList<>();
            RecipeRefusalLedger.flush(context, logger(errors));
            assertEquals(1, errors.size(), "one summary per reload: " + errors);
            assertTrue(errors.get(0).startsWith("Refused 11 data-pack recipes of this mod; none is registered. "
                    + "Reasons: Recipe missing/unknown field=7, recipe has missing or unexpected top-level fields=3, "
                    + "unsupported precision recipe schema or fields=1. First 8 by id: arce_test:w193_00 (Recipe "
                    + "missing/unknown field), "), errors.get(0));
            assertTrue(errors.get(0).length() <= RecipeRefusalLedger.MAX_SUMMARY_CHARS);
        }
        // The flush closed the ledger: a later call throws again, and a second flush logs nothing.
        JsonObject invalid = JsonParser.parseString("{\"type\":\"advancedrocketrycommunity:lathe\"}").getAsJsonObject();
        assertThrows(RuntimeException.class, () -> serializers.get("advancedrocketrycommunity:lathe")
                .fromJson(id("after"), invalid, context));
        List<String> again = new ArrayList<>();
        RecipeRefusalLedger.flush(context, logger(again));
        assertEquals(List.of(), again);
    }

    @Test void validRecipesStillDecodeUnderAnOpenLedgerAndAQuietReloadLogsNothing() {
        ICondition.IContext context = context();
        RecipeRefusalLedger ledger = RecipeRefusalLedger.open(context);
        List<String> errors = new ArrayList<>();
        try {
            assertNotNull(new ClassicMachineRecipe.Serializer().fromJson(id("lathe"),
                    JsonParser.parseString(VALID_LATHE).getAsJsonObject(), context));
            assertNotNull(new RollingMachineRecipe.Serializer().fromJson(id("rolling"), JsonParser.parseString(
                    "{\"type\":\"advancedrocketrycommunity:rolling\",\"schema_version\":1,\"ingredient\":{\"item\":"
                    + "\"minecraft:iron_ingot\"},\"input_count\":2,\"fluid\":{\"fluid\":\"minecraft:water\",\"amount\":"
                    + "100},\"result\":{\"item\":\"minecraft:iron_bars\",\"count\":8},\"processing_time\":100,"
                    + "\"energy_per_tick\":20}").getAsJsonObject(), context));
            assertNotNull(new SmallPlatePressRecipe.Serializer().fromJson(id("press"), JsonParser.parseString(
                    "{\"type\":\"advancedrocketrycommunity:small_plate_press\",\"schema_version\":1,\"ingredient\":"
                    + "{\"item\":\"minecraft:iron_block\"},\"result\":{\"item\":\"minecraft:iron_ingot\",\"count\":4}}")
                    .getAsJsonObject(), context));
            assertNotNull(new PrecisionAssemblerRecipe.Serializer().fromJson(id("precision"), JsonParser.parseString(
                    "{\"type\":\"advancedrocketrycommunity:precision_assembling\",\"schema_version\":1,\"inputs\":"
                    + "[{\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"count\":1},{\"ingredient\":{\"item\":"
                    + "\"minecraft:gold_ingot\"},\"count\":1}],\"outputs\":[{\"item\":"
                    + "\"minecraft:iron_bars\",\"count\":1}],\"processing_time\":20,\"energy_per_tick\":40}")
                    .getAsJsonObject(), context));
            // A valid electrolyzer recipe needs the mod's registered canisters, which this bootstrap lacks.
            assertEquals(0, ledger.count());
        } finally {
            RecipeRefusalLedger.flush(context, logger(errors));
        }
        assertEquals(List.of(), errors, "a reload without a refusal logs nothing");
    }

    @Test void anOversizedTreeIsRefusedByTheBoundedWalkBeforeItIsCopied() {
        JsonObject classic = JsonParser.parseString(VALID_LATHE).getAsJsonObject();
        classic.getAsJsonArray("item_outputs").get(0).getAsJsonObject().addProperty("pad", "x".repeat(32 << 20));
        // The codec's own text bound ("Recipe text bound") would refuse only after json.toString() copied 32 MiB.
        assertEquals("recipe JSON text exceeds its bound", assertThrows(IllegalArgumentException.class,
                () -> new ClassicMachineRecipe.Serializer().fromJson(id("oversized"), classic)).getMessage());
        JsonObject press = JsonParser.parseString("{\"type\":\"advancedrocketrycommunity:small_plate_press\","
                + "\"schema_version\":1,\"ingredient\":{\"item\":\"minecraft:iron_block\"},\"result\":{\"item\":"
                + "\"minecraft:iron_ingot\",\"count\":4}}").getAsJsonObject();
        press.addProperty("pad", "x".repeat(32 << 20));
        RuntimeException refused = assertThrows(RuntimeException.class,
                () -> new SmallPlatePressRecipe.Serializer().fromJson(id("oversized"), press));
        assertEquals("recipe JSON text exceeds its bound", RecipeRefusalLedger.reason(id("oversized"), refused));

        ICondition.IContext context = context();
        RecipeRefusalLedger ledger = RecipeRefusalLedger.open(context);
        try {
            assertNull(new ClassicMachineRecipe.Serializer().fromJson(id("oversized"), classic, context));
            assertNull(new SmallPlatePressRecipe.Serializer().fromJson(id("oversized_press"), press, context));
            assertEquals("Refused 2 data-pack recipes of this mod; none is registered. Reasons: recipe JSON text "
                    + "exceeds its bound=2. First 2 by id: arce_test:oversized (recipe JSON text exceeds its bound), "
                    + "arce_test:oversized_press (recipe JSON text exceeds its bound)", ledger.summary().orElseThrow());
        } finally {
            RecipeRefusalLedger.close(context);
        }
    }

    @Test void otherExceptionsStillPropagateUnderAnOpenLedger() {
        ICondition.IContext context = context();
        RecipeRefusalLedger ledger = RecipeRefusalLedger.open(context);
        try {
            assertThrows(IllegalStateException.class, () -> RecipeRefusalLedger.decode(context, id("bug"), () -> {
                throw new IllegalStateException("a programming error");
            }));
            assertEquals(0, ledger.count());
        } finally {
            RecipeRefusalLedger.close(context);
        }
    }

    private static Map<String, RecipeSerializer<?>> serializers() {
        Map<String, RecipeSerializer<?>> serializers = new LinkedHashMap<>();
        for (String type : CLASSIC_TYPES) {
            String id = "advancedrocketrycommunity:" + type;
            serializers.put(id, new ClassicMachineRecipe.Serializer(id));
        }
        serializers.put("advancedrocketrycommunity:electrolyzing", new ElectrolyzerRecipe.Serializer());
        serializers.put("advancedrocketrycommunity:rolling", new RollingMachineRecipe.Serializer());
        serializers.put("advancedrocketrycommunity:precision_assembling", new PrecisionAssemblerRecipe.Serializer());
        serializers.put("advancedrocketrycommunity:small_plate_press", new SmallPlatePressRecipe.Serializer());
        return serializers;
    }

    private static String summary(RecipeRefusalLedger ledger) {
        String summary = ledger.summary().orElseThrow();
        assertTrue(summary.length() <= RecipeRefusalLedger.MAX_SUMMARY_CHARS, summary);
        return summary;
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("arce_test", path);
    }

    /** A reload's context of its own, compared by identity like Forge's ConditionContext. */
    private static ICondition.IContext context() {
        return new ICondition.IContext() {
            @Override
            public <T> Map<ResourceLocation, Collection<Holder<T>>> getAllTags(ResourceKey<? extends Registry<T>> key) {
                return Collections.emptyMap();
            }
        };
    }

    /** Collects the single-argument ERROR calls that {@link RecipeRefusalLedger#flush} makes. */
    private static Logger logger(List<String> errors) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] {Logger.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("error") && arguments != null && arguments.length == 1) {
                        errors.add(String.valueOf(arguments[0]));
                    }
                    return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                });
    }
}
