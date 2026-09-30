package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Pins ADR-052 sections 3–6 to the accepted reference vectors in docs/work/v1.6.0-preparation/examples.json. */
final class ResourceAlgorithmsTest {
    private static final Path EXAMPLES = Path.of("docs/work/v1.6.0-preparation/examples.json");

    @Test
    void splitMix64MatchesThePublishedAndReferenceOutputs() throws Exception {
        JsonObject examples = examples();
        SplitMix64 zero = new SplitMix64(0L);
        for (JsonElement expected : examples.getAsJsonArray("splitmix64_seed0")) {
            assertEquals(expected.getAsString(), hex(zero.next()));
        }
        for (JsonElement element : examples.getAsJsonArray("splitmix64")) {
            JsonObject vector = element.getAsJsonObject();
            SplitMix64 stream = new SplitMix64(parse(vector.get("seed").getAsString()));
            for (JsonElement expected : vector.getAsJsonArray("outputs")) {
                assertEquals(expected.getAsString(), hex(stream.next()));
            }
        }
        for (JsonElement element : examples.getAsJsonArray("bounded")) {
            JsonObject vector = element.getAsJsonObject();
            SplitMix64 stream = new SplitMix64(parse(vector.get("seed").getAsString()));
            long n = vector.get("n").getAsLong();
            for (JsonElement expected : vector.getAsJsonArray("values")) {
                assertEquals(expected.getAsLong(), stream.bounded(n));
            }
        }
        assertEquals(0x5355525645593031L, SplitMix64.SURVEY01);
        assertThrows(IllegalArgumentException.class, () -> new SplitMix64(1L).bounded(0L));
        assertThrows(IllegalArgumentException.class, () -> new SplitMix64(1L).bounded((1L << 31) + 1L));
    }

    @Test
    void everySurveyVectorMatchesTypesSeedsYieldsAndFingerprint() throws Exception {
        JsonObject examples = examples();
        int checked = 0;
        for (JsonElement element : examples.getAsJsonArray("survey")) {
            JsonObject vector = element.getAsJsonObject();
            List<AsteroidType> types = new ArrayList<>();
            for (JsonElement raw : examples.getAsJsonArray(vector.get("types").getAsString())) {
                types.add(type(raw.getAsJsonObject()));
            }
            List<AsteroidType> candidates = ResourceAlgorithms.candidates(types,
                    ResourceLocation.tryParse(vector.get("system").getAsString()));
            assertEquals(vector.get("candidate_fingerprint").getAsString(), ResourceAlgorithms.fingerprint(candidates));
            List<ResourceAlgorithms.GeneratedInstance> instances = ResourceAlgorithms.survey(
                    parse(vector.get("mission_seed").getAsString()), candidates, vector.get("count").getAsInt());
            JsonArray expected = vector.getAsJsonArray("instances");
            assertEquals(expected.size(), instances.size());
            for (int index = 0; index < expected.size(); index++) {
                JsonArray instance = expected.get(index).getAsJsonArray();
                assertEquals(instance.get(0).getAsString(), instances.get(index).type().id().toString());
                assertEquals(instance.get(1).getAsString(), hex(instances.get(index).seed()));
                assertEquals(entries(instance.get(2).getAsJsonArray()), instances.get(index).yield());
            }
            checked++;
        }
        assertEquals(4, checked);
    }

    @Test
    void mixedNamespacesSortByTheFullIdString() throws Exception {
        List<AsteroidType> types = new ArrayList<>();
        for (JsonElement raw : examples().getAsJsonArray("mixed_namespace_types")) {
            types.add(type(raw.getAsJsonObject()));
        }
        List<AsteroidType> sorted = ResourceAlgorithms.candidates(types, ResourceLocation.tryParse("minecraft:overworld"));
        assertTrue(sorted.get(0).id().toString().compareTo(sorted.get(1).id().toString()) < 0);
        // ResourceLocation.compareTo compares the path first, so it would give the other order here.
        assertTrue(sorted.get(0).id().compareTo(sorted.get(1).id()) > 0);
    }

    @Test
    void truncationDurationsAndGasMatchTheVectors() throws Exception {
        JsonObject examples = examples();
        for (JsonElement element : examples.getAsJsonArray("truncation")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(entries(vector.getAsJsonArray("delivered")),
                    ResourceAlgorithms.truncate(entries(vector.getAsJsonArray("entries")), vector.get("cargo").getAsInt()));
        }
        for (JsonElement element : examples.getAsJsonArray("asteroid_duration")) {
            JsonObject vector = element.getAsJsonObject();
            assertEquals(vector.get("duration").getAsInt(), ResourceAlgorithms.asteroidDuration(
                    vector.get("time_multiplier_pct").getAsInt(), vector.get("config_pct").getAsInt(),
                    vector.get("rating").getAsInt()));
        }
        for (JsonElement element : examples.getAsJsonArray("gas")) {
            JsonObject vector = element.getAsJsonObject();
            JsonObject expected = vector.getAsJsonObject("expected");
            assertEquals(new ResourceAlgorithms.GasResult(expected.get("rate").getAsInt(), expected.get("base").getAsInt(),
                            expected.get("amount").getAsInt(), expected.get("duration").getAsInt()),
                    ResourceAlgorithms.gas(vector.get("amount_per_1000_ticks").getAsInt(), vector.get("rating").getAsInt(),
                            vector.get("cargo").getAsInt(), vector.get("config_pct").getAsInt()));
        }
    }

    @Test
    void theLargestYieldStaysWithinTheInstanceBounds() {
        List<AsteroidType.Ore> ores = new ArrayList<>();
        for (int index = 0; index < AsteroidType.MAX_ORES; index++) {
            ores.add(new AsteroidType.Ore(ResourceLocation.tryParse("test:ore_" + index), 1_000));
        }
        AsteroidType largest = new AsteroidType(ResourceLocation.tryParse("test:largest"), 1, List.of(),
                AsteroidType.MAX_MASS, 100, 50, 100, ResourceLocation.tryParse("test:base"), ores, 1_000,
                "0123456789abcdef");
        for (long seed = 0L; seed < 64L; seed++) {
            List<RewardEntry> yield = ResourceAlgorithms.asteroidYield(largest, seed);
            long total = yield.stream().mapToLong(RewardEntry::count).sum();
            assertTrue(yield.size() <= 17 && total >= 1 && total <= AsteroidType.MAX_MASS);
            RewardEntry.validated(yield, 17, 4_096);
        }
    }

    private static AsteroidType type(JsonObject raw) {
        List<ResourceLocation> systems = new ArrayList<>();
        raw.getAsJsonArray("systems").forEach(system -> systems.add(ResourceLocation.tryParse(system.getAsString())));
        List<AsteroidType.Ore> ores = new ArrayList<>();
        raw.getAsJsonArray("ores").forEach(ore -> ores.add(new AsteroidType.Ore(
                ResourceLocation.tryParse(ore.getAsJsonObject().get("item").getAsString()),
                ore.getAsJsonObject().get("weight").getAsInt())));
        return new AsteroidType(ResourceLocation.tryParse(raw.get("id").getAsString()), raw.get("weight").getAsInt(),
                systems, raw.get("mass").getAsInt(), raw.get("mass_variability_pct").getAsInt(),
                raw.get("richness_pct").getAsInt(), raw.get("richness_variability_pct").getAsInt(),
                ResourceLocation.tryParse(raw.get("base_item").getAsString()), ores,
                raw.get("time_multiplier_pct").getAsInt(), raw.get("table_version").getAsString());
    }

    private static List<RewardEntry> entries(JsonArray raw) {
        List<RewardEntry> entries = new ArrayList<>();
        raw.forEach(entry -> entries.add(new RewardEntry(
                ResourceLocation.tryParse(entry.getAsJsonArray().get(0).getAsString()),
                entry.getAsJsonArray().get(1).getAsInt())));
        return entries;
    }

    private static JsonObject examples() throws Exception {
        return JsonParser.parseString(Files.readString(EXAMPLES)).getAsJsonObject();
    }

    private static long parse(String hex) {
        return Long.parseUnsignedLong(hex, 16);
    }

    private static String hex(long value) {
        return HexFormat.of().toHexDigits(value);
    }
}
