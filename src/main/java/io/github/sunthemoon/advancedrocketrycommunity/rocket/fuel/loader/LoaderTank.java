package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * The loader's one native Fluid, 0 to 16,000 mB, in Forge's own FluidStack envelope ({@code {}} when empty). The
 * envelope has no schema of its own: the root and the carrier that hold it carry theirs.
 */
public final class LoaderTank {
    public static final int CAPACITY = 16_000;
    public static final int MAX_BYTES = 8_192;
    /** The carrier's depth less its own compound, and its nodes less its compound, schema and owner: every valid tank fits a carrier. */
    public static final int MAX_DEPTH = LoaderCarrier.MAX_DEPTH - 1;
    public static final int MAX_NODES = LoaderCarrier.MAX_NODES - 3;
    /** W11A section 1: the loader endpoint signature retains this prefix, the device UUID and the Fluid's native signature. */
    public static final String SIGNATURE_PREFIX = "arce-escrow-fluid-tank-1:";
    /**
     * The payload bound that {@code EscrowOperation} applies to a fill's native Fluid envelope. Inside an operation
     * (12,288 bytes, depth 16, 512 nodes) its framing leaves at least this much, so this bound decides fit alone.
     */
    public static final int PAYLOAD_BYTES = EscrowLimits.FLUID_BYTES;
    public static final int PAYLOAD_DEPTH = 12;
    public static final int PAYLOAD_NODES = 384;
    /** Vanilla's empty Fluid, which no amount may name; a literal, so that pure construction needs no registry. */
    private static final ResourceLocation EMPTY_FLUID = ResourceLocation.tryBuild("minecraft", "empty");
    public static final LoaderTank EMPTY = new LoaderTank(null, null, 0);
    private static final String NAME = "FluidName";
    private static final String AMOUNT = "Amount";
    private static final String TAG = "Tag";

    private final ResourceLocation fluid;
    private final CompoundTag tag;
    private final int amount;

    private LoaderTank(ResourceLocation fluid, CompoundTag tag, int amount) {
        LoaderNbt.require(amount >= 0 && amount <= CAPACITY && amount == 0 == (fluid == null) && (fluid != null || tag == null),
                "Invalid loader tank contents");
        if (fluid != null) {
            LoaderNbt.require(!fluid.equals(EMPTY_FLUID) && LoaderNbt.canonical(fluid.toString()).equals(fluid),
                    "Invalid loader tank Fluid");
            LoaderNbt.require(tag == null || bounded(tag), "Loader tank tag exceeds its envelope");
        }
        this.fluid = fluid;
        this.amount = amount;
        this.tag = tag == null ? null : tag.copy();
        LoaderNbt.require(bounded(encode()), "Loader tank exceeds its envelope");
    }

    /** Pure construction; registration is checked when native data is decoded or converted. */
    public static LoaderTank of(ResourceLocation fluid, CompoundTag tag, int amount) {
        return amount == 0 ? EMPTY : new LoaderTank(Objects.requireNonNull(fluid, "fluid"), tag, amount);
    }

    /** A registered Fluid only; the constructor copies the tag after its envelope check. */
    public static LoaderTank of(FluidStack stack) {
        if (stack.isEmpty()) { return EMPTY; }
        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(stack.getFluid());
        LoaderNbt.require(id != null && stack.getFluid() != Fluids.EMPTY, "Unregistered loader tank Fluid");
        return of(id, stack.getTag(), stack.getAmount());
    }

    public static boolean bounded(Tag tag) {
        return LoaderNbt.bounded(tag, MAX_BYTES, MAX_DEPTH, MAX_NODES);
    }

    public boolean isEmpty() { return amount == 0; }

    /**
     * Whether an escrow operation could ever carry this Fluid. The amount is an int tag of fixed size, so the answer
     * is the same for every amount; intake refuses a Fluid that fails it, while stored data still decodes.
     */
    public boolean escrowCarriable() {
        return isEmpty() || LoaderNbt.bounded(encode(), PAYLOAD_BYTES, PAYLOAD_DEPTH, PAYLOAD_NODES);
    }

    public ResourceLocation fluid() { return fluid; }

    public CompoundTag tag() { return tag == null ? null : tag.copy(); }

    public int amount() { return amount; }

    public int room() { return CAPACITY - amount; }

    /** The same Fluid and native tag with another amount; zero empties the tank. */
    public LoaderTank withAmount(int next) {
        LoaderNbt.require(fluid != null || next == 0, "An empty loader tank has no Fluid to keep");
        return of(fluid, tag, next);
    }

    /** Exactly what {@link FluidStack#writeToNBT} writes, with no copy shared with this tank. */
    public CompoundTag encode() {
        CompoundTag root = new CompoundTag();
        if (fluid != null) {
            root.putString(NAME, fluid.toString());
            root.putInt(AMOUNT, amount);
            if (tag != null) { root.put(TAG, tag.copy()); }
        }
        return root;
    }

    public static LoaderTank decode(Tag raw) {
        return LoaderNbt.decoding("loader tank", () -> {
            LoaderNbt.require(raw instanceof CompoundTag && bounded(raw), "Loader tank exceeds its envelope");
            CompoundTag root = (CompoundTag) raw;
            if (root.isEmpty()) { return EMPTY; }
            LoaderNbt.fields(root, NAME, AMOUNT, TAG);
            ResourceLocation id = LoaderNbt.id(root, NAME);
            int amount = LoaderNbt.integer(root, AMOUNT);
            LoaderNbt.require(amount >= 1 && amount <= CAPACITY, "Loader tank amount outside 1..16,000 mB");
            CompoundTag tag = root.contains(TAG) ? LoaderNbt.compound(root, TAG) : null;
            LoaderNbt.require(!id.equals(EMPTY_FLUID), "The empty Fluid cannot have an amount");
            if (!ForgeRegistries.FLUIDS.containsKey(id) || ForgeRegistries.FLUIDS.getValue(id) == Fluids.EMPTY) {
                throw new LoaderDataException(LoaderDataException.Reason.UNREGISTERED_FLUID, "Unregistered loader tank Fluid " + id);
            }
            // Forge's own reader must rebuild exactly these bytes: no alias, default or dropped field.
            LoaderNbt.require(FluidStack.loadFluidStackFromNBT(root).writeToNBT(new CompoundTag()).equals(root),
                    "Loader tank is not a lossless native envelope");
            return of(id, tag, amount);
        });
    }

    /** A fresh FluidStack for the device's capability; needs the Fluid to be registered. */
    public FluidStack fluidStack() {
        if (fluid == null) { return FluidStack.EMPTY; }
        LoaderNbt.require(ForgeRegistries.FLUIDS.containsKey(fluid), "Unregistered loader tank Fluid");
        return new FluidStack(ForgeRegistries.FLUIDS.getValue(fluid), amount, tag);
    }

    /** W11A section 1: {@code arce-escrow-fluid-tank-1:}, the device UUID and {@link RocketNativeResources#signature} of this envelope. */
    public String signature(UUID device) {
        return RocketNativeResources.hash(SIGNATURE_PREFIX + Objects.requireNonNull(device, "device") + ":"
                + RocketNativeResources.signature(encode()));
    }

    @Override public boolean equals(Object value) {
        return value instanceof LoaderTank other && amount == other.amount && Objects.equals(fluid, other.fluid)
                && Objects.equals(tag, other.tag);
    }

    @Override public int hashCode() { return Objects.hash(fluid, tag, amount); }

    @Override public String toString() {
        return fluid == null ? "LoaderTank[empty]" : "LoaderTank[" + fluid + " " + amount + " mB"
                + (tag == null ? "" : " tag-sha256=" + RocketNativeResources.signature(tag.copy())) + "]";
    }
}
