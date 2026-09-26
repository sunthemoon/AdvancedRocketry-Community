package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Bounded, server-thread transient consent; never retains entities or levels. */
final class RocketDisassemblyConfirmations {
    static final int MAX_PENDING = 64;
    static final long LIFETIME_TICKS = 200L;

    record Quote(UUID entityId, UUID ownerId, UUID snapshotId, String snapshotHash,
                 RocketFlightData flight, double x, double y, double z) {
        Quote {
            Objects.requireNonNull(entityId, "entityId");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(snapshotId, "snapshotId");
            Objects.requireNonNull(snapshotHash, "snapshotHash");
            Objects.requireNonNull(flight, "flight");
            if (!snapshotHash.matches("[0-9a-f]{64}") || flight.fuel().amount() <= 0L
                    || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
                throw new IllegalArgumentException("Invalid fueled-disassembly quote");
            }
        }
    }

    record Offer(UUID token, Quote quote, long issuedAt) {
        boolean expired(long now) {
            return now < issuedAt || now - issuedAt >= LIFETIME_TICKS;
        }
    }

    private final Map<UUID, Offer> pending = new HashMap<>();

    Optional<Offer> offer(UUID playerId, Quote quote, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(quote, "quote");
        expire(now);
        Offer existing = pending.get(playerId);
        if (existing != null && existing.quote().equals(quote)) {
            return Optional.of(existing);
        }
        if (existing == null && pending.size() >= MAX_PENDING) {
            return Optional.empty();
        }
        Offer created = new Offer(UUID.randomUUID(), quote, now);
        pending.put(playerId, created);
        return Optional.of(created);
    }

    Optional<Quote> take(UUID playerId, UUID token, long now) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        expire(now);
        Offer offer = pending.get(playerId);
        if (offer == null || !offer.token().equals(token)) {
            return Optional.empty();
        }
        pending.remove(playerId);
        return Optional.of(offer.quote());
    }

    void forget(UUID playerId) {
        pending.remove(playerId);
    }

    void clear() {
        pending.clear();
    }

    private void expire(long now) {
        if (now < 0L) {
            throw new IllegalArgumentException("Confirmation time cannot be negative");
        }
        pending.values().removeIf(offer -> offer.expired(now));
    }
}
