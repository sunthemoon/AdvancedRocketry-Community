package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Defensive, native-item persistence values. Registry resolution belongs to the Forge adapter. */
public record FuelLoaderData(Role role, CompoundTag item, long bufferedUnits, UUID ownerId,
                             UUID targetRocketId, Batch batch) {
    public FuelLoaderData {
        Objects.requireNonNull(role, "role");
        FuelLoaderStorage.validateItem(item);
        if ((role == Role.EMPTY) != item.isEmpty() || bufferedUnits < 0L
                || (batch != null) != (bufferedUnits > 0L)
                || batch != null && (role != Role.EMPTY || bufferedUnits > batch.totalUnits())
                || targetRocketId != null && (bufferedUnits == 0L || ownerId == null)) {
            throw new IllegalArgumentException("Inconsistent Fuel Loader state");
        }
        item = item.copy();
    }

    @Override public CompoundTag item() { return item.copy(); }

    public static FuelLoaderData empty() { return new FuelLoaderData(Role.EMPTY, new CompoundTag(), 0, null, null, null); }

    public enum Role { EMPTY, INPUT, OUTPUT }

    public record Batch(String definition, long totalUnits, CompoundTag remainder) {
        public Batch {
            if (!FuelLoaderStorage.validId(definition) || totalUnits < 1L || totalUnits > 2_048_000L) {
                throw new IllegalArgumentException("Invalid frozen fuel batch");
            }
            FuelLoaderStorage.validateItem(remainder);
            remainder = remainder.copy();
        }
        @Override public CompoundTag remainder() { return remainder.copy(); }
    }
}
