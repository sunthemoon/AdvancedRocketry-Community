package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ConfiguredModel;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

public final class V180CombustionModels extends BlockStateProvider {
    private final ExistingFileHelper files;

    public V180CombustionModels(PackOutput output, ExistingFileHelper files) {
        super(output, AdvancedRocketryCommunity.MOD_ID, files);
        this.files = files;
    }

    @Override protected void registerStatesAndModels() {
        var texture = new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures");
        V180CombustionArt.FACES.forEach(face -> files.trackGenerated(modLoc("block/combustion_" + face), texture));
        ModelFile off = cube("combustion_generator", "front");
        ModelFile on = cube("combustion_generator_lit", "front_lit");
        getVariantBuilder(ModBlocks.COMBUSTION_GENERATOR.get()).forAllStates(state -> {
            Direction facing = state.getValue(CombustionGeneratorBlock.FACING);
            return ConfiguredModel.builder().modelFile(state.getValue(CombustionGeneratorBlock.LIT) ? on : off)
                    .rotationY(((int) facing.toYRot() + 180) % 360).build();
        });
        simpleBlockItem(ModBlocks.COMBUSTION_GENERATOR.get(), off);
    }

    private ModelFile cube(String name, String front) {
        return models().orientable(name, modLoc("block/combustion_side"), modLoc("block/combustion_" + front),
                modLoc("block/combustion_top"));
    }

    @Override public String getName() { return "v1.8 combustion generator models"; }
}
