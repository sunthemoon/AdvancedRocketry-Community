package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER =
            BLOCK_ENTITIES.register(
                    "electrolyzer",
                    () -> BlockEntityType.Builder.of(
                            ElectrolyzerBlockEntity::new,
                            ModBlocks.ELECTROLYZER.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<OxygenVentBlockEntity>> OXYGEN_VENT =
            BLOCK_ENTITIES.register(
                    "oxygen_vent",
                    () -> BlockEntityType.Builder.of(
                            OxygenVentBlockEntity::new,
                            ModBlocks.OXYGEN_VENT.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<RocketAssemblerBlockEntity>> ROCKET_ASSEMBLER =
            BLOCK_ENTITIES.register(
                    "rocket_assembler",
                    () -> BlockEntityType.Builder.of(
                            RocketAssemblerBlockEntity::new,
                            ModBlocks.ROCKET_ASSEMBLER.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<FuelLoaderBlockEntity>> FUEL_LOADER =
            BLOCK_ENTITIES.register(
                    "fuel_loader",
                    () -> BlockEntityType.Builder.of(
                            FuelLoaderBlockEntity::new,
                            ModBlocks.FUEL_LOADER.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<SatelliteTerminalBlockEntity>> SATELLITE_TERMINAL =
            BLOCK_ENTITIES.register(
                    "satellite_terminal",
                    () -> BlockEntityType.Builder.of(
                            SatelliteTerminalBlockEntity::new,
                            ModBlocks.SATELLITE_TERMINAL.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
