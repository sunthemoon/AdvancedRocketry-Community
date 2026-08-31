package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> ATMOSPHERE_SEALING = create("atmosphere_sealing");
    public static final TagKey<Block> ATMOSPHERE_PERMEABLE = create("atmosphere_permeable");

    private ModBlockTags() {
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, ModIdentity.id(path));
    }
}
