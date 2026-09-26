package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.port.ProcessPortKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Schema-1 legacy Item root or physically persistent Energy port root. */
final class PrecisionAssemblerPortPersistence {
    static final String ROOT = "arce_precision_port";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ROOT_BYTES = 65_536;
    static final int MAX_ITEM_BYTES = 16_384;
    static final int ENERGY_CAPACITY = 20_000;

    private static final Set<String> ITEM_FIELDS = Set.of("schema_version", "port_type", "item");
    private static final Set<String> ENERGY_FIELDS = Set.of("schema_version", "port_type", "energy");
    private static final Set<String> ITEM_STACK_FIELDS = Set.of("id", "Count");

    private PrecisionAssemblerPortPersistence() {
    }

    static CompoundTag encode(PrecisionAssemblerPortType type, ItemStack item, int energy) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(item, "item");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("port_type", type.registryPath());
        if (type.kind() == ProcessPortKind.ITEM) {
            if (energy != 0) {
                throw new IllegalStateException("Item port cannot contain unrelated energy");
            }
            root.put("item", encodeItem(item));
        } else {
            if (!item.isEmpty() || energy < 0 || energy > ENERGY_CAPACITY) {
                throw new IllegalStateException("Energy port resources are invalid");
            }
            root.putInt("energy", energy);
        }
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Precision Assembler port root exceeds 64 KiB");
        }
        return root;
    }

    static DecodeResult decode(CompoundTag parent, PrecisionAssemblerPortType expectedType) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(expectedType, "expectedType");
        if (!parent.contains(ROOT)) {
            return DecodeResult.empty();
        }
        Tag raw = parent.get(ROOT);
        if (!(raw instanceof CompoundTag root)
                || root.sizeInBytes() > MAX_ROOT_BYTES
                || !root.contains("schema_version", Tag.TAG_INT)) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, raw);
        }
        if (root.getInt("schema_version") != SCHEMA_VERSION) {
            return DecodeResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            Set<String> fields = expectedType.kind()
                    == ProcessPortKind.ITEM
                    ? ITEM_FIELDS : ENERGY_FIELDS;
            if (!fields.equals(root.getAllKeys())
                    || !root.contains("port_type", Tag.TAG_STRING)
                    || !expectedType.registryPath().equals(root.getString("port_type"))) {
                throw new IllegalArgumentException("Precision Assembler port identity is invalid");
            }
            PortData data;
            if (expectedType.kind() == ProcessPortKind.ITEM) {
                if (!root.contains("item", Tag.TAG_COMPOUND)) {
                    throw new IllegalArgumentException("Item port payload has the wrong type");
                }
                data = new PortData(decodeItem(root.getCompound("item")), 0);
            } else {
                if (!root.contains("energy", Tag.TAG_INT)) {
                    throw new IllegalArgumentException("Energy port payload has the wrong type");
                }
                int energy = root.getInt("energy");
                if (energy < 0 || energy > ENERGY_CAPACITY) {
                    throw new IllegalArgumentException("Energy port payload exceeds capacity");
                }
                data = new PortData(ItemStack.EMPTY, energy);
            }
            return DecodeResult.supported(data);
        } catch (RuntimeException exception) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    static boolean acceptsItem(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return !stack.hasTag()
                && stack.getCount() >= 1
                && stack.getCount() <= stack.getMaxStackSize()
                && id != null
                && id.toString().length() <= ProcessResourceKey.MAX_RESOURCE_ID_CHARS
                && BuiltInRegistries.ITEM.getOptional(id).orElse(null) == stack.getItem();
    }

    static CompoundTag encodeItem(ItemStack item) {
        if (item.isEmpty()) {
            return new CompoundTag();
        }
        if (!acceptsItem(item)) {
            throw new IllegalStateException("Item port rejected invalid stack");
        }
        CompoundTag encoded = item.save(new CompoundTag());
        if (encoded.sizeInBytes() > MAX_ITEM_BYTES) {
            throw new IllegalStateException("Item port payload exceeds 16 KiB");
        }
        return encoded;
    }

    static ItemStack decodeItem(CompoundTag encoded) {
        if (encoded.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (encoded.sizeInBytes() > MAX_ITEM_BYTES
                || !ITEM_STACK_FIELDS.equals(encoded.getAllKeys())
                || !encoded.contains("id", Tag.TAG_STRING)
                || !encoded.contains("Count", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Item port payload is malformed or oversized");
        }
        ResourceLocation id = ResourceLocation.tryParse(encoded.getString("id"));
        if (id == null || id.toString().length() > ProcessResourceKey.MAX_RESOURCE_ID_CHARS) {
            throw new IllegalArgumentException("Item port payload has an invalid id");
        }
        ItemStack item = ItemStack.of(encoded);
        if (item.isEmpty() || !acceptsItem(item)) {
            throw new IllegalArgumentException("Item port payload has an invalid item");
        }
        return item;
    }

    record PortData(ItemStack item, int energy) {
        PortData {
            item = Objects.requireNonNull(item, "item").copy();
        }

        @Override
        public ItemStack item() {
            return item.copy();
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

        static DecodeResult empty() {
            return new DecodeResult(MultiblockNbtStatus.EMPTY, Optional.empty(), Optional.empty());
        }

        static DecodeResult supported(PortData value) {
            return new DecodeResult(MultiblockNbtStatus.SUPPORTED, Optional.of(value), Optional.empty());
        }

        static DecodeResult rejected(MultiblockNbtStatus status, Tag root) {
            return new DecodeResult(status, Optional.empty(), Optional.ofNullable(root));
        }

        @Override
        public Optional<Tag> preservedRoot() {
            return preservedRoot.map(Tag::copy);
        }
    }
}
