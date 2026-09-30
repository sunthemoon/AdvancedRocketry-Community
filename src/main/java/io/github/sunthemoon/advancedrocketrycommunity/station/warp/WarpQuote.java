package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * What the actor consented to: the observed station, the target, the cost class and the cost.
 * Server-issued only; nothing here comes from the client.
 */
public record WarpQuote(
        GameProfile actor,
        StationState observed,
        ResourceLocation target,
        WarpCostClass costClass,
        int cost,
        boolean targetSystemHasRoutes
) {
    public WarpQuote {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(actor.getId(), "actor id");
        Objects.requireNonNull(observed, "observed");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(costClass, "costClass");
        if (cost <= 0) {
            throw new IllegalArgumentException("Warp cost must be positive");
        }
        if (target.equals(observed.orbitBody())) {
            throw new IllegalArgumentException("A warp target must differ from the current orbit");
        }
    }

    public UUID actorId() {
        return actor.getId();
    }

    public UUID stationId() {
        return observed.stationId();
    }
}
