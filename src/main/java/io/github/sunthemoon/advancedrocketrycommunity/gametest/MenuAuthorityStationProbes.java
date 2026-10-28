package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.DISTANCE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.FOREIGN_OWNER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.RATE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.SPECTATOR;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * W179: the four ADR-065 station controller menus, each on its own station built as {@link StationControlsGameTests}
 * builds them. A controller is a plain block: the station's owner may use it, a player without VISIT access may not
 * open it, and its mutating {@code classic_controls} intents pass one per player per 10 ticks. The holder has no
 * block entity to unload; the unloaded-chunk refusal is an exemption.
 */
final class MenuAuthorityStationProbes {
    private MenuAuthorityStationProbes() {
    }

    static List<MenuAuthorityProbe> all() {
        return List.of(
                // Cancel, gravity step, altitude step and STOP: each reaches the station services.
                probe(StationControllerKind.WARP, 4),
                probe(StationControllerKind.GRAVITY, 0),
                probe(StationControllerKind.ALTITUDE, 0),
                probe(StationControllerKind.ORIENTATION, 12));
    }

    private static MenuAuthorityProbe probe(StationControllerKind kind, int action) {
        return MenuAuthorityProbe.of(kind.id(), true, 100, helper -> new Controller(helper, kind, action),
                DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE).in(MenuAuthorityMachineProbes.SINGLE);
    }

    private static final class Controller extends MenuAuthorityFixture {
        private final StationWarpGameTests.Fixture station;
        private final List<BlockPos> placed = new ArrayList<>();
        private final BlockPos holder;
        private final int action;

        Controller(GameTestHelper helper, StationControllerKind kind, int action) {
            this.action = action;
            station = new StationWarpGameTests.Fixture(helper, "W179 " + kind.id(), CelestialIds.EARTH_ID, 0);
            station.keepLoaded();
            holder = StationControlsGameTests.place(station, placed, kind, station.pad.offset(3, 0, 3));
        }

        @Override
        public ServerLevel level() {
            return station.space;
        }

        @Override
        public UUID owner() {
            return station.ownerId;
        }

        @Override
        public BlockPos stand() {
            return holder.north(2);
        }

        @Override
        public void open(ServerPlayer player) {
            player.gameMode.useItemOn(player, station.space, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(holder), Direction.UP, holder, false));
        }

        @Override
        public void unload() {
            throw new UnsupportedOperationException("A station controller has no block entity to unload");
        }

        /**
         * One intent as the {@code classic_controls} handler hands it to the open menu. The menu replaces its status
         * for every intent it processes, so an unchanged status object means the intent was dropped.
         */
        @Override
        public MenuAuthorityMatrix.Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            StationControllerMenu controller = (StationControllerMenu) menu;
            Optional<StationManagementCode> before = controller.status();
            StationControlsGameTests.send(controller, player, action, 0, 0L);
            Optional<StationManagementCode> after = controller.status();
            if (after == before || after.isEmpty()) {
                return MenuAuthorityMatrix.Outcome.REFUSED;
            }
            return switch (after.get()) {
                case CONTROL_RATE_LIMITED -> MenuAuthorityMatrix.Outcome.RATE_LIMITED;
                case UNAUTHORIZED, AUTHORITY_UNAVAILABLE -> MenuAuthorityMatrix.Outcome.REFUSED;
                default -> MenuAuthorityMatrix.Outcome.ACCEPTED;
            };
        }

        @Override
        public String describe() {
            return "station controller at " + holder.toShortString();
        }

        @Override
        public void close() {
            StationControlsGameTests.clear(station, placed);
            station.close();
        }
    }
}
