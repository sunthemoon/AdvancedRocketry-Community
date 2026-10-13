package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Objects;
import java.util.UUID;

/** Stable tagged source identity; a ground job is never a satellite mission. */
public record InstanceOrigin(Kind kind, UUID id) {
    public enum Kind { SURVEY_MISSION, GROUND_SURVEY }

    public InstanceOrigin {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static InstanceOrigin survey(UUID id) {
        return new InstanceOrigin(Kind.SURVEY_MISSION, id);
    }

    public static InstanceOrigin ground(UUID id) {
        return new InstanceOrigin(Kind.GROUND_SURVEY, id);
    }
}
