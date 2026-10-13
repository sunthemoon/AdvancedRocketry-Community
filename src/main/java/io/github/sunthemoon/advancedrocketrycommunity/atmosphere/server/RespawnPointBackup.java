package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Bounded schema-1 tuple; decoding never changes, copies or discards unsupported input. */
public final class RespawnPointBackup {
    public static final String KEY = "arce_planet_respawn_backup";
    public static final String RETAINED = "retained";
    public static final int MAX_BYTES = 1024;
    public static final int MAX_ID_LENGTH = 256;
    private static final Set<String> FIELDS = Set.of("schema", "dimension", "x", "y", "z", "angle_bits", "forced", "outcome");

    private RespawnPointBackup() { }

    public record Point(ResourceKey<Level> dimension, BlockPos position, float angle, boolean forced) {
        public Point {
            if (dimension == null || position == null || dimension.location().toString().length() > MAX_ID_LENGTH
                    || !Float.isFinite(angle) || Math.abs((long) position.getX()) > 30_000_000L
                    || Math.abs((long) position.getZ()) > 30_000_000L) {
                throw new IllegalArgumentException("Invalid respawn point");
            }
            position = position.immutable(); // y is an exact native int, not a packed BlockPos with 12-bit y.
        }
    }

    public record Backup(Point point, RespawnOutcome outcome) {
        public Backup {
            if (point == null || outcome == null || outcome.accepted()) {
                throw new IllegalArgumentException("Backup requires a refused point");
            }
        }
    }

    public static CompoundTag encode(Backup backup) {
        CompoundTag tag = new CompoundTag();
        Point point = backup.point();
        tag.putInt("schema", 1);
        tag.putString("dimension", point.dimension().location().toString());
        tag.putInt("x", point.position().getX());
        tag.putInt("y", point.position().getY());
        tag.putInt("z", point.position().getZ());
        // FloatTag.valueOf normalizes -0.0 to +0.0; raw bits keep the exact native field value.
        tag.putInt("angle_bits", Float.floatToRawIntBits(point.angle()));
        tag.putBoolean("forced", point.forced());
        tag.putString("outcome", backup.outcome().key());
        return tag;
    }

    public static Optional<Backup> decode(Tag input) {
        if (!(input instanceof CompoundTag tag) || tag.size() < FIELDS.size() || tag.size() > FIELDS.size() + 1
                || !tag.getAllKeys().containsAll(FIELDS)
                || tag.getAllKeys().stream().anyMatch(key -> !FIELDS.contains(key) && !RETAINED.equals(key))) {
            return Optional.empty();
        }
        // The optional opaque old root is retained by identity, not traversed or interpreted. Only new owned
        // fields are bounded. This avoids copying an oversized future root to preserve a later native tuple.
        CompoundTag core = new CompoundTag();
        for (String key : FIELDS) { core.put(key, tag.get(key)); }
        if (!BoundedNbt.fits(core, MAX_BYTES, 2, 9)
                || !tag.contains("schema", Tag.TAG_INT) || tag.getInt("schema") != 1
                || !tag.contains("dimension", Tag.TAG_STRING) || !tag.contains("angle_bits", Tag.TAG_INT)
                || !tag.contains("forced", Tag.TAG_BYTE) || !tag.contains("outcome", Tag.TAG_STRING)
                || !tag.contains("x", Tag.TAG_INT) || !tag.contains("y", Tag.TAG_INT) || !tag.contains("z", Tag.TAG_INT)
                || tag.getByte("forced") < 0 || tag.getByte("forced") > 1
                || tag.getString("dimension").length() > MAX_ID_LENGTH) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("dimension"));
        if (id == null) { return Optional.empty(); }
        RespawnOutcome outcome = null;
        for (RespawnOutcome candidate : RespawnOutcome.values()) {
            if (!candidate.accepted() && candidate.key().equals(tag.getString("outcome"))) { outcome = candidate; }
        }
        if (outcome == null) { return Optional.empty(); }
        try {
            return Optional.of(new Backup(new Point(ResourceKey.create(Registries.DIMENSION, id),
                    new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), Float.intBitsToFloat(tag.getInt("angle_bits")),
                    tag.getBoolean("forced")), outcome));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /** Store before clearing. An unreadable old root is retained verbatim under a temporary envelope. */
    public static void store(CompoundTag persisted, Backup backup) {
        Tag previous = persisted.get(KEY);
        CompoundTag encoded = encode(backup);
        if (previous != null) {
            Tag retained = decode(previous).isPresent() && previous instanceof CompoundTag old ? old.get(RETAINED) : previous;
            if (retained != null) { encoded.put(RETAINED, retained); }
        }
        persisted.put(KEY, encoded);
    }

    /** Called only after verifying the exact restored tuple. Opaque data returns to its original key. */
    public static void complete(CompoundTag persisted) {
        Tag current = persisted.get(KEY);
        if (decode(current).isEmpty()) { return; }
        Tag retained = ((CompoundTag) current).get(RETAINED);
        if (retained == null) { persisted.remove(KEY); } else { persisted.put(KEY, retained); }
    }
}
