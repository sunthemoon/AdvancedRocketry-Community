package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedJournalAvailabilityGameTests {
    @GameTest(template = "rocket_test", batch = "w152_journal_availability", timeoutTicks = 100)
    public static void installedUnreadyFutureMalformedAndUnreadableJournalsNeverAuthorizeUnseal(GameTestHelper helper) {
        var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(new BlockPos(2, 2, 2));
        var original = RocketTransactionSavedData.get(server); var storage = server.overworld().getDataStorage();
        var chest = new ChestBlockEntity(at, Blocks.CHEST.defaultBlockState()); chest.setItem(0, new ItemStack(Items.DIAMOND, 7));
        var payload = new VanillaContainerRocketAdapter().capture(chest);
        var member = new SealedRocketMember(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "0".repeat(64),
                level.dimension().location(), new RocketPosition(at.getX(), at.getY(), at.getZ()), 0,
                RocketBlockStateAdapter.capture(Blocks.CHEST.defaultBlockState()), payload);
        var state = ModBlocks.SEALED_ROCKET_BLOCK.get().defaultBlockState(); level.setBlock(at, state, 2);
        var carrier = new SealedRocketBlockEntity(at, state); carrier.initialize(SealedRocketRoot.stage(member));
        level.setBlockEntity(carrier); carrier.setChanged(); var saved = carrier.saveWithoutMetadata();
        var outside = at.above(2); level.setBlock(outside, Blocks.CHEST.defaultBlockState(), 3);
        ((ChestBlockEntity) level.getBlockEntity(outside)).setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
        var outsidePort = level.getBlockEntity(outside).getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        var future = new CompoundTag(); future.putInt("schema_version", 999);
        var malformed = new CompoundTag(); malformed.putInt("schema_version", 2); malformed.putInt("transactions", 1);
        // Install the real loader's decoded states synchronously. This is not a disk-corruption or restart claim.
        var states = List.of(new RocketTransactionSavedData(), RocketTransactionSavedData.load(future),
                RocketTransactionSavedData.load(malformed), RocketTransactionSavedData.created(ManagedRootPresence.UNREADABLE));
        try {
            for (var unavailable : states) {
                storage.set(RocketTransactionSavedData.DATA_NAME, unavailable);
                helper.assertTrue(RocketTransactionSavedData.get(server) == unavailable && !unavailable.acknowledgedCurrent(),
                        "Fixture did not install an unproved journal");
                carrier.onLoad();
                helper.assertTrue(!RocketRuntime.unsealForReleaseTest(level, carrier) && level.getBlockEntity(at) == carrier
                        && saved.equals(carrier.saveWithoutMetadata()), "Unready/blocked journal exposed or changed C");
                helper.assertTrue(!MenuProvider.class.isInstance(carrier) && !state.hasAnalogOutputSignal()
                        && !carrier.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "Blocked C exposes a native access surface");
                var item = outsidePort.extractItem(0, 1, false);
                helper.assertTrue(item.is(Items.GOLD_INGOT) && item.getCount() == 1 && outsidePort.insertItem(0, item, false).isEmpty()
                        && outsidePort.getStackInSlot(0).getCount() == 3, "Blocked journal froze unrelated same-chunk inventory");
            }
        } finally {
            // No tick or save occurs while an injected state is installed. Restore the actual owner even on failure.
            storage.set(RocketTransactionSavedData.DATA_NAME, original); original.checkpoint(server);
        }
        helper.assertTrue(original.acknowledgedCurrent() && RocketRuntime.unsealForReleaseTest(level, carrier), "Recovered checked authority did not release C");
        var published = (ChestBlockEntity) level.getBlockEntity(at);
        helper.assertTrue(published.getItem(0).is(Items.DIAMOND) && published.getItem(0).getCount() == 7, "Authority recovery changed the original resource");
        helper.succeed();
    }
    private SealedJournalAvailabilityGameTests() { }
}
