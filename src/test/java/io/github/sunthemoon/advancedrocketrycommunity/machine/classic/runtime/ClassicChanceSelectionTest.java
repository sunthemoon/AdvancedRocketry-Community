package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClassicChanceSelectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private ClassicChanceOutput candidate(String item, int count, int weight) { return new ClassicChanceOutput(ResourceLocation.tryParse(item), count, weight); }
    @Test void seedAndOutcomeAreDeterministicAndUseAllIdentityFields() {
        UUID machine = UUID.fromString("d5aab038-abd0-42f8-83d4-0d40cb336fa8");
        long seed = ClassicChanceSelection.seed(machine, "a".repeat(64), 1);
        assertEquals(seed, ClassicChanceSelection.seed(machine, "a".repeat(64), 1));
        assertNotEquals(seed, ClassicChanceSelection.seed(machine, "a".repeat(64), 2));
        assertNotEquals(seed, ClassicChanceSelection.seed(machine, "b".repeat(64), 1));
        assertNotEquals(seed, ClassicChanceSelection.seed(new UUID(0, 1), "a".repeat(64), 1));
        var candidates = C16cMachineDataTest.rows("centrifuging_enriched_lava").chanceOutputs();
        assertEquals(ClassicChanceSelection.choose(candidates, seed, id -> 64), ClassicChanceSelection.choose(candidates, seed, id -> 64));
        assertThrows(IllegalArgumentException.class, () -> ClassicChanceSelection.seed(machine, "a".repeat(64), 0));
    }
    @Test void fourSuccessesStopBeforeRollingLaterCandidatesAndMergingHonorsStackLimits() {
        var candidates = List.of(candidate("minecraft:iron_nugget", 40, 100), candidate("minecraft:iron_nugget", 40, 100),
                candidate("minecraft:iron_nugget", 40, 100), candidate("minecraft:iron_nugget", 40, 100), candidate("minecraft:gold_nugget", 1, 100));
        var calls = new AtomicInteger();
        var result = ClassicChanceSelection.choose(candidates, () -> { calls.incrementAndGet(); return 100; }, id -> 64);
        assertEquals(4, calls.get()); assertEquals(List.of(64, 64, 32), result.stream().map(ClassicItemOutput::count).toList());
        assertTrue(result.stream().allMatch(row -> row.itemId().toString().equals("minecraft:iron_nugget")));
        assertThrows(IllegalArgumentException.class, () -> ClassicChanceSelection.choose(candidates, () -> 0, id -> 64));
        assertThrows(IllegalArgumentException.class, () -> ClassicChanceSelection.choose(candidates, () -> 1, id -> 16));
    }
    @Test void independentRollBoundaryIsInclusiveAndIridiumCapIsExactly25Of10000Pairs() {
        var candidates = C16cMachineDataTest.rows("centrifuging_enriched_lava").chanceOutputs(); int iridium = 0;
        for (int gold = 1; gold <= 100; gold++) {
            for (int rare = 1; rare <= 100; rare++) {
                int[] rolls = {100, 100, 100, gold, rare}; var index = new AtomicInteger();
                var selected = ClassicChanceSelection.choose(candidates, () -> rolls[index.getAndIncrement()], id -> 64);
                assertTrue(selected.size() >= 3 && selected.size() <= 4);
                if (gold <= 75) { assertEquals(4, index.get()); assertEquals("minecraft:gold_nugget", selected.get(3).itemId().toString()); }
                else { assertEquals(5, index.get()); }
                if (selected.stream().anyMatch(row -> row.itemId().getPath().equals("iridium_nugget"))) { iridium++; }
            }
        }
        assertEquals(25, iridium);
    }
    @Test void fixedMachineTenThousandBatchSeedsKeepThreeGuaranteedNuggetsAndBoundedRareRate() {
        var candidates = C16cMachineDataTest.rows("centrifuging_enriched_lava").chanceOutputs(); int gold = 0, rare = 0;
        UUID machine = new UUID(123, 456);
        for (long ordinal = 1; ordinal <= 10_000; ordinal++) {
            var selected = ClassicChanceSelection.choose(candidates, ClassicChanceSelection.seed(machine, "a".repeat(64), ordinal), id -> 64);
            assertEquals(candidates.subList(0, 3).stream().map(ClassicChanceOutput::itemId).toList(), selected.subList(0, 3).stream().map(ClassicItemOutput::itemId).toList());
            assertTrue(selected.size() <= 4);
            if (selected.stream().anyMatch(row -> row.itemId().getPath().equals("gold_nugget"))) { gold++; }
            if (selected.stream().anyMatch(row -> row.itemId().getPath().equals("iridium_nugget"))) { rare++; }
        }
        assertTrue(gold >= 7_300 && gold <= 7_700, "Gold count: " + gold);
        assertTrue(rare >= 8 && rare <= 45, "Iridium count: " + rare);
    }
}
