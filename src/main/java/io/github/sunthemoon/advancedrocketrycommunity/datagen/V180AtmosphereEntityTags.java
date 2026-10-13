package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.NonPlayerAtmosphereService;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.EntityTypeTagsProvider;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * The ADR-066 section 3.3 {@code atmosphere_bypass} entity-type tag, generated empty. Modpacks nominate their actual
 * vacuum-adapted types; the tag exempts only the atmospheric effects and the atmosphere spawn admission.
 */
public final class V180AtmosphereEntityTags extends EntityTypeTagsProvider {
    public V180AtmosphereEntityTags(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup,
                                    ExistingFileHelper existingFiles) {
        super(output, lookup, AdvancedRocketryCommunity.MOD_ID, existingFiles);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(NonPlayerAtmosphereService.ATMOSPHERE_BYPASS);
    }
}
