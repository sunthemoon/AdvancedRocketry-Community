package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.UUID;

/** Immediate admission result, not a promise that the asynchronous release has completed. */
public record RocketReleaseResult(Status status, UUID release, String stage) {
    public enum Status { ADMITTED, PENDING, REFUSED, UNAVAILABLE }
    public boolean accepted() { return status == Status.ADMITTED || status == Status.PENDING; }
    public static RocketReleaseResult refused() { return new RocketReleaseResult(Status.REFUSED, null, "NONE"); }
    public static RocketReleaseResult unavailable() { return new RocketReleaseResult(Status.UNAVAILABLE, null, "NONE"); }
}
