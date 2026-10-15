package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * The schema-1 {@code arce_satellite_bay} root (W27A-DESIGN section 6): {@code schema_version} 1, the placing
 * {@code owner}, then at most one of {@code package} (the raw native item tag, kept byte for byte) or
 * {@code consumed_op}. Pure codec: it refuses and never normalizes, and it never resolves registries.
 */
public final class SatelliteBayRoot {
    public static final String KEY = "arce_satellite_bay";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 4_352;
    public static final int MAX_DEPTH = 18;
    public static final int MAX_NODES = 272;
    /** The native item bound of {@code FuelPayloadBounds.item}. */
    public static final int PACKAGE_MAX_BYTES = 4_096;
    public static final int PACKAGE_MAX_DEPTH = 16;
    public static final int PACKAGE_MAX_NODES = 256;
    /**
     * What a retained raw root may hold and still be written unchanged: a lossless native write (no typed empty
     * list, overlong string or non-canonical NaN), at most vanilla's 2 MiB NBT accounting quota, and nested so
     * that the native chunk reader (depth 512 at the chunk root, the root itself at depth 3) can read it back.
     */
    public static final int NATIVE_MAX_BYTES = 2_097_152;
    public static final int NATIVE_MAX_DEPTH = 510;
    public static final int NATIVE_MAX_NODES = 2_097_152;
    public static final String SCHEMA = "schema_version";
    public static final String OWNER = "owner";
    public static final String PACKAGE = "package";
    public static final String CONSUMED_OP = "consumed_op";
    private static final Set<String> FIELDS = Set.of(SCHEMA, OWNER, PACKAGE, CONSUMED_OP);

    private SatelliteBayRoot() {
    }

    /** One decoded bay: the owner and at most one of a raw package tag or a consumed operation. */
    public record Contents(UUID owner, @Nullable CompoundTag pkg, @Nullable UUID consumedOp) {
        public Contents {
            Objects.requireNonNull(owner, "owner");
            if (pkg != null && consumedOp != null) {
                throw new IllegalArgumentException("A bay holds a package or a consumed operation, never both");
            }
            if (pkg != null && !validPackage(pkg)) {
                throw new IllegalArgumentException("Bay package exceeds the native item bound or is not one item");
            }
            pkg = pkg == null ? null : pkg.copy();
        }

        public static Contents empty(UUID owner) {
            return new Contents(owner, null, null);
        }

        public Optional<CompoundTag> packageTag() {
            return Optional.ofNullable(pkg).map(CompoundTag::copy);
        }

        public Optional<UUID> consumed() {
            return Optional.ofNullable(consumedOp);
        }
    }

    public enum Status { VALID, FUTURE, INVALID }

    public record Decoded(Status status, Optional<Contents> contents) {
        public Decoded {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(contents, "contents");
            if ((status == Status.VALID) != contents.isPresent()) {
                throw new IllegalArgumentException("Only a valid root exposes contents");
            }
        }

        public boolean valid() {
            return status == Status.VALID;
        }
    }

    /** The root's own bound; a non-mutating walk that never copies or decodes. */
    public static boolean bounded(Tag raw) {
        return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    /** Whether native chunk serialization writes this raw tag unchanged and can read it back. */
    public static boolean nativelyKeepable(Tag raw) {
        return BoundedNbt.fits(raw, NATIVE_MAX_BYTES, NATIVE_MAX_DEPTH, NATIVE_MAX_NODES);
    }

    /** The native item bound, one {@code id} string and {@code Count} exactly 1; other item keys stay raw. */
    public static boolean validPackage(CompoundTag pkg) {
        return BoundedNbt.fits(pkg, PACKAGE_MAX_BYTES, PACKAGE_MAX_DEPTH, PACKAGE_MAX_NODES)
                && pkg.getTagType("id") == Tag.TAG_STRING
                && pkg.getTagType("Count") == Tag.TAG_BYTE && pkg.getByte("Count") == 1;
    }

    public static Decoded decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || root.getTagType(SCHEMA) != Tag.TAG_INT) {
            return new Decoded(Status.INVALID, Optional.empty());
        }
        int schema = root.getInt(SCHEMA);
        // A later schema may add fields or grow, so it is recognized before the schema-1 shape checks.
        if (schema > SCHEMA_VERSION) {
            return new Decoded(Status.FUTURE, Optional.empty());
        }
        if (schema != SCHEMA_VERSION || !bounded(root) || !FIELDS.containsAll(root.getAllKeys())
                || !uuid(root, OWNER)
                || root.contains(PACKAGE) && root.contains(CONSUMED_OP)
                || root.contains(PACKAGE) && !(root.get(PACKAGE) instanceof CompoundTag pkg && validPackage(pkg))
                || root.contains(CONSUMED_OP) && !uuid(root, CONSUMED_OP)) {
            return new Decoded(Status.INVALID, Optional.empty());
        }
        return new Decoded(Status.VALID, Optional.of(new Contents(root.getUUID(OWNER),
                root.contains(PACKAGE) ? root.getCompound(PACKAGE) : null,
                root.contains(CONSUMED_OP) ? root.getUUID(CONSUMED_OP) : null)));
    }

    public static CompoundTag encode(Contents contents) {
        Objects.requireNonNull(contents, "contents");
        CompoundTag root = new CompoundTag();
        root.putInt(SCHEMA, SCHEMA_VERSION);
        root.putUUID(OWNER, contents.owner());
        contents.packageTag().ifPresent(pkg -> root.put(PACKAGE, pkg));
        contents.consumed().ifPresent(op -> root.putUUID(CONSUMED_OP, op));
        if (!bounded(root)) {
            throw new IllegalStateException("Satellite bay root exceeds its fixed bound");
        }
        return root;
    }

    /** Exactly the int[4] UUID form; any other type or length refuses. */
    private static boolean uuid(CompoundTag root, String key) {
        return root.hasUUID(key);
    }
}
