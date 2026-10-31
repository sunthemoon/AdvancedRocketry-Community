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
                        output.accept(ModItems.ITEM_INPUT_HATCH.get());
                        output.accept(ModItems.ITEM_OUTPUT_HATCH.get());
                        output.accept(ModItems.FLUID_INPUT_HATCH.get());
                        output.accept(ModItems.FLUID_OUTPUT_HATCH.get());
                        output.accept(ModItems.POWER_INPUT_PLUG.get());
                        output.accept(ModItems.LATHE.get());
                        output.accept(ModItems.OBSERVATORY.get());
                        output.accept(ModItems.ASTROBODY_DATA_PROCESSOR.get());
                        output.accept(ModItems.CRYSTALLIZER.get());
                        output.accept(ModItems.CHEMICAL_REACTOR.get());
                        output.accept(ModItems.PRECISION_LASER_ETCHER.get());
                        output.accept(ModItems.CENTRIFUGE.get());
                        output.accept(ModItems.ARC_FURNACE.get());
                        output.accept(ModItems.CUTTING_MACHINE.get());
                        output.accept(ModItems.BLAST_BRICK.get());
                        output.accept(ModItems.SAW_BLADE.get());
                        output.accept(ModItems.IRON_SAW_BLADE.get());
                        output.accept(ModItems.QUARTZ_CRUCIBLE.get());
                        output.accept(ModItems.QUARTZ_CRUCIBLE_SHELL.get());
                        output.accept(ModItems.COMBUSTION_GENERATOR.get());
                        output.accept(ModItems.SOLAR_GENERATOR.get());
                        output.accept(ModItems.SOLAR_PANEL.get());
                        output.accept(ModItems.SOLAR_ARRAY.get());
                        output.accept(ModItems.SOLAR_ARRAY_PANEL.get());
                        output.accept(ModItems.POWER_OUTPUT_PLUG.get());
                        output.accept(ModItems.FORCE_FIELD_PROJECTOR.get());
                        output.accept(ModItems.PRESSURIZED_TANK.get());
                        output.accept(ModItems.STATION_LIGHT.get());
                        output.accept(ModItems.WARP_CONTROLLER.get());
                        output.accept(ModItems.STATION_GRAVITY_CONTROLLER.get());
                        output.accept(ModItems.STATION_ALTITUDE_CONTROLLER.get());
                        output.accept(ModItems.STATION_ORIENTATION_CONTROLLER.get());
                        output.accept(ModItems.PLANETARY_BEACON.get());
                        output.accept(ModItems.BEACON_FINDER.get());
                        output.accept(ModItems.THERMITE.get());
                        output.accept(ModItems.CARBON_BRICK.get());
                        output.accept(ModItems.USER_INTERFACE.get());
                        output.accept(ModItems.ITEM_IO_BOARD.get());
                        output.accept(ModItems.LIQUID_IO_BOARD.get());
                        output.accept(ModItems.CONTROL_BOARD.get());
                        output.accept(ModItems.TRACKING_CIRCUIT.get());
                        output.accept(ModItems.BASIC_CIRCUIT_PLATE.get());
                        output.accept(ModItems.ADVANCED_CIRCUIT_PLATE.get());
                        output.accept(ModItems.THERMITE_TORCH.get());
                        output.accept(ModItems.AIRLOCK_DOOR.get());
                        output.accept(io.github.sunthemoon.advancedrocketrycommunity.machine.pump.PumpContent.ITEM.get());
                        for (var motor : io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition.values()) {
                            output.accept(io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorContent.item(motor).get());
                        }
                        output.accept(ModItems.ELECTROLYZER.get());
                        output.accept(ModItems.ROLLING_MACHINE.get());
                        output.accept(ModItems.ROLLING_MACHINE_ITEM_INPUT_PORT.get());
                        output.accept(ModItems.ROLLING_MACHINE_FLUID_INPUT_PORT.get());
                        output.accept(ModItems.ROLLING_MACHINE_ENERGY_INPUT_PORT.get());
                        output.accept(ModItems.ROLLING_MACHINE_ITEM_OUTPUT_PORT.get());
                        output.accept(ModItems.PRECISION_ASSEMBLER.get());
                        output.accept(ModItems.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get());
                        output.accept(ModItems.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get());
                        output.accept(ModItems.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get());
                        output.accept(ModItems.EMPTY_CANISTER.get());
                        output.accept(ModItems.GAS_CHARGE_PAD.get());
                        output.accept(ModItems.HYDROGEN_CANISTER.get());
                        output.accept(ModItems.OXYGEN_CANISTER.get());
                        output.accept(io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids.NITROGEN_CANISTER.get());
                        output.accept(io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids.ROCKET_FUEL_BUCKET.get());
                        output.accept(io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids.ENRICHED_LAVA_BUCKET.get());
                        output.accept(ModItems.OXYGEN_VENT.get());
                        output.accept(ModItems.SPACE_SUIT_HELMET.get());
                        output.accept(ModItems.SPACE_SUIT_CHESTPLATE.get());
                        output.accept(ModItems.SPACE_SUIT_LEGGINGS.get());
                        output.accept(ModItems.SPACE_SUIT_BOOTS.get());
                        output.accept(ModItems.ROCKET_ASSEMBLER.get());
                        output.accept(ModItems.FUEL_LOADER.get());
                        output.accept(ModItems.ROCKET_SATELLITE_BAY.get());
                        output.accept(ModItems.FLUID_FUEL_LOADER.get());
                        output.accept(ModItems.ROCKET_FLUID_LOADER.get());
                        output.accept(ModItems.ROCKET_FLUID_UNLOADER.get());
                        output.accept(ModItems.ROCKET_ITEM_LOADER.get());
                        output.accept(ModItems.ROCKET_ITEM_UNLOADER.get());
                        output.accept(ModItems.ROCKET_MONITORING_STATION.get());
                        output.accept(ModItems.LINKER.get());
                        output.accept(ModItems.ROCKET_FUEL_CELL.get());
                        output.accept(ModItems.STATION_DEPLOYMENT_KIT.get());
                        output.accept(ModItems.ROCKET_MOTOR.get());
                        output.accept(ModItems.ROCKET_FUEL_TANK.get());
                        output.accept(ModItems.ADVANCED_ROCKET_MOTOR.get());
                        output.accept(ModItems.BIPROPELLANT_ROCKET_MOTOR.get());
                        output.accept(ModItems.ADVANCED_BIPROPELLANT_ROCKET_MOTOR.get());
                        output.accept(ModItems.BIPROPELLANT_FUEL_TANK.get());
                        output.accept(ModItems.OXIDIZER_FUEL_TANK.get());
                        output.accept(ModItems.NUCLEAR_ROCKET_MOTOR.get());
                        output.accept(ModItems.NUCLEAR_FUEL_TANK.get());
                        output.accept(ModItems.NUCLEAR_CORE.get());
                        output.accept(ModItems.ROCKET_SEAT.get());
                        output.accept(ModItems.GUIDANCE_COMPUTER.get());
                        output.accept(ModItems.SILICON_WAFER.get());
                        output.accept(ModItems.BASIC_CIRCUIT.get());
                        output.accept(ModItems.ATMOSPHERE_ANALYZER.get());
                        output.accept(ModItems.SEAL_DETECTOR.get());
                        output.accept(ModItems.CO2_SCRUBBER.get());
                        output.accept(ModItems.CARBON_SCRUBBER_CARTRIDGE.get());
                        output.accept(ModItems.PIPE_SEAL.get());
                        output.accept(ModItems.ATMOSPHERE_DETECTOR.get());
                        output.accept(ModItems.JACKHAMMER.get());
                        output.accept(ModItems.BASIC_LASER_GUN.get());
                        output.accept(io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent.WORKSTATION.get());
                        io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent.modules().values()
                                .forEach(item -> output.accept(item.get().getDefaultInstance()));
                        output.accept(ModItems.ADVANCED_CIRCUIT.get());
                        output.accept(ModItems.DATA_STORAGE_UNIT.get());
                        output.accept(ModItems.SATELLITE_TERMINAL.get());
                        output.accept(ModItems.SATELLITE_CHASSIS.get());
                        output.accept(ModItems.SATELLITE_SOLAR_MODULE.get());
                        output.accept(ModItems.SATELLITE_CONTROL_CHIP.get());
                        output.accept(ModItems.DATA_SATELLITE_PACKAGE.get());
                        output.accept(ModItems.WARP_CORE.get());
                        output.accept(ModItems.SATELLITE_BUILDER.get());
                        output.accept(ModItems.MICROWAVE_RECEIVER.get());
                        output.accept(ModItems.SATELLITE_PACKAGE.get());
                        output.accept(ModItems.ADVANCED_SOLAR_PANEL.get());
                        output.accept(ModItems.SATELLITE_BATTERY.get());
                        output.accept(ModItems.LARGE_SATELLITE_BATTERY.get());
                        output.accept(ModItems.SATELLITE_CARGO_HOLD.get());
                        output.accept(ModItems.SURVEY_SCANNER_MODULE.get());
                        output.accept(ModItems.SOLAR_TRANSMITTER_MODULE.get());
                        output.accept(ModItems.ASTEROID_DRILL_MODULE.get());
                        output.accept(ModItems.GAS_INTAKE_MODULE.get());
                        output.accept(ModItems.ENDGAME_CASING.get());
                        output.accept(ModItems.LASER_LENS.get());
                        output.accept(ModItems.ORBITAL_LASER_DRILL.get());
                        output.accept(ModItems.LASER_TARGET.get());
                        output.accept(ModItems.GRAVITY_FIELD_CONTROLLER.get());
                        output.accept(ModItems.BLACK_HOLE_GENERATOR.get());
                        output.accept(ModItems.RAILGUN.get());
                        output.accept(ModItems.ELEVATOR_ANCHOR.get());
                        output.accept(ModItems.ELEVATOR_TERMINAL.get());
                    })
                    .build()
    );

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modBus) {
        CREATIVE_TABS.register(modBus);
    }
}
