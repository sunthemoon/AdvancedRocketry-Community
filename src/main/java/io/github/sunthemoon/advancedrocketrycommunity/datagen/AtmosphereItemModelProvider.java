package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Uses vanilla runtime model references; no Mojang texture bytes are copied. */
public final class AtmosphereItemModelProvider extends ItemModelProvider {
    public AtmosphereItemModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        withExistingParent("space_suit_helmet", mcLoc("item/iron_helmet"));
        withExistingParent("space_suit_chestplate", mcLoc("item/iron_chestplate"));
        withExistingParent("space_suit_leggings", mcLoc("item/iron_leggings"));
        withExistingParent("space_suit_boots", mcLoc("item/iron_boots"));
    }
}
