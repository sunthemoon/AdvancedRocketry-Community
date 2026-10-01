package io.github.sunthemoon.advancedrocketrycommunity.endgame.protection;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-054 section 6: an operator-managed, full-height box in one Level that only endgame effects respect. The plan
 * bounds are inclusive block coordinates; the allow list holds the device owners that may act inside it.
 */
public record ProtectedZone(String name, ResourceLocation level, int minX, int minZ, int maxX, int maxZ,
                            List<UUID> allowList) {
    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1," + EndgameLimits.MAX_ZONE_NAME_LENGTH + "}");

    public ProtectedZone {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(allowList, "allowList");
        if (!validName(name)) {
            throw new IllegalArgumentException("Zone names are 1..32 characters of [a-z0-9_-]");
        }
        if (maxX < minX || maxZ < minZ) {
            throw new IllegalArgumentException("Zone bounds are inverted");
        }
        if ((long) maxX - minX + 1L > EndgameLimits.MAX_ZONE_SPAN || (long) maxZ - minZ + 1L > EndgameLimits.MAX_ZONE_SPAN) {
            throw new IllegalArgumentException("A zone spans at most 4,096 x 4,096 blocks in plan view");
        }
        Set<UUID> unique = new LinkedHashSet<>(allowList);
        if (unique.size() != allowList.size() || unique.contains(null)) {
            throw new IllegalArgumentException("A zone allow list holds unique player IDs");
        }
        if (unique.size() > EndgameLimits.MAX_ZONE_ALLOW_LIST) {
            throw new IllegalArgumentException("A zone allow list holds at most 16 players");
        }
        allowList = List.copyOf(allowList);
    }

    /** A zone from two corners in any order. */
    public static ProtectedZone of(String name, ResourceLocation level, int x1, int z1, int x2, int z2,
                                   List<UUID> allowList) {
        return new ProtectedZone(name, level, Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2),
                Math.max(z1, z2), allowList);
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** True when the plan box {@code [minX, maxX] x [minZ, maxZ]} in {@code level} overlaps this zone. */
    public boolean intersects(ResourceLocation level, int boxMinX, int boxMinZ, int boxMaxX, int boxMaxZ) {
        return this.level.equals(level) && boxMinX <= maxX && boxMaxX >= minX && boxMinZ <= maxZ
                && boxMaxZ >= minZ;
    }

    public boolean allows(UUID owner) {
        return allowList.contains(owner);
    }
}
