package io.github.sunthemoon.advancedrocketrycommunity.satellite.network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Client display cache for the latest survey scan result; the client shows it once and then takes it. */
public final class SurveyScanResultCache {
    private static final AtomicReference<Optional<SurveyScanResultPacket>> PENDING =
            new AtomicReference<>(Optional.empty());

    private SurveyScanResultCache() {
    }

    public static void accept(SurveyScanResultPacket packet) {
        PENDING.set(Optional.of(packet));
    }

    /** The unseen result, if any; taking it clears it. */
    public static Optional<SurveyScanResultPacket> take() {
        return PENDING.getAndSet(Optional.empty());
    }

    public static void clear() {
        PENDING.set(Optional.empty());
    }
}
