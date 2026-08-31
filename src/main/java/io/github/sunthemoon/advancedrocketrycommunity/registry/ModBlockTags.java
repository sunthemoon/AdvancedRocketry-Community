package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class ModBlockTags {
    public static final TagKey<Block> ATMOSPHERE_SEALING = create("atmosphere_sealing");
    public static final TagKey<Block> ATMOSPHERE_PERMEABLE = create("atmosphere_permeable");
    public static final TagKey<Block> ROCKET_MOVABLE = create("rocket_movable");
    public static final TagKey<Block> ROCKET_FORBIDDEN = create("rocket_forbidden");
    public static final TagKey<Block> ROCKET_ENGINES = create("rocket_engines");
    public static final TagKey<Block> ROCKET_FUEL_TANKS = create("rocket_fuel_tanks");
    public static final TagKey<Block> ROCKET_SEATS = create("rocket_seats");
    public static final TagKey<Block> ROCKET_GUIDANCE = create("rocket_guidance");

    private ModBlockTags() {
    }

    private static TagKey<Block> create(String path) {
        return TagKey.create(Registries.BLOCK, ModIdentity.id(path));
    }
}
