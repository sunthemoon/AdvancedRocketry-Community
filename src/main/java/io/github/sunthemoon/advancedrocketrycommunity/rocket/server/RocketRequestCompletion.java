package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

/** Player request lifecycle is separate from retained transfer authority. */
record RocketRequestCompletion(Runnable accepted, Runnable discarded) {
    static final RocketRequestCompletion NONE = new RocketRequestCompletion(() -> { }, () -> { });
    RocketRequestCompletion { java.util.Objects.requireNonNull(accepted); java.util.Objects.requireNonNull(discarded); }
}
