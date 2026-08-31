package io.github.sunthemoon.advancedrocketrycommunity.celestial.network;

import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Client display cache that retains the last valid snapshot on future/malformed input. */
public final class CelestialClientCache {
    private static final AtomicReference<State> STATE = new AtomicReference<>(State.empty());

    private CelestialClientCache() {
    }

    public static AcceptResult accept(CelestialSnapshotPacket packet) {
        State previous = STATE.get();
        if (packet.schemaVersion() != CelestialSnapshotCodec.SCHEMA_VERSION) {
            STATE.set(new State(
                    previous.snapshot(),
                    previous.generation(),
                    AcceptResult.UNSUPPORTED_SCHEMA,
                    "unsupported schema " + packet.schemaVersion()
            ));
            return AcceptResult.UNSUPPORTED_SCHEMA;
        }

        DataResult<CelestialSnapshot> decoded = CelestialSnapshotCodec.decode(packet.payload());
        if (decoded.error().isPresent()) {
            STATE.set(new State(
                    previous.snapshot(),
                    previous.generation(),
                    AcceptResult.INVALID_PAYLOAD,
                    decoded.error().orElseThrow().message()
            ));
            return AcceptResult.INVALID_PAYLOAD;
        }

        CelestialSnapshot snapshot = decoded.result().orElseThrow();
        STATE.set(new State(
                snapshot,
                packet.catalogGeneration(),
                AcceptResult.ACCEPTED,
                "accepted"
        ));
        return AcceptResult.ACCEPTED;
    }

    public static Optional<CelestialSnapshot> snapshot() {
        return Optional.ofNullable(STATE.get().snapshot());
    }

    public static long generation() {
        return STATE.get().generation();
    }

    public static AcceptResult lastResult() {
        return STATE.get().lastResult();
    }

    public static String statusMessage() {
        return STATE.get().statusMessage();
    }

    public static void clear() {
        STATE.set(State.empty());
    }

    public enum AcceptResult {
        ACCEPTED,
        UNSUPPORTED_SCHEMA,
        INVALID_PAYLOAD,
        EMPTY
    }

    private record State(
            CelestialSnapshot snapshot,
            long generation,
            AcceptResult lastResult,
            String statusMessage
    ) {
        private static State empty() {
            return new State(null, 0L, AcceptResult.EMPTY, "empty");
        }
    }
}
