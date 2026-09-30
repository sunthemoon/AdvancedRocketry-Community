package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import java.security.SecureRandom;

/**
 * ADR-052 section 3: mission seeds come only from this server-owned {@link SecureRandom}, drawn at start and
 * persisted. Never from a client packet, a position, a name or the time; a replayed start keeps its first seed.
 */
public final class MissionSeeds {
    private final SecureRandom random = new SecureRandom();

    public synchronized long next() {
        return random.nextLong();
    }
}
