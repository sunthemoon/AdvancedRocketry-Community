package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicHatchKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicHatchBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicMachineDefinition;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.AirlockDoorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlock;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.AdvancedMachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.content.MachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarExposure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlock;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortType;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.WarpCoreBlock;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlock>
            SEALED_ROCKET_BLOCK = BLOCKS.register("sealed_rocket_block", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(-1.0F, 3_600_000.0F)
                            .sound(SoundType.METAL).noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBlock> CO2_SCRUBBER =
            BLOCKS.register("co2_scrubber", () -> new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.ScrubberBlock(metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.PipeSealBlock> PIPE_SEAL =
            BLOCKS.register("pipe_seal", () -> new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.PipeSealBlock(metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasChargePadBlock> GAS_CHARGE_PAD =
            BLOCKS.register("gas_charge_pad", () -> new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.GasChargePadBlock(metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.AtmosphereDetectorBlock> ATMOSPHERE_DETECTOR =
            BLOCKS.register("atmosphere_detector", () -> new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device.AtmosphereDetectorBlock(metalProperties().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldProjectorBlock>
            FORCE_FIELD_PROJECTOR = BLOCKS.register("force_field_projector", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldProjectorBlock(
                    metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldBlock>
            FORCE_FIELD = BLOCKS.register("force_field", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.forcefield.ForceFieldBlock(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F, 6.0F)
                            .sound(SoundType.GLASS).noLootTable().pushReaction(PushReaction.BLOCK)));

    public static final RegistryObject<AirlockDoorBlock> AIRLOCK_DOOR = BLOCKS.register(
            "airlock_door", () -> new AirlockDoorBlock(metalProperties().requiresCorrectToolForDrops()
                    .noOcclusion(), CommonConfig::classicDevicesEnabled));
    public static final RegistryObject<CombustionGeneratorBlock> COMBUSTION_GENERATOR = BLOCKS.register(
            "combustion_generator", () -> new CombustionGeneratorBlock(metalProperties().requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> state.getValue(CombustionGeneratorBlock.LIT) ? 13 : 0)));
    /** Inactive until the matching server starts; never initialized by a block factory. */
    public static final SolarExposure SOLAR_EXPOSURE = new SolarExposure();
    public static final RegistryObject<SolarGeneratorBlock> SOLAR_GENERATOR = BLOCKS.register(
            "solar_generator", () -> new SolarGeneratorBlock(metalProperties().requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK), ModBlockEntities::solarGenerator,
                    () -> ModBlockEntities.SOLAR_GENERATOR.get()));
    public static final RegistryObject<Block> SOLAR_PANEL = BLOCKS.register("solar_panel", () ->
            new Block(metalProperties().requiresCorrectToolForDrops()));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayBlock> SOLAR_ARRAY =
            BLOCKS.register("solar_array", () -> new io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayBlock(
                    metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    ModBlockEntities::solarArray, () -> ModBlockEntities.SOLAR_ARRAY.get()));
    public static final RegistryObject<Block> SOLAR_ARRAY_PANEL = BLOCKS.register("solar_array_panel", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayPartBlock(metalProperties().requiresCorrectToolForDrops()));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayOutputBlock> POWER_OUTPUT_PLUG =
            BLOCKS.register("power_output_plug", () -> new io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayOutputBlock(
                    metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> MACHINE_CASING = BLOCKS.register(
            "machine_casing",
            () -> new MachineCasingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> ELECTROLYZER = BLOCKS.register(
            "electrolyzer",
            () -> new ElectrolyzerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .lightLevel(state -> state.getValue(ElectrolyzerBlock.LIT) ? 8 : 0)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<RollingMachineBlock> ROLLING_MACHINE = BLOCKS.register(
            "rolling_machine",
            () -> new RollingMachineBlock(metalProperties())
    );
    public static final RegistryObject<RollingMachinePortBlock> ROLLING_MACHINE_ITEM_INPUT_PORT =
            rollingPort(RollingMachinePortType.ITEM_INPUT);
    public static final RegistryObject<RollingMachinePortBlock> ROLLING_MACHINE_FLUID_INPUT_PORT =
            rollingPort(RollingMachinePortType.FLUID_INPUT);
    public static final RegistryObject<RollingMachinePortBlock> ROLLING_MACHINE_ENERGY_INPUT_PORT =
            rollingPort(RollingMachinePortType.ENERGY_INPUT);
    public static final RegistryObject<RollingMachinePortBlock> ROLLING_MACHINE_ITEM_OUTPUT_PORT =
            rollingPort(RollingMachinePortType.ITEM_OUTPUT);
    public static final RegistryObject<PrecisionAssemblerBlock> PRECISION_ASSEMBLER = BLOCKS.register(
            "precision_assembler",
            () -> new PrecisionAssemblerBlock(metalProperties())
    );
    public static final RegistryObject<PrecisionAssemblerPortBlock> PRECISION_ASSEMBLER_ITEM_INPUT_PORT =
            precisionPort(PrecisionAssemblerPortType.ITEM_INPUT);
    public static final RegistryObject<PrecisionAssemblerPortBlock> PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT =
            precisionPort(PrecisionAssemblerPortType.ITEM_OUTPUT);
    public static final RegistryObject<PrecisionAssemblerPortBlock> PRECISION_ASSEMBLER_ENERGY_INPUT_PORT =
            precisionPort(PrecisionAssemblerPortType.ENERGY_INPUT);
    public static final RegistryObject<Block> OXYGEN_VENT = BLOCKS.register(
            "oxygen_vent",
            () -> new OxygenVentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .lightLevel(state -> state.getValue(OxygenVentBlock.LIT) ? 8 : 0)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> ROCKET_ASSEMBLER = BLOCKS.register(
            "rocket_assembler",
            () -> new RocketAssemblerBlock(metalProperties())
    );
    public static final RegistryObject<Block> FUEL_LOADER = BLOCKS.register(
            "fuel_loader",
            () -> new FuelLoaderBlock(metalProperties())
    );
    public static final RegistryObject<Block> SATELLITE_TERMINAL = BLOCKS.register(
            "satellite_terminal",
            () -> new SatelliteTerminalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .lightLevel(state -> state.getValue(SatelliteTerminalBlock.LIT) ? 7 : 0)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> ROCKET_MOTOR = metalBlock("rocket_motor");
    public static final RegistryObject<Block> ROCKET_FUEL_TANK = metalBlock("rocket_fuel_tank");
    public static final RegistryObject<Block> ADVANCED_ROCKET_MOTOR = metalBlock("advanced_rocket_motor");
    public static final RegistryObject<Block> BIPROPELLANT_ROCKET_MOTOR = metalBlock("bipropellant_rocket_motor");
    public static final RegistryObject<Block> ADVANCED_BIPROPELLANT_ROCKET_MOTOR = metalBlock("advanced_bipropellant_rocket_motor");
    public static final RegistryObject<Block> BIPROPELLANT_FUEL_TANK = metalBlock("bipropellant_fuel_tank");
    public static final RegistryObject<Block> OXIDIZER_FUEL_TANK = metalBlock("oxidizer_fuel_tank");
    public static final RegistryObject<Block> NUCLEAR_ROCKET_MOTOR = BLOCKS.register("nuclear_rocket_motor", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketNuclearNozzleBlock(metalProperties()));
    public static final RegistryObject<Block> NUCLEAR_FUEL_TANK = metalBlock("nuclear_fuel_tank");
    public static final RegistryObject<Block> NUCLEAR_CORE = metalBlock("nuclear_core");
    public static final RegistryObject<Block> ROCKET_SEAT = metalBlock("rocket_seat");
    public static final RegistryObject<Block> GUIDANCE_COMPUTER = metalBlock("guidance_computer");

    public static final RegistryObject<ClassicHatchBlock> ITEM_INPUT_HATCH = classicHatch(ClassicHatchKind.ITEM_INPUT);
    public static final RegistryObject<ClassicHatchBlock> ITEM_OUTPUT_HATCH = classicHatch(ClassicHatchKind.ITEM_OUTPUT);
    public static final RegistryObject<ClassicHatchBlock> FLUID_INPUT_HATCH = classicHatch(ClassicHatchKind.FLUID_INPUT);
    public static final RegistryObject<ClassicHatchBlock> FLUID_OUTPUT_HATCH = classicHatch(ClassicHatchKind.FLUID_OUTPUT);
    public static final RegistryObject<ClassicHatchBlock> POWER_INPUT_PLUG = classicHatch(ClassicHatchKind.POWER_INPUT);
    public static final RegistryObject<ClassicControllerBlock> LATHE = BLOCKS.register("lathe", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<ClassicControllerBlock> OBSERVATORY = BLOCKS.register("observatory", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchControllerBlockEntity(
                            ModBlockEntities.OBSERVATORY.get(), position, state, false)));
    public static final RegistryObject<ClassicControllerBlock> ASTROBODY_DATA_PROCESSOR = BLOCKS.register("astrobody_data_processor", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchControllerBlockEntity(
                            ModBlockEntities.ASTROBODY_DATA_PROCESSOR.get(), position, state, true)));
    public static final RegistryObject<ClassicControllerBlock> ARC_FURNACE = BLOCKS.register("arc_furnace", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(
                            ModBlockEntities.ARC_FURNACE.get(), position, state,
                            ClassicMachineDefinition.ARC_FURNACE)));
    public static final RegistryObject<ClassicControllerBlock> CUTTING_MACHINE = BLOCKS.register("cutting_machine", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(
                            ModBlockEntities.CUTTING_MACHINE.get(), position, state,
                            ClassicMachineDefinition.CUTTING)));
    public static final RegistryObject<Block> BLAST_BRICK = BLOCKS.register("blast_brick", () -> new Block(
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final RegistryObject<Block> SAW_BLADE = metalBlock("saw_blade");
    public static final RegistryObject<ClassicControllerBlock> CRYSTALLIZER = BLOCKS.register("crystallizer", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(ModBlockEntities.CRYSTALLIZER.get(),
                            position, state, ClassicMachineDefinition.CRYSTALLIZER)));
    public static final RegistryObject<ClassicControllerBlock> CHEMICAL_REACTOR = BLOCKS.register("chemical_reactor", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(ModBlockEntities.CHEMICAL_REACTOR.get(),
                            position, state, ClassicMachineDefinition.CHEMICAL_REACTOR)));
    public static final RegistryObject<ClassicControllerBlock> PRECISION_LASER_ETCHER = BLOCKS.register("precision_laser_etcher", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(ModBlockEntities.PRECISION_LASER_ETCHER.get(),
                            position, state, ClassicMachineDefinition.PRECISION_LASER_ETCHER)));
    public static final RegistryObject<ClassicControllerBlock> CENTRIFUGE = BLOCKS.register("centrifuge", () ->
            new ClassicControllerBlock(metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK),
                    (position, state) -> new ClassicControllerBlockEntity(ModBlockEntities.CENTRIFUGE.get(),
                            position, state, ClassicMachineDefinition.CENTRIFUGE)));

    public static final RegistryObject<Block> QUARTZ_CRUCIBLE = BLOCKS.register("quartz_crucible", () -> new Block(
            BlockBehaviour.Properties.of().strength(3.0F, 6.0F).sound(SoundType.STONE).requiresCorrectToolForDrops()));

    private static RegistryObject<ClassicHatchBlock> classicHatch(ClassicHatchKind kind) {
        return BLOCKS.register(kind.blockId().getPath(), () -> new ClassicHatchBlock(
                metalProperties().requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK), kind));
    }

    private ModBlocks() {
    }

    public static final RegistryObject<Block> ROCKET_MONITORING_STATION = BLOCKS.register("rocket_monitoring_station", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBlock(
                    BlockBehaviour.Properties.of().strength(3.0F).requiresCorrectToolForDrops()));

    private static RegistryObject<Block> metalBlock(String name) {
        return BLOCKS.register(name, () -> new Block(metalProperties()));
    }

    private static RegistryObject<RollingMachinePortBlock> rollingPort(RollingMachinePortType type) {
        return BLOCKS.register(
                type.registryPath(),
                () -> new RollingMachinePortBlock(type, metalProperties())
        );
    }

    private static RegistryObject<PrecisionAssemblerPortBlock> precisionPort(PrecisionAssemblerPortType type) {
        return BLOCKS.register(
                type.registryPath(),
                () -> new PrecisionAssemblerPortBlock(type, metalProperties())
        );
    }

    /** ADR-044: stateless warp core; charges the balance of the station it stands in. */
    public static final RegistryObject<Block> WARP_CORE = BLOCKS.register(
            "warp_core",
            () -> new WarpCoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    /** ADR-049 section 5: assembles the non-data satellite kinds from components. */
    public static final RegistryObject<Block> SATELLITE_BUILDER = BLOCKS.register(
            "satellite_builder",
            () -> new SatelliteBuilderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    /** ADR-049 section 9: turns linked solar satellites' output into Forge Energy while loaded. */
    public static final RegistryObject<Block> MICROWAVE_RECEIVER = BLOCKS.register(
            "microwave_receiver",
            () -> new MicrowaveReceiverBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    /** ADR-054 section 16: the structure cell of the endgame multiblocks (laser drill, railgun, generator, anchor). */
    public static final RegistryObject<Block> ENDGAME_CASING = BLOCKS.register(
            "endgame_casing",
            () -> new AdvancedMachineCasingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    /** ADR-055: the orbital laser drill controller; pistons cannot move it and explosions barely scratch it. */
    public static final RegistryObject<Block> ORBITAL_LASER_DRILL = BLOCKS.register(
            "orbital_laser_drill",
            () -> new OrbitalLaserDrillBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BLACK)
                            .requiresCorrectToolForDrops()
                            .strength(5.0F, 1200.0F)
                            .pushReaction(PushReaction.BLOCK)
                            .sound(SoundType.METAL))
    );

    /** ADR-055: the laser target endpoint; blast resistance 1,200 and no pushing, like every endpoint (ADR-054 9.1). */
    public static final RegistryObject<Block> LASER_TARGET = BLOCKS.register(
            "laser_target",
            () -> new LaserTargetBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_BLUE)
                            .requiresCorrectToolForDrops()
                            .strength(5.0F, 1200.0F)
                            .pushReaction(PushReaction.BLOCK)
                            .sound(SoundType.METAL))
    );

    /** ADR-058: the area gravity field controller. */
    public static final RegistryObject<Block> GRAVITY_FIELD_CONTROLLER = BLOCKS.register(
            "gravity_field_controller",
            () -> new GravityFieldBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );

    /** ADR-057: the black-hole generator controller. */
    public static final RegistryObject<Block> BLACK_HOLE_GENERATOR = BLOCKS.register(
            "black_hole_generator",
            () -> new BlackHoleGeneratorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.METAL))
    );

    /** ADR-056: the railgun controller, a ledger endpoint. */
    public static final RegistryObject<Block> RAILGUN = BLOCKS.register(
            "railgun",
            () -> new RailgunBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.METAL))
    );

    /** ADR-059: the elevator anchor controller and the terminal, ledger endpoints. */
    public static final RegistryObject<Block> ELEVATOR_ANCHOR = BLOCKS.register(
            "elevator_anchor",
            () -> new ElevatorEndpointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.METAL), true)
    );
    public static final RegistryObject<Block> ELEVATOR_TERMINAL = BLOCKS.register(
            "elevator_terminal",
            () -> new ElevatorEndpointBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.METAL), false)
    );

    /** ADR-064 section 6: one native tank and one dropped Item resource carrier. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlock>
            PRESSURIZED_TANK = BLOCKS.register("pressurized_tank", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankBlock(
                    metalProperties().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK),
                    () -> ModBlockEntities.PRESSURIZED_TANK.get(),
                    io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig::tankCapacityMultiplier));

    /** Ordinary full-cube station lighting; no owner, BlockEntity or active tick. */
    public static final RegistryObject<Block> STATION_LIGHT = BLOCKS.register("station_light", () ->
            new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> 15)));

    /** ADR-065 C17b: the four station controllers; no BlockEntity, owner or stored value. */
    public static final RegistryObject<Block> WARP_CONTROLLER = stationController(
            io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind.WARP);
    public static final RegistryObject<Block> STATION_GRAVITY_CONTROLLER = stationController(
            io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind.GRAVITY);
    public static final RegistryObject<Block> STATION_ALTITUDE_CONTROLLER = stationController(
            io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind.ALTITUDE);
    public static final RegistryObject<Block> STATION_ORIENTATION_CONTROLLER = stationController(
            io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind.ORIENTATION);

    private static RegistryObject<Block> stationController(
            io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind kind) {
        return BLOCKS.register(kind.id(), () ->
                new io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerBlock(kind,
                        BlockBehaviour.Properties.of()
                                .mapColor(MapColor.COLOR_LIGHT_GRAY)
                                .requiresCorrectToolForDrops()
                                .strength(3.0F, 6.0F)
                                .sound(SoundType.METAL)));
    }

    /** ADR-065 C17c: the planetary beacon controller of a 5-by-3-by-3 pattern; pistons cannot move it. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlock>
            PLANETARY_BEACON = BLOCKS.register("planetary_beacon", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.METAL)
                    .pushReaction(PushReaction.BLOCK)));
    /** ADR-065 C17c (W27b): the one-slot rocket satellite bay; deployment arrives with W27d. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlock>
            ROCKET_SATELLITE_BAY = BLOCKS.register("rocket_satellite_bay", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlock(
                    metalProperties().pushReaction(PushReaction.BLOCK)));
    /** ADR-065 C17a (W11b3): the native-Fluid propulsion loader; admission is not wired yet. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlock>
            FLUID_FUEL_LOADER = BLOCKS.register("fluid_fuel_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlock(
                    metalProperties().pushReaction(PushReaction.BLOCK)));

    /** W11c C-A: fixed-role fluid cargo devices; admission remains unwired. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlock>
            ROCKET_FLUID_LOADER = BLOCKS.register("rocket_fluid_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlock(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.LOAD,
                    metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlock>
            ROCKET_FLUID_UNLOADER = BLOCKS.register("rocket_fluid_unloader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlock(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.UNLOAD,
                    metalProperties().pushReaction(PushReaction.BLOCK)));
    /** W11d D-A: fixed-role item cargo devices; admission remains unwired. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock>
            ROCKET_ITEM_LOADER = BLOCKS.register("rocket_item_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.LOAD,
                    metalProperties().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock>
            ROCKET_ITEM_UNLOADER = BLOCKS.register("rocket_item_unloader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole.UNLOAD,
                    metalProperties().pushReaction(PushReaction.BLOCK)));

    /** Passive ordinary lights: native support/placement, distinct registered loot tables. */
    public static final RegistryObject<TorchBlock> THERMITE_TORCH = BLOCKS.register("thermite_torch", () ->
            new TorchBlock(thermiteTorchProperties(), ParticleTypes.FLAME));
    public static final RegistryObject<WallTorchBlock> THERMITE_WALL_TORCH = BLOCKS.register("thermite_wall_torch", () ->
            new WallTorchBlock(thermiteTorchProperties(), ParticleTypes.FLAME));

    private static BlockBehaviour.Properties thermiteTorchProperties() {
        return BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.WOOD)
                .lightLevel(state -> 14).pushReaction(PushReaction.DESTROY);
    }

    /** ADR-066 section 3.2 extinguished combustion torches: no light, no item, one identity root each. */
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlock>
            UNLIT_TORCH = BLOCKS.register("unlit_torch", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitTorchBlock(unlitTorchProperties()));
    public static final RegistryObject<io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitWallTorchBlock>
            UNLIT_WALL_TORCH = BLOCKS.register("unlit_wall_torch", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch.UnlitWallTorchBlock(unlitTorchProperties()));

    /**
     * forceSolidOn: Forge FluidUtil.tryPlaceFluid admits and then destroys any non-solid block without asking its
     * container gate. It sets only BlockState.isSolid(); collision, shape and light stay a torch's (W33G record).
     */
    private static BlockBehaviour.Properties unlitTorchProperties() {
        return BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.WOOD)
                .lightLevel(state -> 0).pushReaction(PushReaction.BLOCK).forceSolidOn();
    }

    public static final RegistryObject<Block> LANDING_PAD = BLOCKS.register("landing_pad", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadBlock(
                    metalProperties().pushReaction(PushReaction.BLOCK)));

    private static BlockBehaviour.Properties metalProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL);
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
