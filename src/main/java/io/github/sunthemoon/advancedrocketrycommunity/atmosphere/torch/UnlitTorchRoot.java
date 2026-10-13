package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * The schema-1 {@code arce_unlit_torch} root (ADR-066 section 3.2): exactly the source pair, block and item IDs,
 * within 2 KiB, depth 8 and 64 nodes. It owns no inventory. Pure codec; resolution against the live catalog and
 * registries is the block entity's job.
 */
public final class UnlitTorchRoot {
    public static final String KEY = "arce_unlit_torch";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_BYTES = 2_048;
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 64;
    private static final Set<String> FIELDS = Set.of("schema_version", "pair", "block", "item");

    private UnlitTorchRoot() {
    }

    /** The exact original identity of one extinguished torch. */
    public record Identity(ResourceLocation pair, ResourceLocation block, ResourceLocation item) {
        public Identity {
            Objects.requireNonNull(pair, "pair");
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(item, "item");
            for (ResourceLocation id : new ResourceLocation[] {pair, block, item}) {
                if (TorchPairCatalog.canonical(id.toString()) == null) {
                    throw new IllegalArgumentException("Unlit torch IDs have at most "
                            + TorchPairCatalog.MAX_ID_BYTES + " bytes");
                }
            }
        }
    }

    /** Native-write preflight at the root's own bound; never copies or decodes. */
    public static boolean bounded(Tag raw) {
        return BoundedNbt.fits(raw, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    /** The identity of an exactly schema-1 root, or empty for anything else (to be retained unchanged). */
    public static Optional<Identity> decode(Tag raw) {
        if (!(raw instanceof CompoundTag root) || !bounded(root) || !root.getAllKeys().equals(FIELDS)
                || root.getTagType("schema_version") != Tag.TAG_INT || root.getInt("schema_version") != SCHEMA_VERSION) {
            return Optional.empty();
        }
        ResourceLocation pair = string(root, "pair"), block = string(root, "block"), item = string(root, "item");
        return pair == null || block == null || item == null ? Optional.empty()
                : Optional.of(new Identity(pair, block, item));
    }

    public static CompoundTag encode(Identity identity) {
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("pair", identity.pair().toString());
        root.putString("block", identity.block().toString());
        root.putString("item", identity.item().toString());
        return root;
    }

    private static ResourceLocation string(CompoundTag root, String key) {
        return root.getTagType(key) == Tag.TAG_STRING ? TorchPairCatalog.canonical(root.getString(key)) : null;
    }
}
