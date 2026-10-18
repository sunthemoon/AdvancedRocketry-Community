package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

/**
 * Player request lifecycle is separate from retained transfer authority. {@code requester} is the initiating player of
 * a C2S intent, or null for admin and release-test launches (W90b: a warm-up's per-player attribution).
 */
record RocketRequestCompletion(Runnable accepted, Runnable discarded, java.util.UUID requester) {
    static final RocketRequestCompletion NONE = new RocketRequestCompletion(() -> { }, () -> { });
    RocketRequestCompletion { java.util.Objects.requireNonNull(accepted); java.util.Objects.requireNonNull(discarded); }
    RocketRequestCompletion(Runnable accepted, Runnable discarded) { this(accepted, discarded, null); }
}
