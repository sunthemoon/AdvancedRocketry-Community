package io.github.sunthemoon.advancedrocketrycommunity.progression;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.Objects;
import java.util.UUID;

/** Bounded immutable research balance and audit totals for one player. */
public record ResearchAccount(
        int schemaVersion,
        UUID ownerId,
        int balance,
        long lifetimeEarned,
        long lifetimeSpent
) {
    public ResearchAccount {
        Objects.requireNonNull(ownerId, "ownerId");
        if (schemaVersion != SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported research account schema " + schemaVersion);
        }
        if (balance < 0 || balance > SatelliteLimits.MAX_RESEARCH_BALANCE) {
            throw new IllegalArgumentException("Research balance is outside fixed bounds");
        }
        if (lifetimeEarned < 0L || lifetimeEarned > SatelliteLimits.MAX_LIFETIME_RESEARCH
                || lifetimeSpent < 0L || lifetimeSpent > lifetimeEarned) {
            throw new IllegalArgumentException("Research audit totals are invalid");
        }
    }

    public static ResearchAccount empty(UUID ownerId) {
        return new ResearchAccount(
                SatelliteLimits.RESEARCH_ACCOUNT_SCHEMA_VERSION,
                ownerId,
                0,
                0L,
                0L
        );
    }

    public ResearchAccount creditAndSpend(int earned, int spent) {
        if (earned < 0 || spent < 0 || spent > balance + earned) {
            throw new IllegalArgumentException("Research credit/spend values are invalid");
        }
        int nextBalance = Math.addExact(balance, earned) - spent;
        long nextEarned = Math.addExact(lifetimeEarned, earned);
        long nextSpent = Math.addExact(lifetimeSpent, spent);
        if (nextBalance > SatelliteLimits.MAX_RESEARCH_BALANCE
                || nextEarned > SatelliteLimits.MAX_LIFETIME_RESEARCH
                || nextSpent > SatelliteLimits.MAX_LIFETIME_RESEARCH) {
            throw new IllegalStateException("Research account capacity reached");
        }
        return new ResearchAccount(schemaVersion, ownerId, nextBalance, nextEarned, nextSpent);
    }
}
