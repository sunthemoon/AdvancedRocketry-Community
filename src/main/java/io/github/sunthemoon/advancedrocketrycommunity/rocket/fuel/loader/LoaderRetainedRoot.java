package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

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

    Optional<Tag> heldRoot() {
        return held == null ? Optional.empty() : Optional.of(retainedByReference ? held : held.copy());
    }

    void write(LoaderRoot next) {
        CompoundTag encoded = next.encode();
        held = encoded;
        root = next;
        status = LoaderRoot.Status.VALID;
        retainedByReference = false;
    }

    void save(CompoundTag outer) {
        if (held != null) { outer.put(LoaderRoot.KEY, retainedByReference ? held : held.copy()); }
    }

    void load(CompoundTag outer) {
        held = null;
        root = null;
        retainedByReference = false;
        LoaderRoot.Read read = LoaderRoot.read(outer);
        status = read.status();
        if (status != LoaderRoot.Status.ABSENT) {
            Tag raw = outer.get(LoaderRoot.KEY);
            retainedByReference = !LoaderRoot.bounded(raw);
            held = retainedByReference ? raw : raw.copy();
            root = read.root();
        }
    }
}
