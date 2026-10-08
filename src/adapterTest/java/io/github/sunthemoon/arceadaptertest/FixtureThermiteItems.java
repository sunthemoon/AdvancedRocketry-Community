package io.github.sunthemoon.arceadaptertest;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Foreign dust membership is additive test data, never production content. */
final class FixtureThermiteItems {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AdapterTestMod.MOD_ID);
    static final RegistryObject<Item> ALUMINUM = item("thermite_aluminum_dust");
    static final RegistryObject<Item> IRON = item("thermite_iron_dust");
    static final RegistryObject<Item> THERMITE = item("thermite_dust");

    private FixtureThermiteItems() { }

    private static RegistryObject<Item> item(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    static void register(IEventBus bus) { ITEMS.register(bus); }
}
