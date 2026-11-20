package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class EscrowCargoSettlementFluidTest {
    private static final ResourceLocation WATER = ResourceLocation.tryParse("minecraft:water");
    private static final ResourceLocation LAVA = ResourceLocation.tryParse("minecraft:lava");
    private static final ResourceLocation OTHER = ResourceLocation.tryParse("minecraft:flowing_water");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static Stream<Arguments> scenariosAndTanks() {
        return Arrays.stream(EscrowCargoSettlementCuts.Scenario.values()).flatMap(scenario ->
                Stream.of(false, true).flatMap(load -> Stream.of(false, true).map(empty -> Arguments.of(scenario, load, empty))));
    }

    @ParameterizedTest(name = "{0}, load={1}, empty={2}")
    @MethodSource("scenariosAndTanks")
    void fluidIdentityDependsOnEmptinessAndScenario(EscrowCargoSettlementCuts.Scenario scenario, boolean load, boolean empty) {
        int amount = empty ? 0 : load ? 4_000 : 1_000;
        var expected = scenario == EscrowCargoSettlementCuts.Scenario.SUBSTITUTE ? LAVA : WATER;
        var tank = LoaderTank.of(expected, null, amount);
        assertEquals(empty, tank.isEmpty());
        assertEquals(empty ? null : expected, tank.fluid());
        assertTrue(EscrowCargoSettlementCuts.releasedDeviceFluidMatches(scenario, tank));
        assertTrue(EscrowCargoSettlementCuts.releasedDeviceFluidMatches(scenario, LoaderTank.decode(tank.encode())));
        if (empty) {
            assertSame(LoaderTank.EMPTY, tank);
            assertTrue(tank.encode().isEmpty(), "An empty native tank has no FluidName field");
        } else {
            for (var wrong : List.of(expected.equals(WATER) ? LAVA : WATER, OTHER)) {
                assertFalse(EscrowCargoSettlementCuts.releasedDeviceFluidMatches(scenario, LoaderTank.of(wrong, null, amount)),
                        "A wrong non-empty Fluid must not pass: " + wrong);
            }
        }
    }

    static Stream<Arguments> preparedReleaseCases() {
        return Stream.of("BIND_BEFORE", "BIND_AFTER", "O3J_BEFORE", "O3J_AFTER", "BIND_VETO").flatMap(suffix ->
                Stream.of(false, true).map(load -> Arguments.of((load ? "FS_LOAD_" : "FS_") + suffix, load)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("preparedReleaseCases")
    void preparedSurvivorsKeepTheirStartingTankIdentity(String name, boolean load) {
        var scenario = EscrowCargoSettlementCuts.CASES.get(name).scenario();
        assertTrue(scenario.prepared());
        assertFalse(scenario.applied());
        var tank = load ? LoaderTank.of(WATER, null, 5_000) : LoaderTank.EMPTY;
        assertTrue(EscrowCargoSettlementCuts.releasedDeviceFluidMatches(scenario, tank));
        assertEquals(5_000, (load ? 0 : 5_000) + tank.amount(), "Prepared releases still conserve all 5,000 mB");
    }
}
