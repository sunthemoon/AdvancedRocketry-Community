package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemBuffer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowItemCargoPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.VanillaContainerRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Synthetic container snapshots for A0; not captured from a world. */
public final class ItemCargoTestFixtures {
    public static final ResourceLocation CHEST = ResourceLocation.tryParse("minecraft:chest");
    public static final ResourceLocation TRAPPED = ResourceLocation.tryParse("minecraft:trapped_chest");
    public static final ResourceLocation BARREL = ResourceLocation.tryParse("minecraft:barrel");
    private ItemCargoTestFixtures() { }

    public static CompoundTag stack(Item item, int count) { return new ItemStack(item, count).save(new CompoundTag()); }
    public static CompoundTag entry(int slot, CompoundTag stack) {
        var entry = stack.copy(); entry.putByte("Slot", (byte) slot); return entry;
    }
    public static RocketBlockState state(ResourceLocation block) {
        return new RocketBlockState(block, block.equals(BARREL) ? Map.of("facing", "up", "open", "false")
                : Map.of("facing", "north", "type", "single", "waterlogged", "false"));
    }
    public static RocketBlockEntityPayload container(String name, String lock, CompoundTag... entries) {
        var data = new CompoundTag(); var items = new ListTag();
        items.addAll(Arrays.asList(entries)); data.put("Items", items);
        if (name != null) { data.putString("CustomName", name); }
        if (lock != null) { data.putString("Lock", lock); }
        return new RocketBlockEntityPayload(VanillaContainerRocketAdapter.ID, data);
    }
    public static RocketBlock block(int x, RocketBlockState state, RocketBlockEntityPayload payload) {
        return new RocketBlock(new RocketPosition(x, 0, 0), state, payload);
    }
    /** The given containers at x = 0, 1, ..., then a cargo tank and a seat. */
    public static RocketStructureSnapshot snapshot(RocketBlock... containers) {
        var blocks = new ArrayList<>(List.of(containers));
        blocks.add(new RocketBlock(new RocketPosition(9, 0, 0), new RocketBlockState(PressurizedTankRocketAdapter.TYPE, Map.of()),
                PressurizedTankRocketAdapter.payload(new FluidStack(Fluids.WATER, 500))));
        blocks.add(new RocketBlock(new RocketPosition(0, 1, 0),
                new RocketBlockState(ResourceLocation.tryParse("advancedrocketrycommunity:rocket_seat"), Map.of())));
        return CargoTestFixtures.snapshot(blocks);
    }
    public static RocketStructureSnapshot snapshot(ResourceLocation block, CompoundTag... entries) {
        return snapshot(block(0, state(block), container(null, null, entries)));
    }
    public static EscrowItemCargoPayload delta(RocketStructureSnapshot before, int x, CompoundTag stack, int from, int to) {
        return new EscrowItemCargoPayload(new EscrowCargoPayload.Cursor(before.snapshotId(), x, 0, 0,
                VanillaContainerRocketAdapter.ID.toString()), stack, from, to);
    }
    public static SortedMap<Integer, CompoundTag> slots(RocketStructureSnapshot value, int x) {
        var block = value.blocks().stream().filter(b -> b.position().equals(new RocketPosition(x, 0, 0))).findFirst().orElseThrow();
        return RocketItemCargoMutation.container(block.state(), block.blockEntityPayload().orElseThrow());
    }
    public static CompoundTag data(RocketStructureSnapshot value, int x) {
        return value.blocks().stream().filter(b -> b.position().equals(new RocketPosition(x, 0, 0))).findFirst().orElseThrow()
                .blockEntityPayload().orElseThrow().data();
    }
    public static EscrowItemBuffer buffer(CompoundTag... slots) {
        var list = new ArrayList<CompoundTag>(List.of(slots));
        while (list.size() < EscrowItemBuffer.SLOTS) { list.add(new CompoundTag()); }
        return new EscrowItemBuffer(list);
    }
    public static int count(CompoundTag stack) { return stack.getByte("Count"); }
}
