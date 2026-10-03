package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks.Crystal;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * Block states and models of the C15c blocks (ADR-063 section 6), run by the one v1.8 block-state provider
 * ({@link V180MaterialModels}): a pillar log, cut-out leaves, sapling and mushroom, cube planks, and translucent
 * crystal blocks that share one texture and tint it (tint index 0 on every face, from the vanilla leaves model).
 */
final class V180ExoplanetModels {
    private static final ExistingFileHelper.ResourceType TEXTURE =
            new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures");

    private V180ExoplanetModels() {
    }

    static void register(BlockStateProvider provider, ExistingFileHelper existingFiles) {
        for (String texture : V180ExoplanetArt.textures().keySet()) {
            existingFiles.trackGenerated(provider.modLoc(texture), TEXTURE);
        }
        RotatedPillarBlock log = (RotatedPillarBlock) ExoplanetBlocks.LIGHTWOOD_LOG.get();
        provider.logBlock(log);
        provider.simpleBlockItem(log, new ModelFile.UncheckedModelFile(provider.modLoc("block/lightwood_log")));

        ModelFile leaves = provider.models().cubeAll("lightwood_leaves", provider.modLoc("block/lightwood_leaves"))
                .renderType("cutout_mipped");
        provider.simpleBlock(ExoplanetBlocks.LIGHTWOOD_LEAVES.get(), leaves);
        provider.simpleBlockItem(ExoplanetBlocks.LIGHTWOOD_LEAVES.get(), leaves);

        ModelFile planks = provider.models().cubeAll("lightwood_planks", provider.modLoc("block/lightwood_planks"));
        provider.simpleBlock(ExoplanetBlocks.LIGHTWOOD_PLANKS.get(), planks);
        provider.simpleBlockItem(ExoplanetBlocks.LIGHTWOOD_PLANKS.get(), planks);

        cross(provider, ExoplanetBlocks.LIGHTWOOD_SAPLING.get(), "lightwood_sapling");
        cross(provider, ExoplanetBlocks.ELECTRIC_MUSHROOM.get(), "electric_mushroom");

        for (Crystal crystal : Crystal.values()) {
            Block block = ExoplanetBlocks.crystal(crystal).get();
            ModelFile model = provider.models().withExistingParent(crystal.id(), "minecraft:block/leaves")
                    .texture("all", provider.modLoc("block/crystal")).renderType("translucent");
            provider.simpleBlock(block, model);
            provider.simpleBlockItem(block, model);
        }
    }

    /** A cross-shaped plant: the block model and a flat item model of the same texture. */
    private static void cross(BlockStateProvider provider, Block block, String name) {
        provider.simpleBlock(block, provider.models().cross(name, provider.modLoc("block/" + name))
                .renderType("cutout"));
        provider.itemModels().withExistingParent(name, "minecraft:item/generated")
                .texture("layer0", provider.modLoc("block/" + name));
    }
}
