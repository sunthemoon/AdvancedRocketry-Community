package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

/**
 * Player request lifecycle is separate from retained transfer authority. {@code requester} is the initiating player of
 * a C2S intent or the monitor's owner for quota attribution. Only a player intent requires an online owner.
 * Admin and release-test launches have neither attribution nor an online requirement.
 */
record RocketRequestCompletion(Runnable accepted, Runnable discarded, java.util.UUID requester, boolean requiresOnline) {
    static final RocketRequestCompletion NONE = new RocketRequestCompletion(() -> { }, () -> { });
    RocketRequestCompletion { java.util.Objects.requireNonNull(accepted); java.util.Objects.requireNonNull(discarded); }
    RocketRequestCompletion(Runnable accepted, Runnable discarded, java.util.UUID requester) { this(accepted, discarded, requester, requester != null); }
    RocketRequestCompletion(Runnable accepted, Runnable discarded) { this(accepted, discarded, null); }
}
