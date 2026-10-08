package io.github.sunthemoon.advancedrocketrycommunity.registry;

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
    public static final RegistryObject<Block> ROCKET_SEAT = metalBlock("rocket_seat");
    public static final RegistryObject<Block> GUIDANCE_COMPUTER = metalBlock("guidance_computer");

    private ModBlocks() {
    }

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

    /** Passive ordinary lights: native support/placement, distinct registered loot tables. */
    public static final RegistryObject<TorchBlock> THERMITE_TORCH = BLOCKS.register("thermite_torch", () ->
            new TorchBlock(thermiteTorchProperties(), ParticleTypes.FLAME));
    public static final RegistryObject<WallTorchBlock> THERMITE_WALL_TORCH = BLOCKS.register("thermite_wall_torch", () ->
            new WallTorchBlock(thermiteTorchProperties(), ParticleTypes.FLAME));

    private static BlockBehaviour.Properties thermiteTorchProperties() {
        return BlockBehaviour.Properties.of().noCollission().instabreak().sound(SoundType.WOOD)
                .lightLevel(state -> 14).pushReaction(PushReaction.DESTROY);
    }

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
