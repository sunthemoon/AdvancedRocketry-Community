package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Seals a legacy physical Item root after controller ownership is durable. */
final class PrecisionAssemblerPortMigrationPersistence {
    static final String ROOT = "arce_precision_port_migration";
    static final int SCHEMA_VERSION = 1;
    static final int MAX_ROOT_BYTES = 1_024;

    private static final Set<String> FIELDS = Set.of("schema_version", "machine_id", "channel");

    private PrecisionAssemblerPortMigrationPersistence() {
    }

    static CompoundTag encode(Marker marker) {
        Objects.requireNonNull(marker, "marker");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", SCHEMA_VERSION);
        root.putString("machine_id", marker.machineId().toString());
        root.putString("channel", marker.channel());
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Precision port migration marker exceeds 1 KiB");
        }
        return root;
    }

    static DecodeResult decode(CompoundTag parent) {
        Objects.requireNonNull(parent, "parent");
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
                    || !root.contains("channel", Tag.TAG_STRING)) {
                throw new IllegalArgumentException("Precision port migration fields are invalid");
            }
            return DecodeResult.supported(new Marker(
                    UUID.fromString(root.getString("machine_id")), root.getString("channel")));
        } catch (RuntimeException exception) {
            return DecodeResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    record Marker(UUID machineId, String channel) {
        Marker {
            Objects.requireNonNull(machineId, "machineId");
            Objects.requireNonNull(channel, "channel");
            boolean valid = false;
            for (int index = 0; index < PrecisionAssemblerChannels.INPUT_COUNT; index++) {
                valid |= channel.equals(PrecisionAssemblerChannels.input(index));
            }
            for (int index = 0; index < PrecisionAssemblerChannels.OUTPUT_COUNT; index++) {
                valid |= channel.equals(PrecisionAssemblerChannels.output(index));
            }
            if (!valid) {
                throw new IllegalArgumentException("Precision port migration channel is invalid");
            }
        }
    }

    record DecodeResult(
            MultiblockNbtStatus status,
            Optional<Marker> value,
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

        static DecodeResult supported(Marker value) {
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
