package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Schema-1 fluid delta and immutable payload address; never a live capability or a selection request. An instance is
 * natively checked (R39 for fluids): durable records use {@link #validateStructure} and never construct one.
 */
public record EscrowCargoPayload(Cursor cursor, CompoundTag fluid) {
    public record Cursor(UUID snapshot, int x, int y, int z, String adapter) {
        public Cursor {
            java.util.Objects.requireNonNull(snapshot); EscrowTags.id(adapter);
        }
        public CompoundTag encode() {
            var tag = new CompoundTag(); tag.putUUID("s", snapshot);
            tag.putIntArray("p", new int[] {x, y, z}); tag.putString("a", adapter);
            EscrowTags.bound(tag, 256, 2, 8); return tag;
        }
        public void requirePayload(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot value) {
            EscrowTags.require(snapshot.equals(value.snapshotId()) && value.blocks().stream().anyMatch(block ->
                    block.position().x() == x && block.position().y() == y && block.position().z() == z
                    && block.blockEntityPayload().filter(payload -> payload.adapterId().toString().equals(adapter)).isPresent()),
                    "Cargo cursor no longer names its captured payload");
        }
        public static Cursor decode(CompoundTag tag) {
            EscrowTags.bound(tag, 256, 2, 8); EscrowTags.fields(tag, "s", "p", "a");
            EscrowTags.type(tag, "p", Tag.TAG_INT_ARRAY); int[] p = tag.getIntArray("p");
            EscrowTags.require(p.length == 3, "Invalid cargo cursor position");
            return new Cursor(EscrowTags.uuid(tag, "s"), p[0], p[1], p[2], EscrowTags.string(tag, "a"));
        }
    }
    public EscrowCargoPayload {
        java.util.Objects.requireNonNull(cursor).encode();
        EscrowFluidCodec.validateNative(fluid);
        EscrowTags.bound(frame(cursor, fluid), EscrowLimits.FLUID_BYTES, 12, 384);
        fluid = fluid.copy();
    }
    @Override public CompoundTag fluid() { return fluid.copy(); }
    public CompoundTag encode() { return frame(cursor, fluid); }
    private static CompoundTag frame(Cursor cursor, CompoundTag fluid) {
        var tag = new CompoundTag(); tag.putInt("v", 1); tag.put("cursor", cursor.encode());
        tag.put("fluid", fluid.copy()); return tag;
    }
    /** Durable decoding: the frame, cursor and fluid structure only, never a registry lookup or a FluidStack. */
    public static Cursor validateStructure(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.FLUID_BYTES, 12, 384); EscrowTags.fields(tag, "v", "cursor", "fluid");
        EscrowTags.require(EscrowTags.integer(tag, "v") == 1, "Unsupported cargo payload");
        var cursor = Cursor.decode(EscrowTags.compound(tag, "cursor"));
        EscrowFluidCodec.validateStructure(EscrowTags.compound(tag, "fluid"));
        return cursor;
    }
    /** Native materialization for a consumer that acts on the delta; it runs the native check. */
    public static EscrowCargoPayload decode(CompoundTag tag) {
        return new EscrowCargoPayload(validateStructure(tag), EscrowTags.compound(tag, "fluid"));
    }
}
