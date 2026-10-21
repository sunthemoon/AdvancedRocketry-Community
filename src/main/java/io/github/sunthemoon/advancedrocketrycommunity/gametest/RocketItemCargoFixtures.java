package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

final class RocketItemCargoFixtures {
    static final String BATCH = "rocket_item_cargo";
    static final String TEMPLATE = "rocket_test";
    static CompoundTag saved(Item item, int count) { return new ItemStack(item, count).save(new CompoundTag()); }
    static CompoundTag stone(int count) { return saved(Items.STONE, count); }
    /** A claimed, unbound root whose buffer holds {@code stacks} from slot 0. */
    static CompoundTag owned(UUID owner, RocketCargoRole role, CompoundTag... stacks) {
        var root = RocketItemCargoRoot.placed(owner, UUID.randomUUID(), role);
        for (int slot = 0; slot < stacks.length; slot++) { root = root.withSlot(slot, stacks[slot]); }
        return root.encode();
    }
    static CompoundTag outer(Tag raw) { var outer = new CompoundTag(); outer.put(RocketItemCargoRoot.KEY, raw); return outer; }
    static RocketItemCargoBlockEntity place(GameTestHelper helper, BlockPos relative, RocketCargoRole role, Tag raw) {
        helper.setBlock(relative, role == RocketCargoRole.LOAD ? ModBlocks.ROCKET_ITEM_LOADER.get() : ModBlocks.ROCKET_ITEM_UNLOADER.get());
        var device = (RocketItemCargoBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(relative));
        if (raw != null) { device.load(outer(raw)); }
        return device;
    }
    static ItemStack item(RocketCargoRole role) {
        return new ItemStack(role == RocketCargoRole.LOAD ? ModItems.ROCKET_ITEM_LOADER.get() : ModItems.ROCKET_ITEM_UNLOADER.get());
    }
    static IItemHandler port(RocketItemCargoBlockEntity device) {
        return device.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
    }
    /** {@code count} stone whose tag makes the saved stack exactly {@code bytes} bytes. */
    static ItemStack stoneOfBytes(int count, int bytes) {
        ItemStack stack = new ItemStack(Items.STONE, count);
        stack.getOrCreateTag().putByteArray("p", new byte[0]);
        long base = BoundedNbt.measure(stack.save(new CompoundTag()), Integer.MAX_VALUE, 1_024, 1 << 20).bytes();
        stack.getOrCreateTag().putByteArray("p", new byte[(int) (bytes - base)]);
        return stack;
    }
    /** {@code count} stone whose tag makes the saved stack exactly {@code nodes} nodes. */
    static ItemStack stoneOfNodes(int count, int nodes) {
        ItemStack stack = new ItemStack(Items.STONE, count);
        ListTag list = new ListTag(); stack.getOrCreateTag().put("n", list);
        int base = BoundedNbt.measure(stack.save(new CompoundTag()), Integer.MAX_VALUE, 1_024, 1 << 20).nodes();
        for (int index = 0; index < nodes - base; index++) { list.add(ByteTag.valueOf((byte) index)); }
        return stack;
    }
    static int size(ItemStack stack, boolean bytes) {
        var measured = BoundedNbt.measure(stack.save(new CompoundTag()), Integer.MAX_VALUE, 1_024, 1 << 20);
        return bytes ? (int) measured.bytes() : measured.nodes();
    }
    /** The total count of {@code item} among the dropped stacks. */
    static int dropped(List<ItemEntity> drops, Item item) {
        return drops.stream().filter(drop -> drop.getItem().is(item)).mapToInt(drop -> drop.getItem().getCount()).sum();
    }
    static void clear(ServerLevel level, BlockPos absolute) { FluidFuelLoaderFixtures.clear(level, absolute); }
    private RocketItemCargoFixtures() { }
}
