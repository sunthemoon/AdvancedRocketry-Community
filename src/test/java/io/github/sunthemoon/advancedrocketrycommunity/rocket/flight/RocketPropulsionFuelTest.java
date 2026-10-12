package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPropulsionFuelCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class RocketPropulsionFuelTest {
    private static RocketFuelState fuel(RocketPropulsion.Mode mode, long numerator, long denominator) {
        var descriptor = new RocketPropulsion(mode, 1_000, mode == RocketPropulsion.Mode.BIPROPELLANT ? 1_000 : 0,
                numerator, denominator, RocketPropulsion.Settings.DEFAULTS);
        var fuel = RocketFuelState.empty(descriptor).withFluidBank(false,
                ModIdentity.id(mode == RocketPropulsion.Mode.MONO ? "rocket_fuel" : "hydrogen"), null, 1_000);
        return mode == RocketPropulsion.Mode.BIPROPELLANT ? fuel.withFluidBank(true, ModIdentity.id("oxygen"), null, 1_000) : fuel;
    }
    private static RocketFlightPlan plan(long q) {
        return new RocketFlightPlan(3, UUID.randomUUID(), RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.MOON.dimensionId(), q, 10);
    }
    @Test void exactDebitsForAllTiersAndBothBipropellantBanks() {
        var cases = List.of(fuel(RocketPropulsion.Mode.MONO, 1, 1), fuel(RocketPropulsion.Mode.MONO, 3, 5),
                fuel(RocketPropulsion.Mode.BIPROPELLANT, 1, 1), fuel(RocketPropulsion.Mode.BIPROPELLANT, 3, 5),
                fuel(RocketPropulsion.Mode.NUCLEAR, 2, 7));
        long[] expected = {51, 31, 101, 61, 29};
        for (int i = 0; i < cases.size(); i++) {
            var fuel = cases.get(i);
            var quote = RocketPropulsionQuoter.quote(fuel, plan(101), RocketFluidFuelCatalog.defaults());
            assertTrue(quote.success()); var vector = quote.plan().propulsionDebit();
            assertEquals(expected[i], vector.primary());
            assertEquals(fuel.propulsion().orElseThrow().descriptor().mode() == RocketPropulsion.Mode.BIPROPELLANT
                    ? expected[i] : 0, vector.oxidizer());
            var debit = fuel.debit(quote.plan()); assertTrue(debit.success());
            assertEquals(1_000 - expected[i], debit.state().amount());
            assertEquals(RocketFuelCode.REQUEST_REPLAYED, debit.state().debit(quote.plan()).code());
        }
    }
    @Test void insufficientOxidizerRefusesWithoutChangingEitherBank() {
        var fuel = fuel(RocketPropulsion.Mode.BIPROPELLANT, 1, 1).withFluidBank(true, ModIdentity.id("oxygen"), null, 10);
        var before = RocketPropulsionFuelCodec.encode(fuel.propulsion().orElseThrow());
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_FUEL,
                RocketPropulsionQuoter.quote(fuel, plan(101), RocketFluidFuelCatalog.defaults()).code());
        assertEquals(before, RocketPropulsionFuelCodec.encode(fuel.propulsion().orElseThrow()));
    }
    @Test void oversizedPhysicalDebitReturnsARefusalWithoutPublishingAWrappedDisplayQuote() {
        var fuel = fuel(RocketPropulsion.Mode.BIPROPELLANT, 1, 1);
        var catalog = new RocketFluidFuelCatalog(Map.of(
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.BIPROPELLANT_FUEL, ModIdentity.id("hydrogen")),
                new RocketFluidFuelCatalog.Efficiency(1, 1_000),
                new RocketFluidFuelCatalog.Key(RocketPropulsion.Role.OXIDIZER, ModIdentity.id("oxygen")),
                new RocketFluidFuelCatalog.Efficiency(1, 1_000)));
        var quote = RocketPropulsionQuoter.quote(fuel, plan(2_048_000), catalog);
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_CAPACITY, quote.code()); assertEquals(0, quote.requiredFuel());
        assertTrue(quote.optionalPlan().isEmpty()); assertEquals(2_000, fuel.totalAmount());
    }
    @Test void quoteBindsNativeTagsAndRevisionAndSurvivesCatalogChanges() {
        CompoundTag tag = new CompoundTag(); tag.putString("batch", "A");
        var fuel = fuel(RocketPropulsion.Mode.NUCLEAR, 2, 7).withFluidBank(false, ModIdentity.id("hydrogen"), tag, 1_000);
        var quote = RocketPropulsionQuoter.quote(fuel, plan(101), RocketFluidFuelCatalog.defaults()).plan();
        tag.putString("batch", "caller mutation");
        assertEquals("A", fuel.propulsion().orElseThrow().primary().fluidTag().getString("batch"));
        var changed = fuel.withFluidBank(false, ModIdentity.id("hydrogen"), null, 1_000);
        assertEquals(RocketFuelCode.INVALID_AMOUNT, changed.debit(quote).code());
        assertTrue(fuel.debit(quote).success());
        assertEquals(29, quote.propulsionDebit().primary());
        assertNotEquals(RocketFluidFuelCatalog.defaults().signature(), new RocketFluidFuelCatalog(Map.of()).signature());
    }
    @Test void branchSelectionNeverConvertsAbstractUnitsToFluidAndItemsCannotFillTypedBanks() {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 3, 5, RocketPropulsion.Settings.DEFAULTS);
        var mono = RocketFuelState.empty(descriptor).fill(500).state();
        assertThrows(IllegalArgumentException.class, () -> mono.withFluidBank(false, ModIdentity.id("rocket_fuel"), null, 500));
        var quote = RocketPropulsionQuoter.quote(mono, plan(101), RocketFluidFuelCatalog.defaults()).plan();
        assertEquals(101, quote.propulsionDebit().primary());
        assertEquals(399, mono.debit(quote).state().amount());
        assertFalse(fuel(RocketPropulsion.Mode.MONO, 3, 5).fill(1).success());
        assertFalse(fuel(RocketPropulsion.Mode.BIPROPELLANT, 1, 1).fill(1).success());
        assertFalse(fuel(RocketPropulsion.Mode.NUCLEAR, 2, 7).fill(1).success());
    }
    @Test void noFuelAndGravityTogglesDoNotChangeStoredResourcesOrBypassThrust() {
        var settings = RocketPropulsion.Settings.capture(1, 1, 1, false, false, true);
        var fuel = RocketFuelState.empty(new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000, 1, 1, settings));
        var stats = new RocketStats(5, 250, 1_000, 1_000, 1, 1, 1, 0);
        var quote = RocketFlightPlanner.plan(stats, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, UUID.randomUUID(), 10);
        assertTrue(quote.success()); assertEquals(275, quote.requiredFuel());
        assertEquals(0, quote.plan().propulsionDebit().primary()); assertEquals(0, quote.plan().propulsionDebit().oxidizer());
        var debit = fuel.debit(quote.plan()); assertTrue(debit.success()); assertEquals(0, debit.state().totalAmount());
        var weak = new RocketStats(5, 250, 249, 1_000, 1, 1, 1, 0);
        assertEquals(RocketFlightPlanCode.INSUFFICIENT_THRUST,
                RocketFlightPlanner.plan(weak, fuel, RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, UUID.randomUUID(), 10).code());
    }
    @Test void flightCodecRoundTripPreservesPlanBanksTagsAndExactlyOnceLedger() {
        var fuel = fuel(RocketPropulsion.Mode.BIPROPELLANT, 3, 5);
        var flight = RocketFlightData.initial(UUID.randomUUID(), 1_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 10, 0), 10).withFuel(fuel, 10);
        var quote = RocketPropulsionQuoter.quote(fuel, plan(101), RocketFluidFuelCatalog.defaults()).plan();
        flight = flight.withPlan(quote).startCountdown(10);
        var encoded = RocketFlightNbtCodec.encode(flight);
        var restored = RocketFlightNbtCodec.decode(encoded); assertEquals(RocketFlightDecodeResult.Status.VALID, restored.status());
        assertEquals(flight, restored.data().orElseThrow());
        var debit = fuel.debit(restored.data().orElseThrow().plan().orElseThrow());
        assertEquals(debit.state().propulsion().orElseThrow(), RocketPropulsionFuelCodec.decode(
                RocketPropulsionFuelCodec.encode(debit.state().propulsion().orElseThrow())));
    }
    @Test void capturedWaiverKeepsEmptyFuelReadyAfterFailureRecoveryWithoutInventingFluid() {
        var settings = RocketPropulsion.Settings.capture(1, 1, 1, true, false, true);
        var fuel = RocketFuelState.empty(new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 1_000, 1_000, 1, 1, settings));
        assertTrue(fuel.availableForLaunch()); assertFalse(RocketFuelState.empty(1_000).availableForLaunch());
        var flight = RocketFlightData.initial(UUID.randomUUID(), 1_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 10, 0), 10).withFuel(fuel, 10);
        var failed = flight.markFailed(20);
        var recovered = failed.recover(failed.fuel().availableForLaunch(), 30);
        assertEquals(RocketFlightState.FUELED, recovered.state()); assertEquals(fuel, recovered.fuel());
        assertEquals(0, recovered.fuel().totalAmount());
    }
    @Test void oldScalarFlightDataMigratesWithoutInventingFluidAndKeepsTheDebitLedger() {
        UUID transaction = UUID.randomUUID();
        var scalar = RocketFuelState.restore(1_000, 456, List.of(transaction));
        var old = RocketFlightData.initial(UUID.randomUUID(), 1_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 10, 0), 10).withFuel(scalar, 10);
        var restored = RocketFlightNbtCodec.decode(RocketFlightNbtCodec.encode(old)).data().orElseThrow();
        assertEquals(scalar, restored.fuel()); assertTrue(restored.fuel().propulsion().isEmpty());
        assertEquals(RocketFuelCode.REQUEST_REPLAYED, restored.fuel().debit(transaction, 10).code());
    }
    @Test void unknownAndOversizedNativeRootsStayVerbatimAndDisableDecode() {
        var fuel = fuel(RocketPropulsion.Mode.NUCLEAR, 2, 7);
        var flight = RocketFlightData.initial(UUID.randomUUID(), 1_000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(0, 10, 0), 10).withFuel(fuel, 10);
        var root = RocketFlightNbtCodec.encode(flight); root.getCompound("fuel").getCompound("propulsion").putInt("schema", 2);
        var future = RocketFlightNbtCodec.decode(root);
        assertEquals(RocketFlightDecodeResult.Status.INVALID, future.status()); assertEquals(root, future.preservedPayload().orElseThrow());
        for (String envelope : List.of("outer", "fuel", "native")) {
            var extra = RocketFlightNbtCodec.encode(flight);
            var target = switch (envelope) {
                case "fuel" -> extra.getCompound("fuel");
                case "native" -> extra.getCompound("fuel").getCompound("propulsion");
                default -> extra;
            };
            target.putString("future_field", "keep verbatim");
            var rejected = RocketFlightNbtCodec.decode(extra);
            assertEquals(RocketFlightDecodeResult.Status.INVALID, rejected.status());
            assertEquals(extra, rejected.preservedPayload().orElseThrow());
        }
        var huge = new CompoundTag(); huge.putByteArray("opaque", new byte[RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES + 1]);
        var oversized = RocketFlightNbtCodec.decode(huge);
        assertEquals(RocketFlightDecodeResult.Status.INVALID, oversized.status()); assertSame(huge, oversized.preservedPayload().orElseThrow());
    }
}
