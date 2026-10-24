package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBlockEntity;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Extra linker assertions on the real named-pad transfer fixture, after its existing landing assertions. */
public final class RocketMonitorPadAssertions {
    private RocketMonitorPadAssertions() { }
    public static void verify(GameTestHelper helper, ServerLevel level, BlockPos pad, RocketEntity landed) {
        BlockPos device = pad.south(3).above();
        var owner = ConnectedTestPlayers.join(level.getServer(), landed.ownerId().orElseThrow(), "monitorPad", level, device, new ArrayList<>());
        level.setBlockAndUpdate(device, ModBlocks.ROCKET_MONITORING_STATION.get().defaultBlockState());
        var monitor = (RocketMonitorBlockEntity) level.getBlockEntity(device); monitor.claim(owner);
        try {
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
            var stack = owner.getMainHandItem();
            helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(device), Direction.UP, device, false))) == InteractionResult.CONSUME,
                    "Pad fixture monitor selection failed");
            owner.getCooldowns().removeCooldown(ModItems.LINKER.get());
            helper.assertTrue(stack.useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pad), Direction.UP, pad, false))) == InteractionResult.CONSUME,
                    "Managed pad did not resolve its exact journal-identified rocket");
            helper.assertTrue(RocketMonitorBinding.capture(landed).equals(monitor.root().binding()),
                    "Pad binding changed the logical/physical/station identity");
        } finally {
            FluidFuelLoaderFixtures.clear(level, device); level.getServer().getPlayerList().remove(owner);
        }
    }
}
