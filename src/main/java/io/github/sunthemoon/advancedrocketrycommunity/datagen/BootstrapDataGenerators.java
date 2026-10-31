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
        generator.addProvider(event.includeClient() || event.includeServer(), new V180ResearchData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180ResearchArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180LandingPadData(output, event.includeClient(), event.includeServer()));

        // Earlier outputs remain immutable inputs; v1.8 providers publish their replacement models here.
        ExistingFileHelper existingFiles = event.getExistingFileHelper();
        generator.addProvider(event.includeClient(), new V180ItemIconModelProvider(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180MaterialArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180LifeSupportData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ForceFieldData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180SealedRocketData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180CombustionArt(output));
        generator.addProvider(event.includeClient(), new V180FluidArt(output));
        generator.addProvider(event.includeClient(), new V180MotorArt(output));
        generator.addProvider(event.includeClient(), new V180ClassicMachineArt(output));
        generator.addProvider(event.includeClient(), new V180ArcCuttingArt(output));
        generator.addProvider(event.includeClient(), new V180C16cMachineArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180C16cMachineData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeServer(), new V180ChemicalSpecialData(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ArcCuttingData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ClassicMachineData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180PropulsionArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180PropulsionData(output, event.includeClient(), event.includeServer()));
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
        generator.addProvider(event.includeClient() || event.includeServer(), new V180GasChargePadData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeServer(), V180MaterialData.loot(output));
        V180MaterialData.Blocks blockTags = generator.addProvider(event.includeServer(),
                new V180MaterialData.Blocks(output, event.getLookupProvider(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialData.Items(output, event.getLookupProvider(),
                blockTags.contentsGetter(), existingFiles));
        generator.addProvider(event.includeServer(), new V180MaterialRecipes(output));
        generator.addProvider(event.includeServer(), new V180ClassicAdvancementData(output));
        generator.addProvider(event.includeServer(), new V180ClassicMilestoneAdvancementData(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180AtmosphereAnalyzerData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180SealDetectorData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180JackhammerData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180LaserGunData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180EquipmentData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180StationLightData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180StationControlData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180BeaconData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180SatelliteBayData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180FluidFuelLoaderData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180RocketCargoData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180RocketItemCargoData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180RocketMonitorData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ThermiteData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180UnlitTorchData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180CraftingComponentData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180IoBoardData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180LiquidIoBoardData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ControlBoardData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180CircuitPlateData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180TrackingCircuitData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180AirlockData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180SolarData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180SolarArrayData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeServer(), new SolarArrayGameTestStructureProvider(output));
        var worldgen = generator.addProvider(event.includeServer(), V180Worldgen.provider(output, event.getLookupProvider()));
        generator.addProvider(event.includeServer(), new V180BiomeTags(output, worldgen.getRegistryProvider(),
                existingFiles));
        generator.addProvider(event.includeServer(), new V180PlanetDimensions(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180SurfaceArt(output));
        generator.addProvider(event.includeClient(), new V180ExoplanetArt(output));
        generator.addProvider(event.includeClient() || event.includeServer(),
                new V180ExoplanetData(output, event.includeClient(), event.includeServer()));
        generator.addProvider(event.includeClient(), new V180SoundArt(output));
        generator.addProvider(event.includeClient(), new V180SoundDefinitions(output, existingFiles));
        generator.addProvider(event.includeClient(), new V180SuitComponentArt(output));
        generator.addProvider(event.includeClient(), new V180ClosureArt(output));
        generator.addProvider(event.includeClient(), new V180SpaceSuitArt(output));
        generator.addProvider(event.includeClient(), new V180ClosureModels(output));
        generator.addProvider(event.includeServer(), new V180AtmosphereEntityTags(output, event.getLookupProvider(),
                existingFiles));
        generator.addProvider(event.includeServer(), new V180RocketCargoTags(output, event.getLookupProvider(), existingFiles));
    }
}
