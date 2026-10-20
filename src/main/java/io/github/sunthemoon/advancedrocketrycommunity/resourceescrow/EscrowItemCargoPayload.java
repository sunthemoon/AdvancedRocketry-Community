package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Schema-1 frozen item delta; slot selection and container mutation belong to D-P. */
public record EscrowItemCargoPayload(EscrowCargoPayload.Cursor cursor, CompoundTag stack, int from, int to) {
    public EscrowItemCargoPayload {
        java.util.Objects.requireNonNull(cursor).encode();
        EscrowItemStackCodec.decode(stack);
        EscrowTags.require(from >= 0 && from < 27 && to >= 0 && to < 27 && (from < 4 || to < 4), "Invalid cargo slots");
        EscrowItemStackCodec.bound(frame(cursor, stack, from, to), EscrowLimits.FLUID_BYTES, 12, 384);
        stack = stack.copy();
    }
    @Override public CompoundTag stack() { return stack.copy(); }
    public CompoundTag encode() { return frame(cursor, stack, from, to); }
    public void requireDirection(boolean load) {
        EscrowTags.require(load ? from < 4 : to < 4, "Item cargo slots have the wrong direction");
    }
    public static EscrowItemCargoPayload decode(CompoundTag tag) {
        EscrowItemStackCodec.bound(tag, EscrowLimits.FLUID_BYTES, 12, 384);
        EscrowTags.fields(tag, "v", "cursor", "stack", "from", "to");
        EscrowTags.require(EscrowTags.integer(tag, "v") == 1, "Unsupported item cargo payload");
        EscrowTags.type(tag, "from", Tag.TAG_BYTE); EscrowTags.type(tag, "to", Tag.TAG_BYTE);
        return new EscrowItemCargoPayload(EscrowCargoPayload.Cursor.decode(EscrowTags.compound(tag, "cursor")),
                EscrowTags.compound(tag, "stack"), tag.getByte("from"), tag.getByte("to"));
    }
    private static CompoundTag frame(EscrowCargoPayload.Cursor cursor, CompoundTag stack, int from, int to) {
        var tag = new CompoundTag(); tag.putInt("v", 1); tag.put("cursor", cursor.encode()); tag.put("stack", stack.copy());
        tag.putByte("from", (byte) from); tag.putByte("to", (byte) to); return tag;
    }
}
