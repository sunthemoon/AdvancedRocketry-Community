package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
            ForgeRegistries.MENU_TYPES,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<MenuType<ElectrolyzerMenu>> ELECTROLYZER = MENUS.register(
            "electrolyzer",
            () -> IForgeMenuType.create(ElectrolyzerMenu::new)
    );
    public static final RegistryObject<MenuType<RocketFlightMenu>> ROCKET_FLIGHT = MENUS.register(
            "rocket_flight",
            () -> IForgeMenuType.create(RocketFlightMenu::new)
    );

    private ModMenuTypes() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
