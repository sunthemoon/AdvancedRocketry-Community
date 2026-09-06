package io.github.sunthemoon.advancedrocketrycommunity.travel.route.service;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteLimits;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Lifecycle owner that atomically retains the last complete valid route graph. */
public final class RouteCatalogManager {
    private final AtomicReference<State> state = new AtomicReference<>(State.empty());

    public Optional<RouteCatalog> current() {
        return Optional.ofNullable(state.get().catalog());
    }

    public ReloadStatus status() {
        return state.get().status();
    }

    public synchronized boolean applyCandidate(DataResult<RouteCatalog> candidate) {
        State previous = state.get();
        if (candidate.error().isPresent()) {
            String message = truncate(candidate.error().orElseThrow().message());
            state.set(new State(
                    previous.catalog(),
                    new ReloadStatus(
                            previous.catalog() != null,
                            false,
                            previous.status().generation(),
                            previous.status().routeCount(),
                            previous.status().anchorCount(),
                            message
                    )
            ));
            return false;
        }

        RouteCatalog catalog = candidate.result().orElseThrow();
        long generation = previous.status().generation() + 1L;
        state.set(new State(
                catalog,
                new ReloadStatus(
                        true,
                        true,
                        generation,
                        catalog.routeCount(),
                        catalog.anchorCount(),
                        "accepted"
                )
        ));
        return true;
    }

    public void clear() {
        state.set(State.empty());
    }

    private static String truncate(String message) {
        if (message.length() <= RouteLimits.MAX_STATUS_MESSAGE_CHARS) {
            return message;
        }
        return message.substring(0, RouteLimits.MAX_STATUS_MESSAGE_CHARS - 3) + "...";
    }

    public record ReloadStatus(
            boolean ready,
            boolean lastReloadAccepted,
            long generation,
            int routeCount,
            int anchorCount,
            String message
    ) {
    }

    private record State(RouteCatalog catalog, ReloadStatus status) {
        private static State empty() {
            return new State(null, new ReloadStatus(false, false, 0L, 0, 0, "not loaded"));
        }
    }
}
