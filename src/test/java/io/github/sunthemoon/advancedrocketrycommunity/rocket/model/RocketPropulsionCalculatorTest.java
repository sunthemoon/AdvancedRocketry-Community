package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionComponent.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class RocketPropulsionCalculatorTest {
    private static RocketBlock block(RocketPropulsionComponent kind, int index) {
        return new RocketBlock(new RocketPosition(index, 0, 0),
                new RocketBlockState(ModIdentity.id(kind.path()), Map.of()), null);
    }
    private static List<RocketBlock> blocks(RocketPropulsionComponent... kinds) {
        var result = new ArrayList<RocketBlock>();
        for (var kind : kinds) { result.add(block(kind, result.size())); }
        return result;
    }
    private static RocketPropulsionCalculator.Result calculate(List<RocketBlock> blocks) {
        return calculate(blocks, RocketPropulsion.Settings.DEFAULTS);
    }
    private static RocketPropulsionCalculator.Result calculate(List<RocketBlock> blocks, RocketPropulsion.Settings settings) {
        return RocketPropulsionCalculator.calculate(blocks, state -> host(state.blockId()).metrics(),
                state -> host(state.blockId()), settings);
    }
    @Test void tierTablePreservesBasicAndCapturesAdvancedAndBipropellant() {
        var basic = calculate(blocks(BASIC_MONO, MONO_TANK));
        assertEquals(150, basic.stats().mass()); assertEquals(1_000, basic.stats().thrust());
        assertEquals(1_000, basic.stats().fuelCapacity()); assertEquals(1, basic.propulsion().coefficientNumerator());
        var advanced = calculate(blocks(ADVANCED_MONO, MONO_TANK));
        assertEquals(150, advanced.stats().mass()); assertEquals(5_000, advanced.stats().thrust());
        assertEquals(3, advanced.propulsion().coefficientNumerator()); assertEquals(5, advanced.propulsion().coefficientDenominator());
        var bip = calculate(blocks(BIPROPELLANT, BIPROPELLANT_TANK, OXIDIZER_TANK));
        assertEquals(200, bip.stats().mass()); assertEquals(1_000, bip.stats().thrust());
        assertEquals(1_000, bip.propulsion().primaryCapacity()); assertEquals(1_000, bip.propulsion().oxidizerCapacity());
        var highBip = calculate(blocks(ADVANCED_BIPROPELLANT, BIPROPELLANT_TANK, OXIDIZER_TANK));
        assertEquals(5_000, highBip.stats().thrust()); assertEquals(3, highBip.propulsion().coefficientNumerator());
        assertEquals(5, highBip.propulsion().coefficientDenominator());
    }
    @Test void coefficientsAreThrustWeightedAcrossMixedSameModeTiers() {
        var mixed = calculate(blocks(BASIC_MONO, ADVANCED_MONO, MONO_TANK));
        assertEquals(6_000, mixed.stats().thrust());
        assertEquals(2, mixed.propulsion().coefficientNumerator()); assertEquals(3, mixed.propulsion().coefficientDenominator());
        var bip = calculate(blocks(BIPROPELLANT, ADVANCED_BIPROPELLANT, BIPROPELLANT_TANK, OXIDIZER_TANK));
        assertEquals(mixed.propulsion().coefficientNumerator(), bip.propulsion().coefficientNumerator());
        assertEquals(mixed.propulsion().coefficientDenominator(), bip.propulsion().coefficientDenominator());
    }
    @Test void hostScalingOccursOncePerTierAndRoleAndNeverRetunesBasicMetrics() {
        var settings = RocketPropulsion.Settings.capture(1.3333, 0.1, 1, true, true, true);
        var scaled = calculate(blocks(ADVANCED_MONO, ADVANCED_MONO, MONO_TANK), settings);
        assertEquals((long) (10_000 * 1.3333), scaled.stats().thrust()); assertEquals(100, scaled.stats().fuelCapacity());
        var basic = calculate(blocks(BASIC_MONO, MONO_TANK), settings);
        assertEquals(1_000, basic.stats().thrust()); assertEquals(1_000, basic.stats().fuelCapacity());
    }
    @Test void allComponentOrdersProduceTheSameDescriptorAndRefusal() {
        var kinds = blocks(BIPROPELLANT, ADVANCED_BIPROPELLANT, BIPROPELLANT_TANK, OXIDIZER_TANK);
        var expected = calculate(kinds);
        permute(kinds, 0, order -> assertEquals(expected, calculate(order)));
        permute(blocks(BASIC_MONO, BIPROPELLANT, MONO_TANK, BIPROPELLANT_TANK), 0,
                order -> refusal(RocketValidationCode.MIXED_PROPULSION_MODES, order));
        permute(blocks(BIPROPELLANT, MONO_TANK, OXIDIZER_TANK), 0,
                order -> refusal(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, order));
    }
    @Test void everyModeRejectsWrongHostTankRolesAndMissingTypedTanks() {
        refusal(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, blocks(BASIC_MONO, OXIDIZER_TANK));
        refusal(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, blocks(ADVANCED_MONO, NUCLEAR_TANK));
        refusal(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, blocks(BIPROPELLANT, MONO_TANK));
        refusal(RocketValidationCode.WRONG_PROPULSION_TANK_ROLE, blocks(NUCLEAR, BIPROPELLANT_TANK));
        refusal(RocketValidationCode.MISSING_PROPULSION_TANK, blocks(BIPROPELLANT, BIPROPELLANT_TANK));
        refusal(RocketValidationCode.MISSING_PROPULSION_TANK, blocks(BIPROPELLANT, OXIDIZER_TANK));
        refusal(RocketValidationCode.MISSING_PROPULSION_TANK, blocks(NUCLEAR));
    }
    @Test void aggregateCapacityBoundaryAndOverflowAreRejectedBeforeSnapshotOrExtraction() {
        var legal = new ArrayList<RocketBlock>(); legal.add(block(ADVANCED_MONO, 0));
        for (int i = 1; i <= 512; i++) { legal.add(block(MONO_TANK, i)); }
        assertEquals(2_048_000, calculate(legal, RocketPropulsion.Settings.capture(1, 4, 1, true, true, true)).stats().fuelCapacity());
        legal.add(block(MONO_TANK, 513));
        var refused = assertThrows(RocketSnapshotException.class,
                () -> calculate(legal, RocketPropulsion.Settings.capture(1, 4, 1, true, true, true)));
        assertEquals(RocketValidationCode.PROPULSION_CAPACITY_EXCEEDED, refused.code());
        var raw = blocks(BASIC_MONO, MONO_TANK);
        var overflow = assertThrows(RocketSnapshotException.class, () -> RocketPropulsionCalculator.calculate(raw,
                state -> new RocketBlockMetrics(Long.MAX_VALUE, 1, 0, true, false, false),
                state -> EXTERNAL, RocketPropulsion.Settings.DEFAULTS));
        assertEquals(RocketValidationCode.PROPULSION_AGGREGATE_OVERFLOW, overflow.code());
    }
    @Test void externalIntegratedComponentsAreNeverSplitOrGivenFluidEligibility() {
        var integrated = new RocketBlock(new RocketPosition(5, 0, 0),
                new RocketBlockState(ModIdentity.id("advanced_bipropellant_rocket_motor"), Map.of()), null);
        var externalMetrics = new RocketBlockMetrics(123, 4_321, 987, true, true, true);
        var external = RocketPropulsionCalculator.calculate(List.of(integrated), state -> externalMetrics,
                state -> EXTERNAL, RocketPropulsion.Settings.DEFAULTS);
        assertEquals(RocketPropulsion.Mode.ABSTRACT, external.propulsion().mode());
        assertEquals(4_321, external.stats().thrust()); assertEquals(987, external.stats().fuelCapacity());
        var mono = blocks(ADVANCED_MONO, MONO_TANK); mono.add(integrated);
        var joined = RocketPropulsionCalculator.calculate(mono,
                state -> state.equals(integrated.state()) ? externalMetrics : host(state.blockId()).metrics(),
                state -> state.equals(integrated.state()) ? EXTERNAL : host(state.blockId()), RocketPropulsion.Settings.DEFAULTS);
        assertEquals(RocketPropulsion.Mode.ABSTRACT, joined.propulsion().mode()); assertEquals(9_321, joined.stats().thrust());
        var typed = blocks(BIPROPELLANT, BIPROPELLANT_TANK, OXIDIZER_TANK); typed.add(integrated);
        permute(typed, 0, order -> {
            var refusal = assertThrows(RocketSnapshotException.class, () -> RocketPropulsionCalculator.calculate(order,
                    state -> state.equals(integrated.state()) ? externalMetrics : host(state.blockId()).metrics(),
                    state -> state.equals(integrated.state()) ? EXTERNAL : host(state.blockId()), RocketPropulsion.Settings.DEFAULTS));
            assertEquals(RocketValidationCode.EXTERNAL_COMPONENT_ABSTRACT_ONLY, refusal.code());
        });
        var structure = RocketPropulsionCalculator.calculate(typed,
                state -> state.equals(integrated.state()) ? new RocketBlockMetrics(123, 0, 0, false, true, true) : host(state.blockId()).metrics(),
                state -> state.equals(integrated.state()) ? EXTERNAL : host(state.blockId()), RocketPropulsion.Settings.DEFAULTS);
        assertEquals(RocketPropulsion.Mode.BIPROPELLANT, structure.propulsion().mode());
        assertEquals(323, structure.stats().mass()); assertEquals(1, structure.stats().seatCount());
        assertEquals(1, structure.stats().guidanceCount());
    }
    @Test void nuclearUsesOnlyConnectedColumnsAndPartialSupportScalesConsumption() {
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 30; i++) { blocks.add(block(NUCLEAR, i)); }
        blocks.add(new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(ModIdentity.id("nuclear_core"), Map.of()), null));
        blocks.add(block(NUCLEAR_TANK, 31));
        var result = calculate(blocks);
        assertEquals(100_000, result.stats().thrust()); assertEquals(40, result.propulsion().coefficientNumerator());
        assertEquals(147, result.propulsion().coefficientDenominator());
        var full = calculate(List.of(block(NUCLEAR, 0), blocks.get(30), block(NUCLEAR_TANK, 1)));
        assertEquals(3_500, full.stats().thrust()); assertEquals(2, full.propulsion().coefficientNumerator());
        assertEquals(7, full.propulsion().coefficientDenominator());
        refusal(RocketValidationCode.NUCLEAR_CORE_SUPPORT_REQUIRED, blocks(NUCLEAR, NUCLEAR_TANK, CORE));
    }
    @Test void multiplierBoundsRejectNonfiniteOutOfRangeAndOffGridCaptures() {
        for (double value : new double[] {0.09999, 4.00001, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> RocketPropulsion.Settings.capture(value, 1, 1, true, true, true));
            assertThrows(IllegalArgumentException.class, () -> RocketPropulsion.Settings.capture(1, value, 1, true, true, true));
            assertThrows(IllegalArgumentException.class, () -> RocketPropulsion.Settings.capture(1, 1, value, true, true, true));
        }
        assertDoesNotThrow(() -> RocketPropulsion.Settings.capture(0.1, 4, 0.1, false, false, false));
    }
    @Test void nuclearCoreRatingsAreCapturedThenSummedWithinTheAcceptedHelperDomain() {
        var captured = new ArrayList<RocketBlock>();
        for (int i = 0; i < 60; i++) { captured.add(block(NUCLEAR, i)); }
        for (int i = 0; i < 2; i++) {
            captured.add(new RocketBlock(new RocketPosition(i, 1, 0),
                    new RocketBlockState(ModIdentity.id("nuclear_core"), Map.of()), null));
        }
        captured.add(block(NUCLEAR_TANK, 60));
        var settings = RocketPropulsion.Settings.capture(1, 1, 0.100019, true, true, true);
        assertEquals(2 * (long) (100_000 * 0.100019), calculate(captured, settings).stats().thrust());
        var large = new ArrayList<RocketBlock>();
        for (int i = 0; i < 1_024; i++) { large.add(block(NUCLEAR, i)); }
        for (int i = 0; i < 1_023; i++) {
            large.add(new RocketBlock(new RocketPosition(i, 1, 0),
                    new RocketBlockState(ModIdentity.id("nuclear_core"), Map.of()), null));
        }
        large.add(block(NUCLEAR_TANK, 1_024));
        assertEquals(3_584_000, calculate(large, settings).stats().thrust());
    }
    @Test void externalPositiveCapacityAlsoRejectsNuclearAndTypedTankOnlyStructures() {
        for (var hostKinds : List.of(blocks(NUCLEAR, NUCLEAR_TANK, CORE), blocks(BIPROPELLANT_TANK),
                blocks(OXIDIZER_TANK), blocks(NUCLEAR_TANK))) {
            var external = new RocketBlock(new RocketPosition(99, 0, 0),
                    new RocketBlockState(ModIdentity.id("external"), Map.of()), null);
            hostKinds.add(external);
            permute(hostKinds, 0, order -> assertEquals(RocketValidationCode.EXTERNAL_COMPONENT_ABSTRACT_ONLY,
                    assertThrows(RocketSnapshotException.class, () -> RocketPropulsionCalculator.calculate(order,
                            state -> state.equals(external.state()) ? new RocketBlockMetrics(10, 0, 1_000, false, false, false)
                                    : host(state.blockId()).metrics(),
                            state -> state.equals(external.state()) ? EXTERNAL : host(state.blockId()),
                            RocketPropulsion.Settings.DEFAULTS)).code()));
        }
    }
    @Test void largeMixedTierCoefficientFitsTheDerivedBlockAndMultiplierBound() {
        var captured = new ArrayList<RocketBlock>(); captured.add(block(BASIC_MONO, 0));
        for (int i = 1; i <= 2_044; i++) { captured.add(block(ADVANCED_MONO, i)); }
        captured.add(block(MONO_TANK, 2_045));
        var result = calculate(captured, RocketPropulsion.Settings.capture(3.9999, 1, 1, true, true, true));
        assertTrue(result.propulsion().coefficientDenominator() > 100_000_000);
        assertEquals(result.propulsion(), io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence
                .RocketPropulsionCodec.decode(io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence
                        .RocketPropulsionCodec.encode(result.propulsion())));
    }
    private static void refusal(RocketValidationCode code, List<RocketBlock> blocks) {
        assertEquals(code, assertThrows(RocketSnapshotException.class, () -> calculate(blocks)).code());
    }
    private static void permute(List<RocketBlock> values, int cursor, java.util.function.Consumer<List<RocketBlock>> test) {
        if (cursor == values.size()) { test.accept(List.copyOf(values)); return; }
        for (int i = cursor; i < values.size(); i++) {
            Collections.swap(values, cursor, i); permute(values, cursor + 1, test); Collections.swap(values, cursor, i);
        }
    }
}
