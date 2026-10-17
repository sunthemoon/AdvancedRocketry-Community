package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.EnumMap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Native-vector requirements from W11b2 design 2 section 5.1; no live mutation or proof bypass. */
class EscrowNativeBankPrefixTest {
    private static final ResourceLocation WATER = new ResourceLocation("minecraft", "water");

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void oneStableAdapterIdentitySelectsTheWholeVectorPrefixAndProjection() {
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("rocket").toString(), EscrowAdapterIds.ROCKET);
        var old = EscrowTestFixtures.operation(1).destination();
        var endpoint = new EscrowEndpoint(old.kind(), old.level(), old.identity(), old.logicalRocket(), EscrowAdapterIds.ROCKET,
                old.x(), old.y(), old.z(), EscrowEndpoint.Unit.OXIDIZER, old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
        assertTrue(EscrowAdapterIds.nativeRocket(endpoint));
        assertEquals(EscrowEndpoint.Unit.PRIMARY, endpoint.key().unit()); assertEquals(EscrowEndpoint.Unit.OXIDIZER, endpoint.unit());
        assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, EscrowSignatureKind.endpoint(endpoint));
        assertEquals(EscrowSignatureKind.FIXTURE_RESOURCE_V1, EscrowSignatureKind.endpoint(old));
    }

    @Test void twoPrimaryFillsUseOneContiguousWholeRocketFrontier() {
        var before = emptyBipropellant();
        var first = before.withFluidBank(false, WATER, new CompoundTag(), 1_000);
        var second = first.withFluidBank(false, WATER, new CompoundTag(), 2_000);
        var a = operation(1, EscrowEndpoint.Unit.PRIMARY, before, first);
        var b = operation(2, EscrowEndpoint.Unit.PRIMARY, first, second);
        var ledger = assertDoesNotThrow(() -> EscrowTestFixtures.ledger(a, b));
        var chain = EscrowPrefix.chains(ledger.records()).get(a.destination().key());
        assertEquals(2, chain.size());
        assertEquals(EscrowPrefix.Classification.AFTER,
                EscrowPrefix.classify(ledger.id(), chain, frontier(second, a, b)));
    }

    @Test void singleOxidizerFillUsesTheNativeWholeVectorSignature() {
        var before = emptyBipropellant();
        var after = before.withFluidBank(true, WATER, new CompoundTag(), 1_000);
        var op = operation(1, EscrowEndpoint.Unit.OXIDIZER, before, after);
        var ledger = assertDoesNotThrow(() -> EscrowTestFixtures.ledger(op));
        var chain = EscrowPrefix.chains(ledger.records()).get(op.destination().key());
        assertEquals(EscrowPrefix.Classification.BEFORE, EscrowPrefix.classify(ledger.id(), chain,
                new EscrowPrefix.State(ledger.id(), 0, before.resourceSignature(), List.of())));
        assertEquals(EscrowPrefix.Classification.AFTER,
                EscrowPrefix.classify(ledger.id(), chain, frontier(after, op)));
    }

    @Test void primaryThenOxidizerFillsShareTheAppliedRocketPrefix() {
        var before = emptyBipropellant();
        var primary = before.withFluidBank(false, WATER, new CompoundTag(), 1_000);
        var both = primary.withFluidBank(true, WATER, new CompoundTag(), 1_000);
        var a = operation(1, EscrowEndpoint.Unit.PRIMARY, before, primary);
        var b = operation(2, EscrowEndpoint.Unit.OXIDIZER, primary, both);
        var ledger = assertDoesNotThrow(() -> EscrowTestFixtures.ledger(a, b));
        var state = frontier(both, a, b);
        var chains = EscrowPrefix.chains(ledger.records());
        assertEquals(EscrowPrefix.Classification.AFTER,
                EscrowPrefix.classify(ledger.id(), chains.get(a.destination().key()), state));
        assertEquals(EscrowPrefix.Classification.AFTER,
                EscrowPrefix.classify(ledger.id(), chains.get(b.destination().key()), state));
    }

    @Test void primaryOxidizerPrimaryFillsRemainAContiguousNativeChain() {
        var before = emptyBipropellant();
        var primary = before.withFluidBank(false, WATER, new CompoundTag(), 1_000);
        var both = primary.withFluidBank(true, WATER, new CompoundTag(), 1_000);
        var after = both.withFluidBank(false, WATER, new CompoundTag(), 2_000);
        var a = operation(1, EscrowEndpoint.Unit.PRIMARY, before, primary);
        var b = operation(2, EscrowEndpoint.Unit.OXIDIZER, primary, both);
        var c = operation(3, EscrowEndpoint.Unit.PRIMARY, both, after);
        var ledger = assertDoesNotThrow(() -> EscrowTestFixtures.ledger(a, b, c));
        var chain = EscrowPrefix.chains(ledger.records()).get(a.destination().key());
        assertEquals(3, chain.size());
        assertEquals(EscrowPrefix.Classification.AFTER,
                EscrowPrefix.classify(ledger.id(), chain, frontier(after, a, b, c)));
    }

    @Test void blockEntityUnitsKeepTheirSeparatePrefixIdentitiesAndRecordEquality() {
        var op = EscrowTestFixtures.operation(1);
        var source = op.source();
        var other = new EscrowEndpoint(source.kind(), source.level(), source.identity(), null,
                source.type(), source.x(), source.y(), source.z(), EscrowEndpoint.Unit.OXIDIZER,
                source.beforeEpoch(), source.beforeSignature(), source.afterSignature());
        assertNotEquals(source, other);
        assertNotEquals(source.key(), other.key());
        assertEquals(source, EscrowCodec.endpoint(EscrowCodec.endpoint(source)));
        assertEquals(other, EscrowCodec.endpoint(EscrowCodec.endpoint(other)));
        // Non-native entity fixtures also retain their previous unit-aware identity.
        var entity = op.destination();
        var second = new EscrowEndpoint(entity.kind(), entity.level(), entity.identity(), entity.logicalRocket(),
                entity.type(), entity.x(), entity.y(), entity.z(), EscrowEndpoint.Unit.OXIDIZER,
                entity.beforeEpoch(), entity.beforeSignature(), entity.afterSignature());
        assertNotEquals(entity.key(), second.key());
    }

    @Test void mixedBankPartialRetirementCannotSkipAnEarlierBank() {
        var before = emptyBipropellant();
        var primary = before.withFluidBank(false, WATER, new CompoundTag(), 1_000);
        var both = primary.withFluidBank(true, WATER, new CompoundTag(), 1_000);
        var a = operation(1, EscrowEndpoint.Unit.PRIMARY, before, primary);
        var b = operation(2, EscrowEndpoint.Unit.OXIDIZER, primary, both);
        var ledger = EscrowTestFixtures.ledger(a, b);
        assertThrows(IllegalArgumentException.class, () -> ledger.retire(java.util.Set.of(b.id())));
        var retained = ledger.retire(java.util.Set.of(a.id()));
        assertEquals(List.of(b), retained.records());
        assertEquals(EscrowEndpoint.Unit.OXIDIZER, retained.records().get(0).destination().unit());
        assertEquals(b, EscrowCodec.operation(EscrowCodec.operation(b)));
    }

    @Test void mixedBankSettlementRequiresOneWholeRocketObservationAndBothSources() {
        var before = emptyBipropellant();
        var primary = before.withFluidBank(false, WATER, new CompoundTag(), 1_000);
        var both = primary.withFluidBank(true, WATER, new CompoundTag(), 1_000);
        var a = operation(1, EscrowEndpoint.Unit.PRIMARY, before, primary).withReason(EscrowReason.OTHER_STATE);
        var b = operation(2, EscrowEndpoint.Unit.OXIDIZER, primary, both).withReason(EscrowReason.OTHER_STATE);
        var ledger = EscrowTestFixtures.ledger(a, b);
        var observations = new java.util.ArrayList<EscrowSettlementIntent.Observation>();
        for (var op : List.of(a, b)) {
            observations.add(new EscrowSettlementIntent.Observation(op.source(),
                    new EscrowPrefix.State(ledger.id(), op.source().beforeEpoch(), op.source().beforeSignature(), List.of()), true, 5_000));
        }
        var chosen = new EscrowSettlementIntent.Observation(a.destination(), frontier(both, a, b), true, 2_000);
        observations.add(chosen);
        var actor = EscrowTestFixtures.id(800);
        var preview = EscrowSettlementIntent.preview(ledger, a.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                actor, "operator", 10, observations);
        assertEquals(3, preview.observations().size());
        var intent = preview.confirm(actor, preview.digest(), 11, observations);
        var frozen = assertDoesNotThrow(() -> intent.freeze(ledger));
        assertEquals(frozen, EscrowCodec.decode(EscrowCodec.encode(frozen)));
        assertTrue(frozen.records().stream().allMatch(op -> op.settlement().equals(intent.encode())));
        observations.add(new EscrowSettlementIntent.Observation(b.destination(), chosen.state(), true, 2_000));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(ledger, a.id(),
                EscrowSettlementIntent.Choice.ACCEPT_CURRENT, actor, "operator", 10, observations));
    }

    private static RocketFuelState emptyBipropellant() {
        var allowed = new EnumMap<RocketPropulsion.Role, List<String>>(RocketPropulsion.Role.class);
        allowed.putAll(RocketPropulsion.Settings.DEFAULTS.allowedFluids());
        allowed.put(RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of(WATER.toString()));
        allowed.put(RocketPropulsion.Role.OXIDIZER, List.of(WATER.toString()));
        return RocketFuelState.empty(new RocketPropulsion(RocketPropulsion.Mode.BIPROPELLANT,
                4_000, 4_000, 1, 1, RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(allowed)));
    }

    private static EscrowOperation operation(long sequence, EscrowEndpoint.Unit unit,
            RocketFuelState before, RocketFuelState after) {
        var fixture = EscrowTestFixtures.operation(sequence, 41, sequence - 1);
        var destination = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, fixture.destination().level(),
                fixture.destination().identity(), fixture.destination().logicalRocket(),
                "advancedrocketrycommunity:rocket", 0, 0, 0, unit, sequence - 1,
                before.resourceSignature(), after.resourceSignature());
        return EscrowTestFixtures.nativeModel(new EscrowOperation(fixture.id(), sequence, fixture.owner(), fixture.createdTick(),
                fixture.phase(), fixture.reason(), fixture.payload(),
                RocketNativeResources.signature(fixture.payload()), fixture.source(), destination, fixture.guard()));
    }

    private static EscrowPrefix.State frontier(RocketFuelState fuel, EscrowOperation... applied) {
        var markers = java.util.Arrays.stream(applied)
                .map(op -> new EscrowPrefix.Link(op, op.destination(), "DESTINATION").marker()).toList();
        var wrapper = new RocketEscrowSection(EscrowTestFixtures.id(500), applied.length, markers);
        assertEquals(wrapper, RocketEscrowSection.decode(wrapper.encode()));
        assertEquals(applied.length, fuel.propulsion().orElseThrow().revision());
        return new EscrowPrefix.State(wrapper.ledger(), wrapper.epoch(), fuel.resourceSignature(), wrapper.markers());
    }
}
