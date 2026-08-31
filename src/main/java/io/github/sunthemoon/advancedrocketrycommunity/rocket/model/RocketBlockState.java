package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;

/** Registry identity plus sorted serialized block-state properties. */
public final class RocketBlockState implements Comparable<RocketBlockState> {
    private final ResourceLocation blockId;
    private final SortedMap<String, String> properties;
    private final String canonicalKey;

    public RocketBlockState(ResourceLocation blockId, Map<String, String> properties) {
        this.blockId = Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(properties, "properties");
        if (blockId.toString().length() > RocketLimits.MAX_IDENTIFIER_LENGTH) {
            throw invalid("Block identifier exceeds the fixed length limit");
        }
        if (properties.size() > RocketLimits.MAX_BLOCK_PROPERTIES) {
            throw invalid("Block state has too many properties");
        }

        TreeMap<String, String> sorted = new TreeMap<>();
        properties.forEach((name, value) -> {
            if (name == null || name.isBlank()
                    || name.length() > RocketLimits.MAX_PROPERTY_NAME_LENGTH) {
                throw invalid("Invalid block-state property name");
            }
            if (value == null || value.isBlank()
                    || value.length() > RocketLimits.MAX_PROPERTY_VALUE_LENGTH) {
                throw invalid("Invalid block-state property value for " + name);
            }
            sorted.put(name, value);
        });
        this.properties = Collections.unmodifiableSortedMap(sorted);
        StringBuilder key = new StringBuilder(blockId.toString());
        sorted.forEach((name, value) -> key.append('|').append(name).append('=').append(value));
        canonicalKey = key.toString();
    }

    public ResourceLocation blockId() {
        return blockId;
    }

    public SortedMap<String, String> properties() {
        return properties;
    }

    public String canonicalKey() {
        return canonicalKey;
    }

    @Override
    public int compareTo(RocketBlockState other) {
        return canonicalKey.compareTo(other.canonicalKey);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof RocketBlockState other
                && blockId.equals(other.blockId)
                && properties.equals(other.properties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockId, properties);
    }

    @Override
    public String toString() {
        return canonicalKey;
    }

    private static RocketSnapshotException invalid(String message) {
        return new RocketSnapshotException(RocketValidationCode.INVALID_BLOCK_STATE, message);
    }
}
