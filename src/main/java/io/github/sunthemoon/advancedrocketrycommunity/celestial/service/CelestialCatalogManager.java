package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Explicit lifecycle owner for the active immutable server definition catalog. */
public final class CelestialCatalogManager {
    public static final int MAX_STATUS_MESSAGE_CHARS = 2_048;

    private final AtomicReference<State> state = new AtomicReference<>(State.empty());

    public Optional<CelestialCatalog> current() {
        return Optional.ofNullable(state.get().catalog());
    }

    public ReloadStatus status() {
        return state.get().status();
    }

    public synchronized boolean applyCandidate(DataResult<CelestialCatalog> candidate) {
        State previous = state.get();
        if (candidate.error().isPresent()) {
            String message = truncate(candidate.error().orElseThrow().message());
            state.set(new State(
                    previous.catalog(),
                    new ReloadStatus(
                            previous.catalog() != null,
                            false,
                            previous.status().generation(),
                            previous.status().bodyCount(),
                            message
                    )
            ));
            return false;
        }

        CelestialCatalog catalog = candidate.result().orElseThrow();
        long generation = previous.status().generation() + 1L;
        state.set(new State(
                catalog,
                new ReloadStatus(true, true, generation, catalog.size(), "accepted")
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
            int bodyCount,
            String message
    ) {
    }

    private record State(CelestialCatalog catalog, ReloadStatus status) {
        private static State empty() {
            return new State(
                    null,
                    new ReloadStatus(false, false, 0L, 0, "not loaded")
            );
        }
    }
}
