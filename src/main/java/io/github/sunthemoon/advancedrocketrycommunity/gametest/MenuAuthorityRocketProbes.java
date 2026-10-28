package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.DISTANCE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.FOREIGN_OWNER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.RATE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.SPECTATOR;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.UNLOADED;

import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketFlightMenuAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * W179: the rocket menus of the menu-authority matrix. The flight menu's holder is the rocket entity, which anyone
 * near it may inspect and only its owner may command; the bay, fuel loader, cargo devices and monitor are owned
 * blocks whose menus open only for their owner. Fixture cleanup removes the owned devices without their removal
 * callbacks, as their own GameTests do, so no protected-root removal is logged.
 */
final class MenuAuthorityRocketProbes {
    private static final BlockPos DEVICE = new BlockPos(8, 2, 8);

    private MenuAuthorityRocketProbes() {
    }

    static List<MenuAuthorityProbe> all() {
        return List.of(
                MenuAuthorityProbe.of("rocket_flight", true, 100, Flight::new,
                        DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED),
                device("rocket_satellite_bay", (helper, owner) -> SatelliteBayFixtures.place(helper, DEVICE,
                        SatelliteBayFixtures.root(owner, null))),
                device("fluid_fuel_loader", (helper, owner) -> FluidFuelLoaderFixtures.place(helper, DEVICE,
                        FluidFuelLoaderFixtures.owned(owner, LoaderTank.EMPTY))),
                device("rocket_fluid_cargo", (helper, owner) -> RocketFluidCargoFixtures.place(helper, DEVICE,
                        RocketCargoRole.LOAD, RocketFluidCargoFixtures.owned(owner, RocketCargoRole.LOAD, 1_000))),
                device("rocket_item_cargo", (helper, owner) -> RocketItemCargoFixtures.place(helper, DEVICE,
                        RocketCargoRole.LOAD, RocketItemCargoFixtures.owned(owner, RocketCargoRole.LOAD))),
                MenuAuthorityProbe.of("rocket_monitoring_station", true, 40, Monitor::new,
                        DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED).in(MenuAuthorityMachineProbes.SINGLE));
    }

    /** An owned device whose menu has no client action. */
    private static MenuAuthorityProbe device(String menu, BiConsumer<GameTestHelper, UUID> place) {
        return MenuAuthorityProbe.of(menu, false, 40, helper -> new Device(helper, UUID.randomUUID(), place),
                DISTANCE, SPECTATOR, FOREIGN_OWNER, UNLOADED).in(MenuAuthorityMachineProbes.SINGLE);
    }

    private static class Device extends MenuAuthorityFixture.OfBlock {
        Device(GameTestHelper helper, UUID owner, BlockPos holder) {
            super(helper.getLevel(), holder, owner);
        }

        Device(GameTestHelper helper, UUID owner, BiConsumer<GameTestHelper, UUID> place) {
            this(helper, owner, helper.absolutePos(DEVICE));
            place.accept(helper, owner);
        }

        @Override
        public void close() {
            FluidFuelLoaderFixtures.clear(level, holder);
        }
    }

    /** The monitor's intents pass the station controller limiter; PREVIOUS changes nothing without a target. */
    private static final class Monitor extends Device {
        Monitor(GameTestHelper helper) {
            this(helper, UUID.randomUUID());
        }

        private Monitor(GameTestHelper helper, UUID owner) {
            super(helper, owner, RocketMonitorGameTests.monitor(helper, owner).getBlockPos());
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            RocketMonitorMenu monitor = (RocketMonitorMenu) menu;
            monitor.acceptClassicControl(player, new ClassicControlIntentPacket(menu.containerId,
                    ClassicControlKind.ROCKET_MONITOR, 0, 0, 0L));
            boolean limited = monitor.lastView().flatMap(ClassicControlViewPacket::status)
                    .filter(StationManagementCode.CONTROL_RATE_LIMITED::equals).isPresent();
            return limited ? MenuAuthorityMatrix.Outcome.RATE_LIMITED : MenuAuthorityMatrix.Outcome.ACCEPTED;
        }
    }

    /** A real rocket built from scanned blocks; players open its flight menu by using it with an empty hand. */
    private static final class Flight extends MenuAuthorityFixture {
        private final GameTestHelper helper;
        private final UUID owner = UUID.randomUUID();
        private final RocketEntity rocket;

        Flight(GameTestHelper helper) {
            this.helper = helper;
            rocket = FluidFuelLoaderFixtures.rocket(helper, DEVICE, owner);
        }

        @Override
        public ServerLevel level() {
            return helper.getLevel();
        }

        @Override
        public UUID owner() {
            return owner;
        }

        @Override
        public BlockPos stand() {
            return rocket.blockPosition().north(2);
        }

        @Override
        public void open(ServerPlayer player) {
            player.interactOn(rocket, InteractionHand.MAIN_HAND);
        }

        /** As a chunk unload removes an entity. */
        @Override
        public void unload() {
            rocket.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            return RocketFlightMenuAuthority.cancel(player, rocket);
        }

        @Override
        public String describe() {
            return "rocket " + rocket.getUUID() + " at " + rocket.blockPosition().toShortString();
        }

        @Override
        public void close() {
            if (!rocket.isRemoved()) {
                rocket.discard();
            }
        }
    }
}
