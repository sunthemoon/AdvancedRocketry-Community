package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import java.util.*;
import org.junit.jupiter.api.Test;

class RocketFluidFuelCatalogTest {
    private static String json(String role, String id, int numerator, int denominator) {
        return "{\"schema\":1,\"role\":\"" + role + "\",\"fluid\":\"" + id + "\",\"numerator\":" + numerator
                + ",\"denominator\":" + denominator + "}";
    }
    private static Map<RocketPropulsion.Role, Collection<String>> allowed() {
        var allowed = new EnumMap<RocketPropulsion.Role, Collection<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) { allowed.put(role, List.of()); }
        return allowed;
    }
    @Test void defaultsUseRealDistinctFluidRolesAndSameHydrogenCanServeTwoRoles() {
        var catalog = RocketFluidFuelCatalog.defaults();
        assertEquals(new RocketFluidFuelCatalog.Efficiency(2, 1), catalog.find(RocketPropulsion.Role.MONO, ModIdentity.id("rocket_fuel")));
        assertEquals(new RocketFluidFuelCatalog.Efficiency(1, 1), catalog.find(RocketPropulsion.Role.BIPROPELLANT_FUEL, ModIdentity.id("hydrogen")));
        assertEquals(new RocketFluidFuelCatalog.Efficiency(1, 1), catalog.find(RocketPropulsion.Role.WORKING_FLUID, ModIdentity.id("hydrogen")));
        assertNull(catalog.find(RocketPropulsion.Role.OXIDIZER, ModIdentity.id("hydrogen")));
    }
    @Test void completeGenerationIsOrderIndependentAndEfficiencyArithmeticUsesCeiling() {
        var documents = new ArrayList<>(List.of(json("MONO", "test:fuel", 3, 2), json("OXIDIZER", "test:oxygen", 1, 1)));
        var allowed = allowed(); allowed.put(RocketPropulsion.Role.MONO, List.of("test:fuel"));
        allowed.put(RocketPropulsion.Role.OXIDIZER, List.of("test:oxygen"));
        var first = RocketFluidFuelCatalog.parse(documents, id -> true, allowed);
        Collections.reverse(documents);
        assertEquals(first.signature(), RocketFluidFuelCatalog.parse(documents, id -> true, allowed).signature());
        assertEquals(41, first.find(RocketPropulsion.Role.MONO,
                net.minecraft.resources.ResourceLocation.tryParse("test:fuel")).debit(101, 3, 5));
    }
    @Test void unknownFieldsDuplicateRolesUnknownFluidsAndUnqualifiedIdsRejectWholeGeneration() {
        String valid = json("MONO", "test:fuel", 1, 1);
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(valid, valid), id -> true, allowed()));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(valid), id -> false, allowed()));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(json("MONO", "fuel", 1, 1)), id -> true, allowed()));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(valid.replace("}", ",\"extra\":1}")), id -> true, allowed()));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(valid.replace("\"schema\":1", "\"schema\":2")), id -> true, allowed()));
    }
    @Test void limitsAndConfiguredListsAreValidatedBeforePublishing() {
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(Collections.nCopies(65,
                json("MONO", "test:fuel", 1, 1)), id -> true, allowed()));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(" ".repeat(8_193)), id -> true, allowed()));
        for (int invalid : new int[] {0, 1_001}) {
            assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(json("MONO", "test:fuel", invalid, 1)), id -> true, allowed()));
        }
        var configured = allowed(); configured.put(RocketPropulsion.Role.MONO, List.of("test:missing"));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(), id -> true, configured));
        configured.put(RocketPropulsion.Role.MONO, Collections.nCopies(65, "test:fuel"));
        assertThrows(IllegalArgumentException.class, () -> RocketFluidFuelCatalog.parse(List.of(), id -> true, configured));
    }
}
