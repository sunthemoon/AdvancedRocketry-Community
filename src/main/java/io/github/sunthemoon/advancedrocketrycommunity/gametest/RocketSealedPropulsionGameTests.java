package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightGameTestFixtures.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketPropulsionInteraction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Installed B2 fluid consent: container conservation, stale quotes, obstruction and exact native completion. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSealedPropulsionGameTests {
    private static final List<RocketEntity> OWNED = new ArrayList<>();
    private RocketSealedPropulsionGameTests() { }
    @GameTest(template = "rocket_test", batch = "w164_fluid_consent", timeoutTicks = 1200)
    public static void oxideConsentKeepsBothBanksUntilSealedCompletion(GameTestHelper helper) {
        var origin = new BlockPos(3, 2, 3); var level = helper.getLevel(); var pacer = new GameTestTickPacer(level.getServer());
        placeLegalRocket(helper, origin); helper.setBlock(origin, ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get());
        helper.setBlock(origin.west(), ModBlocks.BIPROPELLANT_FUEL_TANK.get());
        helper.setBlock(origin.east(), ModBlocks.OXIDIZER_FUEL_TANK.get());
        var rocket = new RocketEntity[1]; var player = new RocketDisassemblyGameTests.ConsentPlayer[1];
        var retained = new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData[1];
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) { return; } pacer.pace();
            if (rocket[0] == null) {
                var at = helper.absolutePos(origin);
                if (!level.areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4))) { return; }
                var owner = UUID.randomUUID(); var snapshot = successfulSnapshot(helper, origin);
                var world = new ServerLevelRocketTransactionWorld(level, RocketBlockEntityAdapters.defaults(), owner);
                var assembly = new RocketAssemblyTransaction(world, new RocketRegionLockManager(),
                        new RocketOperationLedger(), RocketTransactionJournal.NO_OP).execute(UUID.randomUUID(), snapshot);
                helper.assertTrue(assembly.success(), "Container fixture assembly refused");
                rocket[0] = (RocketEntity) level.getEntity(assembly.rocketEntityId().orElseThrow()); OWNED.add(rocket[0]);
                player[0] = new RocketDisassemblyGameTests.ConsentPlayer(level, owner); player[0].setPos(rocket[0].position());
                var hand = InteractionHand.MAIN_HAND;
                player[0].setItemInHand(hand, new ItemStack(ModItems.HYDROGEN_CANISTER.get()));
                helper.assertTrue(RocketPropulsionInteraction.exchange(player[0], rocket[0], hand) == InteractionResult.CONSUME,
                        "Hydrogen fill refused");
                helper.assertTrue(rocket[0].flightData().orElseThrow().fuel().amount() == 1000
                        && player[0].getItemInHand(hand).is(ModItems.EMPTY_CANISTER.get()), "Primary container debit differs");
                helper.assertTrue(RocketPropulsionInteraction.exchange(player[0], rocket[0], hand) == InteractionResult.CONSUME,
                        "Hydrogen unload refused");
                helper.assertTrue(rocket[0].flightData().orElseThrow().fuel().amount() == 0
                        && player[0].getItemInHand(hand).is(ModItems.HYDROGEN_CANISTER.get()), "Hydrogen round-trip lost content");
                player[0].setItemInHand(hand, new ItemStack(ModItems.OXYGEN_CANISTER.get()));
                helper.assertTrue(RocketPropulsionInteraction.exchange(player[0], rocket[0], hand) == InteractionResult.CONSUME,
                        "Oxidizer fill refused");
                RocketRuntime.requestDisassembly(player[0], rocket[0]); var stale = player[0].token;
                helper.assertTrue(stale != null && rocket[0].isAlive(), "Oxidizer-only bypassed consent");
                helper.assertTrue(RocketPropulsionInteraction.exchange(player[0], rocket[0], hand) == InteractionResult.CONSUME,
                        "Oxidizer unload refused");
                helper.assertTrue(confirm(player[0], stale) == 0, "Unload did not invalidate quote");
                helper.assertTrue(RocketPropulsionInteraction.exchange(player[0], rocket[0], hand) == InteractionResult.CONSUME,
                        "Oxidizer refill refused");
                RocketRuntime.requestDisassembly(player[0], rocket[0]); retained[0] = rocket[0].flightData().orElseThrow();
                helper.setBlock(origin, Blocks.IRON_BLOCK);
                helper.assertTrue(confirm(player[0], player[0].token) == 0 && rocket[0].isAlive()
                        && retained[0].equals(rocket[0].flightData().orElseThrow()), "Failed restoration changed propulsion authority");
                helper.setBlock(origin, Blocks.AIR); RocketRuntime.requestDisassembly(player[0], rocket[0]);
                helper.assertTrue(confirm(player[0], player[0].token) == 1 && rocket[0].isAlive()
                        && player[0].discarded == 0, "Fresh consent did not retain authority pending native proof");
                return;
            }
            if (rocket[0].isAlive()) {
                helper.assertTrue(retained[0].equals(rocket[0].flightData().orElseThrow()) && player[0].discarded == 0,
                        "Pending proof changed either fuel bank"); return;
            }
            if (player[0].discarded == 0) { return; }
            helper.assertTrue(player[0].discarded == 1000, "Completed disposal changed the exact oxidizer amount");
            helper.assertBlockPresent(ModBlocks.BIPROPELLANT_ROCKET_MOTOR.get(), origin);
            assertSnapshotBlocks(helper, new ServerLevelRocketTransactionWorld(level, RocketBlockEntityAdapters.defaults(),
                    rocket[0].ownerId().orElseThrow()), rocket[0].snapshot().orElseThrow());
            var data = RocketTransactionSavedData.get(level.getServer());
            helper.assertTrue(data.acknowledgedCurrent() && data.entries().stream().noneMatch(entry -> entry.record()
                    .rocketEntityIdOptional().filter(rocket[0].getUUID()::equals).isPresent()), "Restoration preceded checked U absence");
            helper.assertTrue(confirm(player[0], player[0].token) == 0, "Consent replay restored twice");
            done[0] = true; helper.succeed();
        });
    }
    private static int confirm(RocketDisassemblyGameTests.ConsentPlayer player, UUID token) {
        return player.serverLevel().getServer().getCommands().performPrefixedCommand(
                player.createCommandSourceStack().withPermission(0), "arce rocket-disassembly confirm " + token);
    }
    @AfterBatch(batch = "w164_fluid_consent") public static void cleanup(ServerLevel level) {
        OWNED.forEach(rocket -> { if (rocket.isAlive()) { rocket.discard(); } }); OWNED.clear();
    }
}
