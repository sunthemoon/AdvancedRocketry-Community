package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketItemCargoFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The two native item device roles, placement ownership, the spill-once drop, recipes and section 5.3's bounds. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketItemCargoGameTests {
    @SuppressWarnings("unchecked")
    private static final Class<AttachCapabilitiesEvent<ItemStack>> ATTACH =
            (Class<AttachCapabilitiesEvent<ItemStack>>) (Class<?>) AttachCapabilitiesEvent.class;

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void allSidesSimulationReentryThreadAndLifecycleAreBounded(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        var spare = new BlockPos(8, 2, 4);
        var level = helper.getLevel();
        try {
            for (var role : RocketCargoRole.values()) {
                boolean load = role == RocketCargoRole.LOAD;
                var device = place(helper, pos, role, owned(UUID.randomUUID(), role, stone(20)));
                var handler = port(device);
                for (Direction side : Direction.values()) {
                    helper.assertTrue(device.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElseThrow() == handler,
                            role + " does not expose one shared all-side port");
                }
                var raw = device.heldRoot().orElseThrow();
                var ten = new ItemStack(Items.STONE, 10);
                var simulatedIn = handler.insertItem(1, ten, true);
                var simulatedOut = handler.extractItem(0, 5, true);
                helper.assertTrue((load ? simulatedIn.isEmpty() : simulatedIn == ten) && ten.getCount() == 10
                        && (load ? simulatedOut.isEmpty() : simulatedOut.is(Items.STONE) && simulatedOut.getCount() == 5)
                        && handler.isItemValid(1, ten) == load && raw.equals(device.heldRoot().orElseThrow()),
                        role + " simulation or direction mutated state");
                helper.assertTrue(load ? handler.extractItem(0, 5, false).isEmpty() : handler.insertItem(1, ten, false) == ten,
                        role + " moved items against its direction");
                helper.assertTrue(raw.equals(device.heldRoot().orElseThrow()), role + " wrong direction changed the root");
                if (load) {
                    var renamed = new ItemStack(Items.STONE, 1).setHoverName(net.minecraft.network.chat.Component.literal("Stone"));
                    var dirt = new ItemStack(Items.DIRT, 1);
                    helper.assertTrue(handler.insertItem(0, renamed, false) == renamed && handler.insertItem(0, dirt, false) == dirt
                            && raw.equals(device.heldRoot().orElseThrow()), "A different tag or item merged");
                }
                var single = new ItemStack(Items.STONE, 1);
                helper.assertTrue(CompletableFuture.supplyAsync(() -> handler.insertItem(1, single, false)).join() == single
                        && CompletableFuture.supplyAsync(() -> handler.extractItem(0, 1, false)).join().isEmpty()
                        && CompletableFuture.supplyAsync(() -> handler.getStackInSlot(0)).join().isEmpty()
                        && raw.equals(device.heldRoot().orElseThrow()), "Off-thread access moved or showed items");
                // A stack created inside the lock (by the item's own capability gathering) re-enters the port.
                int[] inner = {-1};
                var probe = new ItemStack(Items.STONE, 1);
                Consumer<AttachCapabilitiesEvent<ItemStack>> reenter = event -> {
                    if (inner[0] < 0) {
                        inner[0] = load ? handler.insertItem(2, probe, false).getCount() : handler.extractItem(0, 1, false).getCount();
                    }
                };
                MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, EventPriority.NORMAL, false, ATTACH, reenter);
                ItemStack outer;
                try { outer = load ? handler.insertItem(1, ten, false) : handler.extractItem(0, 5, false); }
                finally { MinecraftForge.EVENT_BUS.unregister(reenter); }
                helper.assertTrue(load ? outer.isEmpty() && inner[0] == 1 && device.stack(1).getCount() == 10 && device.stack(2).isEmpty()
                        : outer.getCount() == 5 && inner[0] == 0 && device.stack(0).getCount() == 15, role + " reentrant call moved items");
                device.invalidateCaps(); device.reviveCaps();
                helper.assertTrue(handler.insertItem(1, single, false) == single && handler.extractItem(0, 1, false).isEmpty()
                        && handler.getStackInSlot(0).isEmpty() && port(device) != handler, "Stale handle survived invalidation");
                var fresh = port(device);
                var saved = device.saveWithFullMetadata();
                device.onChunkUnloaded(); level.removeBlockEntity(helper.absolutePos(pos));
                helper.assertTrue(fresh.insertItem(1, single, false) == single && fresh.extractItem(0, 1, false).isEmpty(), "Unloaded handle works");
                var restored = BlockEntity.loadStatic(helper.absolutePos(pos), device.getBlockState(), saved.copy());
                helper.assertTrue(restored instanceof RocketItemCargoBlockEntity, "Wrong reloaded BE type");
                level.setBlockEntity(restored);
                helper.assertTrue(saved.get(RocketItemCargoRoot.KEY).equals(restored.saveWithoutMetadata().get(RocketItemCargoRoot.KEY)),
                        "Unload/reload changed the root");
                clear(level, helper.absolutePos(pos));
                for (CompoundTag unclaimed : new CompoundTag[] {null, owned(null, role, stone(5))}) {
                    var bare = place(helper, spare, role, unclaimed);
                    helper.assertTrue(port(bare).insertItem(0, single, false) == single && port(bare).extractItem(0, 1, false).isEmpty()
                            && port(bare).getStackInSlot(0).isEmpty(), "Unclaimed device moved items");
                    clear(level, helper.absolutePos(spare));
                }
            }
        } finally { clear(level, helper.absolutePos(pos)); clear(level, helper.absolutePos(spare)); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void placersOwnPlainDevicesAndTheBufferSpillsExactlyOnce(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer();
            List<ServerPlayer> players = new ArrayList<>();
            var at = helper.absolutePos(pos);
            try {
                var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Owner", level, at.east(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Other", level, at.west(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var op = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Op", level, at.north(2),
                        new ArrayList<>(), true, new ArrayList<>(), false);
                players.addAll(List.of(owner, other, op));
                server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 4, false));
                for (var role : RocketCargoRole.values()) {
                    UUID previous = null;
                    for (ServerPlayer placer : players) {
                        helper.assertTrue(FluidFuelLoaderFixtures.placeBy(helper, placer, at, item(role)).consumesAction(), "Placement refused");
                        var device = (RocketItemCargoBlockEntity) level.getBlockEntity(at);
                        var root = device.root().orElseThrow();
                        helper.assertTrue(root.role() == role && placer.getUUID().equals(root.owner()) && root.binding() == null
                                && !root.marked() && root.buffer().equals(RocketItemCargoRoot.EMPTY) && !root.device().equals(previous),
                                "The placer does not own a fresh, empty device");
                        previous = root.device();
                        device.load(outer(root.withSlot(0, stone(20)).withSlot(2, saved(Items.ENDER_PEARL, 16)).encode()));
                        if (placer == other) {
                            // An explosion takes the loot table's item first, then the removal spills the buffer.
                            var explosion = new Explosion(level, null, at.getX() + .5, at.getY() + .5, at.getZ() + .5, 1,
                                    false, Explosion.BlockInteraction.DESTROY);
                            explosion.getToBlow().add(at);
                            explosion.finalizeExplosion(false);
                        } else {
                            helper.assertTrue(placer.gameMode.destroyBlock(at), "Ordinary break refused");
                        }
                        var drops = FluidFuelLoaderFixtures.drops(level, at);
                        helper.assertTrue(level.getBlockEntity(at) == null && dropped(drops, item(role).getItem()) == 1
                                && dropped(drops, Items.STONE) == 20 && dropped(drops, Items.ENDER_PEARL) == 16
                                && drops.stream().allMatch(drop -> drop.getItem().is(item(role).getItem())
                                        || drop.getItem().is(Items.STONE) || drop.getItem().is(Items.ENDER_PEARL)),
                                "Not exactly one plain item and the buffer once: " + drops.size());
                        helper.assertTrue(dropped(drops, item(role).getItem()) == 1 && drops.stream()
                                .filter(drop -> drop.getItem().is(item(role).getItem())).allMatch(drop -> !drop.getItem().hasTag()),
                                "The device item carries data");
                        helper.assertTrue(device.stack(0).isEmpty() && device.stack(2).isEmpty()
                                && !device.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "A spilled device still holds items");
                        drops.forEach(ItemEntity::discard);
                    }
                    // Placement by anything but a connected player leaves the device unclaimed.
                    var block = (RocketItemCargoBlock) ((BlockItem) item(role).getItem()).getBlock();
                    level.setBlock(at, block.defaultBlockState(), 3);
                    block.setPlacedBy(level, at, block.defaultBlockState(), null, item(role));
                    var bare = (RocketItemCargoBlockEntity) level.getBlockEntity(at);
                    helper.assertTrue(bare.root().isPresent() && !bare.claimed(), "A non-player placement was claimed");
                    clear(level, at);
                }
            } finally {
                clear(level, at); FluidFuelLoaderFixtures.discardDrops(level, at);
                players.forEach(p -> { server.getPlayerList().getOps().remove(p.getGameProfile()); server.getPlayerList().remove(p); });
            }
        }, pos);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void noPlayerCanInjectBlockEntityNbt(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer();
            var at = helper.absolutePos(pos);
            var player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Nbt", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            var op = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120NbtOp", level, at.west(2),
                    new ArrayList<>(), true, new ArrayList<>(), false);
            try {
                server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 4, false));
                player.setGameMode(GameType.CREATIVE); op.setGameMode(GameType.CREATIVE);
                helper.assertTrue(!player.hasPermissions(2) && op.hasPermissions(2), "NBT fixture permissions wrong");
                for (var role : RocketCargoRole.values()) {
                    var device = place(helper, pos, role, owned(player.getUUID(), role, stone(5)));
                    var before = device.heldRoot().orElseThrow();
                    var forged = item(role);
                    forged.getOrCreateTag().put("BlockEntityTag", outer(owned(UUID.randomUUID(), role, stone(64), stone(64))));
                    helper.assertTrue(device.onlyOpCanSetNbt()
                            && !BlockItem.updateCustomBlockEntityTag(level, player, at, forged)
                            && before.equals(device.heldRoot().orElseThrow()) && !RocketItemCargoItem.placeable(forged), "NBT injection accepted");
                    clear(level, at);
                    for (ServerPlayer placer : List.of(player, op)) {
                        helper.assertTrue(!FluidFuelLoaderFixtures.placeBy(helper, placer, at, forged.copy()).consumesAction()
                                && level.getBlockEntity(at) == null, "Forged block item placed");
                    }
                }
            } finally {
                clear(level, at); server.getPlayerList().getOps().remove(op.getGameProfile());
                server.getPlayerList().remove(player); server.getPlayerList().remove(op);
            }
        }, pos);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void shapedRecipesUseTheItemBoards(GameTestHelper helper) {
        var level = helper.getLevel();
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        for (var role : RocketCargoRole.values()) {
            String id = RocketItemCargoBlock.id(role);
            for (int variant = 0; variant < 2; variant++) {
                grid.clearContent(); int row = role == RocketCargoRole.LOAD ? 3 : 0;
                grid.setItem(row, new ItemStack(variant == 0 ? ModItems.ITEM_IO_BOARD.get() : ModItems.LIQUID_IO_BOARD.get()));
                grid.setItem(row + 1, new ItemStack(ModItems.MACHINE_CASING.get()));
                grid.setItem(row + 2, new ItemStack(ModItems.CONTROL_BOARD.get()));
                grid.setItem(role == RocketCargoRole.LOAD ? 1 : 4, new ItemStack(Items.HOPPER));
                var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
                helper.assertTrue(variant == 0 ? recipe.filter(r -> r.getId().equals(ModIdentity.id(id))
                        && r.assemble(grid, level.registryAccess()).is(item(role).getItem())).isPresent() : recipe.isEmpty(),
                        role + " variant " + variant + " crafted incorrectly");
            }
            helper.assertTrue(level.getServer().getAdvancements().getAdvancement(ModIdentity.id("recipes/misc/" + id)) != null,
                    "Missing unlock advancement");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void stacksOutsideTheBoundsStayWhereTheyAre(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Bounds", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                var big = stoneOfBytes(5, 5_000); var edge = stoneOfBytes(3, 4_096); var over = stoneOfBytes(3, 4_097);
                var nodes = stoneOfNodes(1, 512);
                helper.assertTrue(size(edge, true) == 4_096 && size(over, true) == 4_097 && size(nodes, false) == 512, "Fixture sizes wrong");
                for (var role : RocketCargoRole.values()) {
                    boolean load = role == RocketCargoRole.LOAD;
                    var device = place(helper, pos, role, owned(owner.getUUID(), role, stone(10)));
                    var handler = port(device);
                    var before = device.heldRoot().orElseThrow();
                    if (load) {
                        helper.assertTrue(handler.insertItem(1, big, false) == big && big.getCount() == 5
                                && big.getTag().getByteArray("p").length == stoneOfBytes(5, 5_000).getTag().getByteArray("p").length
                                && !handler.isItemValid(1, big) && handler.insertItem(1, over, false) == over
                                && before.equals(device.heldRoot().orElseThrow()), "An oversized stack entered the buffer");
                        helper.assertTrue(handler.insertItem(1, edge, false).isEmpty() && device.stack(1).getCount() == 3
                                && ItemStack.isSameItemSameTags(device.stack(1), edge), "A stack at the 4,096-byte bound was refused");
                        var pearls = new ItemStack(Items.ENDER_PEARL, 17);
                        var rest = handler.insertItem(2, pearls, false);
                        helper.assertTrue(rest.getCount() == 1 && device.stack(2).getCount() == 16 && pearls.getCount() == 17,
                                "Pearls exceeded their own stack size");
                    }
                    // The owner's menu in either role: an oversized carried stack stays carried.
                    var menu = (RocketItemCargoMenu) device.createMenu(7, owner.getInventory(), owner);
                    var now = device.heldRoot().orElseThrow();
                    menu.setCarried(big.copy());
                    menu.clicked(3, 0, ClickType.PICKUP, owner);
                    helper.assertTrue(menu.getCarried().getCount() == 5 && ItemStack.isSameItemSameTags(menu.getCarried(), big)
                            && now.equals(device.heldRoot().orElseThrow()), role + " menu accepted an oversized stack");
                    menu.setCarried(ItemStack.EMPTY);
                    clear(level, at);
                    // The root's own node bound: three 512-node stacks fit; a fourth, valid alone, does not.
                    device = place(helper, pos, role, owned(owner.getUUID(), role));
                    menu = (RocketItemCargoMenu) device.createMenu(8, owner.getInventory(), owner);
                    for (int slot = 0; slot < 3; slot++) {
                        menu.setCarried(nodes.copy());
                        menu.clicked(slot, 0, ClickType.PICKUP, owner);
                        helper.assertTrue(menu.getCarried().isEmpty() && device.stack(slot).getCount() == 1, "A 512-node stack was refused");
                    }
                    var full = device.heldRoot().orElseThrow();
                    menu.setCarried(nodes.copy());
                    menu.clicked(3, 0, ClickType.PICKUP, owner);
                    helper.assertTrue(menu.getCarried().getCount() == 1 && device.stack(3).isEmpty()
                            && full.equals(device.heldRoot().orElseThrow()), role + " root exceeded its node bound");
                    if (load) {
                        helper.assertTrue(port(device).isItemValid(3, nodes) && port(device).insertItem(3, nodes, false) == nodes
                                && full.equals(device.heldRoot().orElseThrow()), "The port exceeded the root's node bound");
                    }
                    menu.setCarried(new ItemStack(Items.STONE, 7));
                    menu.clicked(3, 0, ClickType.PICKUP, owner);
                    helper.assertTrue(menu.getCarried().isEmpty() && device.stack(3).getCount() == 7, "A small stack no longer fits");
                    clear(level, at);
                }
            } finally { clear(level, at); server.getPlayerList().remove(owner); }
        }, pos);
    }

    private RocketItemCargoGameTests() { }
}
