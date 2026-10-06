package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ClassicRecipeRowsTest {
    @Test void exactImmutableRecordComponentsAreTheFrozenSurface() {
        assertEquals(List.of("canonicalIngredientJson", "count"), components(ClassicItemInput.class));
        assertEquals(List.of("itemId", "count"), components(ClassicItemOutput.class));
        assertEquals(List.of("fluidId", "amount"), components(ClassicFluidRow.class));
    }

    @Test void itemAndTagRowsRetainCanonicalTextWithoutRegistryOrBinding() {
        for (String json : List.of("{\"item\":\"unregistered:input\"}",
                "{\"tag\":\"unbound:input\"}", "{\"item\":\"stone\"}")) {
            var row = new ClassicItemInput(json, 64);
            assertEquals(json, row.canonicalIngredientJson());
            assertEquals(64, row.count());
            assertEquals(row, new ClassicItemInput(json, 64));
        }
    }

    @Test void boundedAlternativesPreserveOrderAndDoNotExpandTags() {
        String entry = "{\"tag\":\"unbound:input\"}";
        String maximum = "[" + String.join(",", Collections.nCopies(32, entry)) + "]";
        assertEquals(maximum, new ClassicItemInput(maximum, 1).canonicalIngredientJson());
        rejects("[" + String.join(",", Collections.nCopies(33, entry)) + "]");
        rejects("[]");
        String mixed = "[{\"item\":\"unknown:b\"},{\"tag\":\"unknown:a\"}]";
        assertEquals(mixed, new ClassicItemInput(mixed, 1).canonicalIngredientJson());
    }

    @Test void bothItemCountsHaveExactBoundaries() {
        String json = "{\"item\":\"unknown:input\"}";
        var id = new ResourceLocation("unknown:output");
        for (int count : new int[] {1, 64}) {
            assertEquals(count, new ClassicItemInput(json, count).count());
            assertEquals(count, new ClassicItemOutput(id, count).count());
        }
        for (int count : new int[] {Integer.MIN_VALUE, -1, 0, 65, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicItemInput(json, count));
            assertThrows(IllegalArgumentException.class, () -> new ClassicItemOutput(id, count));
        }
    }

    @Test void fluidAmountHasExactBoundariesWithoutResolvingAnActualFluid() {
        var id = new ResourceLocation("unknown:fluid");
        for (int amount : new int[] {1, 16_000}) {
            assertEquals(id, new ClassicFluidRow(id, amount).fluidId());
            assertEquals(amount, new ClassicFluidRow(id, amount).amount());
        }
        for (int amount : new int[] {Integer.MIN_VALUE, -1, 0, 16_001, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new ClassicFluidRow(id, amount));
        }
    }

    @Test void resourceIdsAreBoundedAndNonNull() {
        var maximum = new ResourceLocation("x:" + "a".repeat(126));
        var over = new ResourceLocation("x:" + "a".repeat(127));
        assertEquals(maximum, new ClassicItemOutput(maximum, 1).itemId());
        assertEquals(maximum, new ClassicFluidRow(maximum, 1).fluidId());
        assertThrows(IllegalArgumentException.class, () -> new ClassicItemOutput(over, 1));
        assertThrows(IllegalArgumentException.class, () -> new ClassicFluidRow(over, 1));
        assertThrows(NullPointerException.class, () -> new ClassicItemInput(null, 1));
        assertThrows(NullPointerException.class, () -> new ClassicItemOutput(null, 1));
        assertThrows(NullPointerException.class, () -> new ClassicFluidRow(null, 1));
        rejects("{\"tag\":\"x:" + "a".repeat(127) + "\"}");
        assertNotNull(new ClassicItemInput("{\"tag\":\"" + maximum + "\"}", 1));
    }

    @Test void nonCanonicalAndTrailingJsonAreRejectedRatherThanRewritten() {
        for (String json : List.of(" {\"tag\":\"x:a\"}", "{\"tag\": \"x:a\"}",
                "{\"tag\":\"x:a\"} ", "{\"tag\":\"x:a\"}{}", "{\"tag\":\"x:\\u0061\"}")) {
            rejects(json);
        }
    }

    @Test void malformedOrExtendedIngredientGrammarIsRefused() {
        for (String json : List.of("", "null", "1", "true", "\"x:a\"", "{}",
                "{\"nbt\":\"x:a\"}", "{\"item\":1}", "{\"tag\":null}",
                "{\"tag\":\"INVALID\"}", "{\"item\":\"x:a\",\"item\":\"x:b\"}",
                "{\"item\":\"x:a\",\"tag\":\"x:b\"}", "[{\"tag\":\"x:a\"},]",
                "{tag:'x:a'}", "/* comment */{\"tag\":\"x:a\"}", "[[{\"tag\":\"x:a\"}]]")) {
            rejects(json);
        }
    }

    @Test void deepAndOversizedTextIsRejectedWithoutRecursiveParsing() {
        rejects("[".repeat(2_000) + "0" + "]".repeat(2_000));
        rejects("{\"tag\":" + "[".repeat(1_000) + "0" + "]".repeat(1_000) + "}");
        rejects(" ".repeat(4_096));
        rejects(" ".repeat(4_097));
        rejects("{\"tag\":\"x:" + "a".repeat(4_096) + "\"}");
    }

    private static void rejects(String json) {
        assertThrows(IllegalArgumentException.class, () -> new ClassicItemInput(json, 1));
    }

    private static List<String> components(Class<?> type) {
        assertTrue(type.isRecord());
        return Arrays.stream(type.getRecordComponents()).map(component -> component.getName()).toList();
    }
}
