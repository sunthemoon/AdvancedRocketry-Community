package io.github.sunthemoon.advancedrocketrycommunity.rocket.scan;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import java.util.Objects;
import java.util.Optional;

/** One already-loaded world observation. No method on this type can request a chunk load. */
public final class RocketScanObservation {
    public enum Kind {
        EMPTY,
        BOUNDARY,
        MOVABLE,
        FORBIDDEN,
        UNSUPPORTED_BLOCK_ENTITY,
        UNLOADED
    }

    private final Kind kind;
    private final RocketBlockState state;
    private final RocketBlockMetrics metrics;
    private final RocketBlockEntityPayload payload;
    private final String detail;

    private RocketScanObservation(
            Kind kind,
            RocketBlockState state,
            RocketBlockMetrics metrics,
            RocketBlockEntityPayload payload,
            String detail
    ) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.state = state;
        this.metrics = metrics;
        this.payload = payload;
        this.detail = detail == null ? "" : detail;
        if (kind == Kind.MOVABLE) {
            Objects.requireNonNull(state, "movable state");
            Objects.requireNonNull(metrics, "movable metrics");
        } else if (state != null || metrics != null || payload != null) {
            throw new IllegalArgumentException("Only a movable observation may carry captured data");
        }
    }

    public static RocketScanObservation movable(
            RocketBlockState state,
            RocketBlockMetrics metrics,
            RocketBlockEntityPayload payload
    ) {
        return new RocketScanObservation(Kind.MOVABLE, state, metrics, payload, "");
    }

    public static RocketScanObservation movable(RocketBlockState state, RocketBlockMetrics metrics) {
        return movable(state, metrics, null);
    }

    public static RocketScanObservation empty() {
        return simple(Kind.EMPTY, "air");
    }

    public static RocketScanObservation boundary(String detail) {
        return simple(Kind.BOUNDARY, detail);
    }

    public static RocketScanObservation forbidden(String detail) {
        return simple(Kind.FORBIDDEN, detail);
    }

    public static RocketScanObservation unsupportedBlockEntity(String detail) {
        return simple(Kind.UNSUPPORTED_BLOCK_ENTITY, detail);
    }

    public static RocketScanObservation unloaded() {
        return simple(Kind.UNLOADED, "unloaded chunk");
    }

    private static RocketScanObservation simple(Kind kind, String detail) {
        return new RocketScanObservation(kind, null, null, null, detail);
    }

    public Kind kind() {
        return kind;
    }

    public RocketBlockState state() {
        if (state == null) {
            throw new IllegalStateException("Observation does not contain a block state");
        }
        return state;
    }

    public RocketBlockMetrics metrics() {
        if (metrics == null) {
            throw new IllegalStateException("Observation does not contain block metrics");
        }
        return metrics;
    }

    public Optional<RocketBlockEntityPayload> payload() {
        return Optional.ofNullable(payload);
    }

    public String detail() {
        return detail;
    }
}
