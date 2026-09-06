package io.github.sunthemoon.advancedrocketrycommunity.machine.port.forge;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.common.util.NonNullSupplier;

/** Lifecycle-scoped capability views; queries only return pre-created optionals. */
public final class ProcessCapabilityCache {
    private final Map<Key, LazyOptional<?>> views = new HashMap<>();
    private boolean valid = true;

    public <T> void register(
            Capability<T> capability,
            @Nullable Direction side,
            NonNullSupplier<T> viewFactory
    ) {
        if (!valid) {
            throw new IllegalStateException("cannot register on an invalidated capability cache");
        }
        Key key = new Key(capability, side);
        if (views.putIfAbsent(key, LazyOptional.of(viewFactory)) != null) {
            throw new IllegalArgumentException("duplicate capability and side registration");
        }
    }

    public <T> LazyOptional<T> get(Capability<T> capability, @Nullable Direction side) {
        LazyOptional<?> view = views.get(new Key(capability, side));
        return view == null ? LazyOptional.empty() : view.cast();
    }

    public void invalidate() {
        if (valid) {
            views.values().forEach(LazyOptional::invalidate);
            views.clear();
            valid = false;
        }
    }

    public boolean isValid() {
        return valid;
    }

    private record Key(Capability<?> capability, @Nullable Direction side) {
        private Key {
            Objects.requireNonNull(capability, "capability");
        }
    }
}
