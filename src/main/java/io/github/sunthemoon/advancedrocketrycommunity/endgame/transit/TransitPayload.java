package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * ADR-054 section 11 payload: one to four item stacks, each at most 512 bytes encoded (the item ID, the count and the
 * tag together, review R3-L5), so at most 2 KiB. It is kept raw, so a payload whose item no longer exists stays
 * byte-identical (a quarantined record) and decodes only when it is materialized.
 */
public final class TransitPayload {
    public static final int MAX_STACKS = 4;
    public static final int MAX_STACK_BYTES = 512;
    private static final int MAX_STACK_DEPTH = 16;
    private static final int MAX_STACK_NODES = 256;

    private final ListTag raw;

    private TransitPayload(ListTag raw) {
        this.raw = raw;
    }

    /** A stored payload; throws for one outside the bounds (a strict decode). */
    public static TransitPayload raw(ListTag list) {
        if (list.isEmpty() || list.size() > MAX_STACKS || list.getElementType() != Tag.TAG_COMPOUND) {
            throw new IllegalArgumentException("A payload holds 1.." + MAX_STACKS + " stacks");
        }
        for (Tag stack : list) {
            if (!fits(stack)) {
                throw new IllegalArgumentException("A payload stack exceeds " + MAX_STACK_BYTES + " bytes");
            }
        }
        return new TransitPayload(list.copy());
    }

    /** The payload of these stacks, or empty when there are none, more than four or one is too large to escrow. */
    public static Optional<TransitPayload> of(List<ItemStack> stacks) {
        if (stacks.isEmpty() || stacks.size() > MAX_STACKS) {
            return Optional.empty();
        }
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) {
                return Optional.empty();
            }
            CompoundTag encoded = stack.save(new CompoundTag());
            if (!fits(encoded)) {
                return Optional.empty();
            }
            list.add(encoded);
        }
        return Optional.of(new TransitPayload(list));
    }

    /** Whether one stack fits the 512-byte bound ({@code PAYLOAD_TOO_LARGE} otherwise). */
    public static boolean fits(ItemStack stack) {
        return !stack.isEmpty() && fits(stack.save(new CompoundTag()));
    }

    private static boolean fits(Tag stack) {
        return stack instanceof CompoundTag && BoundedNbt.fits(stack, MAX_STACK_BYTES, MAX_STACK_DEPTH,
                MAX_STACK_NODES);
    }

    /**
     * The stacks, or empty when any item is unknown (an item of a removed mod) or the stack is malformed: the record is
     * then quarantined with this raw payload.
     */
    public Optional<List<ItemStack>> decode() {
        List<ItemStack> stacks = new ArrayList<>(raw.size());
        for (int i = 0; i < raw.size(); i++) {
            CompoundTag tag = raw.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("id"));
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id) || tag.getByte("Count") < 1) {
                return Optional.empty();
            }
            ItemStack stack = ItemStack.of(tag);
            if (stack.isEmpty() || stack.is(Items.AIR)) {
                return Optional.empty();
            }
            stacks.add(stack);
        }
        return Optional.of(stacks);
    }

    public int stacks() {
        return raw.size();
    }

    /** A copy of the raw list, for codecs. */
    public ListTag tag() {
        return raw.copy();
    }

    /** The first 16 hex digits of the SHA-256 of the encoded payload, for audit lines. */
    public String hash() {
        try {
            CompoundTag wrapper = new CompoundTag();
            wrapper.put("p", raw);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(wrapper, output);
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Payload hashing failed", exception);
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TransitPayload payload && payload.raw.equals(raw);
    }

    @Override
    public int hashCode() {
        return raw.hashCode();
    }

    @Override
    public String toString() {
        return "payload[" + raw.size() + " stacks, " + hash() + "]";
    }
}
