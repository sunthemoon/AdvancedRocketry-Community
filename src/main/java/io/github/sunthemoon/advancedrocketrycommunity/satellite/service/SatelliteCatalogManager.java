package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit lifecycle owner for the active immutable satellite definition catalog. */
public final class SatelliteCatalogManager {
    public static final int MAX_STATUS_MESSAGE_CHARS = 2_048;

    private final AtomicReference<State> state = new AtomicReference<>(State.empty());

    public Optional<SatelliteCatalog> current() {
        return Optional.ofNullable(state.get().catalog());
    }

    public ReloadStatus status() {
        return state.get().status();
    }

    public synchronized boolean applyCandidate(DataResult<SatelliteCatalog> candidate) {
        State previous = state.get();
        if (candidate.error().isPresent()) {
            state.set(new State(
                    previous.catalog(),
                    new ReloadStatus(
                            previous.catalog() != null,
                            false,
                            previous.status().generation(),
                            previous.status().definitionCount(),
                            truncate(candidate.error().orElseThrow().message())
                    )
            ));
            return false;
        }
        SatelliteCatalog catalog = candidate.result().orElseThrow();
        state.set(new State(
                catalog,
                new ReloadStatus(
                        true,
                        true,
                        previous.status().generation() + 1L,
                        catalog.size(),
                        "accepted"
                )
        ));
        return true;
    }

    public void clear() {
        state.set(State.empty());
    }

    private static String truncate(String message) {
        if (message.length() <= MAX_STATUS_MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, MAX_STATUS_MESSAGE_CHARS - 3) + "...";
    }

    public record ReloadStatus(
            boolean ready,
            boolean lastReloadAccepted,
            long generation,
            int definitionCount,
            String message
    ) {
    }

    private record State(SatelliteCatalog catalog, ReloadStatus status) {
        private static State empty() {
            return new State(null, new ReloadStatus(false, false, 0L, 0, "not loaded"));
        }
    }
}
