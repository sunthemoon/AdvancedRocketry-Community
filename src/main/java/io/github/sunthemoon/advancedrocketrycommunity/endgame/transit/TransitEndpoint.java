package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.Optional;
import java.util.UUID;

/**
 * A loaded ledger endpoint (railgun, elevator anchor or terminal) as the ledger's END-tick pass sees it (ADR-054
 * section 7 "where work runs"): its identity, its transit state, its receive buffer, and a way to mark its block entity
 * changed. A frozen endpoint (retired, or quarantined) runs none of the reconciliation rows.
 */
public interface TransitEndpoint {
    UUID endpointId();

    Optional<UUID> endpointOwner();

    /** Retired or quarantined: its contents are frozen and only a resolve or a removal settles them. */
    boolean transitFrozen();

    TransitSourceState source();

    TransitDestinationState destination();

    TransitDestinationState.ReceiveBuffer receiveBuffer();

    /** The block entity's {@code setChanged()}, after any change to its transit state. */
    void transitChanged();
}
