package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.OxygenCanisterItem;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.content.DevelopmentComponentItem;
import io.github.sunthemoon.advancedrocketrycommunity.station.content.StationDeploymentKitItem;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.DataSatellitePackageItem;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteControlChipItem;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS,
            AdvancedRocketryCommunity.MOD_ID
    );

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
    public static final RegistryObject<Item> EMPTY_CANISTER = ITEMS.register(
            "empty_canister",
            () -> new Item(new Item.Properties().stacksTo(16))
    );
    public static final RegistryObject<Item> HYDROGEN_CANISTER = ITEMS.register(
            "hydrogen_canister",
            () -> new Item(new Item.Properties().stacksTo(16))
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

    private ModItems() {
    }

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
                () -> new SpaceSuitArmorItem(ArmorMaterials.IRON, type, new Item.Properties())
        );
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
