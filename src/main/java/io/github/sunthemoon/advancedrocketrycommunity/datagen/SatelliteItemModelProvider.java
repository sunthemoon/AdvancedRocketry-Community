package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** Item models use existing vanilla assets and the provenance-bound K6 item textures. */
public final class SatelliteItemModelProvider extends ItemModelProvider {
    public SatelliteItemModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        withExistingParent("satellite_chassis", mcLoc("item/minecart"));
        withExistingParent("satellite_solar_module", mcLoc("item/daylight_detector"));
        withExistingParent("satellite_control_chip", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/satellite_control_chip"));
        withExistingParent("data_satellite_package", mcLoc("item/generated"))
                .texture("layer0", modLoc("item/data_satellite_package"));
    }
}
