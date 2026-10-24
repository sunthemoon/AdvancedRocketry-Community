package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketReleasePlanTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static UUID id(long n) { return GovernedTransferFixture.id(n); }
    static RocketTransferRecord legacy() {
        var g = GovernedTransferFixture.create(1, 3, false).record();
        return RocketTransferRecord.create(g.transferId(), g.logicalRocketId(), g.ownerId(), g.sourceEntityId(),
                g.sourceSnapshot(), g.destinationSnapshot(), g.sourceFlightData(), g.destinationFlightData(), g.requiredFuel(), g.createdAtGameTime());
    }
    static RocketFlightData source(RocketTransferRecord t) { return t.sourceFlightData().markFailed(10).recover(true, 10); }
    static RocketReleasePlan plan(RocketTransferRecord t, Hold hold, RocketFlightData flight, Set<UUID> elsewhere) {
        return RocketReleasePlan.create(RocketRejoinRoot.empty(), id(900), t, hold,
                hold == Hold.H1 ? t.sourceEntityId() : t.destinationEntityId().orElseThrow(),
                hold == Hold.H1 ? t.sourceSnapshot() : t.destinationSnapshot(), flight, Set.of(id(100))::contains, elsewhere);
    }

    @Test void h1UsesTransferManifestAndClassifiesTheDepartedRiderWithoutMovingTheNewcomer() {
        var t = legacy(); var live = source(t);
        var me = live.passengers().remove(id(101)).assign(id(700)).orElseThrow();
        var plan = plan(t, Hold.H1, live.withPassengers(me), Set.of(id(101)));
        assertEquals(List.of(id(101), id(102)), plan.batch().released());
        assertEquals(Placement.NOT_REQUIRED, plan.entries().get(0).placement());
        assertEquals(Placement.PENDING, plan.entries().get(1).placement());
        assertTrue(RocketReleasePlan.filter(me, plan.batch().released()).assignment(id(700)).isPresent());
        assertEquals(2, plan.batch().slots().size());
        assertEquals(plan.batch(), RocketRejoinCodec.decode(RocketRejoinCodec.encode(plan.admit(RocketRejoinRoot.empty()))).batches().get(0));
    }

    @Test void h2UsesCurrentManifestAndOnlyRemovesReleasedSeatsFromTheCurrentValue() {
        var t = legacy().destinationSpawned(id(800)).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        var landed = t.destinationFlightData().land(10);
        var me = landed.passengers().remove(id(101)).assign(id(700)).orElseThrow();
        var plan = plan(t, Hold.H2, landed.withPassengers(me), Set.of());
        assertEquals(Set.of(id(700), id(102)), Set.copyOf(plan.batch().released()));
        assertTrue(plan.entries().stream().allMatch(e -> e.placement() == Placement.PENDING));
        assertTrue(RocketReleasePlan.filter(me.remove(id(100)), plan.batch().released()).assignments().isEmpty());
        assertEquals(me.seatCapacity(), RocketReleasePlan.filter(me, plan.batch().released()).seatCapacity());
    }

    @Test void refusesDoubleSeatsGovernedRecordsEmptySetsAndConflictingObligationsWithoutMutation() {
        var t = legacy(); var live = source(t); var root = RocketRejoinRoot.empty();
        assertThrows(IllegalArgumentException.class, () -> plan(t, Hold.H1, live, Set.of(id(102))));
        assertThrows(IllegalArgumentException.class, () -> plan(GovernedTransferFixture.create().record(), Hold.H1, live, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> RocketReleasePlan.create(root, id(900), t, Hold.H1,
                t.sourceEntityId(), t.sourceSnapshot(), live, p -> true, Set.of()));
        var admitted = plan(t, Hold.H1, live, Set.of()).admit(root);
        assertThrows(IllegalArgumentException.class, () -> RocketReleasePlan.create(admitted, id(901), t, Hold.H1,
                t.sourceEntityId(), t.sourceSnapshot(), live, p -> false, Set.of()));
        assertEquals(RocketRejoinRoot.empty(), root);
    }

    @Test void scrubIsLegacyOnlyAndChangesNeitherResourcesNorTransferEnvelope() {
        var t = legacy(); var scrub = t.releasePassengers(Set.of(id(101)));
        assertNotEquals(t.checksum(), scrub.checksum());
        assertEquals(t.sourceSnapshot(), scrub.sourceSnapshot()); assertEquals(t.destinationSnapshot(), scrub.destinationSnapshot());
        assertEquals(t.sourceFlightData().fuel(), scrub.sourceFlightData().fuel());
        assertEquals(t.destinationFlightData().fuel(), scrub.destinationFlightData().fuel());
        assertEquals(t.sourceFlightData().plan(), scrub.sourceFlightData().plan());
        assertEquals(t.phase(), scrub.phase()); assertEquals(t.destinationEntityId(), scrub.destinationEntityId());
        assertEquals(scrub.sourceFlightData().passengers(), scrub.destinationFlightData().passengers());
        assertTrue(scrub.sourceFlightData().passengers().assignment(id(101)).isEmpty());
        assertEquals(scrub.checksum(), scrub.releasePassengers(Set.of(id(101))).checksum());
        assertThrows(IllegalArgumentException.class, () -> GovernedTransferFixture.create().record().releasePassengers(Set.of(id(100))));
    }
}
