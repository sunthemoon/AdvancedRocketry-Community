package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Native envelopes are bounded, registered and lossless before becoming owned snapshots. */
final class ClassicNativePayload {
    private static final Set<String> ITEM_FIELDS = Set.of("id", "Count", "tag");
    private static final Set<String> FLUID_FIELDS = Set.of("FluidName", "Amount", "Tag");

    static CompoundTag item(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.getTag() != null && !ClassicResourcesCodec.bounded(stack.getTag())) {
            throw new IllegalArgumentException("Unbounded Item metadata");
        }
        // Native serialization narrows Count to a byte. Validate the original positive
        // count first so wrapped values cannot become a different, apparently valid stack.
        if (!stack.isEmpty() && (stack.getCount() < 1
                || stack.getCount() > Math.min(64, stack.getMaxStackSize()))) {
            throw new IllegalArgumentException("Original Item count exceeds native slot limit");
        }
        CompoundTag encoded = stack.serializeNBT();
        // Native getters hide negative/invalid raw counts once a stack becomes empty.
        // Inspect its bounded serialized count instead of silently normalizing that input.
        requireBounded(encoded);
        if (stack.isEmpty()) {
            if (!encoded.contains("Count", Tag.TAG_BYTE) || encoded.getByte("Count") != 0
                    || encoded.contains("ForgeCaps")) {
                throw new IllegalArgumentException("Noncanonical empty Item");
            }
            return new CompoundTag();
        }
        decodeItem(encoded);
        return encoded.copy();
    }

    static ItemStack decodeItem(CompoundTag encoded) {
        preflightItem(encoded);
        if (encoded.isEmpty()) { return ItemStack.EMPTY; }
        ItemStack stack = ItemStack.of(encoded.copy());
        if (stack.isEmpty() || stack.getCount() > Math.min(64, stack.getMaxStackSize())
                || !encoded.equals(stack.serializeNBT())) {
            throw new IllegalArgumentException("Native Item cannot be decoded losslessly");
        }
        return stack;
    }

    static void preflightItem(CompoundTag encoded) {
        requireBounded(encoded);
        if (encoded.isEmpty()) { return; }
        // No capability schema is admitted by this leaf; never strip a ForgeCaps payload.
        if (!ITEM_FIELDS.containsAll(encoded.getAllKeys())
                || !encoded.contains("id", Tag.TAG_STRING) || !encoded.contains("Count", Tag.TAG_BYTE)
                || (encoded.contains("tag") && !encoded.contains("tag", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Invalid or unsupported native Item envelope");
        }
        ResourceLocation id = id(encoded.getString("id"));
        if (!ForgeRegistries.ITEMS.containsKey(id) || encoded.getByte("Count") <= 0 || encoded.getByte("Count") > 64) {
            throw new IllegalArgumentException("Unknown Item identity or invalid count");
        }
    }

    static CompoundTag fluid(FluidStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.hasTag() && !ClassicResourcesCodec.bounded(stack.getTag())) {
            throw new IllegalArgumentException("Unbounded Fluid metadata");
        }
        CompoundTag encoded = stack.writeToNBT(new CompoundTag());
        requireBounded(encoded);
        if (stack.isEmpty()) {
            if (!encoded.contains("Amount", Tag.TAG_INT) || encoded.getInt("Amount") != 0) {
                throw new IllegalArgumentException("Noncanonical empty Fluid");
            }
            return new CompoundTag();
        }
        decodeFluid(encoded);
        // Forge's writer aliases Tag; preflight and lossless validation precede this copy.
        return encoded.copy();
    }

    static FluidStack decodeFluid(CompoundTag encoded) {
        preflightFluid(encoded);
        if (encoded.isEmpty()) { return FluidStack.EMPTY; }
        FluidStack stack = FluidStack.loadFluidStackFromNBT(encoded.copy());
        if (stack.isEmpty() || !encoded.equals(stack.writeToNBT(new CompoundTag()))) {
            throw new IllegalArgumentException("Native Fluid cannot be decoded losslessly");
        }
        return stack;
    }

    static void preflightFluid(CompoundTag encoded) {
        requireBounded(encoded);
        if (encoded.isEmpty()) { return; }
        if (!FLUID_FIELDS.containsAll(encoded.getAllKeys())
                || !encoded.contains("FluidName", Tag.TAG_STRING) || !encoded.contains("Amount", Tag.TAG_INT)
                || encoded.getInt("Amount") <= 0 || encoded.getInt("Amount") > ClassicResourceBank.FLUID_CAPACITY
                || (encoded.contains("Tag") && !encoded.contains("Tag", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Invalid native Fluid envelope");
        }
        ResourceLocation id = id(encoded.getString("FluidName"));
        if (!ForgeRegistries.FLUIDS.containsKey(id) || ForgeRegistries.FLUIDS.getValue(id) == Fluids.EMPTY) {
            throw new IllegalArgumentException("Unknown or empty Fluid identity");
        }
    }

    private static ResourceLocation id(String name) {
        ResourceLocation id = name.length() <= ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                ? ResourceLocation.tryParse(name) : null;
        if (id == null || !id.toString().equals(name)) {
            throw new IllegalArgumentException("Invalid or noncanonical native resource identity");
        }
        return id;
    }

    private static void requireBounded(Tag encoded) {
        if (!ClassicResourcesCodec.bounded(encoded)) {
            throw new IllegalArgumentException("Native payload exceeds structural or byte budget");
        }
    }

    private ClassicNativePayload() { }
}
