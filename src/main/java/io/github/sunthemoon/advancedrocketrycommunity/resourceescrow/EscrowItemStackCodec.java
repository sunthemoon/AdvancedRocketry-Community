package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayDeque;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Lossless local cargo-stack codec. Passing this codec alone does not admit a transfer. */
public final class EscrowItemStackCodec {
    public static final int MAX_BYTES = 4_096, MAX_DEPTH = 16, MAX_NODES = 512;
    public enum Status { CARGO_TOO_LARGE, INVALID_DATA }
    public static final class Refusal extends IllegalArgumentException {
        private final Status status;
        private Refusal(Status status, String message) { super(message); this.status = status; }
        public Status status() { return status; }
    }
    private EscrowItemStackCodec() { }

    public static ItemStack decode(CompoundTag saved) {
        bound(saved, MAX_BYTES, MAX_DEPTH, MAX_NODES);
        try {
            EscrowTags.fields(saved, "id", "Count", "tag");
            String name = EscrowTags.string(saved, "id");
            if (name.length() > 128) { throw tooLarge(); }
            EscrowTags.id(name);
            EscrowTags.type(saved, "Count", Tag.TAG_BYTE);
            if (saved.contains("tag")) { EscrowTags.compound(saved, "tag"); }
            noCapabilities(saved);
            var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(name)).orElseThrow();
            int count = saved.getByte("Count");
            EscrowTags.require(item != Items.AIR && count >= 1 && count <= 64, "Invalid cargo item or count");
            var stack = ItemStack.of(saved.copy());
            EscrowTags.require(!stack.isEmpty() && stack.getItem() == item && stack.getCount() == count
                    && count <= stack.getMaxStackSize(), "Invalid native cargo stack");
            var roundTrip = stack.save(new CompoundTag());
            bound(roundTrip, MAX_BYTES, MAX_DEPTH, MAX_NODES);
            EscrowTags.require(roundTrip.equals(saved), "Cargo stack does not round trip losslessly");
            return stack;
        } catch (Refusal refused) { throw refused; }
        catch (RuntimeException invalid) { throw new Refusal(Status.INVALID_DATA, "Invalid or noncanonical cargo stack"); }
    }

    public static CompoundTag encode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) { throw new Refusal(Status.INVALID_DATA, "Empty cargo stack"); }
        // Check the live tag before ItemStack.save recursively copies it. No caller data is changed.
        if (stack.getTag() != null) { bound(stack.getTag(), MAX_BYTES, MAX_DEPTH - 1, MAX_NODES - 3); }
        try {
            var saved = stack.save(new CompoundTag());
            var decoded = decode(saved);
            EscrowTags.require(stack.getCount() == decoded.getCount() && ItemStack.isSameItemSameTags(stack, decoded),
                    "Cargo stack changed during native encoding");
            return saved;
        } catch (Refusal refused) { throw refused; }
        catch (RuntimeException invalid) { throw new Refusal(Status.INVALID_DATA, "Invalid native cargo stack"); }
    }

    /** Count is not merge identity; it remains signed in the full stack, payload and buffer. */
    public static boolean sameItemAndTags(CompoundTag left, CompoundTag right) {
        var a = decode(left); var b = decode(right);
        var identityA = left.copy(); var identityB = right.copy();
        identityA.remove("Count"); identityB.remove("Count");
        return ItemStack.isSameItemSameTags(a, b)
                && RocketNativeResources.signature(identityA).equals(RocketNativeResources.signature(identityB));
    }

    static void bound(Tag tag, int bytes, int depth, int nodes) {
        if (!BoundedNbt.fits(tag, bytes, depth, nodes)) { throw tooLarge(); }
    }
    private static Refusal tooLarge() { return new Refusal(Status.CARGO_TOO_LARGE, "Cargo NBT envelope exceeded"); }
    private static void noCapabilities(CompoundTag root) {
        // The prior native bound caps this traversal, including nested compound/list tags.
        var pending = new ArrayDeque<Tag>(); pending.push(root);
        while (!pending.isEmpty()) {
            var tag = pending.pop();
            if (tag instanceof CompoundTag compound) {
                EscrowTags.require(!compound.contains("ForgeCaps"), "Cargo cannot carry Forge capabilities");
                for (String key : compound.getAllKeys()) { pending.push(compound.get(key)); }
            } else if (tag instanceof ListTag list) { list.forEach(pending::push); }
        }
    }
}
