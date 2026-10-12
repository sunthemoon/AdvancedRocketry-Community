package io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Client cache for the C17c beacon marker snapshot: the last snapshot received, cleared on logout and on a Level
 * change. Nothing reads it yet; the finder display belongs to C17c.
 */
public final class BeaconMarkerCache {
    private static final AtomicReference<BeaconMarkerSnapshotPacket> SNAPSHOT =
            new AtomicReference<>(BeaconMarkerSnapshotPacket.EMPTY);

    private BeaconMarkerCache() {
    }

    public static void accept(BeaconMarkerSnapshotPacket snapshot) {
        SNAPSHOT.set(snapshot);
    }

    public static BeaconMarkerSnapshotPacket snapshot() {
        return SNAPSHOT.get();
    }

    public static void clear() {
        SNAPSHOT.set(BeaconMarkerSnapshotPacket.EMPTY);
    }
}
