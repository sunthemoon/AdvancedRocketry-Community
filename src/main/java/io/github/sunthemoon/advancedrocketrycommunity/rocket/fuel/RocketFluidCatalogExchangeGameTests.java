package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.fluid.ClassicFluids;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFluidCatalogExchangeGameTests {
    private RocketFluidCatalogExchangeGameTests() { }

    @GameTest(template = "rocket_test", batch = "propulsion_catalog_exchange", timeoutTicks = 200)
    public static void replacementCatalogAllowsOldFluidDrainButRefusesRefill(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
        helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
        helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
        helper.setBlock(origin.east(), ModBlocks.ROCKET_FUEL_TANK.get());
        helper.setBlock(origin.west(), ModBlocks.ROCKET_FUEL_TANK.get());
        var at = helper.absolutePos(origin);
        var pacer = new GameTestTickPacer(helper.getLevel().getServer());
        helper.startSequence().thenWaitUntil(() -> {
            boolean ready = helper.getLevel().areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4));
            if (!ready) { pacer.pace(); }
            helper.assertTrue(ready, "Catalog exchange fixture entity chunk not ready");
        }).thenExecute(() -> {
            var task = new RocketStructureScanTask(new ServerLevelRocketScanWorld(helper.getLevel(),
                    RocketBlockEntityAdapters.defaults()), helper.getLevel().dimension().location(),
                    new RocketPosition(at.getX(), at.getY(), at.getZ()), UUID.randomUUID(), helper.getLevel().getGameTime());
            var snapshot = task.step(256).snapshot().orElseThrow();
            var owner = UUID.randomUUID();
            var rocket = ModEntities.ROCKET.get().create(helper.getLevel());
            rocket.initialize(snapshot, UUID.randomUUID(), owner);
            helper.assertTrue(helper.getLevel().addFreshEntity(rocket), "Catalog exchange rocket admission failed");
            var flight = rocket.flightData().orElseThrow();
            rocket.updateFlightData(flight.withFuel(flight.fuel().withFluidBank(false, ModIdentity.id("rocket_fuel"),
                    null, 2_000), helper.getLevel().getGameTime()));
            var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), new GameProfile(owner, "ARCECatalog")) {
                @Override public void displayClientMessage(Component message, boolean actionBar) { }
            };
            player.setPos(rocket.position());
            var previous = RocketFluidFuelRuntime.capture();
            try {
                var replacement = RocketFluidFuelCatalog.parse(List.of(
                        definition("MONO", "minecraft:water"), definition("BIPROPELLANT_FUEL", "advancedrocketrycommunity:hydrogen"),
                        definition("OXIDIZER", "advancedrocketrycommunity:oxygen"), definition("WORKING_FLUID", "advancedrocketrycommunity:hydrogen")),
                        net.minecraftforge.registries.ForgeRegistries.FLUIDS::containsKey,
                        Map.of(RocketPropulsion.Role.MONO, List.of("minecraft:water"),
                                RocketPropulsion.Role.BIPROPELLANT_FUEL, List.of("advancedrocketrycommunity:hydrogen"),
                                RocketPropulsion.Role.OXIDIZER, List.of("advancedrocketrycommunity:oxygen"),
                                RocketPropulsion.Role.WORKING_FLUID, List.of("advancedrocketrycommunity:hydrogen")));
                RocketFluidFuelRuntime.publish(replacement);
                helper.assertTrue(RocketFluidFuelRuntime.capture().find(RocketPropulsion.Role.MONO,
                        ModIdentity.id("rocket_fuel")) == null, "Replacement still authorizes old Fluid");
                for (int bucket = 1; bucket <= 2; bucket++) {
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                    helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, InteractionHand.MAIN_HAND)
                            == InteractionResult.CONSUME, "Old Fluid drain refused after catalog replacement");
                    helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(ClassicFluids.ROCKET_FUEL_BUCKET.get()),
                            "Drain changed the stored Fluid identity");
                    player.getInventory().add(player.getItemInHand(InteractionHand.MAIN_HAND));
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 2_000 - bucket * 1_000
                            && rocket.flightData().orElseThrow().fuel().propulsion().orElseThrow().oxidizer().amount() == 0,
                            "Drain did not preserve both banks");
                }
                long buckets = player.getInventory().items.stream().filter(item -> item.is(ClassicFluids.ROCKET_FUEL_BUCKET.get()))
                        .mapToLong(ItemStack::getCount).sum();
                helper.assertTrue(buckets == 2, "Container conservation differs");
                player.getInventory().selected = 1;
                var before = rocket.flightData().orElseThrow();
                helper.assertTrue(RocketPropulsionInteraction.exchange(player, rocket, InteractionHand.MAIN_HAND)
                        == InteractionResult.FAIL && before.equals(rocket.flightData().orElseThrow())
                        && player.getItemInHand(InteractionHand.MAIN_HAND).is(ClassicFluids.ROCKET_FUEL_BUCKET.get()),
                        "Removed definition allowed incoming Fluid or changed resources on refusal");
            } finally {
                RocketFluidFuelRuntime.publish(previous);
                rocket.discard();
            }
        }).thenSucceed();
    }

    private static String definition(String role, String fluid) {
        return "{\"schema\":1,\"role\":\"" + role + "\",\"fluid\":\"" + fluid + "\",\"numerator\":1,\"denominator\":1}";
    }
}
