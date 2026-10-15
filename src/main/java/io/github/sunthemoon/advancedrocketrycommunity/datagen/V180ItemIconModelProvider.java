package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** v1.8 replacements for the six item models bound to the provenance-recorded K6 icons. */
public final class V180ItemIconModelProvider extends ItemModelProvider {
    public V180ItemIconModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        for (String item : new String[] {"laser_lens", "solar_transmitter_module", "data_satellite_package",
                "survey_scanner_module", "satellite_package", "satellite_control_chip"}) {
            withExistingParent(item, mcLoc("item/generated")).texture("layer0", modLoc("item/" + item));
        }
    }
}
