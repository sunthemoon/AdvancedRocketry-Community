package io.github.sunthemoon.advancedrocketrycommunity.client.compat.jei;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentSupplyInput;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityField;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunRoute;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.GasCanisterHandler;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ResearchService;
import io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;

/** Operating help, not executable recipes. This model and its registration callback require no JEI classes. */
public final class OperationInformation {
    public record Entry(List<ResourceLocation> items, String key, List<Object> arguments) {
        public Entry {
            items = List.copyOf(items);
            arguments = List.copyOf(arguments);
        }

        public Component text() {
            return Component.translatable(key, arguments.toArray());
        }
    }

    public static List<Entry> entries() {
        return List.of(
                info("combustion", new Object[]{CombustionBurn.RATE, CombustionBurn.CAPACITY,
                        CombustionBurn.OUTPUT_PER_TICK, CombustionBurn.RATE
                        * ForgeHooks.getBurnTime(new ItemStack(Items.COAL), RecipeType.SMELTING)},
                        "combustion_generator", "minecraft:coal", "minecraft:charcoal"),
                info("finite_canisters", new Object[]{GasCanisterHandler.CAPACITY},
                        "empty_canister", "oxygen_canister", "hydrogen_canister", "nitrogen_canister"),
                info("gas_charge_pad", new Object[]{GasCanisterHandler.CAPACITY},
                        "gas_charge_pad", "empty_canister", "oxygen_canister", "hydrogen_canister"),
                info("oxygen_vent", new Object[]{AtmosphereLimits.OXYGEN_UNITS_PER_CANISTER,
                        AtmosphereLimits.VENT_OXYGEN_CAPACITY, AtmosphereLimits.VENT_ENERGY_PER_TICK,
                        VentSupplyInput.TICKS_PER_OXYGEN}, "oxygen_vent", "oxygen_canister", "empty_canister"),
                info("suit_workstation", new Object[]{}, "suit_workstation", "jetpack", "pressure_tank", "beacon_finder"),
                info("satellite_builder", new Object[]{SatelliteBuilderBlockEntity.SLOT_MODULE_LAST
                        - SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST + 1, SatelliteBuilderBlockEntity.ASSEMBLY_ENERGY,
                        SatelliteBuilderBlockEntity.REDSTONE_ENERGY},
                        "satellite_builder", "satellite_control_chip", "satellite_package"),
                info("ground_research", new Object[]{ResearchService.SURVEY_TICKS, ResearchService.ANALYSIS_TICKS,
                        ResearchService.ENERGY_PER_TICK, ResearchService.CREDIT}, "observatory", "astrobody_data_processor"),
                info("satellite_terminal", new Object[]{}, "satellite_terminal", "satellite_control_chip"),
                info("laser", new Object[]{LaserDrillSettings.DEFAULTS.costFe(),
                        LaserDrillSettings.DEFAULTS.operationIntervalTicks()}, "orbital_laser_drill", "laser_target", "laser_lens"),
                info("black_hole", new Object[]{}, "black_hole_generator"),
                info("gravity", new Object[]{GravityField.MIN_RADIUS, GravityField.MAX_RADIUS,
                        GravityField.DEFAULT_RADIUS, GravityField.upkeep(GravityField.DEFAULT_RADIUS)}, "gravity_field_controller"),
                info("railgun", new Object[]{RailgunRoute.LOCAL_BASE_FE, RailgunRoute.LOCAL_FE_PER_BLOCK,
                        RailgunRoute.MAX_LOCAL_FE, RailgunRoute.ORBITAL_FE, RailgunRoute.ORBITAL_TRAVEL_TICKS}, "railgun"),
                info("elevator", new Object[]{ElevatorRules.CARGO_FE, ElevatorRules.CARGO_TRAVEL_TICKS,
                        ElevatorRules.RIDE_FE, ElevatorRules.RIDE_COUNTDOWN_TICKS}, "elevator_anchor", "elevator_terminal"),
                info("pump", new Object[]{}, "pump"),
                info("solar_generator", new Object[]{}, "solar_generator"),
                new Entry(List.of(ModIdentity.id("solar_array"), ModIdentity.id("solar_array_panel"),
                        ModIdentity.id("power_output_plug")), "jei.advancedrocketrycommunity.solar_array", List.of()),
                info("beacon", new Object[]{}, "planetary_beacon", "beacon_finder"),
                info("scrubber", new Object[]{}, "co2_scrubber", "carbon_scrubber_cartridge"),
                info("detector", new Object[]{}, "atmosphere_detector"),
                info("rocket_assembler", new Object[]{}, "rocket_assembler"),
                info("fuel_loader", new Object[]{}, "fuel_loader", "rocket_fuel_cell"),
                info("fluid_fuel_loader", new Object[]{}, "fluid_fuel_loader"),
                info("rocket_cargo", new Object[]{}, "rocket_fluid_loader", "rocket_fluid_unloader",
                        "rocket_item_loader", "rocket_item_unloader"),
                info("rocket_monitor", new Object[]{}, "rocket_monitoring_station"),
                info("satellite_bay", new Object[]{}, "rocket_satellite_bay", "satellite_package"),
                info("microwave_receiver", new Object[]{}, "microwave_receiver"),
                info("warp", new Object[]{}, "warp_core", "warp_controller"),
                info("station_controls", new Object[]{}, "station_gravity_controller",
                        "station_altitude_controller", "station_orientation_controller"),
                info("force_field", new Object[]{}, "force_field_projector")
        );
    }

    /** Shared callback is also executable in the ordinary unit-test JVM, where JEI is absent. */
    public static void register(Function<ResourceLocation, Item> items,
                                BiConsumer<List<ItemStack>, Component> registration) {
        for (Entry entry : entries()) {
            List<ItemStack> stacks = entry.items().stream().map(id -> new ItemStack(items.apply(id))).toList();
            registration.accept(stacks, entry.text());
        }
    }

    private static Entry info(String operation, Object[] arguments, String... items) {
        return new Entry(Arrays.stream(items).map(item -> item.contains(":")
                ? ResourceLocation.tryParse(item) : ModIdentity.id(item)).toList(),
                "jei.advancedrocketrycommunity.operation." + operation, List.of(arguments));
    }

    private OperationInformation() { }
}
