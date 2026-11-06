package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.FuelLoaderStorage;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightFeedback;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketUiFeedbackGameTests {
    private RocketUiFeedbackGameTests() { }

    @GameTest(template = "rocket_test", batch = "w289_feedback", timeoutTicks = 100)
    public static void openingConsoleIsQuietAndSeatActionsHaveTheirOwnReplies(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var origin = new BlockPos(3, 2, 3);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBinding.loaded(
                        helper.getLevel(), helper.absolutePos(origin)), "Waiting for rocket entities"))
                .thenExecute(() -> {
            var level = helper.getLevel();
            var owner = UUID.randomUUID();
            var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner);
            var replies = new ArrayList<String>();
            var tokens = new ArrayList<String>();
            var player = ConnectedTestPlayers.join(level.getServer(), owner, "rocketSeatUi", level,
                    helper.absolutePos(origin).east(2), replies, false, tokens);
            replies.clear(); tokens.clear();
            RocketRuntime.openFlightMenu(player, rocket);
            helper.assertTrue(replies.isEmpty(), "Opening the console printed internal data: " + replies);
            RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.BOARD,
                    new TravelTarget.BodySurface(CelestialIds.MOON_ID), UUID.randomUUID());
            helper.assertTrue(player.getVehicle() == rocket && tokens.contains(ConnectedTestPlayers.ACTION_BAR + RocketFlightFeedback.BOARDED),
                    "Board did not give its own reply: " + tokens);
            driver.after(11, () -> {
                try {
                    RocketRuntime.requestFlightIntent(player, rocket.getId(), RocketFlightAction.LEAVE,
                            new TravelTarget.BodySurface(CelestialIds.MOON_ID), UUID.randomUUID());
                    helper.assertTrue(player.getVehicle() == null && tokens.contains(ConnectedTestPlayers.ACTION_BAR + RocketFlightFeedback.LEFT),
                            "Leave did not give its own reply: " + tokens);
                    helper.succeed();
                } finally {
                    player.closeContainer(); level.getServer().getPlayerList().remove(player); rocket.discard();
                }
            });
        });
    }

    @GameTest(template = "rocket_test", batch = "w289_feedback", timeoutTicks = 80)
    public static void fuelCellAndEmptyCanisterUseSpecificRepliesAndOtherItemsStayGeneric(GameTestHelper helper) {
        var loader = FuelLoaderGameTests.loader(helper);
        var level = helper.getLevel(); var at = loader.getBlockPos();
        var owner = UUID.randomUUID(); var tokens = new ArrayList<String>();
        var player = ConnectedTestPlayers.join(level.getServer(), owner, "fuelCellUi", level, at.east(2),
                new ArrayList<>(), false, tokens);
        var hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
        try {
            loader.assignOwner(owner);
            player.getAbilities().instabuild = false;
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROCKET_FUEL_CELL.get()));
            loader.getBlockState().use(level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(player.getMainHandItem().isEmpty() && tokens.contains(ConnectedTestPlayers.ACTION_BAR
                    + "message.advancedrocketrycommunity.fuel_loader.cell_inserted"), "Single fuel cell had generic feedback: " + tokens);
            for (var item : new net.minecraft.world.item.Item[]{ModItems.EMPTY_CANISTER.get(), Items.STICK}) {
                var root = FuelLoaderStorage.encode(new FuelLoaderData(FuelLoaderData.Role.OUTPUT,
                        new ItemStack(item).save(new CompoundTag()), 0, owner, null, null));
                FuelLoaderGameTests.load(loader, root);
                tokens.clear();
                loader.getBlockState().use(level, player, InteractionHand.MAIN_HAND, hit);
                String key = item == ModItems.EMPTY_CANISTER.get() ? "canister_returned" : "item_returned";
                helper.assertTrue(tokens.contains(ConnectedTestPlayers.ACTION_BAR
                        + "message.advancedrocketrycommunity.fuel_loader." + key), "Wrong returned-item feedback: " + tokens);
            }
        } finally {
            level.getServer().getPlayerList().remove(player); FluidFuelLoaderFixtures.clear(level, at);
        }
        helper.succeed();
    }
}
