package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * ADR-054 section 11 record, identity {@code (source, seq)}: at most 2.5 KiB with its payload. A durably acknowledged
 * record keeps no payload (a <em>stub</em>, at most 128 bytes) until its paid endpoint's chunk shows the move saved.
 * Durable means {@code save_epoch > record epoch} (ADR-050 section 2).
 *
 * @param paidEndpoint the endpoint that claimed it, only while {@code CLAIMED}
 * @param ackEpoch     the save epoch of the acknowledgement, 0 while unacknowledged
 * @param redirected   whether an owner or operator redirect moved it
 */
public record TransitRecord(TransitKey key, EndgameSystem system, UUID owner, UUID destination,
                            @Nullable TransitPayload payload, int paidFe, long dispatchEpoch, long arriveAt, State state,
                            @Nullable UUID paidEndpoint, boolean acknowledged, long ackEpoch, boolean redirected) {
    public enum State {
        IN_TRANSIT,
        ARRIVED,
        CLAIMED,
        /** The payload no longer decodes; kept raw until an operator purge. */
        QUARANTINED
    }

    public TransitRecord {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(state, "state");
        if (paidFe < 0 || paidFe > OutboxEntry.MAX_PAID_FE) {
            throw new IllegalArgumentException("paid_fe is outside its bound");
        }
        if (dispatchEpoch < 1L) {
            throw new IllegalArgumentException("A dispatch epoch is positive");
        }
        if ((state == State.CLAIMED) != (paidEndpoint != null)) {
            throw new IllegalArgumentException("Only a claimed record names its paid endpoint");
        }
        if (acknowledged && state != State.CLAIMED) {
            throw new IllegalArgumentException("Only a claimed record is acknowledged");
        }
        if (acknowledged ? ackEpoch < 1L : ackEpoch != 0L) {
            throw new IllegalArgumentException("An acknowledgement carries its epoch, and only it");
        }
        if (payload == null && !acknowledged) {
            throw new IllegalArgumentException("Only an acknowledged record drops its payload");
        }
    }

    public static TransitRecord registered(UUID source, OutboxEntry entry, UUID owner, long epoch, long now) {
        return new TransitRecord(new TransitKey(source, entry.seq()), entry.system(), owner, entry.destination(),
                entry.payload(), entry.paidFe(), epoch, now + entry.travel(), State.IN_TRANSIT, null, false, 0L,
                false);
    }

    public boolean stub() {
        return payload == null;
    }

    public boolean durable(long saveEpoch) {
        return saveEpoch > dispatchEpoch;
    }

    public boolean ackDurable(long saveEpoch) {
        return acknowledged && saveEpoch > ackEpoch;
    }

    /** Names this ID as source, destination or paid endpoint: its tombstone is pinned (section 11). */
    public boolean names(UUID id) {
        return key.source().equals(id) || destination.equals(id) || id.equals(paidEndpoint);
    }

    public TransitRules.RecordFacts facts(UUID endpoint, long saveEpoch) {
        return new TransitRules.RecordFacts(state, destination.equals(endpoint), endpoint.equals(paidEndpoint),
                durable(saveEpoch), acknowledged, ackDurable(saveEpoch));
    }

    public TransitRecord arrived() {
        return with(State.ARRIVED, null, false, 0L, destination);
    }

    /** Claimed at the endpoint, with or without items ({@code CLAIM_RECOVERED}). */
    public TransitRecord claimed(UUID endpoint) {
        return with(State.CLAIMED, endpoint, false, 0L, destination);
    }

    public TransitRecord acknowledged(long epoch) {
        return with(State.CLAIMED, paidEndpoint, true, epoch, destination);
    }

    /** An unacknowledged claim returns to arrived (removal settlement or retirement without live state). */
    public TransitRecord returned() {
        return with(State.ARRIVED, null, false, 0L, destination);
    }

    public TransitRecord redirectedTo(UUID endpoint) {
        return new TransitRecord(key, system, owner, endpoint, payload, paidFe, dispatchEpoch, arriveAt, state,
                paidEndpoint, acknowledged, ackEpoch, true);
    }

    public TransitRecord quarantined() {
        return new TransitRecord(key, system, owner, destination, payload, paidFe, dispatchEpoch, arriveAt,
                State.QUARANTINED, null, false, 0L, redirected);
    }

    /** The stub of a durably acknowledged record: its payload is no longer needed. */
    public TransitRecord asStub() {
        return new TransitRecord(key, system, owner, destination, null, paidFe, dispatchEpoch, arriveAt, state,
                paidEndpoint, acknowledged, ackEpoch, redirected);
    }

    private TransitRecord with(State next, @Nullable UUID paid, boolean ack, long epoch, UUID target) {
        return new TransitRecord(key, system, owner, target, payload, paidFe, dispatchEpoch, arriveAt, next, paid, ack,
                epoch, redirected);
    }
}
