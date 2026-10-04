package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
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

    public static final RegistryObject<MenuType<CombustionGeneratorMenu>> COMBUSTION_GENERATOR = MENUS.register(
            "combustion_generator", () -> IForgeMenuType.create(CombustionGeneratorMenu::new));
    public static final RegistryObject<MenuType<ElectrolyzerMenu>> ELECTROLYZER = MENUS.register(
            "electrolyzer",
            () -> IForgeMenuType.create(ElectrolyzerMenu::new)
    );
    public static final RegistryObject<MenuType<RollingMachineMenu>> ROLLING_MACHINE = MENUS.register(
            "rolling_machine",
            () -> IForgeMenuType.create(RollingMachineMenu::new)
    );
    public static final RegistryObject<MenuType<PrecisionAssemblerMenu>> PRECISION_ASSEMBLER = MENUS.register(
            "precision_assembler",
            () -> IForgeMenuType.create(PrecisionAssemblerMenu::new)
    );
    public static final RegistryObject<MenuType<RocketFlightMenu>> ROCKET_FLIGHT = MENUS.register(
            "rocket_flight",
            () -> IForgeMenuType.create(RocketFlightMenu::new)
    );
    public static final RegistryObject<MenuType<SatelliteTerminalMenu>> SATELLITE_TERMINAL = MENUS.register(
            "satellite_terminal",
            () -> IForgeMenuType.create(SatelliteTerminalMenu::new)
    );

    public static final RegistryObject<MenuType<SatelliteBuilderMenu>> SATELLITE_BUILDER = MENUS.register(
            "satellite_builder",
            () -> IForgeMenuType.create(SatelliteBuilderMenu::new)
    );

    public static final RegistryObject<MenuType<MicrowaveReceiverMenu>> MICROWAVE_RECEIVER = MENUS.register(
            "microwave_receiver",
            () -> IForgeMenuType.create(MicrowaveReceiverMenu::new)
    );

    public static final RegistryObject<MenuType<OrbitalLaserDrillMenu>> ORBITAL_LASER_DRILL = MENUS.register(
            "orbital_laser_drill",
            () -> IForgeMenuType.create(OrbitalLaserDrillMenu::new)
    );

    public static final RegistryObject<MenuType<LaserTargetMenu>> LASER_TARGET = MENUS.register(
            "laser_target",
            () -> IForgeMenuType.create(LaserTargetMenu::new)
    );

    public static final RegistryObject<MenuType<GravityFieldMenu>> GRAVITY_FIELD_CONTROLLER = MENUS.register(
            "gravity_field_controller",
            () -> IForgeMenuType.create(GravityFieldMenu::new)
    );

    public static final RegistryObject<MenuType<BlackHoleGeneratorMenu>> BLACK_HOLE_GENERATOR = MENUS.register(
            "black_hole_generator",
            () -> IForgeMenuType.create(BlackHoleGeneratorMenu::new)
    );

    public static final RegistryObject<MenuType<RailgunMenu>> RAILGUN = MENUS.register(
            "railgun",
            () -> IForgeMenuType.create(RailgunMenu::new)
    );

    public static final RegistryObject<MenuType<ElevatorMenu>> ELEVATOR = MENUS.register(
            "elevator",
            () -> IForgeMenuType.create(ElevatorMenu::new)
    );

    private ModMenuTypes() {
    }

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
