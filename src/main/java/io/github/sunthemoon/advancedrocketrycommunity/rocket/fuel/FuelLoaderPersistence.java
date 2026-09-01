package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Fixed-field schema for a one-slot loader; arbitrary item NBT is never persisted. */
public final class FuelLoaderPersistence {
    public static final String DATA_KEY = "arce_fuel_loader";
    public static final int SCHEMA_VERSION = 1;

    private static final String SCHEMA = "schema_version";
    private static final String ITEM = "item_state";
    private static final String BUFFERED = "buffered_units";
    private static final String OWNER = "owner_id";
    private static final String TARGET = "target_rocket_id";

    private FuelLoaderPersistence() {
    }

    public static CompoundTag encode(
            ItemState itemState,
            long bufferedUnits,
            UUID ownerId,
            UUID targetRocketId
    ) {
        validate(itemState, bufferedUnits, ownerId, targetRocketId);
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA, SCHEMA_VERSION);
        data.putInt(ITEM, itemState.networkId());
        data.putLong(BUFFERED, bufferedUnits);
        if (ownerId != null) {
            data.putUUID(OWNER, ownerId);
        }
        if (targetRocketId != null) {
            data.putUUID(TARGET, targetRocketId);
        }
        return data;
    }

    public static DecodeResult decode(CompoundTag parent) {
        Objects.requireNonNull(parent, "parent");
        if (!parent.contains(DATA_KEY)) {
            return DecodeResult.valid(ItemState.EMPTY, 0L, null, null);
        }
        if (!parent.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return DecodeResult.invalid(invalidSentinel());
        }
        CompoundTag data = parent.getCompound(DATA_KEY);
        if (!data.contains(SCHEMA, Tag.TAG_INT)) {
            return DecodeResult.invalid(data);
        }
        int schema = data.getInt(SCHEMA);
        if (schema > SCHEMA_VERSION) {
            return DecodeResult.future(data);
        }
        if (schema != SCHEMA_VERSION
                || !data.contains(ITEM, Tag.TAG_INT)
                || !data.contains(BUFFERED, Tag.TAG_LONG)
                || (data.contains(OWNER) && !data.hasUUID(OWNER))
                || (data.contains(TARGET) && !data.hasUUID(TARGET))) {
            return DecodeResult.invalid(data);
        }
        try {
            ItemState itemState = ItemState.fromNetworkId(data.getInt(ITEM));
            long bufferedUnits = data.getLong(BUFFERED);
            UUID ownerId = data.hasUUID(OWNER) ? data.getUUID(OWNER) : null;
            UUID targetRocketId = data.hasUUID(TARGET) ? data.getUUID(TARGET) : null;
            validate(itemState, bufferedUnits, ownerId, targetRocketId);
            return DecodeResult.valid(itemState, bufferedUnits, ownerId, targetRocketId);
        } catch (RuntimeException exception) {
            return DecodeResult.invalid(data);
        }
    }

    private static void validate(
            ItemState itemState,
            long bufferedUnits,
            UUID ownerId,
            UUID targetRocketId
    ) {
        Objects.requireNonNull(itemState, "itemState");
        if (bufferedUnits < 0L || bufferedUnits > RocketFlightLimits.FUEL_CELL_UNITS) {
            throw new IllegalArgumentException("Fuel Loader buffer is outside the fixed cell bound");
        }
        if (bufferedUnits > 0L && itemState != ItemState.EMPTY) {
            throw new IllegalArgumentException("Fuel Loader cannot hold an item while a cell is buffered");
        }
        if (targetRocketId != null && (bufferedUnits == 0L || ownerId == null)) {
            throw new IllegalArgumentException("Fuel Loader target requires buffered fuel and an owner");
        }
    }

    private static CompoundTag invalidSentinel() {
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA, 0);
        return data;
    }

    public enum ItemState {
        EMPTY(0),
        FUEL_CELL(1),
        EMPTY_CANISTER(2);

        private final int networkId;

        ItemState(int networkId) {
            this.networkId = networkId;
        }

        public int networkId() {
            return networkId;
        }

        public static ItemState fromNetworkId(int networkId) {
            for (ItemState state : values()) {
                if (state.networkId == networkId) {
                    return state;
                }
            }
            throw new IllegalArgumentException("Unknown Fuel Loader item state " + networkId);
        }
    }

    public enum DecodeStatus {
        VALID,
        FUTURE,
        INVALID
    }

    public record DecodeResult(
            DecodeStatus status,
            ItemState itemState,
            long bufferedUnits,
            UUID ownerId,
            UUID targetRocketId,
            CompoundTag preservedData
    ) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(itemState, "itemState");
            preservedData = preservedData == null ? null : preservedData.copy();
        }

        public static DecodeResult valid(
                ItemState itemState,
                long bufferedUnits,
                UUID ownerId,
                UUID targetRocketId
        ) {
            return new DecodeResult(
                    DecodeStatus.VALID,
                    itemState,
                    bufferedUnits,
                    ownerId,
                    targetRocketId,
                    null
            );
        }

        public static DecodeResult future(CompoundTag data) {
            return blocked(DecodeStatus.FUTURE, data);
        }

        public static DecodeResult invalid(CompoundTag data) {
            return blocked(DecodeStatus.INVALID, data);
        }

        private static DecodeResult blocked(DecodeStatus status, CompoundTag data) {
            return new DecodeResult(status, ItemState.EMPTY, 0L, null, null, data);
        }

        @Override
        public CompoundTag preservedData() {
            return preservedData == null ? null : preservedData.copy();
        }
    }
}
