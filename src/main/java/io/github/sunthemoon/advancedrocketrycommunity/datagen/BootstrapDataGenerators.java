package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
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

        // Earlier version outputs (v1.6 and before) remain immutable resource inputs.
        generator.addProvider(event.includeClient(), new V170LanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new V170LanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeClient(),
                new V170EndgameProviders.Models(output, event.getExistingFileHelper()));
        generator.addProvider(event.includeServer(), V170EndgameProviders.loot(output));
        generator.addProvider(event.includeServer(), new V170EndgameProviders.Recipes(output));
        generator.addProvider(event.includeServer(), new V170EndgameProviders.ToolTags(
                output, event.getLookupProvider(), event.getExistingFileHelper()));
    }
}
