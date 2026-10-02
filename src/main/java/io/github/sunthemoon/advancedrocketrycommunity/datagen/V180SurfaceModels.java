package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.SurfaceContent;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Block states and models of the C15b surface blocks (ADR-063 section 5), added by the one v1.8 block-state provider
 * (a DataGen run allows one provider per name). The textures come from {@link V180SurfaceArt}.
 */
final class V180SurfaceModels {
    private static final ExistingFileHelper.ResourceType TEXTURE =
            new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures");

    private V180SurfaceModels() {
    }

    static void register(BlockStateProvider provider, ExistingFileHelper existingFiles) {
        for (String texture : V180SurfaceArt.textures().keySet()) {
            existingFiles.trackGenerated(provider.modLoc(texture), TEXTURE);
        }
        cube(provider, SurfaceContent.MOON_TURF.get(), "moon_turf");
        cube(provider, SurfaceContent.DARK_MOON_TURF.get(), "dark_moon_turf");
        cube(provider, SurfaceContent.FERRIC_SAND.get(), "ferric_sand");
        cube(provider, SurfaceContent.GEODE_SHELL.get(), "geode_shell");
        RotatedPillarBlock log = (RotatedPillarBlock) SurfaceContent.CHARCOAL_LOG.get();
        provider.logBlock(log);
        provider.simpleBlockItem(log, new ModelFile.UncheckedModelFile(provider.modLoc("block/charcoal_log")));
    }

    private static void cube(BlockStateProvider provider, Block block, String name) {
        ModelFile model = provider.models().cubeAll(name, provider.modLoc("block/" + name));
        provider.simpleBlock(block, model);
        provider.simpleBlockItem(block, model);
    }
}
