package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

public final class ModItemTags {
    public static final TagKey<Item> SILICON_WAFERS = create("silicon_wafers");
    public static final TagKey<Item> BASIC_CIRCUITS = create("circuits/basic");
    public static final TagKey<Item> ADVANCED_CIRCUITS = create("circuits/advanced");
    public static final TagKey<Item> DATA_STORAGE_UNITS = create("data_storage_units");
    public static final TagKey<Item> MACHINE_CASINGS = create("machine_casings");

    private ModItemTags() {
    }

    private static TagKey<Item> create(String path) {
        return ItemTags.create(ModIdentity.id(path));
    }
}
