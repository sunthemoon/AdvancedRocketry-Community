package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Schema 2 codec and explicit schema 1 migration. Does not construct or normalize ItemStacks. */
public final class FuelLoaderStorage {
    public static final String DATA_KEY = FuelLoaderPersistence.DATA_KEY;
    public static final int SCHEMA_VERSION = 2;
    private static final Set<String> KEYS = Set.of("schema_version", "slot_role", "item", "buffered_units",
            "owner_id", "target_rocket_id", "batch");
    private static final Set<String> LEGACY_KEYS = Set.of("schema_version", "item_state", "buffered_units",
            "owner_id", "target_rocket_id");
    private static final Set<String> ITEM_KEYS = Set.of("id", "Count", "tag", "ForgeCaps");
    private static final Set<String> BATCH_KEYS = Set.of("definition", "total_units", "remainder");

    private FuelLoaderStorage() { }

    public static CompoundTag encode(FuelLoaderData value) {
        CompoundTag data = new CompoundTag();
        data.putInt("schema_version", SCHEMA_VERSION);
        data.putInt("slot_role", value.role().ordinal());
        data.put("item", value.item());
        data.putLong("buffered_units", value.bufferedUnits());
        if (value.ownerId() != null) { data.putUUID("owner_id", value.ownerId()); }
        if (value.targetRocketId() != null) { data.putUUID("target_rocket_id", value.targetRocketId()); }
        if (value.batch() != null) {
            CompoundTag batch = new CompoundTag();
            batch.putString("definition", value.batch().definition());
            batch.putLong("total_units", value.batch().totalUnits());
            batch.put("remainder", value.batch().remainder());
            data.put("batch", batch);
        }
        if (!FuelPayloadBounds.root(data)) { throw new IllegalArgumentException("Fuel Loader root exceeds bounds"); }
        return data;
    }

    public static Decoded decode(CompoundTag parent) {
        Tag raw = parent.get(DATA_KEY);
        if (raw == null) { return new Decoded(FuelLoaderData.empty(), null, false, false); }
        if (!FuelPayloadBounds.root(raw)) { return new Decoded(null, raw, false, true); }
        if (!(raw instanceof CompoundTag data) || !data.contains("schema_version", Tag.TAG_INT)) {
            return blocked(raw, false);
        }
        int schema = data.getInt("schema_version");
        if (schema > SCHEMA_VERSION) { return blocked(raw, true); }
        try {
            if (schema == 1) { return migrate(parent, data); }
            if (schema != SCHEMA_VERSION || !KEYS.containsAll(data.getAllKeys())
                    || !data.contains("slot_role", Tag.TAG_INT) || !data.contains("item", Tag.TAG_COMPOUND)
                    || !data.contains("buffered_units", Tag.TAG_LONG)
                    || data.contains("owner_id") && !data.hasUUID("owner_id")
                    || data.contains("target_rocket_id") && !data.hasUUID("target_rocket_id")
                    || data.contains("batch") && !data.contains("batch", Tag.TAG_COMPOUND)) {
                return blocked(raw, false);
            }
            FuelLoaderData.Batch batch = null;
            if (data.contains("batch")) {
                CompoundTag saved = data.getCompound("batch");
                if (!saved.getAllKeys().equals(BATCH_KEYS) || !saved.contains("definition", Tag.TAG_STRING)
                        || !saved.contains("total_units", Tag.TAG_LONG) || !saved.contains("remainder", Tag.TAG_COMPOUND)) {
                    return blocked(raw, false);
                }
                batch = new FuelLoaderData.Batch(saved.getString("definition"), saved.getLong("total_units"),
                        saved.getCompound("remainder"));
            }
            int role = data.getInt("slot_role");
            if (role < 0 || role >= FuelLoaderData.Role.values().length) { return blocked(raw, false); }
            return new Decoded(new FuelLoaderData(FuelLoaderData.Role.values()[role], data.getCompound("item"),
                    data.getLong("buffered_units"), data.hasUUID("owner_id") ? data.getUUID("owner_id") : null,
                    data.hasUUID("target_rocket_id") ? data.getUUID("target_rocket_id") : null, batch), null, false, false);
        } catch (IllegalArgumentException exception) { return blocked(raw, false); }
    }

    private static Decoded migrate(CompoundTag parent, CompoundTag data) {
        if (!LEGACY_KEYS.containsAll(data.getAllKeys())) { return blocked(data, false); }
        var legacy = FuelLoaderPersistence.decode(parent);
        if (legacy.status() != FuelLoaderPersistence.DecodeStatus.VALID) { return blocked(data, false); }
        CompoundTag item = switch (legacy.itemState()) {
            case EMPTY -> new CompoundTag();
            case FUEL_CELL -> nativeItem("rocket_fuel_cell");
            case EMPTY_CANISTER -> nativeItem("empty_canister");
        };
        var role = FuelLoaderData.Role.values()[legacy.itemState().networkId()];
        var batch = legacy.bufferedUnits() == 0L ? null : new FuelLoaderData.Batch(
                ModIdentity.MOD_ID + ":rocket_fuel_cell", 500L, nativeItem("empty_canister"));
        return new Decoded(new FuelLoaderData(role, item, legacy.bufferedUnits(), legacy.ownerId(),
                legacy.targetRocketId(), batch), null, false, false);
    }

    private static CompoundTag nativeItem(String name) {
        CompoundTag item = new CompoundTag();
        item.putString("id", ModIdentity.MOD_ID + ":" + name);
        item.putByte("Count", (byte) 1);
        return item;
    }

    static void validateItem(CompoundTag item) {
        if (!FuelPayloadBounds.item(item) || !item.isEmpty() && (!ITEM_KEYS.containsAll(item.getAllKeys())
                || !item.contains("id", Tag.TAG_STRING) || !validId(item.getString("id"))
                || item.getString("id").equals("minecraft:air")
                || !item.contains("Count", Tag.TAG_BYTE) || item.getByte("Count") != 1
                || item.contains("tag") && !item.contains("tag", Tag.TAG_COMPOUND)
                || item.contains("ForgeCaps") && !item.contains("ForgeCaps", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Invalid single-item fuel payload");
        }
    }

    static boolean validId(String id) {
        return id != null && id.length() <= 255 && id.indexOf(':') > 0 && ResourceLocation.tryParse(id) != null;
    }

    private static Decoded blocked(Tag raw, boolean future) { return new Decoded(null, raw.copy(), future, false); }

    /** Oversized quarantine intentionally holds the original reference; never copy or stringify it. */
    public record Decoded(FuelLoaderData data, Tag preserved, boolean future, boolean quarantined) {
        public boolean valid() { return data != null; }
    }
}
