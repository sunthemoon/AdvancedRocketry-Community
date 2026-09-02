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
