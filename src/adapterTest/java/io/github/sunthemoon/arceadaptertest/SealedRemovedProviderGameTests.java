package io.github.sunthemoon.arceadaptertest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.InvWrapper;

/** R96 provider contract, including references obtained before capability invalidation. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedRemovedProviderGameTests {
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152_hardening", timeoutTicks = 40)
    public static void removedProviderRefusesRawContainerAndPreviouslyAcquiredHandlerAccess(GameTestHelper helper) {
        var target = new FixtureContainerBlockEntity(helper.absolutePos(new BlockPos(1, 2, 1)), AdapterTestMod.CONTAINER.get().defaultBlockState());
        target.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var retained = new InvWrapper(target);
        helper.assertTrue(retained.getStackInSlot(0).getCount() == 7, "Live provider fixture was not populated");
        target.setRemoved();
        helper.assertTrue(target.getItem(0).isEmpty() && target.removeItemNoUpdate(0).isEmpty()
                && target.removeItem(0, 7).isEmpty() && retained.extractItem(0, 7, false).isEmpty(),
                "Removed provider exposed its discarded target through a retained reference");
        var foreign = new ItemStack(Items.GOLD_INGOT, 3);
        helper.assertTrue(retained.insertItem(1, foreign, false).getCount() == 3, "Removed provider consumed foreign input");
        helper.assertTrue(target.isEmpty() && !target.stillValid(null), "Removed provider remained usable");
        helper.succeed();
    }
    private SealedRemovedProviderGameTests() { }
}
