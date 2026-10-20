package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketItemCargoMutation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** D-P fixtures: explicit after snapshots and the unchanged adapter, not item transactions or crash-safe disassembly. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowItemCargoGameTests {
    private record Move(boolean load, Item item, int count, int container, int buffer, int expected) { }

    @GameTest(template = "rocket_test", batch = "item_cargo", timeoutTicks = 40)
    public static void loadAndUnloadAfterPayloadsRestoreThroughTheUnchangedAdapter(GameTestHelper helper) {
        var adapters = RocketBlockEntityAdapters.defaults();
        var moves = List.of(new Move(true, Items.STONE, 5, 0, 0, 15), new Move(true, Items.ENDER_PEARL, 6, 5, 1, 16),
                new Move(true, Items.DIRT, 3, 1, 2, 3), new Move(false, Items.STONE, 10, 0, 0, 0),
                new Move(false, Items.ENDER_PEARL, 4, 5, 3, 6));
        int column = 0;
        for (var block : List.of(Blocks.CHEST, Blocks.BARREL)) {
            var pos = new BlockPos(1 + 2 * column++, 2, 2);
            helper.setBlock(pos, block);
            var container = (BaseContainerBlockEntity) helper.getBlockEntity(pos);
            container.setItem(0, new ItemStack(Items.STONE, 10)); container.setItem(5, new ItemStack(Items.ENDER_PEARL, 10));
            container.setCustomName(Component.literal("Cargo hold"));
            var locked = container.saveWithoutMetadata(); locked.putString("Lock", "key"); container.load(locked);
            var payload = adapters.capture(container).payload();
            helper.assertTrue(payload != null && payload.adapterId().equals(VanillaContainerRocketAdapter.ID)
                    && payload.data().contains("CustomName") && payload.data().getString("Lock").equals("key"), "Container capture changed");
            var state = RocketBlockStateAdapter.capture(container.getBlockState());
            helper.assertTrue(RocketItemCargoMutation.storage(state), "A single chest or a barrel must hold item cargo");
            var before = RocketStructureSnapshot.create(UUID.randomUUID(), helper.getLevel().dimension().location(),
                    new RocketPosition(0, 64, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0), state, payload)),
                    List.of(), new RocketStats(1, 1, 1, 5000, 1, 0, 1, 1), 10, propulsion());
            for (var move : moves) {
                var stack = new ItemStack(move.item(), move.count()).save(new CompoundTag());
                var delta = new EscrowItemCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), 0, 0, 0,
                        VanillaContainerRocketAdapter.ID.toString()), stack,
                        move.load() ? move.buffer() : move.container(), move.load() ? move.container() : move.buffer());
                var after = RocketItemCargoMutation.apply(before, delta, move.load());
                var afterPayload = after.blocks().get(0).blockEntityPayload().orElseThrow();
                helper.assertTrue(adapters.restore(container, afterPayload)
                        && afterPayload.equals(adapters.capture(container).payload()), "After payload did not restore exactly: " + move);
                var held = container.getItem(move.container());
                helper.assertTrue(move.expected() == 0 ? held.isEmpty() : held.is(move.item()) && held.getCount() == move.expected(),
                        "Wrong container count after " + move);
                helper.assertTrue(Component.literal("Cargo hold").equals(container.getCustomName())
                        && adapters.capture(container).payload().data().getString("Lock").equals("key"), "Name or lock was lost");
                helper.assertTrue(before.structureDigest().equals(after.structureDigest())
                        && !before.contentHash().equals(after.contentHash()), "Item cargo changed structure or kept the hash");
            }
            helper.assertTrue(adapters.restore(container, payload), "Before payload no longer restores");
        }
        helper.succeed();
    }

    static RocketPropulsion propulsion() {
        var settings = RocketPropulsion.Settings.DEFAULTS;
        var fluids = new EnumMap<>(settings.allowedFluids()); fluids.put(RocketPropulsion.Role.MONO, List.of("minecraft:water"));
        return new RocketPropulsion(RocketPropulsion.Mode.MONO, 2000, 0, 1, 1, settings.withAllowedFluids(fluids));
    }
}
