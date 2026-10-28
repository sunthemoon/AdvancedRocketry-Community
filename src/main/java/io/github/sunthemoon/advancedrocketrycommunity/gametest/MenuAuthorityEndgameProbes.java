package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.DISTANCE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.FOREIGN_OWNER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.RATE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.SPECTATOR;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.UNLOADED;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Block;

/**
 * W179: the six ADR-054 endgame device menus. Each device is placed outside any station and given an owner, so its
 * owner has full authority and another player only the public view (ADR-054 section 3): the foreign-owner check is
 * that the other player's configuring action is refused. The redstone or reset button is a configuring state intent
 * that the owner may always send, once per {@code intentIntervalTicks}.
 */
final class MenuAuthorityEndgameProbes {
    private MenuAuthorityEndgameProbes() {
    }

    static List<MenuAuthorityProbe> all() {
        return List.of(
                button("orbital_laser_drill", ModBlocks.ORBITAL_LASER_DRILL, OrbitalLaserDrillMenu.BUTTON_REDSTONE),
                button("laser_target", ModBlocks.LASER_TARGET, LaserTargetMenu.BUTTON_RESET),
                button("gravity_field_controller", ModBlocks.GRAVITY_FIELD_CONTROLLER, GravityFieldMenu.BUTTON_REDSTONE),
                button("railgun", ModBlocks.RAILGUN, RailgunMenu.BUTTON_REDSTONE),
                button("elevator", ModBlocks.ELEVATOR_TERMINAL, ElevatorMenu.BUTTON_REDSTONE),
                // The generator's menu has no button; its slots ask itemActionAllowed at every click.
                MenuAuthorityProbe.of("black_hole_generator", true, 40, helper -> new Device(helper,
                                ModBlocks.BLACK_HOLE_GENERATOR, -1),
                        DISTANCE, SPECTATOR, FOREIGN_OWNER, UNLOADED).in(MenuAuthorityMachineProbes.SINGLE));
    }

    private static MenuAuthorityProbe button(String menu, Supplier<? extends Block> block, int button) {
        return MenuAuthorityProbe.of(menu, true, 40, helper -> new Device(helper, block, button),
                DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED).in(MenuAuthorityMachineProbes.SINGLE);
    }

    /** One owned device; {@code button} is its configuring button, or -1 for a slot action. */
    private static final class Device extends MenuAuthorityMachineProbes.Placed {
        private final int button;

        Device(GameTestHelper helper, Supplier<? extends Block> block, int button) {
            super(helper, block);
            this.button = button;
            if (!((EndgameDeviceBlockEntity) level.getBlockEntity(holder)).assignOwner(owner())) {
                throw new IllegalStateException("The endgame fixture owner was not assigned: " + describe());
            }
        }

        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            if (button < 0) {
                return ((EndgameDeviceMenu) menu).itemActionAllowed(EndgameAction.CONFIGURE)
                        ? MenuAuthorityMatrix.Outcome.ACCEPTED : MenuAuthorityMatrix.Outcome.REFUSED;
            }
            MenuAuthorityMatrix.MatrixPlayer matrix = (MenuAuthorityMatrix.MatrixPlayer) player;
            int mark = matrix.messages.size();
            if (menu.clickMenuButton(player, button)) {
                return MenuAuthorityMatrix.Outcome.ACCEPTED;
            }
            boolean limited = matrix.messages.subList(mark, matrix.messages.size()).stream()
                    .anyMatch(message -> message.contains(EndgameCode.RATE_LIMITED.name()));
            return limited ? MenuAuthorityMatrix.Outcome.RATE_LIMITED : MenuAuthorityMatrix.Outcome.REFUSED;
        }
    }
}
