package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowFlightBenchmarkLoadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void refillUsesBoundedAutomationWithoutDuplicatingQueuedSupply() {
        var items = new ItemStackHandler(2);
        var energy = new EnergyStorage(40000);
        items.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
        items.setStackInSlot(1, new ItemStack(Items.GLASS_BOTTLE, 4));
        for (int i = 0; i < 2; i++) { EscrowFlightBenchmarkLoad.refill(items, energy, Items.IRON_INGOT); }
        assertEquals(4, items.getStackInSlot(0).getCount());
        assertTrue(items.getStackInSlot(1).isEmpty());
        assertEquals(40000, energy.getEnergyStored());
    }

    @Test void refusedSupplyRemainsAFailure() {
        var items = new ItemStackHandler(2) {
            @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
        };
        assertThrows(IllegalArgumentException.class,
                () -> EscrowFlightBenchmarkLoad.refill(items, new EnergyStorage(40000), Items.IRON_INGOT));
    }

    @Test void admissionRespectsCaptureCadenceWithoutWaitingForFlightCompletion() {
        var budget = new EscrowBarrierBudget(); var requested = new boolean[8];
        for (int tick = 0; tick < 8; tick++) {
            long now = tick;
            EscrowFlightBenchmarkProbe.admitLaunches(requested, () -> budget.available("earth", true, now), i -> {
                assertTrue(budget.reserve("earth", true, now));
                budget.captured(256); requested[i] = true;
            });
            assertEquals(tick + 1, budget.pending(), "No prior flight has completed or released its capture");
            assertFalse(budget.available("earth", true, now), "A second same-tick capture must remain refused");
        }
        for (boolean value : requested) { assertTrue(value); }
        assertEquals(8, budget.pending());
    }

    @Test void benchmarkPadsAndSourcesKeepTheDiscoveryMarginInsideTheirOriginChunk() {
        var sources = new java.util.HashSet<net.minecraft.core.BlockPos>();
        var pads = new java.util.HashSet<net.minecraft.core.BlockPos>();
        for (int i = 0; i < 8; i++) {
            var source = EscrowFlightBenchmarkLoad.source(i); var pad = EscrowFlightBenchmarkLoad.destination(i);
            sources.add(source); pads.add(pad);
            for (var pos : java.util.List.of(source, pad)) {
                assertEquals(pos.getX() >> 4, (pos.getX() - 1) >> 4);
                assertEquals(pos.getX() >> 4, (pos.getX() + 2) >> 4);
                assertEquals(pos.getZ() >> 4, (pos.getZ() - 1) >> 4);
                assertEquals(pos.getZ() >> 4, (pos.getZ() + 2) >> 4);
            }
        }
        assertEquals(8, sources.size()); assertEquals(8, pads.size());
    }

    @Test void productQuotePinsExactFuelConservationInsteadOfTheOldSyntheticDebit() {
        var descriptor = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Mode.MONO,
                2000, 0, 1, 1, io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Settings.DEFAULTS);
        var fuel = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(descriptor)
                .withFluidBank(false, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket_fuel"), null, 1000);
        var planned = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner.plan(
                EscrowFlightBenchmarkLoad.flightStats(), fuel,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner.EARTH,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner.MOON, java.util.UUID.randomUUID(), 1);
        assertTrue(planned.success());
        assertEquals(EscrowFlightBenchmarkProbe.ROUTE_FUEL, planned.plan().requiredFuel());
        assertEquals(EscrowFlightBenchmarkProbe.PRIMARY_DEBIT, planned.plan().propulsionDebit().primary());
        assertEquals(EscrowFlightBenchmarkProbe.LANDED_FUEL, fuel.debit(planned.plan()).state().totalAmount());
    }
}
