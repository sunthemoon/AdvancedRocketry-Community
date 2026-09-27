package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteTerminalPersistenceGameTests {
    private static final String KEY = "SatelliteTerminal";
    private SatelliteTerminalPersistenceGameTests() { }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void unknownNativeItemSurvivesBlockDropAndActualPlacement(GameTestHelper helper) {
        var terminal = place(helper);
        CompoundTag raw = terminal.saveWithoutMetadata().getCompound(KEY);
        var item = new ItemStack(Items.AMETHYST_SHARD, 3).serializeNBT();
        item.putString("id", "removed_mod:payload"); item.putInt("Slot", 2);
        var items = new ListTag(); items.add(item); raw.getCompound("inventory").put("Items", items);
        roundTripQuarantined(helper, terminal, raw);
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void futureAndScalarRootsSurviveNativePlacementWithoutMerge(GameTestHelper helper) {
        var terminal = place(helper);
        CompoundTag future = new CompoundTag(); future.putInt("schema_version", 99); future.putString("opaque", "keep");
        roundTripQuarantined(helper, terminal, future);
        // Reuse the source after the first removal; no native item is retained in its old BE.
        terminal = place(helper);
        roundTripQuarantined(helper, terminal, StringTag.valueOf("opaque scalar root"));
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void inventoryShapesAreRejectedBeforeNativeAllocationOrNormalization(GameTestHelper helper) {
        var terminal = place(helper);
        CompoundTag valid = terminal.saveWithoutMetadata().getCompound(KEY);
        for (int variant = 0; variant < 7; variant++) {
            CompoundTag raw = valid.copy(); CompoundTag inventory = raw.getCompound("inventory");
            var item = new ItemStack(Items.AMETHYST_SHARD).serializeNBT(); item.putInt("Slot", 2);
            var items = new ListTag(); items.add(item); inventory.put("Items", items);
            switch (variant) {
                case 0 -> inventory.putInt("Size", Integer.MAX_VALUE);
                case 1 -> items.add(item.copy());
                case 2 -> item.putInt("Slot", 6);
                case 3 -> item.putInt("Count", 1);
                case 4 -> item.putByte("Count", (byte) 65);
                case 5 -> item.putString("discarded_unknown_field", "must not disappear");
                case 6 -> raw.putString("owner_id", "invalid UUID");
            }
            load(terminal, raw); assertBlocked(helper, terminal, raw);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void unregisteredKnownPayloadStaysExtractableButCannotAssemble(GameTestHelper helper) {
        var terminal = place(helper); CompoundTag raw = terminal.saveWithoutMetadata().getCompound(KEY);
        var item = new ItemStack(Items.PRISMARINE_CRYSTALS, 3).serializeNBT(); item.putInt("Slot", 2);
        var items = new ListTag(); items.add(item); raw.getCompound("inventory").put("Items", items);
        load(terminal, raw);
        helper.assertTrue(terminal.saveWithoutMetadata().get(KEY).equals(raw), "Unregistered known payload changed on load");
        var handler = terminal.menuInventory();
        helper.assertTrue(!handler.isItemValid(2, new ItemStack(Items.PRISMARINE_CRYSTALS)), "Unregistered input became an assembly component");
        helper.assertTrue(handler.extractItem(2, 64, false).getCount() == 3, "Unregistered native item was trapped or erased");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void oversizedAndDeepRootsAreRetainedWithoutCopyOrSurvivalBreak(GameTestHelper helper) {
        var terminal = place(helper);
        var oversized = new CompoundTag(); oversized.putByteArray("opaque", new byte[65536]);
        var deep = new CompoundTag(); CompoundTag cursor = deep;
        for (int i = 0; i < 30; i++) { var child = new CompoundTag(); cursor.put("n", child); cursor = child; }
        for (var raw : java.util.List.of(oversized, deep)) {
            load(terminal, raw);
            helper.assertTrue(terminal.saveWithoutMetadata().get(KEY) == raw, "Unbounded root was copied/normalized");
            var state = terminal.getBlockState();
            helper.assertTrue(!state.getBlock().onDestroyedByPlayer(state, helper.getLevel(), terminal.getBlockPos(),
                    player(helper, terminal), true, state.getFluidState()), "Uncarryable root allowed survival break");
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void menuTracksIdentityTargetsAndRejectsStaleCatalogActions(GameTestHelper helper) {
        var terminal = place(helper); var player = player(helper, terminal);
        var catalog = SatelliteTerminalTargets.current();
        var menu = new SatelliteTerminalMenu(1, player.getInventory(), terminal, catalog);
        var chip = new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.SATELLITE_CONTROL_CHIP.get());
        var identity = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), ResourceLocation.tryParse("arce_adapter_test:research_payload"));
        SatelliteItemData.write(chip, identity); terminal.menuInventory().insertItem(3, chip, false);
        helper.assertTrue(menu.targets().equals(java.util.List.of(ResourceLocation.tryParse("advancedrocketrycommunity:earth"),
                ResourceLocation.tryParse("advancedrocketrycommunity:moon"))), "Menu kept built-in targets after changing identity");
        var stale = new SatelliteTerminalMenu(2, player.getInventory(), terminal,
                new SatelliteTerminalTargets(catalog.generation() + 1, catalog.definitions()));
        var before = terminal.saveWithoutMetadata();
        helper.assertTrue(!stale.stillValid(player) && !stale.clickMenuButton(player, 1), "Stale catalog accepted menu action");
        helper.assertTrue(before.equals(terminal.saveWithoutMetadata()), "Stale action changed terminal data");
        var absent = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), ResourceLocation.tryParse("removed_mod:payload"));
        helper.assertTrue(SatelliteRuntime.launch(player, absent, ResourceLocation.tryParse("advancedrocketrycommunity:earth")).code()
                == SatelliteOperationCode.DEFINITION_NOT_FOUND, "Missing definition allowed new launch");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void replacedTerminalInvalidatesOldMenuAndCachedHandler(GameTestHelper helper) {
        var terminal = place(helper); var player = player(helper, terminal);
        var handler = terminal.menuInventory();
        var component = new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.SATELLITE_CHASSIS.get(), 3);
        helper.assertTrue(handler.insertItem(0, component, false).isEmpty(), "Replacement fixture input rejected");
        var menu = new SatelliteTerminalMenu(1, player.getInventory(), terminal, SatelliteTerminalTargets.current());
        BlockPos pos = terminal.getBlockPos();
        helper.getLevel().destroyBlock(pos, true);
        helper.getLevel().setBlockAndUpdate(pos, ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState());
        helper.assertTrue(!menu.stillValid(player) && menu.quickMoveStack(player, 0).isEmpty(), "Old menu duplicated removed resources");
        helper.assertTrue(handler.extractItem(0, 64, false).isEmpty(), "Cached removed inventory remained extractable");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
        helper.assertTrue(drops.stream().filter(drop -> drop.getItem().is(component.getItem())).mapToInt(drop -> drop.getItem().getCount()).sum() == 3,
                "Replacement duplicated or lost dropped components");
        helper.succeed();
    }

    @GameTest(template = "rocket_test", timeoutTicks = 30)
    public static void nativeItemByteBudgetExcludesHandlerSlotMetadata(GameTestHelper helper) {
        var terminal = place(helper);
        var chip = new ItemStack(io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems.SATELLITE_CONTROL_CHIP.get());
        var tag = chip.getOrCreateTag(); tag.putString("padding", "");
        int base = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteNbtSize.uncompressedBytes(chip.serializeNBT());
        tag.putString("padding", "x".repeat(4096 - base));
        helper.assertTrue(io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteNbtSize.uncompressedBytes(chip.serializeNBT()) == 4096,
                "Native item fixture does not meet exact byte boundary");
        helper.assertTrue(terminal.menuInventory().insertItem(3, chip, false).isEmpty(), "Maximum valid native item rejected");
        var saved = terminal.saveWithoutMetadata(); terminal.load(saved);
        helper.assertTrue(terminal.menuInventory().extractItem(3, 1, true).serializeNBT().equals(chip.serializeNBT()),
                "Handler Slot metadata incorrectly quarantined valid native item");
        helper.assertTrue(saved.equals(terminal.saveWithoutMetadata()), "Boundary item changed after reload");
        helper.succeed();
    }

    private static SatelliteTerminalBlockEntity place(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.SATELLITE_TERMINAL.get());
        return (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SatellitePersistence"));
        player.setPos(terminal.getBlockPos().getX() + 0.5, terminal.getBlockPos().getY() + 1, terminal.getBlockPos().getZ() + 0.5);
        return player;
    }

    private static void load(SatelliteTerminalBlockEntity terminal, Tag raw) {
        var parent = new CompoundTag(); parent.put(KEY, raw); terminal.load(parent); terminal.setChanged();
    }

    private static void assertBlocked(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, Tag raw) {
        helper.assertTrue(raw.equals(terminal.saveWithoutMetadata().get(KEY)), "Quarantined root changed");
        helper.assertTrue(terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().receiveEnergy(1000, false) == 0,
                "Quarantined terminal accepted power");
        for (int slot = 0; slot < 6; slot++) { helper.assertTrue(terminal.menuInventory().extractItem(slot, 64, false).isEmpty(), "Quarantine exposed items"); }
    }

    private static void roundTripQuarantined(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, Tag raw) {
        load(terminal, raw); assertBlocked(helper, terminal, raw);
        var level = helper.getLevel(); var pos = terminal.getBlockPos();
        helper.assertTrue(level.destroyBlock(pos, true), "Native terminal removal failed");
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5));
        helper.assertTrue(drops.size() == 1, "Quarantine duplicated items or omitted terminal");
        ItemStack carried = drops.get(0).getItem().copy(); drops.get(0).discard();
        helper.assertTrue(carried.getCount() == 1 && BlockItem.getBlockEntityData(carried).get(KEY).equals(raw), "Carried root differs");
        BlockPos destination = pos.south(3); level.setBlockAndUpdate(destination.below(), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(destination, Blocks.AIR.defaultBlockState());
        var player = player(helper, terminal); player.setPos(destination.getX() + 2, destination.getY(), destination.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, carried);
        var hit = new BlockHitResult(Vec3.atCenterOf(destination.below()).add(0, 0.5, 0), Direction.UP, destination.below(), false);
        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, carried, hit);
        helper.assertTrue(((BlockItem) carried.getItem()).place(context).consumesAction(), "Actual BlockItem placement failed");
        var placed = (SatelliteTerminalBlockEntity) level.getBlockEntity(destination);
        assertBlocked(helper, placed, raw);
    }
}
