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

        generator.addProvider(event.includeClient(), new ModBlockStateProvider(
                output,
                event.getExistingFileHelper()
        ));
        generator.addProvider(event.includeClient(), new ModItemModelProvider(
                output,
                event.getExistingFileHelper()
        ));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output, "en_us"));
        generator.addProvider(event.includeClient(), new ModLanguageProvider(output, "zh_cn"));
        generator.addProvider(event.includeServer(), ModLootTableProvider.create(output));
        generator.addProvider(event.includeServer(), new ModRecipeProvider(output));
    }
}
