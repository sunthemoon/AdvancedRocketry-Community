package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.OxygenCanisterItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitMaterial;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.AtmosphereAnalyzerItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.instrument.SealDetectorItem;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.content.DevelopmentComponentItem;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterItem;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.tool.JackhammerItem;
import io.github.sunthemoon.advancedrocketrycommunity.station.content.StationDeploymentKitItem;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.DataSatellitePackageItem;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteControlChipItem;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<Item> JACKHAMMER = ITEMS.register("jackhammer", JackhammerItem::new);
    public static final RegistryObject<Item> BASIC_LASER_GUN = ITEMS.register("basic_laser_gun",
            io.github.sunthemoon.advancedrocketrycommunity.equipment.laser.BasicLaserGunItem::new);
    public static final RegistryObject<Item> CO2_SCRUBBER = blockItem("co2_scrubber", ModBlocks.CO2_SCRUBBER);
    public static final RegistryObject<Item> PIPE_SEAL = blockItem("pipe_seal", ModBlocks.PIPE_SEAL);
    public static final RegistryObject<Item> ATMOSPHERE_DETECTOR = blockItem("atmosphere_detector", ModBlocks.ATMOSPHERE_DETECTOR);
    public static final RegistryObject<Item> CARBON_SCRUBBER_CARTRIDGE = ITEMS.register("carbon_scrubber_cartridge", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> FORCE_FIELD_PROJECTOR = blockItem("force_field_projector", ModBlocks.FORCE_FIELD_PROJECTOR);
    public static final RegistryObject<Item> AIRLOCK_DOOR = blockItem("airlock_door", ModBlocks.AIRLOCK_DOOR);
    public static final RegistryObject<Item> COMBUSTION_GENERATOR = blockItem("combustion_generator",
            ModBlocks.COMBUSTION_GENERATOR);
    public static final RegistryObject<Item> SOLAR_GENERATOR = blockItem("solar_generator", ModBlocks.SOLAR_GENERATOR);
    public static final RegistryObject<Item> SOLAR_PANEL = blockItem("solar_panel", ModBlocks.SOLAR_PANEL);
    public static final RegistryObject<Item> SOLAR_ARRAY = blockItem("solar_array", ModBlocks.SOLAR_ARRAY);
    public static final RegistryObject<Item> SOLAR_ARRAY_PANEL = blockItem("solar_array_panel", ModBlocks.SOLAR_ARRAY_PANEL);
    public static final RegistryObject<Item> POWER_OUTPUT_PLUG = blockItem("power_output_plug", ModBlocks.POWER_OUTPUT_PLUG);
    public static final RegistryObject<Item> MACHINE_CASING = ITEMS.register(
            "machine_casing",
            () -> new BlockItem(ModBlocks.MACHINE_CASING.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ELECTROLYZER = ITEMS.register(
            "electrolyzer",
            () -> new BlockItem(ModBlocks.ELECTROLYZER.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ROLLING_MACHINE = blockItem(
            "rolling_machine",
            ModBlocks.ROLLING_MACHINE
    );
    public static final RegistryObject<Item> ROLLING_MACHINE_ITEM_INPUT_PORT = blockItem(
            "rolling_machine_item_input_port",
            ModBlocks.ROLLING_MACHINE_ITEM_INPUT_PORT
    );
    public static final RegistryObject<Item> ROLLING_MACHINE_FLUID_INPUT_PORT = blockItem(
            "rolling_machine_fluid_input_port",
            ModBlocks.ROLLING_MACHINE_FLUID_INPUT_PORT
    );
    public static final RegistryObject<Item> ROLLING_MACHINE_ENERGY_INPUT_PORT = blockItem(
            "rolling_machine_energy_input_port",
            ModBlocks.ROLLING_MACHINE_ENERGY_INPUT_PORT
    );
    public static final RegistryObject<Item> ROLLING_MACHINE_ITEM_OUTPUT_PORT = blockItem(
            "rolling_machine_item_output_port",
            ModBlocks.ROLLING_MACHINE_ITEM_OUTPUT_PORT
    );
    public static final RegistryObject<Item> PRECISION_ASSEMBLER = blockItem(
            "precision_assembler",
            ModBlocks.PRECISION_ASSEMBLER
    );
    public static final RegistryObject<Item> PRECISION_ASSEMBLER_ITEM_INPUT_PORT = blockItem(
            "precision_assembler_item_input_port",
            ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT
    );
    public static final RegistryObject<Item> PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT = blockItem(
            "precision_assembler_item_output_port",
            ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT
    );
    public static final RegistryObject<Item> PRECISION_ASSEMBLER_ENERGY_INPUT_PORT = blockItem(
            "precision_assembler_energy_input_port",
            ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT
    );
    public static final RegistryObject<Item> GAS_CHARGE_PAD = blockItem("gas_charge_pad", ModBlocks.GAS_CHARGE_PAD);
    public static final RegistryObject<Item> EMPTY_CANISTER = ITEMS.register(
            "empty_canister",
            () -> new GasCanisterItem(new Item.Properties().stacksTo(16))
    );
    public static final RegistryObject<Item> HYDROGEN_CANISTER = ITEMS.register(
            "hydrogen_canister",
            () -> new GasCanisterItem(new Item.Properties().stacksTo(16))
    );
    public static final RegistryObject<Item> OXYGEN_CANISTER = ITEMS.register(
            "oxygen_canister",
            () -> new OxygenCanisterItem(new Item.Properties().stacksTo(16))
    );
    public static final RegistryObject<Item> OXYGEN_VENT = ITEMS.register(
            "oxygen_vent",
            () -> new BlockItem(ModBlocks.OXYGEN_VENT.get(), new Item.Properties())
    );
    public static final RegistryObject<Item> ROCKET_ASSEMBLER = blockItem(
            "rocket_assembler",
            ModBlocks.ROCKET_ASSEMBLER
    );
    public static final RegistryObject<Item> FUEL_LOADER = blockItem(
            "fuel_loader",
            ModBlocks.FUEL_LOADER
    );
    public static final RegistryObject<Item> ROCKET_FUEL_CELL = ITEMS.register(
            "rocket_fuel_cell",
            () -> new Item(new Item.Properties().stacksTo(16))
    );
    public static final RegistryObject<Item> STATION_DEPLOYMENT_KIT = ITEMS.register(
            "station_deployment_kit",
            () -> new StationDeploymentKitItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> ROCKET_MOTOR = blockItem(
            "rocket_motor",
            ModBlocks.ROCKET_MOTOR
    );
    public static final RegistryObject<Item> ROCKET_FUEL_TANK = blockItem(
            "rocket_fuel_tank",
            ModBlocks.ROCKET_FUEL_TANK
    );
    public static final RegistryObject<Item> ROCKET_SEAT = blockItem(
            "rocket_seat",
            ModBlocks.ROCKET_SEAT
    );
    public static final RegistryObject<Item> ADVANCED_ROCKET_MOTOR = blockItem("advanced_rocket_motor", ModBlocks.ADVANCED_ROCKET_MOTOR);
    public static final RegistryObject<Item> BIPROPELLANT_ROCKET_MOTOR = blockItem("bipropellant_rocket_motor", ModBlocks.BIPROPELLANT_ROCKET_MOTOR);
    public static final RegistryObject<Item> ADVANCED_BIPROPELLANT_ROCKET_MOTOR = blockItem("advanced_bipropellant_rocket_motor", ModBlocks.ADVANCED_BIPROPELLANT_ROCKET_MOTOR);
    public static final RegistryObject<Item> BIPROPELLANT_FUEL_TANK = blockItem("bipropellant_fuel_tank", ModBlocks.BIPROPELLANT_FUEL_TANK);
    public static final RegistryObject<Item> OXIDIZER_FUEL_TANK = blockItem("oxidizer_fuel_tank", ModBlocks.OXIDIZER_FUEL_TANK);
    public static final RegistryObject<Item> NUCLEAR_ROCKET_MOTOR = blockItem("nuclear_rocket_motor", ModBlocks.NUCLEAR_ROCKET_MOTOR);
    public static final RegistryObject<Item> NUCLEAR_FUEL_TANK = blockItem("nuclear_fuel_tank", ModBlocks.NUCLEAR_FUEL_TANK);
    public static final RegistryObject<Item> NUCLEAR_CORE = blockItem("nuclear_core", ModBlocks.NUCLEAR_CORE);
    public static final RegistryObject<Item> GUIDANCE_COMPUTER = blockItem(
            "guidance_computer",
            ModBlocks.GUIDANCE_COMPUTER
    );
    public static final RegistryObject<Item> SPACE_SUIT_HELMET = spaceSuit(
            "space_suit_helmet",
            ArmorItem.Type.HELMET
    );
    public static final RegistryObject<Item> SPACE_SUIT_CHESTPLATE = spaceSuit(
            "space_suit_chestplate",
            ArmorItem.Type.CHESTPLATE
    );
    public static final RegistryObject<Item> SPACE_SUIT_LEGGINGS = spaceSuit(
            "space_suit_leggings",
            ArmorItem.Type.LEGGINGS
    );
    public static final RegistryObject<Item> SPACE_SUIT_BOOTS = spaceSuit(
            "space_suit_boots",
            ArmorItem.Type.BOOTS
    );
    public static final RegistryObject<Item> ATMOSPHERE_ANALYZER = ITEMS.register(
            "atmosphere_analyzer",
            () -> new AtmosphereAnalyzerItem(new Item.Properties(), CommonConfig::classicDevicesEnabled)
    );
    public static final RegistryObject<Item> SEAL_DETECTOR = ITEMS.register(
            "seal_detector",
            () -> new SealDetectorItem(new Item.Properties(), CommonConfig::classicDevicesEnabled)
    );
    public static final RegistryObject<Item> SILICON_WAFER = component("silicon_wafer");
    public static final RegistryObject<Item> BASIC_CIRCUIT = component("basic_circuit");
    public static final RegistryObject<Item> ADVANCED_CIRCUIT = component("advanced_circuit");
    public static final RegistryObject<Item> DATA_STORAGE_UNIT = component("data_storage_unit");
    public static final RegistryObject<Item> SATELLITE_TERMINAL = blockItem(
            "satellite_terminal",
            ModBlocks.SATELLITE_TERMINAL
    );
    public static final RegistryObject<Item> WARP_CORE = blockItem("warp_core", ModBlocks.WARP_CORE);
    public static final RegistryObject<Item> SATELLITE_CHASSIS = component("satellite_chassis");
    public static final RegistryObject<Item> SATELLITE_SOLAR_MODULE = component("satellite_solar_module");
    public static final RegistryObject<Item> SATELLITE_CONTROL_CHIP = ITEMS.register(
            "satellite_control_chip",
            () -> new SatelliteControlChipItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> DATA_SATELLITE_PACKAGE = ITEMS.register(
            "data_satellite_package",
            () -> new DataSatellitePackageItem(new Item.Properties().stacksTo(1))
    );

    // v1.6 (ADR-049): the Satellite Builder, the generic package and the satellite components.
    public static final RegistryObject<Item> SATELLITE_BUILDER = blockItem("satellite_builder", ModBlocks.SATELLITE_BUILDER);
    public static final RegistryObject<Item> MICROWAVE_RECEIVER = blockItem("microwave_receiver", ModBlocks.MICROWAVE_RECEIVER);
    public static final RegistryObject<Item> SATELLITE_PACKAGE = ITEMS.register(
            "satellite_package",
            () -> new DataSatellitePackageItem(new Item.Properties().stacksTo(1))
    );
    public static final RegistryObject<Item> ADVANCED_SOLAR_PANEL = component("advanced_solar_panel");
    public static final RegistryObject<Item> SATELLITE_BATTERY = component("satellite_battery");
    public static final RegistryObject<Item> LARGE_SATELLITE_BATTERY = component("large_satellite_battery");
    public static final RegistryObject<Item> SATELLITE_CARGO_HOLD = component("satellite_cargo_hold");
    public static final RegistryObject<Item> SURVEY_SCANNER_MODULE = component("survey_scanner_module");
    public static final RegistryObject<Item> SOLAR_TRANSMITTER_MODULE = component("solar_transmitter_module");
    public static final RegistryObject<Item> ASTEROID_DRILL_MODULE = component("asteroid_drill_module");
    public static final RegistryObject<Item> GAS_INTAKE_MODULE = component("gas_intake_module");

    // v1.7 (ADR-054 section 16): shared endgame content.
    public static final RegistryObject<Item> ENDGAME_CASING = blockItem("endgame_casing", ModBlocks.ENDGAME_CASING);
    /** ADR-055: the laser drill's lens; it sits in the controller's lens slot and is not consumed. */
    public static final RegistryObject<Item> LASER_LENS = ITEMS.register("laser_lens", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ORBITAL_LASER_DRILL = blockItem("orbital_laser_drill",
            ModBlocks.ORBITAL_LASER_DRILL);
    public static final RegistryObject<Item> LASER_TARGET = blockItem("laser_target", ModBlocks.LASER_TARGET);
    public static final RegistryObject<Item> GRAVITY_FIELD_CONTROLLER = blockItem("gravity_field_controller",
            ModBlocks.GRAVITY_FIELD_CONTROLLER);
    public static final RegistryObject<Item> BLACK_HOLE_GENERATOR = blockItem("black_hole_generator",
            ModBlocks.BLACK_HOLE_GENERATOR);
    public static final RegistryObject<Item> RAILGUN = blockItem("railgun", ModBlocks.RAILGUN);
    public static final RegistryObject<Item> ELEVATOR_ANCHOR = blockItem("elevator_anchor", ModBlocks.ELEVATOR_ANCHOR);
    public static final RegistryObject<Item> ELEVATOR_TERMINAL = blockItem("elevator_terminal",
            ModBlocks.ELEVATOR_TERMINAL);

    public static final RegistryObject<Item> PRESSURIZED_TANK = ITEMS.register("pressurized_tank", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.machine.tank.PressurizedTankItem(
                    ModBlocks.PRESSURIZED_TANK.get(), new Item.Properties()));
    public static final RegistryObject<Item> STATION_LIGHT = blockItem("station_light", ModBlocks.STATION_LIGHT);
    public static final RegistryObject<Item> WARP_CONTROLLER = blockItem("warp_controller", ModBlocks.WARP_CONTROLLER);
    public static final RegistryObject<Item> STATION_GRAVITY_CONTROLLER = blockItem("station_gravity_controller",
            ModBlocks.STATION_GRAVITY_CONTROLLER);
    public static final RegistryObject<Item> STATION_ALTITUDE_CONTROLLER = blockItem("station_altitude_controller",
            ModBlocks.STATION_ALTITUDE_CONTROLLER);
    public static final RegistryObject<Item> STATION_ORIENTATION_CONTROLLER = blockItem(
            "station_orientation_controller", ModBlocks.STATION_ORIENTATION_CONTROLLER);
    /** ADR-065 C17c: the beacon and its finder; the finder is the one registration of the shared HEAD module ID. */
    public static final RegistryObject<Item> PLANETARY_BEACON = blockItem("planetary_beacon", ModBlocks.PLANETARY_BEACON);
    /** ADR-065 C17c (W27b): a plain bay item; the bay's root never travels in a {@code BlockEntityTag}. */
    public static final RegistryObject<Item> ROCKET_SATELLITE_BAY = blockItem("rocket_satellite_bay",
            ModBlocks.ROCKET_SATELLITE_BAY);
    /** ADR-065 C17a (W11b3): the nonstackable loader item, which carries a broken device's owner and tank. */
    public static final RegistryObject<Item> FLUID_FUEL_LOADER = ITEMS.register("fluid_fuel_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem(
                    ModBlocks.FLUID_FUEL_LOADER.get(), new Item.Properties().stacksTo(1)));
    /** W11c reuses the bounded owner/tank carrier and BlockEntityTag refusal unchanged. */
    public static final RegistryObject<Item> ROCKET_FLUID_LOADER = ITEMS.register("rocket_fluid_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem(
                    ModBlocks.ROCKET_FLUID_LOADER.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> ROCKET_FLUID_UNLOADER = ITEMS.register("rocket_fluid_unloader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem(
                    ModBlocks.ROCKET_FLUID_UNLOADER.get(), new Item.Properties().stacksTo(1)));
    /** W11d: plain item devices that carry nothing and refuse a BlockEntityTag; the buffer spills on removal. */
    public static final RegistryObject<Item> ROCKET_ITEM_LOADER = ITEMS.register("rocket_item_loader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoItem(
                    ModBlocks.ROCKET_ITEM_LOADER.get(), new Item.Properties()));
    public static final RegistryObject<Item> ROCKET_ITEM_UNLOADER = ITEMS.register("rocket_item_unloader", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoItem(
                    ModBlocks.ROCKET_ITEM_UNLOADER.get(), new Item.Properties()));
    /** W11b3: the linker's rocket-binding half; its other roles arrive with W11e. */
    public static final RegistryObject<Item> LINKER = ITEMS.register("linker", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem(
                    new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> BEACON_FINDER = ITEMS.register("beacon_finder", () ->
            new io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderItem(new Item.Properties()));
    public static final RegistryObject<Item> CARBON_BRICK = ITEMS.register("carbon_brick", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> USER_INTERFACE = ITEMS.register("user_interface", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ITEM_IO_BOARD = ITEMS.register("item_io_board", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> LIQUID_IO_BOARD = ITEMS.register("liquid_io_board", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> CONTROL_BOARD = ITEMS.register("control_board", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> TRACKING_CIRCUIT = ITEMS.register("tracking_circuit", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BASIC_CIRCUIT_PLATE = ITEMS.register("basic_circuit_plate", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> ADVANCED_CIRCUIT_PLATE = ITEMS.register("advanced_circuit_plate", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> THERMITE = ITEMS.register("thermite", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> THERMITE_TORCH = ITEMS.register("thermite_torch", () ->
            new StandingAndWallBlockItem(ModBlocks.THERMITE_TORCH.get(), ModBlocks.THERMITE_WALL_TORCH.get(),
                    new Item.Properties(), Direction.DOWN));

    public static final RegistryObject<Item> ITEM_INPUT_HATCH = blockItem("item_input_hatch", ModBlocks.ITEM_INPUT_HATCH);
    public static final RegistryObject<Item> ITEM_OUTPUT_HATCH = blockItem("item_output_hatch", ModBlocks.ITEM_OUTPUT_HATCH);
    public static final RegistryObject<Item> FLUID_INPUT_HATCH = blockItem("fluid_input_hatch", ModBlocks.FLUID_INPUT_HATCH);
    public static final RegistryObject<Item> FLUID_OUTPUT_HATCH = blockItem("fluid_output_hatch", ModBlocks.FLUID_OUTPUT_HATCH);
    public static final RegistryObject<Item> POWER_INPUT_PLUG = blockItem("power_input_plug", ModBlocks.POWER_INPUT_PLUG);
    public static final RegistryObject<Item> LATHE = blockItem("lathe", ModBlocks.LATHE);
    public static final RegistryObject<Item> OBSERVATORY = blockItem("observatory", ModBlocks.OBSERVATORY);
    public static final RegistryObject<Item> ASTROBODY_DATA_PROCESSOR = blockItem("astrobody_data_processor", ModBlocks.ASTROBODY_DATA_PROCESSOR);
    public static final RegistryObject<Item> CRYSTALLIZER = blockItem("crystallizer", ModBlocks.CRYSTALLIZER);
    public static final RegistryObject<Item> CHEMICAL_REACTOR = blockItem("chemical_reactor", ModBlocks.CHEMICAL_REACTOR);
    public static final RegistryObject<Item> PRECISION_LASER_ETCHER = blockItem("precision_laser_etcher", ModBlocks.PRECISION_LASER_ETCHER);
    public static final RegistryObject<Item> CENTRIFUGE = blockItem("centrifuge", ModBlocks.CENTRIFUGE);
    public static final RegistryObject<Item> ARC_FURNACE = blockItem("arc_furnace", ModBlocks.ARC_FURNACE);
    public static final RegistryObject<Item> CUTTING_MACHINE = blockItem("cutting_machine", ModBlocks.CUTTING_MACHINE);
    public static final RegistryObject<Item> BLAST_BRICK = blockItem("blast_brick", ModBlocks.BLAST_BRICK);
    public static final RegistryObject<Item> SAW_BLADE = blockItem("saw_blade", ModBlocks.SAW_BLADE);
    public static final RegistryObject<Item> IRON_SAW_BLADE = component("iron_saw_blade");
    public static final RegistryObject<Item> QUARTZ_CRUCIBLE = blockItem("quartz_crucible", ModBlocks.QUARTZ_CRUCIBLE);
    public static final RegistryObject<Item> QUARTZ_CRUCIBLE_SHELL = component("quartz_crucible_shell");

    private ModItems() {
    }

    public static final RegistryObject<Item> ROCKET_MONITORING_STATION = blockItem("rocket_monitoring_station", ModBlocks.ROCKET_MONITORING_STATION);

    public static final RegistryObject<Item> LANDING_PAD = blockItem("landing_pad", ModBlocks.LANDING_PAD);

    private static RegistryObject<Item> component(String name) {
        return ITEMS.register(name, () -> new DevelopmentComponentItem(new Item.Properties()));
    }

    private static RegistryObject<Item> blockItem(
            String name,
            RegistryObject<? extends net.minecraft.world.level.block.Block> block
    ) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static RegistryObject<Item> spaceSuit(String name, ArmorItem.Type type) {
        return ITEMS.register(
                name,
                () -> new SpaceSuitArmorItem(SpaceSuitMaterial.INSTANCE, type, new Item.Properties())
        );
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
