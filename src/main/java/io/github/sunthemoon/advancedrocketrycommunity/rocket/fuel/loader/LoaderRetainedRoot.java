package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Keeps unsupported data verbatim; oversized roots are retained by reference rather than recursively copied. */
final class LoaderRetainedRoot {
    @Nullable private Tag held;
    @Nullable LoaderRoot root;
    LoaderRoot.Status status = LoaderRoot.Status.ABSENT;
    private boolean retainedByReference;
    boolean unkeepable;

    Optional<Tag> heldRoot() {
        return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy());
    }

    void write(LoaderRoot next) {
        CompoundTag encoded = next.encode();
        held = encoded;
        root = next;
        status = LoaderRoot.Status.VALID;
        retainedByReference = false;
        unkeepable = false;
    }

    void save(CompoundTag outer) {
        if (held != null) { outer.put(LoaderRoot.KEY, retainedByReference ? held : held.copy()); }
    }

    void load(CompoundTag outer) {
        held = null;
        root = null;
        retainedByReference = false;
        unkeepable = false;
        LoaderRoot.Read read = LoaderRoot.read(outer);
        status = read.status();
        if (status != LoaderRoot.Status.ABSENT) {
            Tag raw = outer.get(LoaderRoot.KEY);
            // Same terrain envelope position as W109. WORK_LIMIT is keepable, not a native incompatibility.
            unkeepable = !SatelliteBayRoot.nativelyKeepable(raw);
            retainedByReference = !LoaderRoot.bounded(raw);
            held = retainedByReference ? raw : raw.copy();
            root = read.root();
        }
    }
}
