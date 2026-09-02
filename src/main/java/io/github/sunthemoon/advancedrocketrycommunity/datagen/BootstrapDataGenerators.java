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

        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        generator.addProvider(event.includeClient(), new SatelliteBlockStateProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new SatelliteItemModelProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new SatelliteLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new SatelliteLanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), new SatelliteRecipeProvider(output));
        generator.addProvider(event.includeServer(), SatelliteLootTableProvider.create(output));
        generator.addProvider(
                event.includeServer(),
                new SatelliteBlockTagsProvider(output, event.getLookupProvider(), existingFiles)
        );
        generator.addProvider(event.includeServer(), new SatelliteDefinitionProvider(output));
    }
}
