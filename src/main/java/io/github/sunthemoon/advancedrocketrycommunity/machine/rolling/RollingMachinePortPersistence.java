package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Strict machine-specific schema for one typed port's retained resources. */
final class RollingMachinePortPersistence {
    static final String ROOT = "arce_rolling_port";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ROOT_BYTES = 65_536;
    static final int MAX_ITEM_BYTES = 16_384;

    private static final Set<String> ITEM_FIELDS = Set.of("schema_version", "port_type", "item");
    private static final Set<String> FLUID_FIELDS = Set.of("schema_version", "port_type", "fluid");
    private static final Set<String> ENERGY_FIELDS = Set.of("schema_version", "port_type", "energy");
    private static final Set<String> ITEM_STACK_FIELDS = Set.of("id", "Count");
    private static final Set<String> FLUID_STACK_FIELDS = Set.of("FluidName", "Amount");

    private RollingMachinePortPersistence() {
    }

    static CompoundTag encode(
            RollingMachinePortType type,
            ItemStack item,
            FluidStack fluid,
            int energy
    ) {
        Objects.requireNonNull(type, "type");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("port_type", type.registryPath());
        switch (type.kind()) {
            case ITEM -> {
                requireEmpty(fluid, energy);
                root.put("item", encodeItem(item));
            }
            case FLUID -> {
                requireEmpty(item, energy);
                root.put("fluid", encodeFluid(fluid));
            }
            case ENERGY -> {
                requireEmpty(item, fluid);
                if (energy < 0 || energy > RollingMachinePortBlockEntity.ENERGY_CAPACITY) {
                    throw new IllegalStateException("Rolling Machine port energy is outside its bound");
                }
                root.putInt("energy", energy);
            }
        }
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Rolling Machine port data exceeded its 64 KiB bound");
        }
        return root;
    }

    static DecodeResult decode(CompoundTag parent, RollingMachinePortType expectedType) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(expectedType, "expectedType");
        if (!parent.contains(ROOT)) {
            return DecodeResult.empty();
        }
        Tag raw = parent.get(ROOT);
        if (!(raw instanceof CompoundTag root)) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, raw);
        }
        if (root.sizeInBytes() > MAX_ROOT_BYTES
                || !root.contains("schema_version", Tag.TAG_INT)) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            return DecodeResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            if (!expectedFields(expectedType).equals(root.getAllKeys())
                    || !root.contains("port_type", Tag.TAG_STRING)
                    || !expectedType.registryPath().equals(root.getString("port_type"))) {
                throw new IllegalArgumentException("invalid Rolling Machine port root");
            }
            PortData data = switch (expectedType.kind()) {
                case ITEM -> new PortData(
                        decodeItem(requireCompound(root, "item")),
                        FluidStack.EMPTY,
                        0
                );
                case FLUID -> new PortData(
                        ItemStack.EMPTY,
                        decodeFluid(requireCompound(root, "fluid")),
                        0
                );
                case ENERGY -> {
                    if (!root.contains("energy", Tag.TAG_INT)) {
                        throw new IllegalArgumentException("Rolling Machine energy has the wrong type");
                    }
                    int energy = root.getInt("energy");
                    if (energy < 0 || energy > RollingMachinePortBlockEntity.ENERGY_CAPACITY) {
                        throw new IllegalArgumentException("Rolling Machine energy is outside its bound");
                    }
                    yield new PortData(ItemStack.EMPTY, FluidStack.EMPTY, energy);
                }
            };
            return DecodeResult.supported(data);
        } catch (RuntimeException exception) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    private static CompoundTag encodeItem(ItemStack item) {
        ItemStack checked = Objects.requireNonNull(item, "item");
        if (checked.isEmpty()) {
            return new CompoundTag();
        }
        if (!acceptsItem(checked)) {
            throw new IllegalStateException("Rolling Machine port item is invalid");
        }
        CompoundTag encoded = checked.save(new CompoundTag());
        if (encoded.sizeInBytes() > MAX_ITEM_BYTES) {
            throw new IllegalStateException("Rolling Machine port item exceeded its NBT bound");
        }
        return encoded;
    }

    private static ItemStack decodeItem(CompoundTag encoded) {
        if (encoded.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (encoded.sizeInBytes() > MAX_ITEM_BYTES) {
            throw new IllegalArgumentException("Rolling Machine port item exceeds its NBT bound");
        }
        if (!ITEM_STACK_FIELDS.equals(encoded.getAllKeys())
                || !encoded.contains("id", Tag.TAG_STRING)
                || !encoded.contains("Count", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Rolling Machine port item has unexpected fields");
        }
        ItemStack item = ItemStack.of(encoded);
        if (item.isEmpty() || !acceptsItem(item)) {
            throw new IllegalArgumentException("Rolling Machine port item is invalid");
        }
        return item;
    }

    private static CompoundTag encodeFluid(FluidStack fluid) {
        FluidStack checked = Objects.requireNonNull(fluid, "fluid");
        if (checked.isEmpty()) {
            return new CompoundTag();
        }
        if (checked.getFluid() != Fluids.WATER
                || checked.hasTag()
                || checked.getAmount() < 1
                || checked.getAmount() > RollingMachinePortBlockEntity.FLUID_CAPACITY) {
            throw new IllegalStateException("Rolling Machine port fluid is invalid");
        }
        return checked.writeToNBT(new CompoundTag());
    }

    private static FluidStack decodeFluid(CompoundTag encoded) {
        if (encoded.isEmpty()) {
            return FluidStack.EMPTY;
        }
        if (!FLUID_STACK_FIELDS.equals(encoded.getAllKeys())
                || !encoded.contains("FluidName", Tag.TAG_STRING)
                || !encoded.contains("Amount", Tag.TAG_INT)) {
            throw new IllegalArgumentException("Rolling Machine port fluid has unexpected fields");
        }
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(encoded);
        if (fluid.isEmpty()
                || fluid.getFluid() != Fluids.WATER
                || fluid.hasTag()
                || fluid.getAmount() < 1
                || fluid.getAmount() > RollingMachinePortBlockEntity.FLUID_CAPACITY) {
            throw new IllegalArgumentException("Rolling Machine port fluid is invalid");
        }
        return fluid;
    }

    private static CompoundTag requireCompound(CompoundTag root, String field) {
        if (!root.contains(field, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Rolling Machine port field has the wrong type");
        }
        return root.getCompound(field);
    }

    static boolean acceptsItem(ItemStack item) {
        ItemStack checked = Objects.requireNonNull(item, "item");
        return checked.isEmpty()
                || (!checked.hasTag()
                && checked.getCount() >= 1
                && checked.getCount() <= checked.getMaxStackSize());
    }

    private static Set<String> expectedFields(RollingMachinePortType type) {
        return switch (type.kind()) {
            case ITEM -> ITEM_FIELDS;
            case FLUID -> FLUID_FIELDS;
            case ENERGY -> ENERGY_FIELDS;
        };
    }

    private static void requireEmpty(FluidStack fluid, int energy) {
        if (!Objects.requireNonNull(fluid, "fluid").isEmpty() || energy != 0) {
            throw new IllegalStateException("Item port data contained an unrelated resource");
        }
    }

    private static void requireEmpty(ItemStack item, int energy) {
        if (!Objects.requireNonNull(item, "item").isEmpty() || energy != 0) {
            throw new IllegalStateException("Fluid port data contained an unrelated resource");
        }
    }

    private static void requireEmpty(ItemStack item, FluidStack fluid) {
        if (!Objects.requireNonNull(item, "item").isEmpty()
                || !Objects.requireNonNull(fluid, "fluid").isEmpty()) {
            throw new IllegalStateException("Energy port data contained an unrelated resource");
        }
    }

    record PortData(ItemStack item, FluidStack fluid, int energy) {
        PortData {
            item = item.copy();
            fluid = fluid.copy();
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }

        @Override
        public FluidStack fluid() {
            return fluid.copy();
        }
    }

    record DecodeResult(
            MultiblockNbtStatus status,
            Optional<PortData> value,
            Optional<Tag> preservedRoot
    ) {
        DecodeResult {
            Objects.requireNonNull(status, "status");
            value = Objects.requireNonNull(value, "value");
            preservedRoot = Objects.requireNonNull(preservedRoot, "preservedRoot").map(Tag::copy);
        }

        private static DecodeResult empty() {
            return new DecodeResult(MultiblockNbtStatus.EMPTY, Optional.empty(), Optional.empty());
        }

        private static DecodeResult supported(PortData value) {
            return new DecodeResult(
                    MultiblockNbtStatus.SUPPORTED,
                    Optional.of(value),
                    Optional.empty()
            );
        }

        private static DecodeResult rejected(MultiblockNbtStatus status, Tag root) {
            return new DecodeResult(status, Optional.empty(), Optional.ofNullable(root));
        }

        @Override
        public Optional<Tag> preservedRoot() {
            return preservedRoot.map(Tag::copy);
        }
    }
}
