package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** ADR-054 section 1: the five endgame systems; the ID is stable in audit lines and the API event. */
public enum EndgameSystem {
    LASER_DRILL("laser_drill"),
    RAILGUN("railgun"),
    BLACK_HOLE_GENERATOR("black_hole_generator"),
    GRAVITY_FIELD("gravity_field"),
    SPACE_ELEVATOR("space_elevator");

    private final String id;

    EndgameSystem(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public ResourceLocation key() {
        return ModIdentity.id(id);
    }

    public static Optional<EndgameSystem> byId(String id) {
        for (EndgameSystem system : values()) {
            if (system.id.equals(id)) {
                return Optional.of(system);
            }
        }
        return Optional.empty();
    }
}
