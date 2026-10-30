package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6OrdinaryProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyTheExactOrdinarySelectionIsEnabled() {
        assertTrue(EscrowLoaderS6OrdinaryProbe.selected(true, "LF_S6_F9_RESPAWN"));
        assertFalse(EscrowLoaderS6OrdinaryProbe.selected(false, "LF_S6_F9_RESPAWN"));
        for (var cut : new String[] {null, "LF_S6_F9", "LF_S6_F9_RESPAWN_OTHER", "LF_M_K7"}) {
            assertFalse(EscrowLoaderS6OrdinaryProbe.selected(true, cut));
        }
    }
    @Test void historicalRecordRetainsTheFirstTypedDebitAndAddsExactlyOneSecondDebit() {
        var from = snapshot(); var base = settled(); var wrapper = new RocketEscrowSection(UUID.randomUUID(), 0, List.of());
        var owner = UUID.randomUUID(); var source = UUID.randomUUID(); var transfer = UUID.randomUUID(); var dest = UUID.randomUUID();
        var record = EscrowLoaderS6OrdinaryProbe.historical(from, base, wrapper, owner, source, transfer, dest, 300);
        assertNotNull(record); assertEquals(2, record.schemaVersion()); assertTrue(record.nativeTransfer().isEmpty());
        assertEquals(RocketTransferPhase.COMMITTED, record.phase()); assertEquals(dest, record.destinationEntityId().orElseThrow());
        assertEquals(base.fuel(), record.sourceFlightData().fuel()); assertSame(from, record.sourceSnapshot());
        assertEquals(500, base.fuel().amount()); assertEquals(250, record.destinationFlightData().fuel().amount());
        assertEquals(3, record.destinationFlightData().fuel().propulsion().orElseThrow().revision());
        assertEquals(List.of(base.fuel().committedDebits().get(0), transfer), record.destinationFlightData().fuel().committedDebits());
        assertEquals(250, record.sourceFlightData().plan().orElseThrow().propulsionDebit().primary());
        assertEquals(new RocketPosition(96, 100, 64), record.destinationSnapshot().sourceOrigin());
        assertEquals(440, record.sourceFlightData().stateStartedGameTime());
        assertEquals(460, record.destinationFlightData().stateStartedGameTime());
        assertEquals(RocketFlightState.LANDED, base.state()); assertEquals(0, wrapper.epoch()); assertNotNull(wrapper.ledger());
    }
    @Test void historicalSetupDoesNotAcceptAnUnsettledCreditedOrUnboundSource() {
        var from = snapshot(); var base = settled(); var bound = new RocketEscrowSection(UUID.randomUUID(), 0, List.of());
        for (var section : List.of(RocketEscrowSection.neutral(), new RocketEscrowSection(bound.ledger(), 1, List.of()))) {
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6OrdinaryProbe.historical(from, base, section,
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 300));
        }
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6OrdinaryProbe.historical(from, base, bound,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 239));
    }
    @Test void retainedSettledSourceMakesHistoricalCommittedRespawnStructurallyUnreachable() {
        var base = settled(); var source = UUID.randomUUID(); var logical = base.logicalRocketId();
        var wrapper = new RocketEscrowSection(UUID.randomUUID(), 0, List.of());
        var record = EscrowLoaderS6OrdinaryProbe.historical(snapshot(), base, wrapper, UUID.randomUUID(), source,
                UUID.randomUUID(), UUID.randomUUID(), 300);
        var action = RocketTransferRecoveryDecision.decide(record.phase(), new RocketTransferPresence(true, false));
        assertEquals(RocketTransferRecoveryAction.KEEP_SOURCE, action);
        assertNotEquals(RocketTransferRecoveryAction.REBUILD_DESTINATION, action);
        var ledger = new EscrowLedger(wrapper.ledger(), 1, 6, List.of(), List.of(), List.of(), 1, List.of(), List.of());
        assertNull(EscrowRecoveryFence.reason(ledger, logical, record.transferId(), action,
                List.of(new EscrowRecoveryFence.Rocket(source, wrapper.epoch(), false, true)), List.of()),
                "The epoch-zero source return is permitted; this is not an escrow-fence failure");
        assertEquals(500, record.sourceFlightData().fuel().amount());
        assertEquals(1, record.sourceFlightData().fuel().committedDebits().size());
        assertEquals(RocketTransferRecoveryAction.REBUILD_DESTINATION,
                RocketTransferRecoveryDecision.decide(record.phase(), new RocketTransferPresence(false, false)),
                "W77's missing-source setup has a different product action");
    }
    private static RocketStructureSnapshot snapshot() {
        return RocketStructureSnapshot.create(UUID.randomUUID(), RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(80, 100, 64),
                List.of(new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of()))),
                List.of(new RocketPosition(0, 0, 0)), new RocketStats(1, 100, 1000, 2000, 1, 1, 1, 0), 0, mono(2000));
    }
    private static RocketFlightData settled() {
        var first = UUID.randomUUID();
        var fuel = RocketFuelState.empty(mono(2000)).withFluidBank(false, ROCKET_FUEL, null, 1000);
        var plan = new RocketFlightPlan(3, first, RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.MOON.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), RocketFlightPlanner.EARTH.dimensionId(), 500, 0)
                .withPropulsionDebit(new RocketPropulsionDebit(500, 0, 1, fuel.resourceSignature(), RocketNativeResources.hash("first")));
        return RocketFlightData.initial(UUID.randomUUID(), 2000, 1, RocketFlightPlanner.EARTH.bodyId(),
                RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(64, 100, 64), 0).withFuel(fuel, 0)
                .withPlan(plan).startCountdown(0).completeCountdown(60).beginTransit(first, 140)
                .arriveAtDestination(fuel.debit(plan).state(), RocketFlightPlanner.MOON.bodyId(),
                        RocketFlightPlanner.EARTH.dimensionId(), new RocketPosition(80, 100, 64), 160).land(240);
    }
}
