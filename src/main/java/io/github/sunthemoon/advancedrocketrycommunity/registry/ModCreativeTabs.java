package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS = DeferredRegister.create(
            Registries.CREATIVE_MODE_TAB,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<CreativeModeTab> MAIN = CREATIVE_TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.advancedrocketrycommunity.main"))
                    .icon(() -> new ItemStack(ModItems.SILICON_WAFER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.MACHINE_CASING.get());
                        output.accept(ModItems.ELECTROLYZER.get());
                        output.accept(ModItems.EMPTY_CANISTER.get());
                        output.accept(ModItems.HYDROGEN_CANISTER.get());
                        output.accept(ModItems.OXYGEN_CANISTER.get());
                        output.accept(ModItems.OXYGEN_VENT.get());
                        output.accept(ModItems.SPACE_SUIT_HELMET.get());
                        output.accept(ModItems.SPACE_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.SPACE_SUIT_LEGGINGS.get());
                        output.accept(ModItems.SPACE_SUIT_BOOTS.get());
                        output.accept(ModItems.ROCKET_ASSEMBLER.get());
                        output.accept(ModItems.FUEL_LOADER.get());
                        output.accept(ModItems.ROCKET_FUEL_CELL.get());
                        output.accept(ModItems.STATION_DEPLOYMENT_KIT.get());
                        output.accept(ModItems.ROCKET_MOTOR.get());
                        output.accept(ModItems.ROCKET_FUEL_TANK.get());
                        output.accept(ModItems.ROCKET_SEAT.get());
                        output.accept(ModItems.GUIDANCE_COMPUTER.get());
                        output.accept(ModItems.SILICON_WAFER.get());
                        output.accept(ModItems.BASIC_CIRCUIT.get());
                        output.accept(ModItems.ADVANCED_CIRCUIT.get());
                        output.accept(ModItems.DATA_STORAGE_UNIT.get());
                        output.accept(ModItems.SATELLITE_TERMINAL.get());
                        output.accept(ModItems.SATELLITE_CHASSIS.get());
                        output.accept(ModItems.SATELLITE_SOLAR_MODULE.get());
                        output.accept(ModItems.SATELLITE_CONTROL_CHIP.get());
                        output.accept(ModItems.DATA_SATELLITE_PACKAGE.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }
}
