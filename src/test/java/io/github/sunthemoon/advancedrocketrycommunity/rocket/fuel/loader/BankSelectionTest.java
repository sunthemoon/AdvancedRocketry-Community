package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionFuel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class BankSelectionTest {
    private static final ResourceLocation FUEL = ModIdentity.id("rocket_fuel");
    private static final ResourceLocation HYDROGEN = ModIdentity.id("hydrogen");
    private static final ResourceLocation OXYGEN = ModIdentity.id("oxygen");
    private static final RocketFluidFuelCatalog CATALOG = RocketFluidFuelCatalog.defaults();
    private static final RocketPropulsion MONO = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1,
            RocketPropulsion.Settings.DEFAULTS);
    private static final RocketPropulsion BIPROPELLANT = new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 2_000, 1_500,
            1, 1, RocketPropulsion.Settings.DEFAULTS);

    private static CompoundTag batch(String value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("batch", value);
        return tag;
    }

    private static RocketPropulsionFuel fuel(RocketPropulsion descriptor, RocketFuelBank primary, RocketFuelBank oxidizer) {
        return new RocketPropulsionFuel(descriptor, primary, oxidizer, 4);
    }

    private static RocketPropulsionFuel mono(RocketFuelBank primary) { return fuel(MONO, primary, RocketFuelBank.EMPTY); }

    @Test void theUniqueBankMovesExactlyOneSevenHundredFortyNineAndOneThousandMillibuckets() {
        var one = BankSelection.plan(mono(RocketFuelBank.EMPTY), CATALOG, LoaderTank.of(FUEL, null, 1));
        assertEquals(FluidFuelLoaderStatus.READY, one.status());
        assertEquals(BankSelection.Bank.PRIMARY, one.plan().bank());
        assertEquals(RocketPropulsion.Role.MONO, one.plan().role());
        assertEquals(1, one.plan().moved());
        assertEquals(LoaderTank.EMPTY, one.plan().tankAfter());
        assertEquals(RocketFuelBank.fluid(FUEL, null, 1), one.plan().fuelAfter().primary());

        // W02-F3: a bank at 1,251 of 2,000 mB and a 1,000 mB input move 749 and keep 251.
        var partial = BankSelection.plan(mono(RocketFuelBank.fluid(FUEL, null, 1_251)), CATALOG, LoaderTank.of(FUEL, null, 1_000));
        assertEquals(749, partial.plan().moved());
        assertEquals(LoaderTank.of(FUEL, null, 251), partial.plan().tankAfter());
        assertEquals(2_000, partial.plan().fuelAfter().primary().amount());

        var capped = BankSelection.plan(mono(RocketFuelBank.EMPTY), CATALOG, LoaderTank.of(FUEL, null, 16_000));
        assertEquals(1_000, capped.plan().moved());
        assertEquals(LoaderTank.of(FUEL, null, 15_000), capped.plan().tankAfter());
        assertEquals(1_000, capped.plan().fuelAfter().primary().amount());
        assertEquals(5, capped.plan().fuelAfter().revision());
        assertEquals(RocketFuelBank.EMPTY, capped.plan().fuelAfter().oxidizer());
    }

    @Test void fullIncompatibleAndEmptyCasesRefuseWithTheirOwnStatus() {
        var tank = LoaderTank.of(FUEL, null, 1_000);
        assertEquals(FluidFuelLoaderStatus.BANK_FULL,
                BankSelection.plan(mono(RocketFuelBank.fluid(FUEL, null, 2_000)), CATALOG, tank).status());
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                BankSelection.plan(mono(RocketFuelBank.EMPTY), CATALOG, LoaderTank.of(HYDROGEN, null, 1_000)).status());
        // The current catalog and the rocket's captured allowlist must both admit the Fluid.
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                BankSelection.plan(mono(RocketFuelBank.EMPTY), new RocketFluidFuelCatalog(Map.of()), tank).status());
        var denied = new RocketPropulsion(RocketPropulsion.Mode.MONO, 2_000, 0, 1, 1, MONO.settings().withAllowedFluids(allowed(Map.of())));
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                BankSelection.plan(fuel(denied, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY), CATALOG, tank).status());
        var abstractOnly = new RocketPropulsion(RocketPropulsion.Mode.ABSTRACT, 2_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                BankSelection.plan(fuel(abstractOnly, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY), CATALOG, tank).status());
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID, BankSelection.plan(null, CATALOG, tank).status());
        assertEquals(FluidFuelLoaderStatus.EMPTY, BankSelection.plan(mono(RocketFuelBank.EMPTY), CATALOG, LoaderTank.EMPTY).status());
        // A full compatible bank is BANK_FULL even beside an incompatible one.
        var fullPrimary = fuel(BIPROPELLANT, RocketFuelBank.fluid(HYDROGEN, null, 2_000), RocketFuelBank.EMPTY);
        assertEquals(FluidFuelLoaderStatus.BANK_FULL,
                BankSelection.plan(fullPrimary, CATALOG, LoaderTank.of(HYDROGEN, null, 10)).status());
        for (var refused : List.of(FluidFuelLoaderStatus.BANK_FULL, FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                FluidFuelLoaderStatus.EMPTY, FluidFuelLoaderStatus.AMBIGUOUS_BANK)) {
            assertTrue(refused.refusal());
            assertNull(new BankSelection.Result(refused, null).plan());
        }
    }

    @Test void bipropellantSelectsTheOneBankItsFluidFits() {
        var empty = fuel(BIPROPELLANT, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY);
        var hydrogen = BankSelection.plan(empty, CATALOG, LoaderTank.of(HYDROGEN, null, 500));
        assertEquals(BankSelection.Bank.PRIMARY, hydrogen.plan().bank());
        assertEquals(RocketPropulsion.Role.BIPROPELLANT_FUEL, hydrogen.plan().role());
        var oxygen = BankSelection.plan(empty, CATALOG, LoaderTank.of(OXYGEN, null, 5_000));
        assertEquals(BankSelection.Bank.OXIDIZER, oxygen.plan().bank());
        assertEquals(1_000, oxygen.plan().moved());
        assertEquals(RocketFuelBank.fluid(OXYGEN, null, 1_000), oxygen.plan().fuelAfter().oxidizer());
        assertEquals(RocketFuelBank.EMPTY, oxygen.plan().fuelAfter().primary());
    }

    @Test void aFluidEligibleForBothBanksIsAmbiguousUntilOneIsFull() {
        var settings = BIPROPELLANT.settings().withAllowedFluids(allowed(Map.of(
                RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(HYDROGEN.toString()),
                RocketPropulsion.Role.OXIDIZER, List.of(HYDROGEN.toString()))));
        var both = new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 2_000, 1_500, 1, 1, settings);
        var catalog = new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.BIPROPELLANT_FUEL, HYDROGEN), new RocketFluidFuelCatalog.Efficiency(1, 1),
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.OXIDIZER, HYDROGEN), new RocketFluidFuelCatalog.Efficiency(1, 1)));
        var tank = LoaderTank.of(HYDROGEN, null, 1_000);
        assertEquals(FluidFuelLoaderStatus.AMBIGUOUS_BANK,
                BankSelection.plan(fuel(both, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY), catalog, tank).status());
        var primaryFull = BankSelection.plan(fuel(both, RocketFuelBank.fluid(HYDROGEN, null, 2_000), RocketFuelBank.EMPTY), catalog, tank);
        assertEquals(BankSelection.Bank.OXIDIZER, primaryFull.plan().bank());
        var oxidizerFull = BankSelection.plan(fuel(both, RocketFuelBank.EMPTY, RocketFuelBank.fluid(HYDROGEN, null, 1_500)), catalog, tank);
        assertEquals(BankSelection.Bank.PRIMARY, oxidizerFull.plan().bank());
        assertEquals(FluidFuelLoaderStatus.BANK_FULL, BankSelection.plan(fuel(both, RocketFuelBank.fluid(HYDROGEN, null, 2_000),
                RocketFuelBank.fluid(HYDROGEN, null, 1_500)), catalog, tank).status());
        // Without the current catalog's oxidizer entry, only the primary bank remains eligible.
        var primaryOnly = new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.BIPROPELLANT_FUEL, HYDROGEN), new RocketFluidFuelCatalog.Efficiency(1, 1)));
        assertEquals(BankSelection.Bank.PRIMARY,
                BankSelection.plan(fuel(both, RocketFuelBank.EMPTY, RocketFuelBank.EMPTY), primaryOnly, tank).plan().bank());
    }

    @Test void nativeTagsArePreservedAndOtherTagsOrAbstractFuelAreNeverOverwritten() {
        var tagged = LoaderTank.of(FUEL, batch("a"), 1_000);
        var planned = BankSelection.plan(mono(RocketFuelBank.EMPTY), CATALOG, tagged).plan();
        assertEquals(RocketFuelBank.fluid(FUEL, batch("a"), 1_000), planned.fuelAfter().primary());
        assertEquals(LoaderTank.EMPTY, planned.tankAfter());
        var kept = BankSelection.plan(mono(RocketFuelBank.fluid(FUEL, batch("a"), 1_500)), CATALOG, LoaderTank.of(FUEL, batch("a"), 2_000)).plan();
        assertEquals(RocketFuelBank.fluid(FUEL, batch("a"), 2_000), kept.fuelAfter().primary());
        assertEquals(LoaderTank.of(FUEL, batch("a"), 1_500), kept.tankAfter());
        for (RocketFuelBank other : List.of(RocketFuelBank.fluid(FUEL, batch("b"), 10), RocketFuelBank.fluid(FUEL, null, 10),
                RocketFuelBank.fluid(FUEL, new CompoundTag(), 10), RocketFuelBank.abstractUnits(10))) {
            assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID, BankSelection.plan(mono(other), CATALOG, tagged).status(), other.toString());
        }
        // An untagged tank does not join a bank whose native tag is an empty compound, nor the reverse.
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID, BankSelection.plan(mono(RocketFuelBank.fluid(FUEL, new CompoundTag(), 10)),
                CATALOG, LoaderTank.of(FUEL, null, 10)).status());
        assertEquals(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                BankSelection.plan(mono(RocketFuelBank.abstractUnits(1)), CATALOG, LoaderTank.of(FUEL, null, 10)).status());
    }

    @Test void planningHasNoSideEffects() {
        var bank = RocketFuelBank.fluid(FUEL, batch("a"), 1_251);
        var fuel = mono(bank);
        CompoundTag tag = batch("a");
        var tank = LoaderTank.of(FUEL, tag, 1_000);
        var before = fuel.resourceSignature();
        var plan = BankSelection.plan(fuel, CATALOG, tank).plan();
        assertEquals(before, fuel.resourceSignature());
        assertEquals(LoaderTank.of(FUEL, batch("a"), 1_000), tank);
        plan.tankAfter().tag().putString("batch", "changed");
        assertEquals(batch("a"), tank.tag());
        assertEquals(batch("a"), plan.tankAfter().tag());
        assertEquals(batch("a"), plan.fuelAfter().primary().fluidTag());
    }

    @Test void stepArithmeticIsExactIntegerMillibucketsAndOverflowSafe() {
        assertEquals(1, BankSelection.step(1, Long.MAX_VALUE));
        assertEquals(749, BankSelection.step(1_000, 749));
        assertEquals(1_000, BankSelection.step(1_000, 1_000));
        assertEquals(1_000, BankSelection.step(Integer.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(1_000, BankSelection.step(Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(999, BankSelection.step(Long.MAX_VALUE, 999));
        assertEquals(0, BankSelection.step(0, 1_000));
        assertEquals(0, BankSelection.step(1_000, 0));
        for (long[] negative : new long[][] {{-1, 0}, {0, -1}, {Long.MIN_VALUE, 1_000}, {1_000, Long.MIN_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> BankSelection.step(negative[0], negative[1]));
        }
        // The largest representable bank one millibucket short of its capacity takes exactly one more.
        long most = (long) RocketLimits.MAX_BLOCKS * 1_000;
        var huge = new RocketPropulsion(RocketPropulsion.Mode.MONO, most, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var last = BankSelection.plan(fuel(huge, RocketFuelBank.fluid(FUEL, null, most - 1), RocketFuelBank.EMPTY), CATALOG,
                LoaderTank.of(FUEL, null, LoaderTank.CAPACITY)).plan();
        assertEquals(1, last.moved());
        assertEquals(most, last.fuelAfter().primary().amount());
        assertEquals(LoaderTank.CAPACITY - 1, last.tankAfter().amount());
    }

    private static Map<RocketPropulsion.Role, List<String>> allowed(Map<RocketPropulsion.Role, List<String>> entries) {
        var all = new EnumMap<RocketPropulsion.Role, List<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) { all.put(role, entries.getOrDefault(role, List.of())); }
        return all;
    }
}
