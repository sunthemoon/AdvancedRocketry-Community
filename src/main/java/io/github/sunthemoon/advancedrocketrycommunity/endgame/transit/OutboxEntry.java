package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import java.util.Objects;
import java.util.UUID;

/**
 * ADR-054 section 11 source state: one escrowed payload at its source, {@code {seq, destination, payload, paid_fe,
 * travel, system}}, with the travel time fixed at escrow (review R1-L11).
 */
public record OutboxEntry(long seq, UUID destination, TransitPayload payload, int paidFe, int travel,
                          EndgameSystem system) {
    public static final int MAX_PAID_FE = 1_000_000;
    public static final int MAX_TRAVEL_TICKS = 600;

    public OutboxEntry {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(system, "system");
        if (seq < 1L) {
            throw new IllegalArgumentException("An outbox seq starts at 1");
        }
        if (paidFe < 0 || paidFe > MAX_PAID_FE) {
            throw new IllegalArgumentException("paid_fe is outside 0.." + MAX_PAID_FE);
        }
        if (travel < 1 || travel > MAX_TRAVEL_TICKS) {
            throw new IllegalArgumentException("travel is outside 1.." + MAX_TRAVEL_TICKS);
        }
        if (system != EndgameSystem.RAILGUN && system != EndgameSystem.SPACE_ELEVATOR) {
            throw new IllegalArgumentException("Only railgun and elevator cargo uses the ledger");
        }
    }
}
