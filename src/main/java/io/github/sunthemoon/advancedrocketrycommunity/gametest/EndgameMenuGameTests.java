package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Device menus: every slot change, including a partial shift-click and a merge into a non-empty slot, marks the block
 * entity changed, so the next save stores what the player left (ADR-054 section 8, review C11R-C1); a viewer with
 * public {@code VIEW} only takes and puts nothing (ADR-054 section 3, review C11R-H2).
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndgameMenuGameTests {
    private EndgameMenuGameTests() {
    }

    @GameTest(template = "empty", batch = "endgame_menu_marker_save", timeoutTicks = 100)
    public static void aPartialShiftClickOutOfALaserTargetIsSaved(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID ownerId = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(ownerId), "Owner not assigned");
        ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "menuMarkerOwner", level, pos.east(2),
                new ArrayList<>());
        try {
            target.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
            fillAllBut(owner, 10, Items.COBBLESTONE);
            LaserTargetMenu menu = new LaserTargetMenu(51, owner.getInventory(), target);
            owner.containerMenu = menu;
            LevelChunk chunk = level.getChunkAt(pos);
            chunk.setUnsaved(true);
            level.getChunkSource().save(true);
            helper.assertTrue(storedBufferCount(level, chunk, pos) == 64, "The fixture stack did not reach the disk");
            menu.clicked(0, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(target.buffer().getStackInSlot(0).getCount() == 54
                    && owner.getInventory().countItem(Items.COBBLESTONE) == 64, "The partial move was not partial");
            helper.assertTrue(chunk.isUnsaved(), "A partial shift-click left the chunk clean");
            level.getChunkSource().save(true);
            int stored = storedBufferCount(level, chunk, pos);
            helper.assertTrue(stored == 54, "The disk kept " + stored + " after a partial shift-click, not 54");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            server.getPlayerList().remove(owner);
            target.buffer().setStackInSlot(0, ItemStack.EMPTY);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_menu_drill_save", timeoutTicks = 100)
    public static void aPartialShiftClickOutOfTheDrillOutputMarksItChanged(GameTestHelper helper) {
        OrbitalLaserDrillGameTests.Fixture fixture = new OrbitalLaserDrillGameTests.Fixture(helper,
                CelestialIds.EARTH_ID);
        ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "menuDrillOwner", fixture.space,
                fixture.controller.south(3), new ArrayList<>());
        try {
            OrbitalLaserDrillBlockEntity drill = fixture.drill();
            drill.storage().output().setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
            fillAllBut(owner, 10, Items.DIAMOND);
            OrbitalLaserDrillMenu menu = new OrbitalLaserDrillMenu(52, owner.getInventory(), drill);
            owner.containerMenu = menu;
            LevelChunk chunk = fixture.space.getChunkAt(fixture.controller);
            chunk.setUnsaved(false);
            menu.clicked(1, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(drill.storage().output().getStackInSlot(0).getCount() == 54,
                    "The partial move was not partial");
            helper.assertTrue(chunk.isUnsaved(), "A partial shift-click out of the output left the chunk clean");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            fixture.server.getPlayerList().remove(owner);
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "endgame_menu_fuel_save", timeoutTicks = 100)
    public static void aShiftClickMergedIntoGeneratorFuelMarksItChanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.BLACK_HOLE_GENERATOR.get().defaultBlockState());
        BlackHoleGeneratorBlockEntity generator = (BlackHoleGeneratorBlockEntity) level.getBlockEntity(pos);
        UUID ownerId = UUID.randomUUID();
        helper.assertTrue(generator.assignOwner(ownerId), "Owner not assigned");
        ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "menuFuelOwner", level, pos.east(2),
                new ArrayList<>());
        try {
            generator.fuel().setStackInSlot(0, new ItemStack(Items.STICK, 10));
            owner.getInventory().setItem(9, new ItemStack(Items.STICK, 5));
            BlackHoleGeneratorMenu menu = new BlackHoleGeneratorMenu(53, owner.getInventory(), generator);
            owner.containerMenu = menu;
            LevelChunk chunk = level.getChunkAt(pos);
            chunk.setUnsaved(false);
            // Inventory slot 9, the first main-inventory slot, is the first menu slot after the fuel slots.
            menu.clicked(BlackHoleGeneratorBlockEntity.FUEL_SLOTS, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(generator.fuel().getStackInSlot(0).getCount() == 15,
                    "The sticks were not merged into the first fuel slot");
            helper.assertTrue(chunk.isUnsaved(), "A merge into a non-empty fuel slot left the chunk clean");
        } finally {
            owner.containerMenu = owner.inventoryMenu;
            server.getPlayerList().remove(owner);
            generator.fuel().setStackInSlot(0, ItemStack.EMPTY);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** Outside stations a stranger has public VIEW of another player's laser target, and takes nothing. */
    @GameTest(template = "empty", batch = "endgame_menu_stranger", timeoutTicks = 100)
    public static void aStrangerSeesALaserTargetButTakesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, ModBlocks.LASER_TARGET.get().defaultBlockState());
        LaserTargetBlockEntity target = (LaserTargetBlockEntity) level.getBlockEntity(pos);
        UUID ownerId = UUID.randomUUID();
        helper.assertTrue(target.assignOwner(ownerId), "Owner not assigned");
        target.buffer().setStackInSlot(0, new ItemStack(Items.DIAMOND, 5));
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer stranger = ConnectedTestPlayers.join(server, UUID.randomUUID(), "menuStranger", level,
                    pos.east(2), new ArrayList<>());
            joined.add(stranger);
            open(level, stranger, pos);
            helper.assertTrue(stranger.containerMenu instanceof LaserTargetMenu menu
                    && menu.stillValid(stranger) && !menu.itemActionAllowed(EndgameAction.WITHDRAW),
                    "The stranger's view is not a public view: " + stranger.containerMenu);
            LaserTargetMenu menu = (LaserTargetMenu) stranger.containerMenu;
            menu.clicked(0, 0, ClickType.QUICK_MOVE, stranger);
            menu.clicked(0, 0, ClickType.PICKUP, stranger);
            menu.clicked(0, 0, ClickType.THROW, stranger);
            menu.clicked(0, 0, ClickType.SWAP, stranger);
            helper.assertTrue(target.buffer().getStackInSlot(0).getCount() == 5
                            && stranger.getInventory().countItem(Items.DIAMOND) == 0 && menu.getCarried().isEmpty(),
                    "A stranger took drops from another player's laser target");
            ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "menuTargetOwner", level, pos.west(2),
                    new ArrayList<>());
            joined.add(owner);
            open(level, owner, pos);
            owner.containerMenu.clicked(0, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(owner.getInventory().countItem(Items.DIAMOND) == 5
                    && target.buffer().getStackInSlot(0).isEmpty(), "The owner could not take the drops");
        } finally {
            joined.forEach(player -> {
                player.containerMenu = player.inventoryMenu;
                server.getPlayerList().remove(player);
            });
            target.buffer().setStackInSlot(0, ItemStack.EMPTY);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    /** A station member has VIEW of the station owner's drill: no output, no lens, no lens put back. */
    @GameTest(template = "empty", batch = "endgame_menu_member", timeoutTicks = 100)
    public static void aStationMemberSeesTheDrillButTakesAndPutsNothing(GameTestHelper helper) {
        OrbitalLaserDrillGameTests.Fixture fixture = new OrbitalLaserDrillGameTests.Fixture(helper,
                CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        drill.storage().lens().setStackInSlot(0, new ItemStack(ModItems.LASER_LENS.get()));
        drill.storage().output().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer member = ConnectedTestPlayers.join(fixture.server, UUID.randomUUID(), "menuMember",
                    fixture.space, fixture.controller.south(3), new ArrayList<>());
            joined.add(member);
            fixture.stations.invite(fixture.stationId, member.getUUID());
            fixture.stations.acceptInvitation(fixture.stationId, member.getUUID());
            open(fixture.space, member, fixture.controller);
            helper.assertTrue(member.containerMenu instanceof OrbitalLaserDrillMenu menu && menu.stillValid(member)
                    && !menu.itemActionAllowed(EndgameAction.WITHDRAW)
                    && !menu.itemActionAllowed(EndgameAction.CONFIGURE),
                    "The member's view is not VIEW only: " + member.containerMenu);
            OrbitalLaserDrillMenu menu = (OrbitalLaserDrillMenu) member.containerMenu;
            menu.clicked(1, 0, ClickType.QUICK_MOVE, member);
            menu.clicked(0, 0, ClickType.QUICK_MOVE, member);
            menu.clicked(1, 0, ClickType.PICKUP, member);
            helper.assertTrue(member.getInventory().countItem(Items.DIAMOND) == 0
                            && member.getInventory().countItem(ModItems.LASER_LENS.get()) == 0
                            && drill.storage().output().getStackInSlot(0).getCount() == 7
                            && drill.storage().lensPresent() && menu.getCarried().isEmpty(),
                    "A station member took the drill's output or lens");
            drill.storage().lens().setStackInSlot(0, ItemStack.EMPTY);
            member.getInventory().setItem(9, new ItemStack(ModItems.LASER_LENS.get()));
            menu.clicked(menu.slots.size() - 36, 0, ClickType.QUICK_MOVE, member);
            helper.assertTrue(!drill.storage().lensPresent()
                            && member.getInventory().countItem(ModItems.LASER_LENS.get()) == 1,
                    "A station member put a lens into the station owner's drill");
            ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "menuDrillStationOwner",
                    fixture.space, fixture.controller.south(3).east(), new ArrayList<>());
            joined.add(owner);
            open(fixture.space, owner, fixture.controller);
            owner.containerMenu.clicked(1, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(owner.getInventory().countItem(Items.DIAMOND) == 7, "The owner could not take output");
        } finally {
            joined.forEach(player -> {
                player.containerMenu = player.inventoryMenu;
                fixture.server.getPlayerList().remove(player);
            });
            fixture.close();
        }
        helper.succeed();
    }

    /**
     * Review C11R-M4: once the drill's station is gone its position is unavailable; its owner still opens a
     * withdraw-only menu and takes the output, while a former member cannot open it.
     */
    @GameTest(template = "empty", batch = "endgame_menu_unavailable", timeoutTicks = 100)
    public static void theOwnerWithdrawsFromADrillInAnUnavailableStation(GameTestHelper helper) {
        OrbitalLaserDrillGameTests.Fixture fixture = new OrbitalLaserDrillGameTests.Fixture(helper,
                CelestialIds.EARTH_ID);
        OrbitalLaserDrillBlockEntity drill = fixture.drill();
        drill.storage().output().setStackInSlot(0, new ItemStack(Items.DIAMOND, 7));
        List<ServerPlayer> joined = new ArrayList<>();
        try {
            ServerPlayer member = ConnectedTestPlayers.join(fixture.server, UUID.randomUUID(), "menuFormerMember",
                    fixture.space, fixture.controller.south(3), new ArrayList<>());
            joined.add(member);
            fixture.stations.invite(fixture.stationId, member.getUUID());
            fixture.stations.acceptInvitation(fixture.stationId, member.getUUID());
            fixture.stations.delete(fixture.stationId);
            open(fixture.space, member, fixture.controller);
            helper.assertTrue(!(member.containerMenu instanceof OrbitalLaserDrillMenu),
                    "A non-owner opened a drill in an unavailable station");
            ServerPlayer owner = ConnectedTestPlayers.join(fixture.server, fixture.owner, "menuUnavailableOwner",
                    fixture.space, fixture.controller.south(3).east(), new ArrayList<>());
            joined.add(owner);
            open(fixture.space, owner, fixture.controller);
            helper.assertTrue(owner.containerMenu instanceof OrbitalLaserDrillMenu menu && menu.stillValid(owner)
                            && menu.itemActionAllowed(EndgameAction.WITHDRAW)
                            && !menu.itemActionAllowed(EndgameAction.CONFIGURE),
                    "The owner did not get a withdraw-only menu: " + owner.containerMenu);
            owner.containerMenu.clicked(1, 0, ClickType.QUICK_MOVE, owner);
            helper.assertTrue(owner.getInventory().countItem(Items.DIAMOND) == 7
                    && drill.storage().output().getStackInSlot(0).isEmpty(), "The owner could not withdraw");
            helper.assertTrue(!owner.containerMenu.clickMenuButton(owner, OrbitalLaserDrillMenu.BUTTON_START)
                    && !drill.running(), "A withdraw-only menu started the drill");
        } finally {
            joined.forEach(player -> {
                player.containerMenu = player.inventoryMenu;
                fixture.server.getPlayerList().remove(player);
            });
            fixture.close();
        }
        helper.succeed();
    }

    /** Right-clicks the block as the player would (the block's own authority check opens the menu or refuses). */
    private static void open(ServerLevel level, ServerPlayer player, BlockPos pos) {
        level.getBlockState(pos).use(level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
    }

    /** Fills the inventory with dirt, leaving room for exactly {@code room} more of {@code item} in slot 0. */
    private static void fillAllBut(ServerPlayer player, int room, net.minecraft.world.item.Item item) {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
        }
        player.getInventory().setItem(0, new ItemStack(item, 64 - room));
    }

    /** The count in buffer slot 0 of the laser target in the chunk tag currently stored on disk. */
    private static int storedBufferCount(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        CompoundTag tag = level.getChunkSource().chunkMap.read(chunk.getPos()).join().orElse(null);
        if (tag == null) {
            return -1;
        }
        ListTag entities = tag.getList("block_entities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundTag entity = entities.getCompound(i);
            if (entity.getInt("x") == pos.getX() && entity.getInt("y") == pos.getY()
                    && entity.getInt("z") == pos.getZ()) {
                ListTag items = entity.getCompound("endgame").getCompound("buffer").getList("Items", Tag.TAG_COMPOUND);
                return items.isEmpty() ? 0 : items.getCompound(0).getByte("Count");
            }
        }
        return -2;
    }
}
