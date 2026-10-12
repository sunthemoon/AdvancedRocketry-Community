package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/** Whole immutable catalog generation, without registry callbacks during flight. */
public final class RocketFluidFuelCatalog {
    public record Key(RocketPropulsion.Role role, ResourceLocation fluid) { }
    public record Efficiency(int numerator, int denominator) {
        public Efficiency {
            if (numerator < 1 || numerator > 1_000 || denominator < 1 || denominator > 1_000) {
                throw new IllegalArgumentException("Fluid efficiency outside 1..1000");
            }
        }
        public long debit(long q, long numerator, long denominator) {
            BigInteger top = BigInteger.valueOf(q).multiply(BigInteger.valueOf(numerator))
                    .multiply(BigInteger.valueOf(this.denominator));
            BigInteger bottom = BigInteger.valueOf(denominator).multiply(BigInteger.valueOf(this.numerator));
            return top.add(bottom).subtract(BigInteger.ONE).divide(bottom).longValueExact();
        }
    }
    private final Map<Key, Efficiency> entries;
    private final String signature;
    public RocketFluidFuelCatalog(Map<Key, Efficiency> entries) {
        if (entries.size() > 64) { throw new IllegalArgumentException("Too many rocket Fluid fuels"); }
        this.entries = Map.copyOf(entries);
        StringBuilder canonical = new StringBuilder("rocket-fluid-fuels-1\n");
        entries.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().role().name() + ":" + entry.getKey().fluid()))
                .forEach(entry -> canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        signature = RocketNativeResources.hash(canonical.toString());
    }
    public Efficiency find(RocketPropulsion.Role role, ResourceLocation id) { return entries.get(new Key(role, id)); }
    public String signature() { return signature; }
    public static RocketFluidFuelCatalog defaults() {
        return new RocketFluidFuelCatalog(Map.of(
                new Key(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")), new Efficiency(2, 1),
                new Key(RocketPropulsion.Role.BIPROPELLANT_FUEL, ModIdentity.id("hydrogen")), new Efficiency(1, 1),
                new Key(RocketPropulsion.Role.OXIDIZER, ModIdentity.id("oxygen")), new Efficiency(1, 1),
                new Key(RocketPropulsion.Role.WORKING_FLUID, ModIdentity.id("hydrogen")), new Efficiency(1, 1)));
    }
    public static RocketFluidFuelCatalog parse(Collection<String> documents, Predicate<ResourceLocation> knownSource,
            Map<RocketPropulsion.Role, ? extends Collection<String>> allowed) {
        if (documents.size() > 64 || allowed.values().stream().mapToInt(Collection::size).sum() > 64) {
            throw new IllegalArgumentException("Too many rocket Fluid fuels");
        }
        Map<Key, Efficiency> entries = new HashMap<>();
        for (String text : documents) {
            if (text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 8_192) { throw new IllegalArgumentException("Fluid entry too large"); }
            var parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) { throw new IllegalArgumentException("Fluid entry is not an object"); }
            JsonObject root = parsed.getAsJsonObject();
            if (!root.keySet().equals(Set.of("schema", "role", "fluid", "numerator", "denominator"))
                    || integer(root, "schema") != 1 || !root.get("role").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("role").isString() || !root.get("fluid").isJsonPrimitive()
                    || !root.getAsJsonPrimitive("fluid").isString()) { throw new IllegalArgumentException("Invalid Fluid entry schema"); }
            var role = RocketPropulsion.Role.valueOf(root.get("role").getAsString());
            String name = root.get("fluid").getAsString();
            var id = name.length() <= 128 && name.contains(":") ? ResourceLocation.tryParse(name) : null;
            if (id == null || !id.toString().equals(name) || !knownSource.test(id)) { throw new IllegalArgumentException("Unknown source Fluid"); }
            var key = new Key(role, id);
            if (entries.putIfAbsent(key, new Efficiency(integer(root, "numerator"), integer(root, "denominator"))) != null) {
                throw new IllegalArgumentException("Duplicate role/Fluid definition");
            }
        }
        for (var entry : allowed.entrySet()) {
            if (new HashSet<>(entry.getValue()).size() != entry.getValue().size()) { throw new IllegalArgumentException("Duplicate allowed Fluid"); }
            for (String name : entry.getValue()) {
                if (!RocketPropulsion.Settings.validFluidId(name)
                        || !entries.containsKey(new Key(entry.getKey(), ResourceLocation.tryParse(name)))) {
                    throw new IllegalArgumentException("Configured Fluid lacks an explicit role definition");
                }
            }
        }
        return new RocketFluidFuelCatalog(entries);
    }
    private static int integer(JsonObject root, String name) {
        if (!root.get(name).isJsonPrimitive() || !root.getAsJsonPrimitive(name).isNumber()) {
            throw new IllegalArgumentException("Invalid Fluid integer");
        }
        return root.get(name).getAsBigDecimal().intValueExact();
    }
}
