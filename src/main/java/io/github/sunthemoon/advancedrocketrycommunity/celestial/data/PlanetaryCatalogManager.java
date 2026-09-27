package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.binding.PlanetaryBindingStore;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Server-lifetime owner: a single publication point for definitions, routes and generation. */
public final class PlanetaryCatalogManager {
    private final AtomicReference<State> state = new AtomicReference<>(State.empty());
    private PlanetaryBindingStore bindings;
    private final CelestialCatalogManager celestialView = CelestialCatalogManager.readOnly(() -> {
        State captured = state.get();
        var pair = captured.catalog();
        return new CelestialCatalogManager.Snapshot(pair == null ? null : pair.celestial(),
                new CelestialCatalogManager.ReloadStatus(pair != null, captured.accepted(), captured.generation(),
                        pair == null ? 0 : pair.celestial().size(), captured.message()));
    });

    public CelestialCatalogManager celestialView() {
        return celestialView;
    }

    public Optional<Generation> capture() {
        State captured = state.get();
        return captured.catalog() == null ? Optional.empty()
                : Optional.of(new Generation(captured.generation(), captured.catalog()));
    }

    public ReloadStatus status() {
        State captured = state.get();
        var pair = captured.catalog();
        return new ReloadStatus(pair != null, captured.accepted(), captured.generation(),
                pair == null ? 0 : pair.celestial().size(), pair == null ? 0 : pair.routes().routeCount(),
                pair == null ? 0 : pair.routes().anchorCount(), captured.message());
    }

    public synchronized boolean applyCandidate(DataResult<PlanetaryCatalog> candidate) {
        State previous = state.get();
        if (candidate.error().isPresent()) {
            state.set(new State(previous.catalog(), previous.generation(), false,
                    ReloadDiagnostics.bound(candidate.error().orElseThrow().message())));
            return false;
        }
        PlanetaryCatalog next = candidate.result().orElseThrow();
        long generation = Math.incrementExact(previous.generation());
        try {
            if (bindings != null) {
                bindings.accept(next.celestial());
            }
        } catch (IOException | IllegalArgumentException exception) {
            state.set(new State(previous.catalog(), previous.generation(), false,
                    ReloadDiagnostics.bound("Planetary binding rejection: " + exception.getMessage())));
            return false;
        }
        state.set(new State(next, generation, true, "accepted"));
        return true;
    }

    public synchronized int bindWorld(Path worldRoot) throws IOException {
        if (bindings != null) {
            throw new IllegalStateException("Planetary bindings already belong to a world");
        }
        PlanetaryCatalog initial = capture().orElseThrow(() ->
                new IllegalStateException("Planetary resources must load before world binding")).catalog();
        try {
            bindings = PlanetaryBindingStore.open(worldRoot, initial.celestial());
            return bindings.current().entries().size();
        } catch (IOException | IllegalArgumentException exception) {
            state.set(new State(null, 0, false, ReloadDiagnostics.bound(exception.getMessage())));
            throw exception;
        }
    }

    public synchronized void clear() {
        bindings = null;
        state.set(State.empty());
    }

    public record Generation(long generation, PlanetaryCatalog catalog) {
    }

    public record ReloadStatus(boolean ready, boolean lastReloadAccepted, long generation,
                               int bodyCount, int routeCount, int anchorCount, String message) {
    }

    private record State(PlanetaryCatalog catalog, long generation, boolean accepted, String message) {
        private static State empty() {
            return new State(null, 0, false, "not loaded");
        }
    }
}
