package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.registries.ForgeRegistries;

public final class RocketBlockStateAdapter {
    private RocketBlockStateAdapter() {
    }

    public static RocketBlockState capture(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null) {
            throw new IllegalArgumentException("Block state has no Forge registry identity");
        }
        TreeMap<String, String> properties = new TreeMap<>();
        for (Property<?> property : state.getProperties()) {
            properties.put(property.getName(), propertyValueName(state, property));
        }
        return new RocketBlockState(blockId, properties);
    }

    public static Optional<BlockState> restore(RocketBlockState serialized) {
        if (!ForgeRegistries.BLOCKS.containsKey(serialized.blockId())) {
            return Optional.empty();
        }
        Block block = ForgeRegistries.BLOCKS.getValue(serialized.blockId());
        if (block == null) {
            return Optional.empty();
        }
        BlockState state = block.defaultBlockState();
        if (serialized.properties().size() != state.getProperties().size()) {
            return Optional.empty();
        }
        for (Map.Entry<String, String> entry : serialized.properties().entrySet()) {
            Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
            if (property == null) {
                return Optional.empty();
            }
            Optional<BlockState> updated = setProperty(state, property, entry.getValue());
            if (updated.isEmpty()) {
                return Optional.empty();
            }
            state = updated.orElseThrow();
        }
        return Optional.of(state);
    }

    private static <T extends Comparable<T>> String propertyValueName(
            BlockState state,
            Property<T> property
    ) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> Optional<BlockState> setProperty(
            BlockState state,
            Property<T> property,
            String serializedValue
    ) {
        return property.getValue(serializedValue).map(value -> state.setValue(property, value));
    }
}
