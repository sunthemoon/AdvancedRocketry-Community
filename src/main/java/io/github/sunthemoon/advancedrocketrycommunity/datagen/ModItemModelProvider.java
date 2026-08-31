package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class ModItemModelProvider extends ItemModelProvider {
    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        canister("empty_canister");
        canister("hydrogen_canister");
        canister("oxygen_canister");
    }

    private void canister(String name) {
        withExistingParent(name, mcLoc("item/generated"))
                .texture("layer0", mcLoc("item/glass_bottle"));
    }
}
