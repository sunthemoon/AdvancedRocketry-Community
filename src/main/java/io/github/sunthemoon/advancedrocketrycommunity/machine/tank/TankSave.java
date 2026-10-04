package io.github.sunthemoon.advancedrocketrycommunity.machine.tank;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent schema 1 shared verbatim between a tank BE and its dropped Item. */
public final class TankSave {
    public static final String ROOT = "arce_pressurized_tank";
    public static final int MAX_BYTES = 8_192;
    private static final Set<String> KEYS = Set.of("schema", "fluid");
    private static final Set<String> FLUID_KEYS = Set.of("FluidName", "Amount", "Tag");

    public static boolean bounded(Tag tag) { return BoundedNbt.fits(tag, MAX_BYTES, 16, 1_024); }

    public static boolean safe(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return true;
        }
        if (fluid.getAmount() <= 0 || (fluid.hasTag() && !bounded(fluid.getTag()))) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
        return id != null && id.toString().length() <= ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                && ForgeRegistries.FLUIDS.containsKey(id) && fluid.getFluid() != Fluids.EMPTY
                && bounded(fluid.writeToNBT(new CompoundTag()));
    }

    public static CompoundTag encode(FluidStack fluid) {
        if (!safe(fluid)) {
            throw new IllegalArgumentException("Fluid payload exceeds tank limits");
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.put("fluid", fluid.isEmpty() ? new CompoundTag() : fluid.writeToNBT(new CompoundTag()));
        if (!bounded(root)) {
            throw new IllegalArgumentException("Tank root exceeds aggregate budget");
        }
        // Forge's fluid writer retains the input Tag reference; copy only after aggregate preflight.
        return root.copy();
    }

    public static boolean fits(FluidStack fluid) {
        try { encode(fluid); return true; }
        catch (IllegalArgumentException refused) { return false; }
    }

    public static FluidStack decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().equals(KEYS)
                || !root.contains("schema", Tag.TAG_INT) || root.getInt("schema") != 1
                || !root.contains("fluid", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Unsupported tank root");
        }
        CompoundTag encoded = root.getCompound("fluid");
        if (encoded.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (!FLUID_KEYS.containsAll(encoded.getAllKeys()) || !encoded.contains("FluidName", Tag.TAG_STRING)
                || !encoded.contains("Amount", Tag.TAG_INT) || encoded.getInt("Amount") <= 0
                || (encoded.contains("Tag") && !encoded.contains("Tag", Tag.TAG_COMPOUND))) {
            throw new IllegalArgumentException("Invalid fluid envelope");
        }
        String name = encoded.getString("FluidName");
        ResourceLocation id = name.length() <= ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                ? ResourceLocation.tryParse(name) : null;
        if (id == null || !ForgeRegistries.FLUIDS.containsKey(id)
                || ForgeRegistries.FLUIDS.getValue(id) == Fluids.EMPTY) {
            throw new IllegalArgumentException("Unknown or empty fluid identity");
        }
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(encoded);
        if (!safe(fluid) || fluid.isEmpty() || !fluid.writeToNBT(new CompoundTag()).equals(encoded)) {
            throw new IllegalArgumentException("Tank fluid cannot be decoded losslessly");
        }
        // Forge's decoder retains the payload Tag reference; the bounded decoded snapshot must own it.
        return fluid.copy();
    }

    private TankSave() { }
}
