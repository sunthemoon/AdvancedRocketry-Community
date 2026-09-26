package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Versioned controller-chunk owner for the seven Precision Item channels. */
final class PrecisionAssemblerResourcePersistence {
    static final String ROOT = "arce_precision_resources";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ROOT_BYTES = 65_536;
    static final int ITEM_COUNT = PrecisionAssemblerChannels.INPUT_COUNT
            + PrecisionAssemblerChannels.OUTPUT_COUNT;

    private static final Set<String> FIELDS = Set.of("schema_version", "machine_id", "phase", "items");

    private PrecisionAssemblerResourcePersistence() {
    }

    static CompoundTag encode(ResourceData data) {
        Objects.requireNonNull(data, "data");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("machine_id", data.machineId().toString());
        root.putString("phase", data.phase().id());
        ListTag items = new ListTag();
        for (ItemStack item : data.items()) {
            items.add(PrecisionAssemblerPortPersistence.encodeItem(item));
        }
        root.put("items", items);
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Precision Assembler controller resources exceed 64 KiB");
        }
        return root;
    }

    static DecodeResult decode(CompoundTag parent, UUID expectedMachineId) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(expectedMachineId, "expectedMachineId");
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
            if (!FIELDS.equals(root.getAllKeys())
                    || !root.contains("machine_id", Tag.TAG_STRING)
                    || !root.contains("phase", Tag.TAG_STRING)
                    || !root.contains("items", Tag.TAG_LIST)) {
                throw new IllegalArgumentException("Precision resource fields are invalid");
            }
            UUID machineId = UUID.fromString(root.getString("machine_id"));
            if (!machineId.equals(expectedMachineId)) {
                throw new IllegalArgumentException("Precision resource owner does not match controller");
            }
            Phase phase = Phase.fromId(root.getString("phase"));
            ListTag encoded = root.getList("items", Tag.TAG_COMPOUND);
            if (encoded.size() != ITEM_COUNT || encoded.getElementType() != Tag.TAG_COMPOUND) {
                throw new IllegalArgumentException("Precision resource slot count or element type is invalid");
            }
            List<ItemStack> items = new ArrayList<>(ITEM_COUNT);
            for (int index = 0; index < ITEM_COUNT; index++) {
                items.add(PrecisionAssemblerPortPersistence.decodeItem(encoded.getCompound(index)));
            }
            return DecodeResult.supported(new ResourceData(machineId, phase, items));
        } catch (RuntimeException exception) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    enum Phase {
        PREPARING("preparing"),
        ACTIVE("active");

        private final String id;

        Phase(String id) {
            this.id = id;
        }

        String id() {
            return id;
        }

        static Phase fromId(String id) {
            for (Phase phase : values()) {
                if (phase.id.equals(id)) {
                    return phase;
                }
            }
            throw new IllegalArgumentException("Unknown Precision resource phase");
        }
    }

    record ResourceData(UUID machineId, Phase phase, List<ItemStack> items) {
        ResourceData {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(items, "items");
            if (items.size() != ITEM_COUNT) {
                throw new IllegalArgumentException("Precision resource slot count is invalid");
            }
            items = items.stream().map(item -> {
                if (!PrecisionAssemblerPortPersistence.acceptsItem(item)) {
                    throw new IllegalArgumentException("Precision resource Item is invalid");
                }
                return item.copy();
            }).toList();
        }

        @Override
        public List<ItemStack> items() {
            return items.stream().map(ItemStack::copy).toList();
        }
    }

    record DecodeResult(
            MultiblockNbtStatus status,
            Optional<ResourceData> value,
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

        static DecodeResult supported(ResourceData value) {
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
