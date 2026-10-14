package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
        return new EscrowOperation(fixture.id(), sequence, fixture.owner(), fixture.createdTick(),
                fixture.phase(), fixture.reason(), fixture.payload(),
                RocketNativeResources.signature(fixture.payload()), fixture.source(), destination, fixture.guard());
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
