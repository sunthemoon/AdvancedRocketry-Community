package io.github.sunthemoon.advancedrocketrycommunity.celestial.service;

import com.mojang.serialization.DataResult;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Explicit lifecycle owner for the active immutable server definition catalog. */
public final class CelestialCatalogManager {
    public static final int MAX_STATUS_MESSAGE_CHARS = 2_048;

    private final AtomicReference<Snapshot> state;
    private final Supplier<Snapshot> snapshots;

    /** Standalone owner for isolated celestial services; the server uses a paired read-only view. */
    public CelestialCatalogManager() {
        state = new AtomicReference<>(Snapshot.empty());
        snapshots = state::get;
    }

    private CelestialCatalogManager(Supplier<Snapshot> snapshots) {
        state = null;
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
    }

    public static CelestialCatalogManager readOnly(Supplier<Snapshot> snapshots) {
        return new CelestialCatalogManager(snapshots);
    }

    public Snapshot snapshot() {
        return snapshots.get();
    }

    public Optional<CelestialCatalog> current() {
        return Optional.ofNullable(snapshot().catalog());
    }

    public ReloadStatus status() {
        return snapshot().status();
    }

    public synchronized boolean applyCandidate(DataResult<CelestialCatalog> candidate) {
        requireMutable();
        Snapshot previous = state.get();
        if (candidate.error().isPresent()) {
            String message = truncate(candidate.error().orElseThrow().message());
            state.set(new Snapshot(
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
        state.set(new Snapshot(
                catalog,
                new ReloadStatus(true, true, generation, catalog.size(), "accepted")
        ));
        return true;
    }

    public synchronized void clear() {
        requireMutable();
        state.set(Snapshot.empty());
    }

    private void requireMutable() {
        if (state == null) {
            throw new IllegalStateException("Celestial view must be updated through its paired catalog owner");
        }
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

    public record Snapshot(CelestialCatalog catalog, ReloadStatus status) {
        private static Snapshot empty() {
            return new Snapshot(
                    null,
                    new ReloadStatus(false, false, 0L, 0, "not loaded")
            );
        }
    }
}
