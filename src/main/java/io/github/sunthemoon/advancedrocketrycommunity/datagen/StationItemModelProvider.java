package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Uses an existing vanilla texture; no Mojang image bytes are copied. */
public final class StationItemModelProvider extends ItemModelProvider {
    public StationItemModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        withExistingParent("station_deployment_kit", mcLoc("item/generated"))
                .texture("layer0", mcLoc("item/ender_eye"));
    }
}

