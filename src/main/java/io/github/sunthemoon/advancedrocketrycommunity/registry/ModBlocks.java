package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlock;
import io.github.sunthemoon.advancedrocketrycommunity.content.MachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortType;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachinePortType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.WarpCoreBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID
    );

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
