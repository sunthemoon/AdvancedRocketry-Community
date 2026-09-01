package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/** References a vanilla model at runtime; no Mojang texture bytes are copied. */
public final class FlightItemModelProvider extends ItemModelProvider {
    public FlightItemModelProvider(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void registerModels() {
        withExistingParent("rocket_fuel_cell", mcLoc("item/fire_charge"));
    }
}
