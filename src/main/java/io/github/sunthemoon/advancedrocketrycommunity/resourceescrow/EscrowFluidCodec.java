package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.fluids.FluidStack;

/** Bounded durable fluid structure and a separate native check (R39 for fluids). Neither alone admits a transfer. */
public final class EscrowFluidCodec {
    private EscrowFluidCodec() { }

    /** Registry-independent durable validation; never looks a Fluid up or builds a FluidStack. */
    public static void validateStructure(CompoundTag saved) {
        EscrowTags.bound(saved, EscrowLimits.FLUID_BYTES, 12, 384);
        EscrowTags.fields(saved, "FluidName", "Amount", "Tag");
        EscrowTags.id(EscrowTags.string(saved, "FluidName"));
        int amount = EscrowTags.integer(saved, "Amount");
        EscrowTags.require(amount >= 1 && amount <= 1_000, "Invalid escrow fluid amount");
        if (saved.contains("Tag")) { EscrowTags.compound(saved, "Tag"); }
    }

    /**
     * Native check for admission, C2, O2 and readback: a registered, non-empty Fluid whose canonical form is exactly the
     * saved one. A failure belongs to that operation's REPAIR handling, not to durable ledger validity.
     */
    public static FluidStack validateNative(CompoundTag saved) {
        validateStructure(saved);
        var fluid = FluidStack.loadFluidStackFromNBT(saved.copy());
        EscrowTags.require(!fluid.isEmpty() && fluid.writeToNBT(new CompoundTag()).equals(saved),
                "Noncanonical or unavailable native Fluid");
        return fluid;
    }
}
