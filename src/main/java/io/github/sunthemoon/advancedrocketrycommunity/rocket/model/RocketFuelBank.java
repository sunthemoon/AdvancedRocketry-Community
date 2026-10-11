package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Immutable content branch. Abstract units are never represented as native Fluid. */
public final class RocketFuelBank {
    public enum Branch { EMPTY, ABSTRACT, FLUID }
    public static final RocketFuelBank EMPTY = new RocketFuelBank(Branch.EMPTY, 0, null, null);
    private final Branch branch;
    private final long amount;
    private final ResourceLocation fluid;
    private final CompoundTag tag;
    private RocketFuelBank(Branch branch, long amount, ResourceLocation fluid, CompoundTag tag) {
        if (amount < 0 || amount > (long) RocketLimits.MAX_BLOCKS * 1_000 || branch == Branch.EMPTY != (amount == 0)
                || branch == Branch.FLUID != (fluid != null) || branch != Branch.FLUID && tag != null
                || fluid != null && fluid.toString().length() > 128
                || tag != null && !RocketNativeResources.bounded(tag, 8_192)) {
            throw new IllegalArgumentException("Invalid native propulsion bank");
        }
        this.branch = Objects.requireNonNull(branch); this.amount = amount; this.fluid = fluid;
        this.tag = tag == null ? null : tag.copy();
    }
    public static RocketFuelBank abstractUnits(long amount) {
        return amount == 0 ? EMPTY : new RocketFuelBank(Branch.ABSTRACT, amount, null, null);
    }
    public static RocketFuelBank fluid(ResourceLocation id, CompoundTag tag, long amount) {
        return amount == 0 ? EMPTY : new RocketFuelBank(Branch.FLUID, amount, Objects.requireNonNull(id), tag);
    }
    public Branch branch() { return branch; }
    public long amount() { return amount; }
    public ResourceLocation fluidId() { return fluid; }
    public CompoundTag fluidTag() { return tag == null ? null : tag.copy(); }
    public RocketFuelBank withAmount(long amount) {
        return branch == Branch.FLUID ? fluid(fluid, tag, amount) : abstractUnits(amount);
    }
    public String signature() {
        var root = new CompoundTag(); root.putString("branch", branch.name()); root.putLong("amount", amount);
        if (fluid != null) { root.putString("fluid", fluid.toString()); }
        if (tag != null) { root.put("tag", tag); }
        return RocketNativeResources.signature(root);
    }
    @Override public boolean equals(Object value) {
        return value instanceof RocketFuelBank other && branch == other.branch && amount == other.amount
                && Objects.equals(fluid, other.fluid) && Objects.equals(tag, other.tag);
    }
    @Override public int hashCode() { return Objects.hash(branch, amount, fluid, tag); }
    public String summary(RocketPropulsion.Role role) {
        return role.name() + ": " + (fluid == null ? branch.name() : fluid.toString()) + " " + amount
                + (tag == null ? "" : " tag-sha256=" + RocketNativeResources.signature(tag));
    }
}
