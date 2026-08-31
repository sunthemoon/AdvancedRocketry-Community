package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Lifecycle-owned in-memory overlap lock. It must be cleared when its server stops. */
public final class RocketRegionLockManager {
    private final Map<UUID, RocketRegion> active = new LinkedHashMap<>();

    public synchronized Optional<LockToken> tryAcquire(UUID transactionId, RocketRegion region) {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(region, "region");
        if (active.containsKey(transactionId)
                || active.values().stream().anyMatch(region::overlaps)) {
            return Optional.empty();
        }
        active.put(transactionId, region);
        return Optional.of(new LockToken(this, transactionId, region));
    }

    public synchronized int activeCount() {
        return active.size();
    }

    public synchronized void clear() {
        active.clear();
    }

    private synchronized void release(UUID transactionId, RocketRegion region) {
        active.remove(transactionId, region);
    }

    public static final class LockToken implements AutoCloseable {
        private final RocketRegionLockManager manager;
        private final UUID transactionId;
        private final RocketRegion region;
        private boolean closed;

        private LockToken(
                RocketRegionLockManager manager,
                UUID transactionId,
                RocketRegion region
        ) {
            this.manager = manager;
            this.transactionId = transactionId;
            this.region = region;
        }

        public UUID transactionId() {
            return transactionId;
        }

        public RocketRegion region() {
            return region;
        }

        @Override
        public synchronized void close() {
            if (!closed) {
                closed = true;
                manager.release(transactionId, region);
            }
        }
    }
}
