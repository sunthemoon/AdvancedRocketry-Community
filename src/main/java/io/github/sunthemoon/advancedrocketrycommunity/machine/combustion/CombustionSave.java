package io.github.sunthemoon.advancedrocketrycommunity.machine.combustion;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent ADR-064 schema. Preflight precedes recursive copies and ItemStack decoding. */
public final class CombustionSave {
    public static final String ROOT = "arce_combustion_generator";
    public static final int MAX_BYTES = 8_192;
    private static final Set<String> KEYS = Set.of("schema", "energy", "duration", "remaining", "fuel");
    private static final Set<String> ITEM_KEYS = Set.of("id", "Count", "tag", "ForgeCaps");

    public record Snapshot(CombustionBurn.State burn, ItemStack fuel) { }

    public static boolean bounded(Tag tag) {
        return BoundedNbt.fits(tag, MAX_BYTES, 16, 1_024);
    }

    public static boolean safeStack(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (stack.getCount() <= 0 || stack.getCount() > Math.min(64, stack.getMaxStackSize())
                || (stack.getTag() != null && !bounded(stack.getTag()))) {
            return false;
        }
        return bounded(stack.serializeNBT());
    }

    public static CompoundTag encode(CombustionBurn.State burn, ItemStack fuel) {
        if (!safeStack(fuel)) {
            throw new IllegalArgumentException("Fuel payload exceeds combustion limits");
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.putInt("energy", burn.energy());
        root.putInt("duration", burn.duration());
        root.putInt("remaining", burn.remaining());
        root.put("fuel", fuel.isEmpty() ? new CompoundTag() : fuel.serializeNBT());
        if (!bounded(root)) {
            throw new IllegalArgumentException("Combustion root exceeds its aggregate budget");
        }
        return root;
    }

    public static boolean fits(CombustionBurn.State burn, ItemStack fuel) {
        try {
            encode(burn, fuel);
            return true;
        } catch (IllegalArgumentException refused) {
            return false;
        }
    }

    public static Snapshot decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().equals(KEYS)
                || integer(root, "schema") != 1 || !root.contains("fuel", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Unsupported combustion root");
        }
        CombustionBurn.State state = new CombustionBurn.State(integer(root, "energy"),
                integer(root, "duration"), integer(root, "remaining"));
        CompoundTag item = root.getCompound("fuel");
        if (item.isEmpty()) {
            return new Snapshot(state, ItemStack.EMPTY);
        }
        if (!ITEM_KEYS.containsAll(item.getAllKeys()) || !item.contains("id", Tag.TAG_STRING)
                || !item.contains("Count", Tag.TAG_BYTE) || item.getString("id").length() > 256
                || (item.contains("tag") && !item.contains("tag", Tag.TAG_COMPOUND))
                || (item.contains("ForgeCaps") && !item.contains("ForgeCaps", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Invalid fuel encoding");
        }
        ResourceLocation id = ResourceLocation.tryParse(item.getString("id"));
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id) || item.getByte("Count") <= 0) {
            throw new IllegalArgumentException("Unknown fuel or impossible count");
        }
        ItemStack fuel = ItemStack.of(item);
        if (fuel.isEmpty() || !safeStack(fuel) || !fuel.serializeNBT().equals(item)) {
            throw new IllegalArgumentException("Fuel cannot be decoded losslessly");
        }
        return new Snapshot(state, fuel);
    }

    private static int integer(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing combustion integer: " + key);
        }
        return root.getInt(key);
    }

    private CombustionSave() { }
}
