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
        generator.addProvider(event.includeClient(), new V180CombustionArt(output));
        generator.addProvider(event.includeClient(), new V180FluidArt(output));
        generator.addProvider(event.includeClient(), new V180MotorArt(output));
        generator.addProvider(event.includeClient(), new V180TankArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180TankData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180PumpArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180PumpData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180MotorData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180FluidData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180CombustionModels(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180MaterialModels(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180LanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new V180LanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), V180MaterialData.loot(output));
        V180MaterialData.Blocks blockTags = generator.addProvider(event.includeServer(),
                new V180MaterialData.Blocks(output, event.getLookupProvider(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialData.Items(output, event.getLookupProvider(),
                blockTags.contentsGetter(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialRecipes(output));
        generator.addProvider(event.includeServer(), new V180ClassicAdvancementData(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180AtmosphereAnalyzerData(output, event.includeClient(), event.includeServer()));
        var worldgen = generator.addProvider(event.includeServer(), V180Worldgen.provider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new V180BiomeTags(output, worldgen.getRegistryProvider(),
                existingFiles));
        generator.addProvider(event.includeServer(), new V180PlanetDimensions(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180SurfaceArt(output));
        generator.addProvider(event.includeClient(), new V180ExoplanetArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ExoplanetData(output, event.includeClient(), event.includeServer()));
    }
}
