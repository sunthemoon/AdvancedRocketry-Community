package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Strict independent schema 1; preflight before recursive copies or fluid decoding. */
public final class PumpSave {
    public static final String ROOT = "arce_pump";
    public static final int MAX_BYTES = 8_192;
    private static final Set<String> REQUIRED = Set.of("schema", "energy", "cooldown", "fluid");
    private static final Set<String> ALLOWED = Set.of("schema", "energy", "cooldown", "fluid", "owner");
    private static final Set<String> FLUID_KEYS = Set.of("FluidName", "Amount", "Tag");
    public record Snapshot(UUID owner, int energy, int cooldown, FluidStack fluid) { }

    public static boolean bounded(Tag raw) { return BoundedNbt.fits(raw, MAX_BYTES, 16, 1_024); }
    public static CompoundTag encode(UUID owner, int energy, int cooldown, FluidStack fluid) {
        new PumpBudget.State(energy, fluid.getAmount(), cooldown);
        if (fluid.hasTag() && !bounded(fluid.getTag())) {
            throw new IllegalArgumentException("Pump fluid tag exceeds budget");
        }
        CompoundTag payload = new CompoundTag();
        if (!fluid.isEmpty()) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
            if (id == null || id.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                    || !ForgeRegistries.FLUIDS.containsKey(id)) {
                throw new IllegalArgumentException("Pump fluid identity exceeds limits or is unregistered");
            }
            payload = fluid.writeToNBT(new CompoundTag());
            if (!FLUID_KEYS.containsAll(payload.getAllKeys())
                    || !payload.contains("FluidName", Tag.TAG_STRING)
                    || !id.toString().equals(payload.getString("FluidName"))
                    || !payload.contains("Amount", Tag.TAG_INT) || payload.getInt("Amount") != fluid.getAmount()
                    || (payload.contains("Tag") && !payload.contains("Tag", Tag.TAG_COMPOUND))) {
                throw new IllegalArgumentException("Pump fluid envelope is not lossless");
            }
        }
        if (!bounded(payload)) {
            throw new IllegalArgumentException("Pump fluid payload exceeds budget");
        }
        CompoundTag root = new CompoundTag();
        root.putInt("schema", 1);
        root.putInt("energy", energy);
        root.putInt("cooldown", cooldown);
        if (owner != null) { root.putUUID("owner", owner); }
        root.put("fluid", payload);
        if (!bounded(root)) { throw new IllegalArgumentException("Pump root exceeds aggregate budget"); }
        return root.copy();
    }
    public static boolean fits(UUID owner, int energy, int cooldown, FluidStack fluid) {
        try { encode(owner, energy, cooldown, fluid); return true; }
        catch (IllegalArgumentException refused) { return false; }
    }
    public static Snapshot decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root) || !root.getAllKeys().containsAll(REQUIRED)
                || !ALLOWED.containsAll(root.getAllKeys()) || integer(root, "schema") != 1
                || !root.contains("fluid", Tag.TAG_COMPOUND)
                || (root.contains("owner") && !root.hasUUID("owner"))) {
            throw new IllegalArgumentException("Unsupported pump root");
        }
        UUID owner = root.contains("owner") ? root.getUUID("owner") : null;
        CompoundTag payload = root.getCompound("fluid");
        FluidStack fluid = FluidStack.EMPTY;
        if (!payload.isEmpty()) {
            if (!payload.getAllKeys().containsAll(Set.of("FluidName", "Amount"))
                    || !FLUID_KEYS.containsAll(payload.getAllKeys()) || !payload.contains("FluidName", Tag.TAG_STRING)
                    || !payload.contains("Amount", Tag.TAG_INT) || payload.getInt("Amount") <= 0
                    || payload.getString("FluidName").length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                    || (payload.contains("Tag") && !payload.contains("Tag", Tag.TAG_COMPOUND))) {
                throw new IllegalArgumentException("Invalid pump fluid payload");
            }
            ResourceLocation id = ResourceLocation.tryParse(payload.getString("FluidName"));
            if (id == null || !ForgeRegistries.FLUIDS.containsKey(id)) {
                throw new IllegalArgumentException("Unknown pump fluid");
            }
            fluid = FluidStack.loadFluidStackFromNBT(payload);
            if (fluid.isEmpty() || !fluid.writeToNBT(new CompoundTag()).equals(payload)) {
                throw new IllegalArgumentException("Pump fluid cannot decode losslessly");
            }
        }
        int energy = integer(root, "energy"), cooldown = integer(root, "cooldown");
        new PumpBudget.State(energy, fluid.getAmount(), cooldown);
        return new Snapshot(owner, energy, cooldown, fluid.copy());
    }
    private static int integer(CompoundTag root, String key) {
        if (!root.contains(key, Tag.TAG_INT)) { throw new IllegalArgumentException("Invalid pump integer " + key); }
        return root.getInt(key);
    }
    private PumpSave() { }
}
