package io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceAlgorithms;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-057 sections 1, 2 and 4 against the C10 reference vectors ({@code examples.json}, {@code black_hole}). */
final class BlackHoleTest {
    private static final Predicate<ResourceLocation> VANILLA = id -> id.getNamespace().equals("minecraft");
    private static final ResourceLocation COBBLESTONE = ResourceLocation.tryBuild("minecraft", "cobblestone");
    private static final ResourceLocation STICK = ResourceLocation.tryBuild("minecraft", "stick");

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void theBurnStateMachineMatchesTheReferenceVectors() {
        assertBurn(List.of(STICK), Map.of(), 2_000_000, 0, 500, 100, 10, 0, 5_000, 490, 1);
        assertBurn(List.of(COBBLESTONE, COBBLESTONE, COBBLESTONE), Map.of(COBBLESTONE, 1), 2_000_000, 0, 500, 100, 5,
                0, 1_500, 0, 3);
        assertBurn(List.of(STICK), Map.of(), 1_200, 0, 500, 100, 5, 0, 1_000, 498, 1);
        assertBurn(List.of(STICK), Map.of(), 1_200, 0, 500, 100, 4, 100, 1_100, 497, 1);
        assertBurn(List.of(STICK), Map.of(), 500, 500, 500, 100, 3, 0, 500, 0, 0);
        assertBurn(List.of(STICK), Map.of(), 2_000_000, 0, 500, 10, 2, 0, 100, 498, 1);
    }

    @Test
    void theRateIsFixedWhenAnItemStartsAndBoundedBy8192() {
        BlackHoleBurn.Step first = BlackHoleBurn.tick(new BlackHoleBurn.State(0, 0, 0), true, () -> 10, 2_000_000,
                2_048, 400);
        assertEquals(8_192, first.state().rate(), "2,048 FE at 400 % is the 8,192 FE ceiling");
        BlackHoleBurn.Step later = BlackHoleBurn.tick(first.state(), true, () -> 10, 2_000_000, 2_048, 10);
        assertEquals(8_192, later.state().rate(), "a percent change waits for the next item");
        assertTrue(!later.consumed());
    }

    @Test
    void codecsAreStrictAndVersionTheRawBytes() {
        byte[] raw = bytes("{\"schema_version\": 1, \"body\": \"advancedrocketrycommunity:cygnus_x1\","
                + " \"output_fe_per_tick\": 500, \"fuel_table\": \"advancedrocketrycommunity:default\"}");
        SingularityProfile profile = BlackHoleCodec.decodeSingularity(ModIdentity.id("cygnus_x1"), raw);
        assertEquals(500, profile.outputFePerTick());
        assertEquals(ResourceAlgorithms.sha256Hex16(raw), profile.version());
        for (String bad : List.of(
                "{\"schema_version\": 1, \"body\": \"a:b\", \"output_fe_per_tick\": 0, \"fuel_table\": \"a:c\"}",
                "{\"schema_version\": 1, \"body\": \"a:b\", \"output_fe_per_tick\": 2049, \"fuel_table\": \"a:c\"}",
                "{\"schema_version\": 2, \"body\": \"a:b\", \"output_fe_per_tick\": 5, \"fuel_table\": \"a:c\"}",
                "{\"schema_version\": 1, \"body\": \"a:b\", \"output_fe_per_tick\": 5}",
                "{\"schema_version\": 1, \"body\": \"a:b\", \"output_fe_per_tick\": 5, \"fuel_table\": \"a:c\", \"x\": 1}")) {
            assertThrows(RuntimeException.class, () -> BlackHoleCodec.decodeSingularity(ModIdentity.id("x"), bytes(bad)),
                    bad);
        }
        BlackHoleFuelTable table = BlackHoleCodec.decodeFuelTable(ModIdentity.id("default"), bytes(
                "{\"schema_version\": 1, \"default_burn_ticks\": 500, \"entries\": ["
                        + "{\"item\": \"minecraft:stone\", \"burn_ticks\": 1}]}"), VANILLA);
        assertEquals(1, table.burnTicks(ResourceLocation.tryBuild("minecraft", "stone")));
        assertEquals(500, table.burnTicks(STICK));
        for (String bad : List.of(
                "{\"schema_version\": 1, \"default_burn_ticks\": 0, \"entries\": []}",
                "{\"schema_version\": 1, \"default_burn_ticks\": 72001, \"entries\": []}",
                "{\"schema_version\": 1, \"default_burn_ticks\": 5, \"entries\": [{\"item\": \"mod:x\", \"burn_ticks\": 1}]}",
                "{\"schema_version\": 1, \"default_burn_ticks\": 5, \"entries\": [{\"item\": \"minecraft:a\", "
                        + "\"burn_ticks\": 1}, {\"item\": \"minecraft:a\", \"burn_ticks\": 2}]}",
                "{\"schema_version\": 1, \"default_burn_ticks\": 5, \"entries\": [{\"item\": \"minecraft:a\"}]}")) {
            assertThrows(RuntimeException.class, () -> BlackHoleCodec.decodeFuelTable(ModIdentity.id("x"), bytes(bad),
                    VANILLA), bad);
        }
        String big = "{\"schema_version\": 1, \"default_burn_ticks\": 5, \"entries\": []}" + " ".repeat(8 * 1024);
        assertThrows(IllegalArgumentException.class, () -> BlackHoleCodec.decodeFuelTable(ModIdentity.id("x"),
                bytes(big), VANILLA));
    }

    @Test
    void theSetIsCrossCheckedAndAppliesOnlyToOrbitableNonLandableBodies() {
        BlackHoleFuelTable fuel = new BlackHoleFuelTable(ModIdentity.id("default"), 500, Map.of(), "0000000000000000");
        SingularityProfile star = profile("star", StarSystemContent.TAU_CETI);
        SingularityProfile planet = profile("planet", StarSystemContent.TAU_CETI_E);
        SingularityProfile moon = profile("moon", CelestialIds.MOON_ID);
        BlackHoleData data = BlackHoleData.create(List.of(star, planet, moon), List.of(fuel)).result().orElseThrow();
        CelestialCatalog catalog = catalog();
        assertEquals(Optional.of(planet), data.at(StarSystemContent.TAU_CETI_E, catalog),
                "an orbitable, not landable body applies");
        assertEquals(Optional.empty(), data.at(StarSystemContent.TAU_CETI, catalog), "a star is not orbitable");
        assertEquals(Optional.empty(), data.at(CelestialIds.MOON_ID, catalog), "a landable body never applies");
        assertEquals(Optional.empty(), data.at(ModIdentity.id("gone"), catalog));
        assertTrue(BlackHoleData.create(List.of(star, profile("again", StarSystemContent.TAU_CETI)), List.of(fuel))
                .error().isPresent(), "one profile per body");
        assertTrue(BlackHoleData.create(List.of(new SingularityProfile(ModIdentity.id("x"), StarSystemContent.TAU_CETI,
                500, ModIdentity.id("missing"), "0000000000000000")), List.of(fuel)).error().isPresent(),
                "a missing fuel table");
        Map<ResourceLocation, byte[]> files = new LinkedHashMap<>();
        files.put(ModIdentity.id("x"), bytes("{}"));
        assertTrue(BlackHoleDataReloadListener.build(files, Map.of(), List.of(), VANILLA).error().isPresent(),
                "one bad file rejects the whole reload");
    }

    private static void assertBurn(List<ResourceLocation> fuel, Map<ResourceLocation, Integer> burnTicks, int capacity,
                                   int energy, int output, int percent, int ticks, int extractPerTick,
                                   int expectedEnergy, int expectedRemaining, int expectedConsumed) {
        BlackHoleFuelTable table = new BlackHoleFuelTable(ModIdentity.id("t"), 500, burnTicks, "0000000000000000");
        Deque<ResourceLocation> slots = new ArrayDeque<>(fuel);
        BlackHoleBurn.State state = new BlackHoleBurn.State(energy, 0, 0);
        int consumed = 0;
        for (int tick = 0; tick < ticks; tick++) {
            BlackHoleBurn.Step step = BlackHoleBurn.tick(state, !slots.isEmpty(), () -> table.burnTicks(slots.peek()),
                    capacity, output, percent);
            if (step.consumed()) {
                slots.pop();
                consumed++;
            }
            state = step.state();
            int extracted = Math.min(extractPerTick, state.energy());
            state = new BlackHoleBurn.State(state.energy() - extracted, state.remaining(), state.rate());
        }
        assertEquals(expectedEnergy, state.energy());
        assertEquals(expectedRemaining, state.remaining());
        assertEquals(expectedConsumed, consumed);
    }

    private static SingularityProfile profile(String name, ResourceLocation body) {
        return new SingularityProfile(ModIdentity.id(name), body, 500, ModIdentity.id("default"), "0000000000000000");
    }

    private static CelestialCatalog catalog() {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(PlanetaryContent.definitions());
        bodies.addAll(StarSystemContent.definitions());
        return CelestialCatalog.create(bodies).getOrThrow(false, message -> {
            throw new AssertionError(message);
        });
    }

    private static byte[] bytes(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
