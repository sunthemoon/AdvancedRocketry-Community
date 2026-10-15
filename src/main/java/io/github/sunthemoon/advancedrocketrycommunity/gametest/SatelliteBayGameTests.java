package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteBayFixtures.BATCH;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.SatelliteBayFixtures.TEMPLATE;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.VanillaInventoryCodeHooks;

/** W27b A1: the bay's menu, automation, owner and transaction locks, and the drop-once removal policy. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteBayGameTests {
    private SatelliteBayGameTests() {
    }

    private static boolean holds(SatelliteBayBlockEntity bay, ItemStack expected) {
        return bay.packageState().filter(state -> state.available()
                && ItemStack.matches(state.liveCopy(), expected.copyWithCount(1))).isPresent();
    }

    private static boolean empty(SatelliteBayBlockEntity bay) {
        return bay.contents().filter(contents -> contents.pkg() == null).isPresent();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void theMenuOpensForTheOwnerOrAnOperatorWithinEightBlocksAndNeverForASpectator(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos relative = new BlockPos(8, 2, 8);
        BlockPos at = helper.absolutePos(relative);
        UUID ownerId = UUID.randomUUID();
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer operator = null;
        try {
            SatelliteBayBlockEntity bay = SatelliteBayFixtures.place(helper, relative,
                    SatelliteBayFixtures.root(ownerId, null));
            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w27bOwner", level, at.north(2),
                    new ArrayList<>());
            joined.add(owner);
            helper.assertTrue(bay.admitted(owner) && bay.createMenu(1, owner.getInventory(), owner) != null,
                    "The owner could not open the bay");
            ServerPlayer stranger = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w27bStranger", level,
                    at.south(2), new ArrayList<>());
            joined.add(stranger);
            helper.assertTrue(!bay.admitted(stranger) && bay.createMenu(2, stranger.getInventory(), stranger) == null,
                    "Another player opened the bay");
            operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w27bOperator", level, at.east(2),
                    new ArrayList<>(), true);
            joined.add(operator);
            server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 2, false));
            helper.assertTrue(bay.admitted(operator), "An operator could not open the bay");
            // A spectator is refused even with the owner's own UUID.
            ServerPlayer spectator = new ServerPlayer(server, level, new GameProfile(ownerId, "w27bSpectator")) {
                @Override
                public boolean isSpectator() {
                    return true;
                }
            };
            spectator.setPos(at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 2.5D);
            helper.assertTrue(!bay.admitted(spectator) && bay.createMenu(3, spectator.getInventory(), spectator) == null,
                    "A spectator opened the bay");
            owner.teleportTo(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D + 7.9D);
            helper.assertTrue(bay.admitted(owner), "The owner was refused within eight blocks");
            owner.teleportTo(at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D + 8.1D);
            helper.assertTrue(!bay.admitted(owner), "The owner opened the bay from beyond eight blocks");
            owner.teleportTo(at.getX() + 0.5D, at.getY(), at.getZ() - 1.5D);

            SatelliteBayMenu menu = (SatelliteBayMenu) bay.createMenu(4, owner.getInventory(), owner);
            helper.assertTrue(menu.view().phase() == SatelliteBayStatus.Phase.NONE
                            && menu.view().reason() == SatelliteBayStatus.Reason.DEPLOYMENT_UNAVAILABLE,
                    "Status slots do not show the fixed deployment-unavailable reason");
            ItemStack pkg = SatelliteBayFixtures.dataPackage(ownerId);
            menu.setCarried(pkg.copy());
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().isEmpty() && holds(bay, pkg), "Placing into the menu slot failed");
            menu.setCarried(new ItemStack(Items.STONE));
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(menu.getCarried().is(Items.STONE) && holds(bay, pkg), "The slot accepted a foreign item");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), pkg) && empty(bay), "Taking from the slot failed");
            owner.getInventory().setItem(0, menu.getCarried());
            menu.setCarried(ItemStack.EMPTY);
            menu.quickMoveStack(owner, 28);
            helper.assertTrue(owner.getInventory().getItem(0).isEmpty() && holds(bay, pkg), "Shift-click in failed");
            menu.quickMoveStack(owner, 0);
            helper.assertTrue(empty(bay) && owner.getInventory().contains(pkg), "Shift-click out failed");
            owner.teleportTo(at.getX() + 0.5D, at.getY(), at.getZ() + 12.5D);
            helper.assertTrue(!menu.stillValid(owner), "The menu stayed valid beyond eight blocks");
        } finally {
            SatelliteBayFixtures.clear(level, at);
            if (operator != null) {
                server.getPlayerList().getOps().remove(operator.getGameProfile());
            }
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void hoppersAndDroppersGoThroughTheSameHandlerInSimulationAndInTheActualCalls(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bayRelative = new BlockPos(8, 4, 8);
        BlockPos above = bayRelative.above();
        BlockPos below = bayRelative.below();
        BlockPos side = bayRelative.west();
        UUID ownerId = UUID.randomUUID();
        try {
            SatelliteBayBlockEntity bay = SatelliteBayFixtures.place(helper, bayRelative,
                    SatelliteBayFixtures.root(ownerId, null));
            IItemHandler handler = bay.itemHandler();
            ItemStack mine = SatelliteBayFixtures.solarPackage(ownerId);
            ItemStack theirs = SatelliteBayFixtures.dataPackage(UUID.randomUUID());
            helper.assertTrue(handler.isItemValid(0, mine) && handler.insertItem(0, mine, true).isEmpty()
                    && empty(bay), "A simulated insertion was refused or changed the bay");

            // The vanilla hopper tick reaches the bay only through the item handler; the foreign package stays.
            helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
            HopperBlockEntity hopper = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(above));
            hopper.setItem(0, theirs.copy());
            hopper.setItem(1, mine.copy());
            HopperBlockEntity.pushItemsTick(level, helper.absolutePos(above), level.getBlockState(helper.absolutePos(above)),
                    hopper);
            helper.assertTrue(holds(bay, mine) && hopper.getItem(1).isEmpty()
                    && ItemStack.matches(hopper.getItem(0), theirs), "The hopper insertion did not follow the owner lock");
            helper.assertTrue(ItemStack.matches(handler.extractItem(0, 1, true), mine) && holds(bay, mine),
                    "A simulated extraction changed the bay");

            helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
            HopperBlockEntity collector = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(below));
            helper.assertTrue(Boolean.TRUE.equals(VanillaInventoryCodeHooks.extractHook(level, collector))
                    && empty(bay) && ItemStack.matches(collector.getItem(0), mine), "The hopper did not pull the package");
            collector.setItem(0, ItemStack.EMPTY);

            helper.setBlock(side, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            BlockPos dropperAt = helper.absolutePos(side);
            DispenserBlockEntity dropper = (DispenserBlockEntity) level.getBlockEntity(dropperAt);
            dropper.setItem(0, theirs.copy());
            level.getBlockState(dropperAt).tick(level, dropperAt, level.random);
            helper.assertTrue(empty(bay) && ItemStack.matches(dropper.getItem(0), theirs),
                    "The dropper inserted another owner's package");
            dropper.setItem(0, mine.copy());
            level.getBlockState(dropperAt).tick(level, dropperAt, level.random);
            helper.assertTrue(holds(bay, mine) && dropper.getItem(0).isEmpty(), "The dropper did not insert the package");
            helper.assertTrue(handler.insertItem(0, mine, false).getCount() == 1 && holds(bay, mine),
                    "A full bay accepted a second package");
        } finally {
            for (BlockPos relative : List.of(above, below, side)) {
                level.removeBlockEntity(helper.absolutePos(relative));
                level.setBlock(helper.absolutePos(relative), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            SatelliteBayFixtures.clear(level, helper.absolutePos(bayRelative));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void insertionRequiresThePackageOwnerToBeTheBayOwner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos owned = new BlockPos(4, 2, 4);
        BlockPos unowned = new BlockPos(11, 2, 11);
        UUID ownerId = UUID.randomUUID();
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            SatelliteBayBlockEntity bay = SatelliteBayFixtures.place(helper, owned, SatelliteBayFixtures.root(ownerId, null));
            ItemStack theirs = SatelliteBayFixtures.solarPackage(UUID.randomUUID());
            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w27bOwnerRefusal", level,
                    helper.absolutePos(owned).north(2), new ArrayList<>());
            joined.add(owner);
            SatelliteBayMenu menu = (SatelliteBayMenu) bay.createMenu(1, owner.getInventory(), owner);
            helper.assertTrue(!menu.getSlot(0).mayPlace(theirs) && !bay.itemHandler().isItemValid(0, theirs),
                    "The menu slot accepted another owner's package");
            helper.assertTrue(ItemStack.matches(bay.itemHandler().insertItem(0, theirs, true), theirs)
                            && ItemStack.matches(bay.itemHandler().insertItem(0, theirs, false), theirs) && empty(bay),
                    "The handler inserted another owner's package");
            menu.setCarried(theirs.copy());
            menu.clicked(0, 0, ClickType.PICKUP, owner);
            helper.assertTrue(ItemStack.matches(menu.getCarried(), theirs) && empty(bay), "A click moved the package");
            // A bay without an owner (as /setblock leaves one) accepts nobody's package.
            SatelliteBayBlockEntity bare = SatelliteBayFixtures.place(helper, unowned);
            ItemStack anyone = SatelliteBayFixtures.dataPackage(ownerId);
            helper.assertTrue(bare.ownerId().isEmpty() && !bare.itemHandler().isItemValid(0, anyone)
                    && ItemStack.matches(bare.itemHandler().insertItem(0, anyone, false), anyone)
                    && bare.heldRoot().isEmpty(), "An unowned bay accepted a package");
        } finally {
            SatelliteBayFixtures.clear(level, helper.absolutePos(owned));
            SatelliteBayFixtures.clear(level, helper.absolutePos(unowned));
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }

    /** One tick, so the per-tick transaction recovery never sees the planted record. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void aTransactionClaimLocksInsertionExtractionAndRemoval(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos origin = new BlockPos(5, 2, 5);
        BlockPos full = origin.east();
        BlockPos spare = origin.west();
        BlockPos side = spare.west();
        UUID ownerId = UUID.randomUUID();
        UUID transaction = UUID.randomUUID();
        RocketTransactionSavedData journal = RocketTransactionSavedData.get(server);
        RocketStructureSnapshot snapshot = null;
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            helper.setBlock(origin, ModBlocks.ROCKET_MOTOR.get());
            helper.setBlock(origin.above(), ModBlocks.ROCKET_SEAT.get());
            helper.setBlock(origin.above(2), ModBlocks.GUIDANCE_COMPUTER.get());
            ItemStack pkg = SatelliteBayFixtures.dataPackage(ownerId);
            SatelliteBayBlockEntity loaded = SatelliteBayFixtures.place(helper, full, SatelliteBayFixtures.root(ownerId, pkg));
            SatelliteBayBlockEntity open = SatelliteBayFixtures.place(helper, spare, SatelliteBayFixtures.root(ownerId, null));
            snapshot = scan(helper, origin);
            RocketRegion region = RocketRegion.fromSnapshot(snapshot);
            helper.assertTrue(region.contains(position(helper.absolutePos(full)))
                    && region.contains(position(helper.absolutePos(spare))), "The claim fixture misses a bay");
            journal.journalFor(snapshot, ownerId).write(new RocketTransactionRecord(transaction,
                    RocketTransactionType.ASSEMBLY, RocketTransactionPhase.SNAPSHOT_VALIDATED, snapshot.snapshotId(),
                    snapshot.contentHash(), region, 0, null));
            helper.assertTrue(loaded.claimed() && loaded.guard() == SatelliteBayStatus.Guard.CLAIMED,
                    "The journal record does not claim the bay");

            ItemStack spareStack = SatelliteBayFixtures.solarPackage(ownerId);
            helper.assertTrue(loaded.itemHandler().extractItem(0, 1, true).isEmpty()
                    && loaded.itemHandler().extractItem(0, 1, false).isEmpty() && holds(loaded, pkg),
                    "A claimed bay released its package");
            helper.assertTrue(!open.itemHandler().isItemValid(0, spareStack)
                    && ItemStack.matches(open.itemHandler().insertItem(0, spareStack, true), spareStack)
                    && ItemStack.matches(open.itemHandler().insertItem(0, spareStack, false), spareStack) && empty(open),
                    "A claimed bay accepted a package");
            helper.setBlock(side, Blocks.DROPPER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            BlockPos dropperAt = helper.absolutePos(side);
            DispenserBlockEntity dropper = (DispenserBlockEntity) level.getBlockEntity(dropperAt);
            dropper.setItem(0, spareStack.copy());
            level.getBlockState(dropperAt).tick(level, dropperAt, level.random);
            helper.assertTrue(empty(open) && ItemStack.matches(dropper.getItem(0), spareStack),
                    "The dropper reached a claimed bay");

            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w27bClaim", level,
                    helper.absolutePos(origin).north(2), new ArrayList<>());
            joined.add(owner);
            SatelliteBayMenu fullMenu = (SatelliteBayMenu) loaded.createMenu(1, owner.getInventory(), owner);
            SatelliteBayMenu openMenu = (SatelliteBayMenu) open.createMenu(2, owner.getInventory(), owner);
            helper.assertTrue(fullMenu != null && openMenu != null && !fullMenu.getSlot(0).mayPickup(owner)
                    && !openMenu.getSlot(0).mayPlace(spareStack), "The menu slot ignored the transaction claim");
            BlockEvent.BreakEvent breaking = new BlockEvent.BreakEvent(level, helper.absolutePos(full),
                    loaded.getBlockState(), owner);
            MinecraftForge.EVENT_BUS.post(breaking);
            helper.assertTrue(breaking.isCanceled(), "A claimed bay allowed player break");
            Explosion explosion = new Explosion(level, null, helper.absolutePos(full).getX(), helper.absolutePos(full).getY(),
                    helper.absolutePos(full).getZ(), 1.0F, false, Explosion.BlockInteraction.DESTROY);
            explosion.getToBlow().add(helper.absolutePos(full));
            MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
            helper.assertTrue(explosion.getToBlow().isEmpty(), "A claimed bay stayed in the explosion");
            helper.assertTrue(Block.getDrops(loaded.getBlockState(), level, helper.absolutePos(full), loaded).isEmpty()
                    && holds(loaded, pkg), "A claimed bay produced drops");

            journal.journalFor(snapshot, ownerId).remove(transaction);
            helper.assertTrue(!loaded.claimed() && ItemStack.matches(loaded.itemHandler().extractItem(0, 1, true), pkg)
                    && open.itemHandler().isItemValid(0, spareStack), "Removing the record did not lift the lock");
        } finally {
            if (snapshot != null) {
                journal.journalFor(snapshot, ownerId).remove(transaction);
            }
            for (BlockPos relative : List.of(origin, origin.above(), origin.above(2), side)) {
                level.removeBlockEntity(helper.absolutePos(relative));
                level.setBlock(helper.absolutePos(relative), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            SatelliteBayFixtures.clear(level, helper.absolutePos(full));
            SatelliteBayFixtures.clear(level, helper.absolutePos(spare));
            joined.forEach(player -> server.getPlayerList().remove(player));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void aValidBayDropsItsPackageExactlyOnceOnBreakAndOnExplosion(GameTestHelper helper) {
        BlockPos broken = new BlockPos(2, 2, 2);
        BlockPos exploded = new BlockPos(2, 2, 13);
        BlockPos consumed = new BlockPos(13, 2, 2);
        BlockPos creative = new BlockPos(13, 2, 13);
        BlockPos lootFirst = new BlockPos(8, 2, 2);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<ServerPlayer> joined = new ArrayList<>();
            try {
                ItemStack first = SatelliteBayFixtures.dataPackage(ownerId);
                ItemStack second = SatelliteBayFixtures.solarPackage(ownerId);
                ItemStack third = SatelliteBayFixtures.dataPackage(ownerId);
                SatelliteBayFixtures.place(helper, broken, SatelliteBayFixtures.root(ownerId, first));
                SatelliteBayBlockEntity blown = SatelliteBayFixtures.place(helper, exploded,
                        SatelliteBayFixtures.root(ownerId, second));
                SatelliteBayFixtures.place(helper, consumed, SatelliteBayFixtures.consumedRoot(ownerId));
                SatelliteBayFixtures.place(helper, creative, SatelliteBayFixtures.root(ownerId, third));
                ServerPlayer player = ConnectedTestPlayers.join(server, ownerId, "w27bBreaker", level,
                        helper.absolutePos(new BlockPos(8, 2, 8)), new ArrayList<>(), false, new ArrayList<>(), false);
                joined.add(player);
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(broken)), "Survival break was refused");
                assertDrops(helper, broken, first, 1);
                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(consumed)), "Consumed bay break refused");
                assertDrops(helper, consumed, null, 1);

                BlockPos blownAt = helper.absolutePos(exploded);
                Explosion explosion = new Explosion(level, null, blownAt.getX() + 0.5D, blownAt.getY() + 0.5D,
                        blownAt.getZ() + 0.5D, 1.0F, false, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(blownAt);
                MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                helper.assertTrue(explosion.getToBlow().contains(blownAt) && !blown.protectedFromRemoval(),
                        "A valid bay left the explosion");
                explosion.finalizeExplosion(false);
                helper.assertTrue(level.getBlockState(blownAt).isAir(), "The explosion kept the bay");
                assertDrops(helper, exploded, second, 1);

                player.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(creative)), "Creative break was refused");
                assertDrops(helper, creative, third, 0);

                // Loot first (as an explosion or another remover does), then removal: one shared extraction flag.
                ItemStack fourth = SatelliteBayFixtures.solarPackage(ownerId);
                SatelliteBayBlockEntity looted = SatelliteBayFixtures.place(helper, lootFirst,
                        SatelliteBayFixtures.root(ownerId, fourth));
                BlockPos lootAt = helper.absolutePos(lootFirst);
                List<ItemStack> loot = Block.getDrops(looted.getBlockState(), level, lootAt, looted);
                helper.assertTrue(loot.size() == 2 && loot.stream().filter(stack -> ItemStack.matches(stack, fourth)).count() == 1
                        && loot.stream().filter(stack -> stack.is(ModItems.ROCKET_SATELLITE_BAY.get())).count() == 1,
                        "Loot did not yield the bay and its package: " + loot);
                helper.assertTrue(Block.getDrops(looted.getBlockState(), level, lootAt, looted).size() == 1,
                        "A second loot roll emitted the package again");
                level.setBlock(lootAt, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                assertDrops(helper, lootFirst, null, 0);
            } finally {
                for (BlockPos relative : List.of(broken, exploded, consumed, creative, lootFirst)) {
                    SatelliteBayFixtures.clear(level, helper.absolutePos(relative));
                    SatelliteBayFixtures.discardDrops(level, helper.absolutePos(relative));
                }
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, broken, exploded, consumed, creative, lootFirst);
    }

    /** Exactly {@code bays} plain bay items and, when {@code pkg} is set, that one package; nothing else. */
    private static void assertDrops(GameTestHelper helper, BlockPos relative, ItemStack pkg, int bays) {
        List<ItemEntity> drops = SatelliteBayFixtures.drops(helper.getLevel(), helper.absolutePos(relative));
        long bayItems = drops.stream().filter(drop -> drop.getItem().is(ModItems.ROCKET_SATELLITE_BAY.get())
                && drop.getItem().getCount() == 1 && !drop.getItem().hasTag()).count();
        long packages = drops.stream().filter(drop -> pkg != null && ItemStack.matches(drop.getItem(), pkg)).count();
        helper.assertTrue(bayItems == bays && packages == (pkg == null ? 0 : 1)
                        && drops.size() == bays + (pkg == null ? 0 : 1),
                "Expected " + bays + " plain bay item(s) and " + (pkg == null ? 0 : 1) + " package at " + relative
                        + ", found " + drops.stream().map(drop -> drop.getItem().toString()).toList());
    }

    static RocketStructureSnapshot scan(GameTestHelper helper, BlockPos relativeOrigin) {
        ServerLevel level = helper.getLevel();
        RocketStructureScanTask scan = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, RocketBlockEntityAdapters.defaults()), level.dimension().location(),
                position(helper.absolutePos(relativeOrigin)), UUID.randomUUID(), level.getGameTime());
        RocketScanResult result = scan.step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        helper.assertTrue(result.status() == RocketScanResult.Status.SUCCESS,
                "The bay rocket did not scan: " + result.issues());
        return result.snapshot().orElseThrow();
    }

    static RocketPosition position(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }
}
