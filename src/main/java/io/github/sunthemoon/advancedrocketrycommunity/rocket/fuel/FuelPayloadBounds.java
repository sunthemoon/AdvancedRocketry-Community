package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import net.minecraft.nbt.Tag;

/** Fuel-specific budgets for the shared non-buffering NBT preflight. */
public final class FuelPayloadBounds {
    private FuelPayloadBounds() { }

    public static boolean item(Tag tag) { return BoundedNbt.fits(tag, 4096, 16, 256); }
    public static boolean root(Tag tag) { return BoundedNbt.fits(tag, 16384, 20, 1024); }
}
