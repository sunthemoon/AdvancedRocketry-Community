package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModSounds;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.common.data.SoundDefinitionsProvider;

public final class ModSoundDefinitionsProvider extends SoundDefinitionsProvider {
    public ModSoundDefinitionsProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFileHelper);
    }

    @Override
    public void registerSounds() {
        add(
                ModSounds.UI_SELECT,
                definition()
                        .subtitle("subtitle.advancedrocketrycommunity.ui_select")
                        .with(sound("advancedrocketrycommunity:ui_select"))
        );
    }
}
