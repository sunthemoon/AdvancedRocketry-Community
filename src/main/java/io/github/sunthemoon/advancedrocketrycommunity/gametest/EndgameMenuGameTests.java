package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleGeneratorMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserTargetMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.OrbitalLaserDrillMenu;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-054 section 8 for device menus: every slot change, including a partial shift-click and a merge into a
 * non-empty slot, marks the block entity changed, so the next save stores what the player left (review C11R-C1).
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
