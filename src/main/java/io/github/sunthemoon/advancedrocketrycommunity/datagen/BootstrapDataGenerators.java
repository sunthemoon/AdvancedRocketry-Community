package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(
        modid = AdvancedRocketryCommunity.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public final class BootstrapDataGenerators {
    private BootstrapDataGenerators() {
    }

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();

        // Earlier version outputs (v1.7 and before) remain immutable resource inputs.
        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        generator.addProvider(event.includeClient(), new V180MaterialArt(output));
        generator.addProvider(event.includeClient(), new V180MaterialModels(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180LanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new V180LanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), V180MaterialData.loot(output));
        V180MaterialData.Blocks blockTags = generator.addProvider(event.includeServer(),
                new V180MaterialData.Blocks(output, event.getLookupProvider(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialData.Items(output, event.getLookupProvider(),
                blockTags.contentsGetter(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialRecipes(output));
        var worldgen = generator.addProvider(event.includeServer(), V180Worldgen.provider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new V180BiomeTags(output, worldgen.getRegistryProvider(),
                existingFiles));
        generator.addProvider(event.includeServer(), new V180PlanetDimensions(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180SurfaceArt(output));
    }
}
