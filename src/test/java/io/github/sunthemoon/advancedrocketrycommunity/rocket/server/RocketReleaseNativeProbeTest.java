package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketReleaseNativeProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void destinationAuthorityMustBeLoadedBeforeTheRestartObserverCountsIt() {
        assertFalse(RocketReleaseNativeProbe.authorityReady("H2", true, false));
        assertFalse(RocketReleaseNativeProbe.authorityReady("H2", false, true));
        assertTrue(RocketReleaseNativeProbe.authorityReady("H2", true, true));
        assertTrue(RocketReleaseNativeProbe.authorityReady("H1", true, false));
    }
    private static RocketTransferRecord legacy() {
        var t = GovernedTransferFixture.create().record();
        return RocketTransferRecord.create(t.transferId(), t.logicalRocketId(), t.ownerId(), t.sourceEntityId(),
                t.sourceSnapshot(), t.destinationSnapshot(), t.sourceFlightData(), t.destinationFlightData(), t.requiredFuel(), t.createdAtGameTime());
    }
    @Test void liveTransferMayTemporarilyHaveItsExactTwoRecordedPhysicalCopies() {
        var prepared = legacy();
        var destination = GovernedTransferFixture.id(800);
        var pair = List.of(prepared.sourceEntityId(), destination);
        assertFalse(RocketReleaseNativeProbe.transientPair(prepared, pair));
        var spawned = prepared.destinationSpawned(destination);
        assertTrue(RocketReleaseNativeProbe.transientPair(spawned, pair));
        assertTrue(RocketReleaseNativeProbe.transientPair(spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED), pair));
        var committed = spawned.advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
        assertFalse(RocketReleaseNativeProbe.transientPair(committed, pair));
    }
    @Test void wrongDuplicateOrAdditionalPhysicalCopiesRemainFailures() {
        var t = legacy().destinationSpawned(GovernedTransferFixture.id(800));
        assertFalse(RocketReleaseNativeProbe.transientPair(t, List.of(t.sourceEntityId(), GovernedTransferFixture.id(801))));
        assertFalse(RocketReleaseNativeProbe.transientPair(t, List.of(t.sourceEntityId(), t.sourceEntityId())));
        assertFalse(RocketReleaseNativeProbe.transientPair(t, List.of(t.sourceEntityId(), t.destinationEntityId().orElseThrow(), GovernedTransferFixture.id(801))));
    }
}
