package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Schema-1 fluid delta and immutable payload address; never a live capability or a selection request. */
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
        EscrowTags.bound(fluid, EscrowLimits.FLUID_BYTES, 12, 384);
        EscrowTags.fields(fluid, "FluidName", "Amount", "Tag");
        EscrowTags.id(EscrowTags.string(fluid, "FluidName"));
        int amount = EscrowTags.integer(fluid, "Amount");
        EscrowTags.require(amount >= 1 && amount <= 1_000, "Invalid cargo fluid amount");
        if (fluid.contains("Tag")) { EscrowTags.compound(fluid, "Tag"); }
        var nativeFluid = net.minecraftforge.fluids.FluidStack.loadFluidStackFromNBT(fluid.copy());
        EscrowTags.require(!nativeFluid.isEmpty() && nativeFluid.writeToNBT(new CompoundTag()).equals(fluid),
                "Noncanonical or unavailable cargo Fluid");
        EscrowTags.bound(frame(cursor, fluid), EscrowLimits.FLUID_BYTES, 12, 384);
        fluid = fluid.copy();
    }
    @Override public CompoundTag fluid() { return fluid.copy(); }
    public CompoundTag encode() { return frame(cursor, fluid); }
    private static CompoundTag frame(Cursor cursor, CompoundTag fluid) {
        var tag = new CompoundTag(); tag.putInt("v", 1); tag.put("cursor", cursor.encode());
        tag.put("fluid", fluid.copy()); return tag;
    }
    public static EscrowCargoPayload decode(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.FLUID_BYTES, 12, 384); EscrowTags.fields(tag, "v", "cursor", "fluid");
        EscrowTags.require(EscrowTags.integer(tag, "v") == 1, "Unsupported cargo payload");
        return new EscrowCargoPayload(Cursor.decode(EscrowTags.compound(tag, "cursor")), EscrowTags.compound(tag, "fluid"));
    }
}
