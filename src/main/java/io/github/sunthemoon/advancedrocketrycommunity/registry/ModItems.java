package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.content.DevelopmentComponentItem;
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
    public static final RegistryObject<Item> SILICON_WAFER = component("silicon_wafer");
    public static final RegistryObject<Item> BASIC_CIRCUIT = component("basic_circuit");
    public static final RegistryObject<Item> ADVANCED_CIRCUIT = component("advanced_circuit");
    public static final RegistryObject<Item> DATA_STORAGE_UNIT = component("data_storage_unit");

    private ModItems() {
    }

    private static RegistryObject<Item> component(String name) {
        return ITEMS.register(name, () -> new DevelopmentComponentItem(new Item.Properties()));
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
