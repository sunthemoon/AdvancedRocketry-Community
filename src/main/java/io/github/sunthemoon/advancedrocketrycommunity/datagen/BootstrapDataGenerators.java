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

        // Earlier version outputs remain immutable resource inputs.
        generator.addProvider(event.includeClient(), new V140PlanetaryLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new V140PlanetaryLanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), new PlanetaryContentProvider(output));
        generator.addProvider(event.includeServer(), new PlanetaryExposureProvider(output));
        generator.addProvider(event.includeServer(), new net.minecraftforge.common.data.DatapackBuiltinEntriesProvider(
                output, event.getLookupProvider(), PlanetaryWorldgen.builder(), java.util.Set.of(AdvancedRocketryCommunity.MOD_ID)));
    }
}
