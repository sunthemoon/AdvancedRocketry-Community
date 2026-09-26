package io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import java.util.HashSet;
import java.util.Set;
import java.util.OptionalInt;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Session-scoped failure isolation and detached oxygen preparation, without live items. */
final class SuitOxygenAccess {
    static final long MAX_CALLBACK_NANOS = 5_000_000L;
    private final Set<ResourceLocation> disabled = new HashSet<>();
    private final LongSupplier clock;
    private boolean calling;

    SuitOxygenAccess() {
        this(System::nanoTime);
    }

    SuitOxygenAccess(LongSupplier clock) {
        this.clock = clock;
    }

    Read read(SuitEquipmentCatalog.Provider provider, Tag saved) {
        requireIdle();
        if (disabled(provider)) {
            return null;
        }
        CompoundTag data = SuitOxygenPayloads.decode(saved, provider);
        if (data == null) {
            return null;
        }
        CompoundTag original = saved == null ? null : (CompoundTag) saved.copy();
        try {
            OptionalInt oxygen = readData(provider, data);
            if (oxygen.isEmpty()) {
                return null;
            }
            if (saved == null && oxygen.getAsInt() != 0) {
                throw new IllegalArgumentException("New tanks must be empty");
            }
            return new Read(provider, original, data, oxygen.getAsInt());
        } catch (RuntimeException exception) {
            disable(provider, "read");
            return null;
        }
    }

    CompoundTag prepare(Read read, int oxygen) {
        requireIdle();
        if (read == null || disabled(read.provider()) || oxygen < 0
                || oxygen > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            return null;
        }
        try {
            CompoundTag updated = call(() -> read.provider().callbacks().writeOxygen(read.data().copy(), oxygen));
            CompoundTag envelope = SuitOxygenPayloads.encode(read.provider(), updated);
            if (envelope == null) {
                throw new IllegalArgumentException("Invalid generated oxygen data");
            }
            OptionalInt actual = readData(read.provider(), envelope.getCompound("data"));
            if (actual.isEmpty() || actual.getAsInt() != oxygen) {
                throw new IllegalArgumentException("Generated oxygen differs from requested amount");
            }
            return envelope;
        } catch (RuntimeException exception) {
            disable(read.provider(), "update");
            return null;
        }
    }

    boolean disabled(SuitEquipmentCatalog.Provider provider) {
        return disabled.contains(provider.id());
    }

    void clear() {
        requireIdle();
        disabled.clear();
    }

    private OptionalInt readData(SuitEquipmentCatalog.Provider provider, CompoundTag data) {
        CompoundTag argument = data.copy();
        OptionalInt oxygen = call(() -> provider.callbacks().readOxygen(argument));
        if (!SuitOxygenPayloads.bounded(argument) || !data.equals(argument) || oxygen == null
                || oxygen.isPresent() && (oxygen.getAsInt() < 0
                || oxygen.getAsInt() > AtmosphereLimits.SUIT_OXYGEN_CAPACITY)) {
            throw new IllegalArgumentException("Invalid oxygen read result");
        }
        return oxygen;
    }

    private <T> T call(Supplier<T> callback) {
        requireIdle();
        calling = true;
        long start = clock.getAsLong();
        try {
            T result = callback.get();
            if (clock.getAsLong() - start > MAX_CALLBACK_NANOS) {
                throw new IllegalStateException("Suit provider exceeded returned-time budget");
            }
            return result;
        } finally {
            calling = false;
        }
    }

    private void requireIdle() {
        if (calling) {
            throw new IllegalStateException("Reentrant suit oxygen access");
        }
    }

    private void disable(SuitEquipmentCatalog.Provider provider, String phase) {
        if (disabled.add(provider.id())) {
            LogUtils.getLogger().warn("Suit oxygen provider {} disabled for this server session: {} contract failure",
                    provider.id(), phase);
        }
    }

    record Read(SuitEquipmentCatalog.Provider provider, CompoundTag original, CompoundTag data, int oxygen) { }
}
