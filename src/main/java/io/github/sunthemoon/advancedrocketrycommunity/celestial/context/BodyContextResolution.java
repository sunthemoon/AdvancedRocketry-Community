package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

import java.util.Objects;
import java.util.Optional;

/** Tri-state result that distinguishes an unrelated resolver from an authoritative miss. */
public record BodyContextResolution(Status status, Optional<BodyContext> context) {
    public BodyContextResolution {
        Objects.requireNonNull(status, "status");
        context = Objects.requireNonNull(context, "context");
        if ((status == Status.RESOLVED) != context.isPresent()) {
            throw new IllegalArgumentException("Body context resolution status and value disagree");
        }
    }

    public static BodyContextResolution unhandled() {
        return new BodyContextResolution(Status.UNHANDLED, Optional.empty());
    }

    public static BodyContextResolution unresolved() {
        return new BodyContextResolution(Status.UNRESOLVED, Optional.empty());
    }

    public static BodyContextResolution resolved(BodyContext context) {
        return new BodyContextResolution(Status.RESOLVED, Optional.of(context));
    }

    public enum Status {
        UNHANDLED,
        UNRESOLVED,
        RESOLVED
    }
}
