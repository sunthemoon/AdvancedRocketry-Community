package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

/** Unforgeable S1 capability from the sidecar owner, invalid after acknowledged S2 or shutdown. */
public final class RocketReleasePermit {
    private final RocketRejoinSavedData owner;
    private final RocketRejoinRoot.Batch batch;
    RocketReleasePermit(RocketRejoinSavedData owner, RocketRejoinRoot.Batch batch) { this.owner = owner; this.batch = batch; }
    public RocketRejoinRoot.Batch batch() { return batch; }
    public boolean valid() { return owner.releaseAcknowledged(batch); }
}
