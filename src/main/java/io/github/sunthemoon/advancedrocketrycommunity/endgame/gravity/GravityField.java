package io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameIdOrder;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-058 sections 2 to 5: one active area gravity field. Its box is the cube of radius {@code r} around the
 * controller, clipped to the station region's horizontal bounds inside a station. Inside a station it affects every
 * player at most at 1.00 g; elsewhere only its owner and players who trust the owner.
 *
 * @param multiplier the target multiplier in hundredths (10..200)
 */
public record GravityField(UUID id, UUID owner, ResourceLocation level, Box box, int multiplier, boolean inStation) {
    public static final int MIN_RADIUS = 2;
    public static final int MAX_RADIUS = 16;
    public static final int DEFAULT_RADIUS = 8;
    public static final int MIN_MULTIPLIER = 10;
    public static final int MAX_MULTIPLIER = 200;
    public static final int MULTIPLIER_STEP = 5;
    public static final int DEFAULT_MULTIPLIER = 50;
    /** Inside a station a field is capped at the top of ADR-041's station command range (review R2-L6). */
    public static final int STATION_CAP = 100;

    public GravityField {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(box, "box");
        if (multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER || multiplier % MULTIPLIER_STEP != 0) {
            throw new IllegalArgumentException("A field multiplier is 10..200 in steps of 5");
        }
    }

    /** {@code 5 + 2 × r} FE per tick, at most 37. */
    public static int upkeep(int radius) {
        requireRadius(radius);
        return 5 + 2 * radius;
    }

    public static void requireRadius(int radius) {
        if (radius < MIN_RADIUS || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("A field radius is 2..16");
        }
    }

    /** The value a player inside the box gets, in hundredths. */
    public int effective() {
        return inStation ? Math.min(multiplier, STATION_CAP) : multiplier;
    }

    /** Consent (review R1-M9, R2-M1): station players, the owner, or players who trust the owner. */
    public boolean affects(UUID player, Set<UUID> trusted) {
        return inStation || owner.equals(player) || trusted.contains(owner);
    }

    /**
     * The winner among fields whose box contains the position and that affect the player: the smallest box volume,
     * ties broken by the lower device ID in canonical string order (ADR-054 section 7).
     */
    public static Optional<GravityField> winner(Collection<GravityField> candidates, int x, int y, int z, UUID player,
                                                Set<UUID> trusted) {
        return candidates.stream()
                .filter(field -> field.box().contains(x, y, z) && field.affects(player, trusted))
                .min(Comparator.comparingLong((GravityField field) -> field.box().volume())
                        .thenComparing(GravityField::id, EndgameIdOrder.ORDER));
    }

    /** An inclusive block box. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Box {
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IllegalArgumentException("An inverted field box");
            }
        }

        /** The cube of radius {@code r}, clipped horizontally to {@code clip} when given; empty if nothing is left. */
        public static Optional<Box> of(int x, int y, int z, int radius, Optional<Clip> clip) {
            requireRadius(radius);
            int minX = x - radius;
            int maxX = x + radius;
            int minZ = z - radius;
            int maxZ = z + radius;
            if (clip.isPresent()) {
                minX = Math.max(minX, clip.get().minX());
                maxX = Math.min(maxX, clip.get().maxX());
                minZ = Math.max(minZ, clip.get().minZ());
                maxZ = Math.min(maxZ, clip.get().maxZ());
                if (minX > maxX || minZ > maxZ) {
                    return Optional.empty();
                }
            }
            return Optional.of(new Box(minX, y - radius, minZ, maxX, y + radius, maxZ));
        }

        public boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }

        public long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }
    }

    /** A station region's inclusive horizontal bounds. */
    public record Clip(int minX, int minZ, int maxX, int maxZ) {
    }
}
