package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W292: native rider placement and its existing visual projection agree on the assigned seat. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSeatPositionGameTests {
    private RocketSeatPositionGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_seat_position")
    public static void tallRocketSeatsTheRiderAtTheTopAndKeepsLeaveAccessible(GameTestHelper helper) {
        var level = helper.getLevel();
        var player = FirstEventGameTestPlayers.join(level.getServer(), UUID.randomUUID(), "SeatTop", level,
                helper.absolutePos(new BlockPos(3, 2, 3)));
        var anchor = new RocketPosition(0, 10, 0);
        var rocket = rocket(helper, List.of(anchor), player.getUUID());
        try {
            // Use the normal BOARD request, then the same native placement hook as a riding tick.
            player.setPos(rocket.position());
            player.containerMenu = new RocketFlightMenu(1, player.getInventory(), rocket);
            RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD,
                    RocketDestination.EARTH, UUID.randomUUID());
            helper.assertTrue(player.getVehicle() == rocket, "Normal boarding did not mount the rider");
            rocket.positionRider(player);
            assertSeat(helper, rocket, player, anchor);
            helper.assertTrue(player.distanceToSqr(rocket) > 64 && player.containerMenu.stillValid(player),
                    "Tall seat lost access to its flight console");
            RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.LEAVE,
                    RocketDestination.EARTH, UUID.randomUUID());
            helper.assertTrue(!player.isPassenger()
                    && rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).isEmpty(),
                    "LEAVE from a tall seat was rejected by the base-distance check");
            // A detached player at that same height must not gain remote console access.
            player.setPos(rocket.getX(), rocket.getY() + 10.15D, rocket.getZ());
            helper.assertTrue(!player.containerMenu.stillValid(player), "Detached distant player retained console access");
            RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD,
                    RocketDestination.EARTH, UUID.randomUUID());
            helper.assertTrue(!player.isPassenger(), "Distant unmounted player bypassed the boarding distance");
            helper.succeed();
        } finally {
            player.stopRiding(); level.getServer().getPlayerList().remove(player); rocket.discard();
        }
    }

    @GameTest(template = "rocket_test", batch = "rocket_seat_position")
    public static void differentHeightSeatsKeepTheirSlotsAcrossMovementAndReload(GameTestHelper helper) {
        var first = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SeatLow"));
        var second = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SeatHigh"));
        var low = new RocketPosition(-1, 3, 1);
        var high = new RocketPosition(1, 8, -1);
        var rocket = rocket(helper, List.of(high, low), first.getUUID());
        RocketEntity restored = null;
        try {
            var flight = rocket.flightData().orElseThrow();
            var manifest = flight.passengers().assign(first.getUUID()).orElseThrow()
                    .assign(second.getUUID()).orElseThrow();
            rocket.updateFlightData(flight.withPassengers(manifest));
            helper.assertTrue(first.startRiding(rocket, true) && second.startRiding(rocket, true), "Riders did not mount");
            rocket.positionRider(first); rocket.positionRider(second);
            assertSeat(helper, rocket, first, low); assertSeat(helper, rocket, second, high);
            first.stopRiding();
            rocket.setPos(rocket.position().add(2, 7, -2));
            rocket.positionRider(second);
            assertSeat(helper, rocket, second, high);
            var saved = rocket.saveWithoutId(new CompoundTag());
            second.stopRiding(); rocket.discard();
            restored = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(restored != null, "Reloaded rocket type unavailable");
            restored.load(saved);
            helper.assertTrue(helper.getLevel().addFreshEntity(restored) && second.startRiding(restored, true),
                    "Retained high-seat rider did not remount after reload");
            restored.positionRider(second);
            assertSeat(helper, restored, second, high);
            helper.assertTrue(restored.flightData().orElseThrow().passengers().equals(manifest), "Placement changed the roster");
            helper.succeed();
        } finally {
            first.stopRiding(); second.stopRiding(); rocket.discard(); if (restored != null) { restored.discard(); }
        }
    }

    @GameTest(template = "rocket_test", batch = "rocket_seat_position")
    public static void missingSeatAndPendingVisualKeepTheEstablishedFallback(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SeatPending"));
        var rocket = rocket(helper, List.of(), player.getUUID());
        try {
            // Forced mounting also covers the pending-metadata slot (zero known seats).
            helper.assertTrue(player.startRiding(rocket, true), "Fallback rider did not mount");
            rocket.positionRider(player);
            helper.assertTrue(player.position().distanceTo(rocket.position().add(0, 1.15D, 0)) < 1.0E-9,
                    "Missing server seat changed the established fallback");
            var placement = new RocketPassengerPlacement();
            helper.assertTrue(placement.forVisual(null, 1, 2).equals(RocketPassengerPosition.forSeat(1, 2)),
                    "Pending client visual changed the assigned fallback slot");
            helper.assertTrue(placement.forVisual(RocketVisualSnapshot.fromServerSnapshot(rocket.snapshot().orElseThrow()), 1, 2)
                    .equals(RocketPassengerPosition.forSeat(1, 2)), "Missing visual seat changed the fallback");
            helper.succeed();
        } finally { player.stopRiding(); rocket.discard(); }
    }

    private static RocketEntity rocket(GameTestHelper helper, List<RocketPosition> anchors, UUID owner) {
        var blocks = new ArrayList<RocketBlock>();
        for (int y = 0; y <= 10; y++) {
            var pos = new RocketPosition(0, y, 0);
            blocks.add(new RocketBlock(pos, RocketBlockStateAdapter.capture(anchors.contains(pos)
                    ? ModBlocks.ROCKET_SEAT.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState())));
        }
        for (var anchor : anchors) {
            if (anchor.x() != 0 || anchor.z() != 0) {
                blocks.add(new RocketBlock(anchor, RocketBlockStateAdapter.capture(ModBlocks.ROCKET_SEAT.get().defaultBlockState())));
            }
        }
        var at = helper.absolutePos(new BlockPos(3, 2, 3));
        var origin = new RocketPosition(at.getX(), at.getY(), at.getZ());
        var dimension = helper.getLevel().dimension().location();
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), dimension, origin, blocks, anchors,
                new RocketStats(blocks.size(), 100, 1_000, 1_000, 1, anchors.size(), 1, 0), helper.getLevel().getGameTime());
        var logical = UUID.randomUUID();
        var flight = RocketFlightData.initial(logical, 1_000, anchors.size(), RocketDestination.EARTH.bodyId(), dimension,
                origin, helper.getLevel().getGameTime());
        var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(rocket != null, "Rocket entity type unavailable");
        rocket.initializeTransferred(snapshot, logical, owner, flight);
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Rocket was not published");
        return rocket;
    }

    private static void assertSeat(GameTestHelper helper, RocketEntity rocket, ServerPlayer player, RocketPosition anchor) {
        Vec3 expected = rocket.position().add(anchor.x(), anchor.y() + 0.15D, anchor.z());
        helper.assertTrue(player.position().distanceTo(expected) < 1.0E-9,
                "Rider was not at its assigned seat: expected " + expected + ", got " + player.position());
        int slot = rocket.flightData().orElseThrow().passengers().assignment(player.getUUID()).orElseThrow().seatIndex();
        var placement = new RocketPassengerPlacement();
        var visual = RocketVisualSnapshot.fromServerSnapshot(rocket.snapshot().orElseThrow());
        var offset = placement.forVisual(visual, slot, rocket.flightData().orElseThrow().passengers().seatCapacity());
        helper.assertTrue(rocket.position().add(offset.x(), offset.y(), offset.z()).distanceTo(expected) < 1.0E-9,
                "Existing visual structure did not reproduce the server's seat position");
        helper.assertTrue(placement.forVisual(null, slot, rocket.flightData().orElseThrow().passengers().seatCapacity())
                .equals(RocketPassengerPosition.forSeat(slot, rocket.flightData().orElseThrow().passengers().seatCapacity())),
                "A cleared client snapshot retained a stale seat");
    }
}
