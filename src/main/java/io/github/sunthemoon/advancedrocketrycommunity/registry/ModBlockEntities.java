package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.WarpCoreBlockEntity;
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
    public static final RegistryObject<BlockEntityType<RollingMachineBlockEntity>> ROLLING_MACHINE =
            BLOCK_ENTITIES.register(
                    "rolling_machine",
                    () -> BlockEntityType.Builder.of(
                            RollingMachineBlockEntity::new,
                            ModBlocks.ROLLING_MACHINE.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<RollingMachinePortBlockEntity>> ROLLING_MACHINE_PORT =
            BLOCK_ENTITIES.register(
                    "rolling_machine_port",
                    () -> BlockEntityType.Builder.of(
                            RollingMachinePortBlockEntity::new,
                            ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT.get(),
                            ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT.get(),
                            ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT.get(),
                            ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<PrecisionAssemblerBlockEntity>> PRECISION_ASSEMBLER =
            BLOCK_ENTITIES.register(
                    "precision_assembler",
                    () -> BlockEntityType.Builder.of(
                            PrecisionAssemblerBlockEntity::new,
                            ModBlocks.PRECISION_ASSEMBLER.get()
                    ).build(null)
            );
    public static final RegistryObject<BlockEntityType<PrecisionAssemblerPortBlockEntity>> PRECISION_ASSEMBLER_PORT =
            BLOCK_ENTITIES.register(
                    "precision_assembler_port",
                    () -> BlockEntityType.Builder.of(
                            PrecisionAssemblerPortBlockEntity::new,
                            ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get(),
                            ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get(),
                            ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get()
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

    public static final RegistryObject<BlockEntityType<WarpCoreBlockEntity>> WARP_CORE =
            BLOCK_ENTITIES.register(
                    "warp_core",
                    () -> BlockEntityType.Builder.of(
                            WarpCoreBlockEntity::new,
                            ModBlocks.WARP_CORE.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<SatelliteBuilderBlockEntity>> SATELLITE_BUILDER =
            BLOCK_ENTITIES.register(
                    "satellite_builder",
                    () -> BlockEntityType.Builder.of(
                            SatelliteBuilderBlockEntity::new,
                            ModBlocks.SATELLITE_BUILDER.get()
                    ).build(null)
            );

    public static final RegistryObject<BlockEntityType<MicrowaveReceiverBlockEntity>> MICROWAVE_RECEIVER =
            BLOCK_ENTITIES.register(
                    "microwave_receiver",
                    () -> BlockEntityType.Builder.of(
                            MicrowaveReceiverBlockEntity::new,
                            ModBlocks.MICROWAVE_RECEIVER.get()
                    ).build(null)
            );

    /** ADR-055: the orbital laser drill controller. */
    public static final RegistryObject<BlockEntityType<OrbitalLaserDrillBlockEntity>> ORBITAL_LASER_DRILL =
            BLOCK_ENTITIES.register(
                    "orbital_laser_drill",
                    () -> BlockEntityType.Builder.of(
                            OrbitalLaserDrillBlockEntity::new,
                            ModBlocks.ORBITAL_LASER_DRILL.get()
                    ).build(null)
            );

    /** ADR-055: the laser target endpoint. */
    public static final RegistryObject<BlockEntityType<LaserTargetBlockEntity>> LASER_TARGET =
            BLOCK_ENTITIES.register(
                    "laser_target",
                    () -> BlockEntityType.Builder.of(
                            LaserTargetBlockEntity::new,
                            ModBlocks.LASER_TARGET.get()
                    ).build(null)
            );

    /** ADR-058: the area gravity field controller. */
    public static final RegistryObject<BlockEntityType<GravityFieldBlockEntity>> GRAVITY_FIELD_CONTROLLER =
            BLOCK_ENTITIES.register(
                    "gravity_field_controller",
                    () -> BlockEntityType.Builder.of(
                            GravityFieldBlockEntity::new,
                            ModBlocks.GRAVITY_FIELD_CONTROLLER.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
