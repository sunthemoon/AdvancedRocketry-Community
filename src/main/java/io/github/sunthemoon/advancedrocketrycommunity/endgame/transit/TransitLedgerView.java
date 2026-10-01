package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * What an endpoint's reconciliation needs from the ledger (ADR-054 section 11). The service implements it over the
 * endgame root: every mutation is one coalesced root change, and every audit line goes to the endgame audit.
 */
public interface TransitLedgerView {
    long saveEpoch();

    long dispatchedThrough(UUID source);

    Optional<TransitRecord> record(TransitKey key);

    /** Records whose destination or paid endpoint is this endpoint, in key order. */
    List<TransitRecord> forEndpoint(UUID endpoint);

    boolean registrationDurable(UUID endpoint);

    /** Step 2: {@code OK}, {@code ROOT_FULL} (wait) or {@code RATE_LIMITED} (the per-tick registration cap). */
    EndgameCode register(UUID source, UUID owner, OutboxEntry entry, long now);

    /** One record of this tick's reconciliation budget (at most 64 across all endpoints); false when spent. */
    boolean takeReconciliation();

    void claim(TransitKey key, UUID endpoint);

    /** The ledger was behind this endpoint's claim ({@code CLAIM_RECOVERED}). */
    void recover(TransitKey key, UUID endpoint);

    void acknowledge(TransitKey key);

    /** The payload no longer decodes: the record keeps it raw and is never claimed. */
    void quarantine(TransitKey key);

    /** One audit line for this endpoint. */
    void audit(String action, String result, UUID endpoint, @Nullable UUID owner, String fields);
}
