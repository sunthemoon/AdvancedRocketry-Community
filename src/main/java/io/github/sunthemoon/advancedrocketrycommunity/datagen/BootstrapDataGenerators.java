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
        generator.addProvider(
                event.includeClient(),
                new RocketBlockStateProvider(output, existingFiles)
        );
        generator.addProvider(event.includeClient(), new RocketLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new RocketLanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), new RocketRecipeProvider(output));
        generator.addProvider(event.includeServer(), RocketLootTableProvider.create(output));
        generator.addProvider(event.includeServer(), new RocketGameTestStructureProvider(output));
        generator.addProvider(
                event.includeServer(),
                new RocketBlockTagsProvider(
                        output,
                        event.getLookupProvider(),
                        existingFiles
                )
        );
    }
}
