package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransfer;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.OxygenTransferResult;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/** Fixed-schema oxygen data stored only on the v0.4 chest piece. */
public final class SpaceSuitOxygen {
    public static final int SCHEMA_VERSION = 1;
    private static final String DATA_KEY = "arce_space_suit_oxygen";
    private static final String SCHEMA_KEY = "schema_version";
    private static final String OXYGEN_KEY = "oxygen_units";

    private SpaceSuitOxygen() {
    }

    public static ReadResult read(ItemStack stack) {
        if (!(stack.getItem() instanceof SpaceSuitArmorItem armor)
                || armor.getType() != ArmorItem.Type.CHESTPLATE) {
            return new ReadResult(DataStatus.INVALID, 0);
        }
        return readTag(stack.getTag());
    }

    static ReadResult readTag(CompoundTag root) {
        if (root == null || !root.contains(DATA_KEY)) {
            return new ReadResult(DataStatus.VALID, 0);
        }
        if (!root.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return new ReadResult(DataStatus.INVALID, 0);
        }
        CompoundTag data = root.getCompound(DATA_KEY);
        if (!data.contains(SCHEMA_KEY, Tag.TAG_INT)) {
            return new ReadResult(DataStatus.INVALID, 0);
        }
        int schema = data.getInt(SCHEMA_KEY);
        if (schema > SCHEMA_VERSION) {
            return new ReadResult(DataStatus.FUTURE, 0);
        }
        if (schema != SCHEMA_VERSION || !data.contains(OXYGEN_KEY, Tag.TAG_INT)) {
            return new ReadResult(DataStatus.INVALID, 0);
        }
        int oxygen = data.getInt(OXYGEN_KEY);
        if (oxygen < 0 || oxygen > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            return new ReadResult(DataStatus.INVALID, 0);
        }
        return new ReadResult(DataStatus.VALID, oxygen);
    }

    public static boolean set(ItemStack stack, int oxygenUnits) {
        ReadResult current = read(stack);
        if (current.status() != DataStatus.VALID) {
            return false;
        }
        return setTag(stack.getOrCreateTag(), oxygenUnits);
    }

    static boolean setTag(CompoundTag root, int oxygenUnits) {
        ReadResult current = readTag(root);
        if (current.status() != DataStatus.VALID
                || oxygenUnits < 0
                || oxygenUnits > AtmosphereLimits.SUIT_OXYGEN_CAPACITY) {
            return false;
        }
        CompoundTag data = new CompoundTag();
        data.putInt(SCHEMA_KEY, SCHEMA_VERSION);
        data.putInt(OXYGEN_KEY, oxygenUnits);
        root.put(DATA_KEY, data);
        return true;
    }

    public static OxygenTransferResult fillOneCanister(ItemStack stack) {
        ReadResult current = read(stack);
        if (current.status() != DataStatus.VALID) {
            return new OxygenTransferResult(false, 0, 0);
        }
        OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(
                current.oxygenUnits(),
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY
        );
        if (transfer.accepted() && !set(stack, transfer.oxygenUnits())) {
            throw new IllegalStateException("Validated suit oxygen could not be updated");
        }
        return transfer;
    }

    static OxygenTransferResult fillOneCanisterTag(CompoundTag root) {
        ReadResult current = readTag(root);
        if (current.status() != DataStatus.VALID) {
            return new OxygenTransferResult(false, 0, 0);
        }
        OxygenTransferResult transfer = OxygenTransfer.fillOneCanister(
                current.oxygenUnits(),
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY
        );
        if (transfer.accepted() && !setTag(root, transfer.oxygenUnits())) {
            throw new IllegalStateException("Validated suit oxygen tag could not be updated");
        }
        return transfer;
    }

    public enum DataStatus {
        VALID,
        FUTURE,
        INVALID
    }

    public record ReadResult(DataStatus status, int oxygenUnits) {
    }
}
