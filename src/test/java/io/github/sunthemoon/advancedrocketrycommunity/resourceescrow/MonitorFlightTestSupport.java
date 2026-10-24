package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import java.util.List;

/** Package bridge for the monitor's checked-acceptance component tests; no production API is widened. */
public final class MonitorFlightTestSupport {
    private MonitorFlightTestSupport() { }
    private static EscrowGovernedDispatch.Authority authority(GovernedTransferFixture.Fixture fixture, RocketTransferSavedData journal) {
        var arm = fixture.record().nativeTransfer().orElseThrow().armed();
        var root = new EscrowLedger(arm.ledger(), 0, arm.generation(), List.of(), List.of(), List.of(), arm.serial(), List.of(), List.of(arm));
        return EscrowGovernedDispatch.acquire(() -> root, () -> true, journal, journal.find(fixture.record().transferId()).orElse(fixture.record()));
    }
    public static boolean authorized(GovernedTransferFixture.Fixture fixture, RocketTransferSavedData journal) {
        return authority(fixture, journal) != null;
    }
    public static RocketFlightData countdown(GovernedTransferFixture.Fixture fixture, RocketTransferSavedData journal) {
        return EscrowGovernedSourceAction.issue(authority(fixture, journal), EscrowGovernedSourceAction.Action.START_COUNTDOWN,
                fixture.source(), 20, () -> true).consume(fixture::source);
    }
}
