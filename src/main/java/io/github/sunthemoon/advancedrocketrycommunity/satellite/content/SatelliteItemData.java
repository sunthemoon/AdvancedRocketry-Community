package io.github.sunthemoon.advancedrocketrycommunity.satellite.content;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.BoundedCelestialCodecs;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteNbtSize;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Strict, independently versioned item identity codec with a fixed NBT budget. */
public final class SatelliteItemData {
    /** Schema 1 still encodes {@code data} identities; schema 2 adds kind and components (ADR-049 §6). */
    public static final int LEGACY_SCHEMA_VERSION = 1;
    public static final int SCHEMA_VERSION = 2;
    public static final String DATA_KEY = "SatelliteIdentity";

    private SatelliteItemData() {
    }

    public static void write(ItemStack stack, SatelliteIdentity identity) {
        Objects.requireNonNull(stack, "stack");
        writeTag(stack.getOrCreateTag(), identity);
    }

    static void writeTag(CompoundTag root, SatelliteIdentity identity) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(identity, "identity");
        CompoundTag data = new CompoundTag();
        boolean dataKind = identity.kind() == SatelliteKind.DATA;
        data.putInt("schema_version", dataKind ? LEGACY_SCHEMA_VERSION : SCHEMA_VERSION);
        data.putUUID("satellite_id", identity.satelliteId());
        data.putUUID("owner_id", identity.ownerId());
        data.putString("definition_id", identity.definitionId().toString());
        if (!dataKind) {
            data.putString("kind", identity.kind().id());
            ListTag components = new ListTag();
            identity.components().forEach(id -> components.add(StringTag.valueOf(id.toString())));
            data.put("components", components);
        }
        if (SatelliteNbtSize.uncompressedBytes(data) > SatelliteLimits.MAX_RECORD_NBT_BYTES) {
            throw new IllegalStateException("Satellite item identity exceeds its fixed NBT bound");
        }
        root.put(DATA_KEY, data);
    }

    public static DecodeResult read(ItemStack stack) {
        Objects.requireNonNull(stack, "stack");
        return readTag(stack.getTag());
    }

    static DecodeResult readTag(CompoundTag root) {
        if (root == null || !root.contains(DATA_KEY)) {
            return DecodeResult.empty();
        }
        if (!root.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
            return DecodeResult.invalid();
        }
        CompoundTag data = root.getCompound(DATA_KEY);
        if (SatelliteNbtSize.uncompressedBytes(data) > SatelliteLimits.MAX_RECORD_NBT_BYTES
                || !data.contains("schema_version", Tag.TAG_INT)) {
            return DecodeResult.invalid();
        }
        int schema = data.getInt("schema_version");
        if (schema > SCHEMA_VERSION) {
            return DecodeResult.future();
        }
        if ((schema != LEGACY_SCHEMA_VERSION && schema != SCHEMA_VERSION)
                || !data.hasUUID("satellite_id")
                || !data.hasUUID("owner_id")
                || !data.contains("definition_id", Tag.TAG_STRING)) {
            return DecodeResult.invalid();
        }
        String rawDefinition = data.getString("definition_id");
        if (rawDefinition.length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS) {
            return DecodeResult.invalid();
        }
        ResourceLocation definitionId = ResourceLocation.tryParse(rawDefinition);
        if (definitionId == null) {
            return DecodeResult.invalid();
        }
        if (schema == LEGACY_SCHEMA_VERSION) {
            if (data.contains("kind") || data.contains("components")) {
                return DecodeResult.invalid();
            }
            return DecodeResult.valid(new SatelliteIdentity(
                    data.getUUID("satellite_id"),
                    data.getUUID("owner_id"),
                    definitionId
            ));
        }
        Optional<SatelliteKind> kind = data.contains("kind", Tag.TAG_STRING)
                ? SatelliteKind.parse(data.getString("kind")) : Optional.empty();
        Tag rawComponents = data.get("components");
        if (kind.isEmpty() || kind.get() == SatelliteKind.DATA
                || !(rawComponents instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_STRING)
                || list.isEmpty() || list.size() > SatelliteLimits.MAX_BLUEPRINT_COMPONENTS) {
            return DecodeResult.invalid();
        }
        List<ResourceLocation> components = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            String raw = list.getString(index);
            ResourceLocation component = raw.length() > BoundedCelestialCodecs.MAX_RESOURCE_LOCATION_CHARS
                    ? null : ResourceLocation.tryParse(raw);
            if (component == null) {
                return DecodeResult.invalid();
            }
            components.add(component);
        }
        return DecodeResult.valid(new SatelliteIdentity(
                data.getUUID("satellite_id"),
                data.getUUID("owner_id"),
                definitionId,
                kind.get(),
                components
        ));
    }

    public enum DecodeStatus {
        EMPTY,
        VALID,
        FUTURE,
        INVALID
    }

    public record DecodeResult(DecodeStatus status, Optional<SatelliteIdentity> identity) {
        public DecodeResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(identity, "identity");
            if ((status == DecodeStatus.VALID) != identity.isPresent()) {
                throw new IllegalArgumentException("Only valid item data may expose an identity");
            }
        }

        public static DecodeResult empty() {
            return new DecodeResult(DecodeStatus.EMPTY, Optional.empty());
        }

        public static DecodeResult valid(SatelliteIdentity identity) {
            return new DecodeResult(DecodeStatus.VALID, Optional.of(identity));
        }

        public static DecodeResult future() {
            return new DecodeResult(DecodeStatus.FUTURE, Optional.empty());
        }

        public static DecodeResult invalid() {
            return new DecodeResult(DecodeStatus.INVALID, Optional.empty());
        }
    }
}
