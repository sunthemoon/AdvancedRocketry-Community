package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketFlightNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowAccountingCaptureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static EscrowLedger empty() { return EscrowTestFixtures.ledger().accounting(List.of(), List.of()); }
    static EscrowNativeBinding.Admission cargo(boolean loading) {
        var c = EscrowCargoEligibilityTest.sample(loading, 1000, -1);
        var result = EscrowCargoEligibility.check(empty(), List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoSelection.Status.ELIGIBLE, result.status());
        return result.capture();
    }

    record Fuel(EscrowNativeBinding.Admission admission, CompoundTag before, CompoundTag after,
                CompoundTag deviceBefore, CompoundTag deviceAfter) { }
    static Fuel fuel(boolean second) {
        var sample = EscrowReconciliationBindingBoundaryTest.sample();
        var old = second ? sample.b() : sample.a(); var before = second ? sample.first() : sample.before();
        var after = second ? sample.after() : sample.first();
        var device = new LoaderRoot(old.owner(), EscrowTestFixtures.id(364_000 + old.sequence()), null,
                LoaderTank.of(ResourceLocation.tryParse("minecraft:water"), null, 5000), LoaderEscrowSection.neutral());
        var source = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, old.source().level(), device.device(), null,
                EscrowAdapterIds.LOADER, (int) old.sequence(), 64, 0, EscrowEndpoint.Unit.TANK, 0, device.signature(),
                device.tank().withAmount(4900).signature(device.device()));
        var b = old.nativeBinding();
        var model = new EscrowOperation(old.id(), old.sequence(), old.owner(), 0, old.phase(), old.reason(), old.payload(), old.payloadHash(),
                source, old.destination(), old.guard());
        var deviceBefore = EscrowLoaderEndpointTest.envelope(device);
        deviceBefore.putInt("x", source.x()); deviceBefore.putInt("z", source.z());
        var deviceAfter = EscrowLoaderEndpoint.change(sample.ledger().id(), model, deviceBefore);
        var binding = new EscrowNativeBinding(b.kind(), b.physical(), b.logical(), b.owner(), b.snapshot(), b.snapshotHash(), b.invariant(),
                EscrowNativeBinding.reference(source), b.destinationReference(), b.beforeWrapper(), b.afterWrapper(), b.beforeFrontier(), b.afterFrontier(),
                EscrowNativeEnvelope.digest(deviceBefore), EscrowNativeEnvelope.digest(deviceAfter), b.destinationBefore(), b.destinationAfter(), true);
        var op = new EscrowOperation(model.id(), model.sequence(), model.owner(), 0, model.phase(), model.reason(), model.payload(), model.payloadHash(),
                source, model.destination(), model.guard(), model.kind(), model.settlement(), binding);
        var admission = EscrowAccountingCapture.bind(op, RocketEscrowSection.decode(before.getCompound("wrapper")),
                before, after, deviceBefore, deviceAfter, null, null);
        return new Fuel(admission, before, after, deviceBefore, deviceAfter);
    }

    @Test void actualFuelAndBothCargoDirectionsCaptureAndVerifyTheirNativePlan() {
        var fuel = fuel(false); assertEquals(EscrowFluidAccount.Scope.FUEL, fuel.admission().account().baseline().scope());
        for (boolean loading : List.of(true, false)) {
            var admission = cargo(loading); var baseline = admission.account().baseline();
            var nativeCapture = EscrowCargoEligibilityTest.sample(loading, 1000, -1);
            assertEquals(admission.operation().accountingBaseline(), EscrowCargoCapture.capture(empty().id(), admission.operation(),
                    nativeCapture.before(), nativeCapture.after(), nativeCapture.deviceBefore(), nativeCapture.deviceAfter()).operation().accountingBaseline());
            assertEquals(EscrowFluidAccount.Scope.CARGO, baseline.scope()); assertEquals(1000, baseline.quantity());
            assertEquals(2000, baseline.source(baseline.moved()));
            assertEquals(0, baseline.destination(baseline.moved()));
            assertEquals(EscrowFluidAccount.Status.OK, baseline.verifyPlan(baseline.sourceAfter(), baseline.destinationAfter()));
            var root = EscrowAccountingCapture.append(empty(), List.of(), admission);
            assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
        }
        var root = EscrowAccountingCapture.append(empty(), List.of(), fuel.admission());
        assertEquals(root, EscrowCodec.decode(EscrowCodec.encode(root)));
    }

    @Test void bothFuelBanksAndNativeTagIdentityAreAccountedWithoutAbstractFallback() {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT, 4000, 4000, 1, 1,
                RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(Map.of(RocketPropulsion.Role.MONO, List.of(),
                        RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of("minecraft:water"),
                        RocketPropulsion.Role.OXIDIZER, List.of("minecraft:water", "minecraft:lava"), RocketPropulsion.Role.WORKING_FLUID, List.of())));
        var water = ResourceLocation.tryParse("minecraft:water"); var lava = ResourceLocation.tryParse("minecraft:lava");
        var fuel = RocketFuelState.empty(descriptor).withFluidBank(false, water, null, 1000).withFluidBank(true, lava, null, 700);
        var vector = EscrowAccountingCapture.fuel(fuel);
        assertEquals(2, vector.keys().size());
        assertEquals(1000, vector.amount(EscrowFluidAccountCodec.key(LoaderTank.of(water, null, 1).encode()).value()));
        assertEquals(700, vector.amount(EscrowFluidAccountCodec.key(LoaderTank.of(lava, null, 1).encode()).value()));
        var same = RocketFuelState.empty(descriptor).withFluidBank(false, water, null, 1000).withFluidBank(true, water, null, 700);
        assertEquals(1700, EscrowAccountingCapture.fuel(same).amount(vector.keys().stream().filter(k -> k.fluid().equals("minecraft:water")).findFirst().orElseThrow()));
        assertThrows(IllegalArgumentException.class, () -> EscrowAccountingCapture.fuel(RocketFuelState.empty(1000)));
        assertNotEquals(EscrowAccountingCapture.tank(LoaderTank.of(water, null, 1).encode()),
                EscrowAccountingCapture.tank(LoaderTank.of(water, new CompoundTag(), 1).encode()));
    }

    @Test void changedPlanOrCollateralBankIsRefusedWithoutChangingAnyCapture() {
        var f = fuel(false); var before = f.before().copy(); var after = f.after().copy(); var source = f.deviceBefore().copy();
        var sourceTank = EscrowLoaderEndpoint.root(f.deviceBefore()).tank();
        var sourceAfter = EscrowLoaderEndpoint.root(f.deviceAfter()).tank();
        var fuelBefore = RocketFlightNbtCodec.decode(f.before().getCompound("flight")).data().orElseThrow().fuel();
        var fuelAfter = RocketFlightNbtCodec.decode(f.after().getCompound("flight")).data().orElseThrow().fuel();
        assertEquals(f.admission().account(), EscrowAccountingCapture.fuel(f.admission().operation(), f.admission().before(),
                sourceTank, sourceAfter, fuelBefore, fuelAfter).account());
        assertThrows(IllegalArgumentException.class, () -> EscrowAccountingCapture.fuel(f.admission().operation(), f.admission().before(),
                sourceTank, sourceAfter.withAmount(4899), fuelBefore, fuelAfter));
        var changed = f.after().copy(); var flight = RocketFlightNbtCodec.decode(changed.getCompound("flight")).data().orElseThrow();
        changed.put("flight", RocketFlightNbtCodec.encode(EscrowNativeEnvelope.fuelOnly(flight,
                flight.fuel().withFluidBank(false, ResourceLocation.tryParse("minecraft:water"), null, 99))));
        assertThrows(IllegalArgumentException.class, () -> EscrowAccountingCapture.bind(f.admission().operation(), f.admission().before(),
                f.before(), changed, f.deviceBefore(), f.deviceAfter(), null, null));
        assertEquals(before, f.before()); assertEquals(after, f.after()); assertEquals(source, f.deviceBefore());
    }

    @Test void baselineRowsAreAddedAndNormallyRemovedWithTheOperationAndNoReceiptHistory() {
        var admission = cargo(true); var root = EscrowAccountingCapture.append(empty(), List.of(), admission);
        assertEquals(1, root.fluidAccounts().size()); assertEquals(1, root.records().size());
        var retired = EscrowFloors.retire(root, java.util.Set.of(admission.operation().id()), Map.of());
        assertTrue(retired.records().isEmpty()); assertTrue(retired.fluidAccounts().isEmpty()); assertTrue(retired.tombstones().isEmpty());
        assertTrue(retired.floors().isEmpty()); assertEquals(root.issuedThrough(), retired.issuedThrough());
    }

    @Test void oversizedNewFormatCargoIsRefusedBeforeChangingTheRootOrNativeCaptures() {
        for (boolean loading : List.of(true, false)) {
            boolean refused = false;
            for (int blob = 6500; blob <= 7900; blob += 100) {
                var c = EscrowCargoEligibilityTest.sample(loading, 1000, blob); var root = empty();
                var saved = EscrowCodec.encode(root); var before = c.before().copy(); var device = c.deviceBefore().copy();
                var result = EscrowCargoEligibility.check(root, List.of(), c.op(), c.before(), c.after(), c.deviceBefore(), c.deviceAfter(), 64_000);
                assertEquals(saved, EscrowCodec.encode(root)); assertEquals(before, c.before()); assertEquals(device, c.deviceBefore());
                if (result.refusal() == EscrowCoordinator.Transfer.CARGO_TOO_LARGE) { assertNull(result.capture()); refused = true; break; }
                assertNotNull(result.capture());
            }
            assertTrue(refused);
        }
    }
}
