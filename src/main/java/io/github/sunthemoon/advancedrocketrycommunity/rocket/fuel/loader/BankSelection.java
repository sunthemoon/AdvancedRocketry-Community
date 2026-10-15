package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionFuel;
import java.util.Objects;

/**
 * Design section 4: the unique eligible propulsion bank for the loader's Fluid, and W02-F3's refill step of
 * {@code min(1,000, local mB, bank room)} in integer mB. Both are side-effect-free simulations that precede a capture.
 */
public final class BankSelection {
    public static final int MAX_STEP_MB = 1_000;

    public enum Bank { PRIMARY, OXIDIZER }

    /** The selected bank and the planned step; a capture still revalidates it against the live stores. */
    public record Plan(Bank bank, RocketPropulsion.Role role, int moved, LoaderTank tankAfter, RocketPropulsionFuel fuelAfter) {
        public Plan {
            Objects.requireNonNull(bank, "bank");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(tankAfter, "tankAfter");
            Objects.requireNonNull(fuelAfter, "fuelAfter");
            LoaderNbt.require(moved >= 1 && moved <= MAX_STEP_MB, "A refill step moves 1 to 1,000 mB");
        }
    }

    /** READY with a plan, or a refusal with none. */
    public record Result(FluidFuelLoaderStatus status, Plan plan) {
        public Result {
            Objects.requireNonNull(status, "status");
            LoaderNbt.require(status == FluidFuelLoaderStatus.READY == (plan != null), "Only READY carries a plan");
        }
    }

    private BankSelection() { }

    /**
     * Filters the banks by propulsion role, the current catalog, complete native Fluid compatibility and positive
     * room. One eligible bank is planned; more than one is AMBIGUOUS_BANK; none is BANK_FULL when a compatible bank
     * merely lacks room, and INCOMPATIBLE_FLUID otherwise. Abstract fuel and a bank holding another Fluid or another
     * native tag are never compatible, so they are never overwritten. A rocket without a native vector has no bank.
     */
    public static Result plan(RocketPropulsionFuel fuel, RocketFluidFuelCatalog catalog, LoaderTank tank) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(tank, "tank");
        if (tank.isEmpty()) { return refused(FluidFuelLoaderStatus.EMPTY); }
        if (fuel == null) { return refused(FluidFuelLoaderStatus.INCOMPATIBLE_FLUID); }
        Bank chosen = null;
        long room = 0;
        boolean compatible = false;
        for (Bank bank : Bank.values()) {
            if (!holdsFluid(fuel, bank) || !compatible(fuel, bank, catalog, tank)) { continue; }
            compatible = true;
            long free = Math.subtractExact(capacity(fuel, bank), contents(fuel, bank).amount());
            if (free <= 0) { continue; }
            if (chosen != null) { return refused(FluidFuelLoaderStatus.AMBIGUOUS_BANK); }
            chosen = bank;
            room = free;
        }
        if (chosen == null) {
            return refused(compatible ? FluidFuelLoaderStatus.BANK_FULL : FluidFuelLoaderStatus.INCOMPATIBLE_FLUID);
        }
        int moved = step(tank.amount(), room);
        RocketFuelBank next = RocketFuelBank.fluid(tank.fluid(), tank.tag(), Math.addExact(contents(fuel, chosen).amount(), moved));
        RocketPropulsionFuel after = fuel.updated(chosen == Bank.PRIMARY ? next : fuel.primary(),
                chosen == Bank.OXIDIZER ? next : fuel.oxidizer());
        return new Result(FluidFuelLoaderStatus.READY,
                new Plan(chosen, role(fuel, chosen), moved, tank.withAmount(tank.amount() - moved), after));
    }

    /** W02-F3: {@code min(1,000, local, room)} in integer mB, with no rounding and no whole-container requirement. */
    public static int step(long local, long room) {
        if (local < 0 || room < 0) { throw new IllegalArgumentException("Refill inputs must be nonnegative"); }
        return (int) Math.min(MAX_STEP_MB, Math.min(local, room));
    }

    private static boolean holdsFluid(RocketPropulsionFuel fuel, Bank bank) {
        RocketPropulsion.Mode mode = fuel.descriptor().mode();
        return bank == Bank.PRIMARY ? mode != RocketPropulsion.Mode.ABSTRACT : mode == RocketPropulsion.Mode.BIPROPELLANT;
    }

    private static boolean compatible(RocketPropulsionFuel fuel, Bank bank, RocketFluidFuelCatalog catalog, LoaderTank tank) {
        RocketPropulsion.Role role = role(fuel, bank);
        RocketFuelBank contents = contents(fuel, bank);
        return fuel.descriptor().settings().allows(role, tank.fluid()) && catalog.find(role, tank.fluid()) != null
                && (contents.branch() == RocketFuelBank.Branch.EMPTY || contents.branch() == RocketFuelBank.Branch.FLUID
                        && tank.fluid().equals(contents.fluidId()) && Objects.equals(tank.tag(), contents.fluidTag()));
    }

    private static RocketPropulsion.Role role(RocketPropulsionFuel fuel, Bank bank) {
        return bank == Bank.PRIMARY ? fuel.descriptor().primaryRole() : RocketPropulsion.Role.OXIDIZER;
    }

    private static RocketFuelBank contents(RocketPropulsionFuel fuel, Bank bank) {
        return bank == Bank.PRIMARY ? fuel.primary() : fuel.oxidizer();
    }

    private static long capacity(RocketPropulsionFuel fuel, Bank bank) {
        return bank == Bank.PRIMARY ? fuel.descriptor().primaryCapacity() : fuel.descriptor().oxidizerCapacity();
    }

    private static Result refused(FluidFuelLoaderStatus status) { return new Result(status, null); }
}
