package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class AtmosphereDamageTypeTagsProvider extends DamageTypeTagsProvider {
    public AtmosphereDamageTypeTagsProvider(
            PackOutput output,
            CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFiles
    ) {
        super(output, lookupProvider, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider lookupProvider) {
        tag(DamageTypeTags.BYPASSES_ARMOR).addOptional(ModIdentity.id("vacuum"));
        tag(DamageTypeTags.BYPASSES_SHIELD).addOptional(ModIdentity.id("vacuum"));
        tag(DamageTypeTags.BYPASSES_ENCHANTMENTS).addOptional(ModIdentity.id("vacuum"));
        tag(DamageTypeTags.BYPASSES_INVULNERABILITY).addOptional(ModIdentity.id("vacuum"));
    }
}
