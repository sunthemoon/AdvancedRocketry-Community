package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketItemCargoFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W120-R1: the owner's menu through real click packets. Every click on a device slot conserves the items of the
 * device, the cursor and the player inventory, counting the contents of bundles.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketItemCargoMenuGameTests {
    /** A saved bundle holding {@code count} of {@code item}. */
    static CompoundTag bundle(Item item, int count) {
        var bundle = new ItemStack(Items.BUNDLE);
        var contents = new ListTag(); contents.add(saved(item, count));
        bundle.getOrCreateTag().put("Items", contents);
        return bundle.save(new CompoundTag());
    }

    /** How many {@code item} a stack holds, itself or inside a bundle. */
    static int count(ItemStack stack, Item item) {
        int total = stack.is(item) ? stack.getCount() : 0;
        if (stack.is(Items.BUNDLE) && stack.hasTag()) {
            for (Tag inner : stack.getTag().getList("Items", Tag.TAG_COMPOUND)) { total += count(ItemStack.of((CompoundTag) inner), item); }
        }
        return total;
    }

    /** The device's four slots, the cursor and the whole player inventory. */
    static int total(RocketItemCargoBlockEntity device, RocketItemCargoMenu menu, ServerPlayer owner, Item item) {
        int total = count(menu.getCarried(), item);
        for (int slot = 0; slot < RocketItemCargoMenu.DEVICE_SLOTS; slot++) { total += count(device.stack(slot), item); }
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) { total += count(owner.getInventory().getItem(slot), item); }
        return total;
    }

    static RocketItemCargoMenu open(GameTestHelper helper, RocketItemCargoBlockEntity device, ServerPlayer owner) {
        var at = device.getBlockPos();
        helper.assertTrue(device.getBlockState().use(helper.getLevel(), owner, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)).consumesAction()
                && owner.containerMenu instanceof RocketItemCargoMenu, "Owner menu did not open");
        return (RocketItemCargoMenu) owner.containerMenu;
    }

    static void click(ServerPlayer owner, RocketItemCargoMenu menu, int slot, int button, ClickType type) {
        owner.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, button,
                type, menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }

    /** Leaves nothing carried, so closing the menu drops nothing. */
    static void close(RocketItemCargoMenu menu, ServerPlayer owner) {
        menu.setCarried(ItemStack.EMPTY); owner.closeContainer();
    }

    /**
     * A bundle's own click override acts on the stack the slot hands out. Right-clicking the device's bundle with an
     * empty cursor must not copy its diamonds, and right-clicking it with a carried stack must not delete that stack.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void aBundleInADeviceSlotNeitherDuplicatesNorDeletes(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120fBundle", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                for (var role : RocketCargoRole.values()) {
                    for (boolean carrying : new boolean[] {false, true}) {
                        owner.getInventory().clearContent();
                        var device = place(helper, pos, role, owned(owner.getUUID(), role, bundle(Items.DIAMOND, 32)));
                        var menu = open(helper, device, owner);
                        if (carrying) { menu.setCarried(new ItemStack(Items.DIRT, 16)); }
                        int diamonds = total(device, menu, owner, Items.DIAMOND), dirt = total(device, menu, owner, Items.DIRT);
                        int bundles = total(device, menu, owner, Items.BUNDLE);
                        click(owner, menu, 0, 1, ClickType.PICKUP);
                        String where = role + (carrying ? " carrying dirt" : " with an empty cursor");
                        helper.assertTrue(total(device, menu, owner, Items.DIAMOND) == diamonds && total(device, menu, owner, Items.DIRT) == dirt
                                && total(device, menu, owner, Items.BUNDLE) == bundles,
                                where + ": a right-click on the device's bundle changed the item totals: diamonds "
                                        + diamonds + " to " + total(device, menu, owner, Items.DIAMOND) + ", dirt " + dirt + " to "
                                        + total(device, menu, owner, Items.DIRT));
                        // The device decides: an empty cursor takes the whole bundle; a carried stack swaps with it.
                        helper.assertTrue(carrying ? device.stack(0).is(Items.DIRT) && device.stack(0).getCount() == 16
                                        && count(menu.getCarried(), Items.DIAMOND) == 32 && menu.getCarried().is(Items.BUNDLE)
                                        : device.stack(0).isEmpty() && menu.getCarried().is(Items.BUNDLE)
                                        && count(menu.getCarried(), Items.DIAMOND) == 32,
                                where + ": the click was not decided by the device");
                        close(menu, owner); clear(level, at);
                    }
                }
            } finally { owner.closeContainer(); owner.getInventory().clearContent(); clear(level, at); server.getPlayerList().remove(owner); }
        }, pos);
    }

    private record Click(int slot, int button, ClickType type) { }

    /** What a scene must leave: the device, the cursor, the player inventory and what the player dropped. */
    @FunctionalInterface
    private interface Outcome {
        boolean holds(RocketItemCargoBlockEntity device, ItemStack carried, Inventory inventory, List<ItemEntity> drops);
    }

    /** One click sequence from a fresh device (stacks from slot 0), cursor and inventory (by inventory index). */
    private record Scene(String name, List<CompoundTag> device, ItemStack carried, Map<Integer, ItemStack> inventory,
                         List<Click> clicks, Outcome outcome) {
        /** Whether every click names a device slot or no slot at all, so a refusing device leaves everything as it was. */
        boolean deviceOnly() {
            return clicks.stream().allMatch(click -> click.slot() == -999 || click.slot() >= 0 && click.slot() < RocketItemCargoMenu.DEVICE_SLOTS);
        }
    }

    private static Click pickup(int slot, int button) { return new Click(slot, button, ClickType.PICKUP); }
    private static Click swap(int slot, int key) { return new Click(slot, key, ClickType.SWAP); }
    private static Click toss(int slot, int button) { return new Click(slot, button, ClickType.THROW); }
    /** A left drag: header 0 starts, 1 adds a slot and 2 ends. */
    private static Click drag(int slot, int header) {
        return new Click(slot, AbstractContainerMenu.getQuickcraftMask(header, 0), ClickType.QUICK_CRAFT);
    }
    /** The menu index of player inventory slot {@code index}: 9 to 35 follow the device's slots, then the hotbar. */
    private static int menuIndex(int index) {
        return RocketItemCargoMenu.DEVICE_SLOTS + (index < 9 ? 27 + index : index - 9);
    }
    private static boolean holds(ItemStack stack, Item item, int count) { return stack.is(item) && stack.getCount() == count; }
    private static ItemStack stack(Item item, int count) { return new ItemStack(item, count); }

    private static List<Scene> scenes() {
        var bundle = ItemStack.of(bundle(Items.DIAMOND, 32));
        return List.of(
                new Scene("right-click takes half, rounded up", List.of(stone(21)), ItemStack.EMPTY, Map.of(), List.of(pickup(0, 1)),
                        (device, carried, inventory, drops) -> holds(carried, Items.STONE, 11) && holds(device.stack(0), Items.STONE, 10)),
                new Scene("right-click places one", List.of(stone(20)), stack(Items.DIRT, 5), Map.of(), List.of(pickup(1, 1)),
                        (device, carried, inventory, drops) -> holds(carried, Items.DIRT, 4) && holds(device.stack(1), Items.DIRT, 1)),
                new Scene("right-click adds one of the same", List.of(stone(20)), stack(Items.STONE, 5), Map.of(), List.of(pickup(0, 1)),
                        (device, carried, inventory, drops) -> holds(carried, Items.STONE, 4) && holds(device.stack(0), Items.STONE, 21)),
                new Scene("left-click swaps different items", List.of(stone(20)), stack(Items.DIRT, 5), Map.of(), List.of(pickup(0, 0)),
                        (device, carried, inventory, drops) -> holds(carried, Items.STONE, 20) && holds(device.stack(0), Items.DIRT, 5)),
                new Scene("right-click on the device's bundle takes it whole", List.of(bundle(Items.DIAMOND, 32)), ItemStack.EMPTY,
                        Map.of(), List.of(pickup(0, 1)), (device, carried, inventory, drops) -> device.stack(0).isEmpty()
                                && carried.is(Items.BUNDLE) && count(carried, Items.DIAMOND) == 32),
                new Scene("right-click with a stack on the device's bundle swaps them", List.of(bundle(Items.DIAMOND, 32)),
                        stack(Items.DIRT, 16), Map.of(), List.of(pickup(0, 1)), (device, carried, inventory, drops) ->
                                holds(device.stack(0), Items.DIRT, 16) && carried.is(Items.BUNDLE) && count(carried, Items.DIAMOND) == 32),
                new Scene("right-click with a bundle on a stack swaps them", List.of(stone(20)), bundle, Map.of(), List.of(pickup(0, 1)),
                        (device, carried, inventory, drops) -> holds(carried, Items.STONE, 20) && device.stack(0).is(Items.BUNDLE)
                                && count(device.stack(0), Items.DIAMOND) == 32),
                new Scene("right-click with a bundle on an empty slot places it whole", List.of(stone(20)), bundle, Map.of(),
                        List.of(pickup(1, 1)), (device, carried, inventory, drops) -> carried.isEmpty() && device.stack(1).is(Items.BUNDLE)
                                && count(device.stack(1), Items.DIAMOND) == 32),
                new Scene("a number key takes the stack into an empty hotbar slot", List.of(stone(20)), ItemStack.EMPTY, Map.of(),
                        List.of(swap(0, 2)), (device, carried, inventory, drops) -> holds(inventory.getItem(2), Items.STONE, 20)
                                && device.stack(0).isEmpty()),
                new Scene("a number key fills an empty device slot", List.of(stone(20)), ItemStack.EMPTY, Map.of(3, stack(Items.DIRT, 7)),
                        List.of(swap(1, 3)), (device, carried, inventory, drops) -> inventory.getItem(3).isEmpty()
                                && holds(device.stack(1), Items.DIRT, 7)),
                new Scene("a number key swaps", List.of(stone(20)), ItemStack.EMPTY, Map.of(4, stack(Items.DIRT, 6)), List.of(swap(0, 4)),
                        (device, carried, inventory, drops) -> holds(inventory.getItem(4), Items.STONE, 20) && holds(device.stack(0), Items.DIRT, 6)),
                new Scene("the offhand key swaps", List.of(stone(20)), ItemStack.EMPTY, Map.of(Inventory.SLOT_OFFHAND, stack(Items.DIRT, 3)),
                        List.of(swap(0, Inventory.SLOT_OFFHAND)), (device, carried, inventory, drops) ->
                                holds(inventory.getItem(Inventory.SLOT_OFFHAND), Items.STONE, 20) && holds(device.stack(0), Items.DIRT, 3)),
                new Scene("a swap key outside the hotbar and the offhand changes nothing", List.of(stone(20)), ItemStack.EMPTY,
                        Map.of(20, stack(Items.DIRT, 2)), List.of(swap(0, 20)), (device, carried, inventory, drops) ->
                                holds(inventory.getItem(20), Items.DIRT, 2) && holds(device.stack(0), Items.STONE, 20)),
                new Scene("throw drops one item, then the rest", List.of(stone(20)), ItemStack.EMPTY, Map.of(), List.of(toss(0, 0), toss(0, 1)),
                        (device, carried, inventory, drops) -> device.stack(0).isEmpty() && dropped(drops, Items.STONE) == 20 && drops.size() == 2),
                new Scene("throw with a carried stack changes nothing", List.of(stone(20)), stack(Items.DIRT, 5), Map.of(), List.of(toss(0, 1)),
                        (device, carried, inventory, drops) -> holds(device.stack(0), Items.STONE, 20) && holds(carried, Items.DIRT, 5) && drops.isEmpty()),
                new Scene("double-click gathers from the player inventory only", List.of(stone(20)), stack(Items.STONE, 5),
                        Map.of(10, stack(Items.STONE, 10)), List.of(new Click(menuIndex(9), 0, ClickType.PICKUP_ALL)),
                        (device, carried, inventory, drops) -> holds(carried, Items.STONE, 15) && holds(device.stack(0), Items.STONE, 20)
                                && inventory.getItem(10).isEmpty()),
                new Scene("a drag skips the device slots", List.of(stone(20)), stack(Items.DIRT, 12), Map.of(),
                        List.of(drag(-999, 0), drag(1, 1), drag(2, 1), drag(menuIndex(9), 1), drag(menuIndex(10), 1), drag(-999, 2)),
                        (device, carried, inventory, drops) -> carried.isEmpty() && device.stack(1).isEmpty() && device.stack(2).isEmpty()
                                && holds(inventory.getItem(9), Items.DIRT, 6) && holds(inventory.getItem(10), Items.DIRT, 6)),
                new Scene("a drag over device slots only changes nothing", List.of(stone(20)), stack(Items.DIRT, 12), Map.of(),
                        List.of(drag(-999, 0), drag(1, 1), drag(2, 1), drag(-999, 2)),
                        (device, carried, inventory, drops) -> holds(carried, Items.DIRT, 12) && device.stack(1).isEmpty()
                                && device.stack(2).isEmpty()));
    }

    private static int[] totals(RocketItemCargoBlockEntity device, RocketItemCargoMenu menu, ServerPlayer owner, List<ItemEntity> drops) {
        Item[] counted = {Items.STONE, Items.DIRT, Items.DIAMOND, Items.BUNDLE};
        int[] totals = new int[counted.length];
        for (int index = 0; index < counted.length; index++) {
            totals[index] = total(device, menu, owner, counted[index]);
            for (ItemEntity drop : drops) { totals[index] += count(drop.getItem(), counted[index]); }
        }
        return totals;
    }

    private static List<ItemStack> snapshot(ServerPlayer owner) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) { stacks.add(owner.getInventory().getItem(slot).copy()); }
        return stacks;
    }

    private static boolean same(List<ItemStack> before, List<ItemStack> after) {
        for (int slot = 0; slot < before.size(); slot++) {
            if (!ItemStack.matches(before.get(slot), after.get(slot))) { return false; }
        }
        return before.size() == after.size();
    }

    /**
     * W120-R4: right-click, number-key and offhand swap, throw, double-click and drag on device slots, including the
     * R1 bundle cases, in both roles. Each scene conserves the device, cursor, inventory and dropped totals and leaves
     * the device's own outcome; on an escrow-held device every scene leaves the root unchanged, and one that names only
     * device slots also leaves the cursor and the inventory unchanged.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void everyClickTypeOnADeviceSlotConservesItemsAndALockedDeviceRefusesIt(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120fClicks", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                for (var role : RocketCargoRole.values()) {
                    for (boolean locked : new boolean[] {false, true}) {
                        for (var scene : scenes()) { play(helper, pos, owner, role, locked, scene); }
                    }
                }
            } finally {
                owner.closeContainer(); owner.getInventory().clearContent(); clear(level, at);
                FluidFuelLoaderFixtures.discardDrops(level, owner.blockPosition()); server.getPlayerList().remove(owner);
            }
        }, pos);
    }

    private static void play(GameTestHelper helper, BlockPos pos, ServerPlayer owner, RocketCargoRole role, boolean locked, Scene scene) {
        var level = helper.getLevel(); var at = helper.absolutePos(pos);
        var root = RocketItemCargoRoot.placed(owner.getUUID(), UUID.randomUUID(), role);
        for (int slot = 0; slot < scene.device().size(); slot++) { root = root.withSlot(slot, scene.device().get(slot)); }
        if (locked) {
            root = new RocketItemCargoRoot(root.owner(), root.device(), role, null, root.buffer(), new RocketEscrowSection(UUID.randomUUID(), 1,
                    List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, role.markerRole(), 1))));
        }
        var device = place(helper, pos, role, root.encode());
        var menu = open(helper, device, owner);
        owner.getInventory().clearContent();
        scene.inventory().forEach((index, stack) -> owner.getInventory().setItem(index, stack.copy()));
        menu.setCarried(scene.carried().copy());
        var raw = device.heldRoot().orElseThrow(); var carried = menu.getCarried().copy(); var inventory = snapshot(owner);
        int[] before = totals(device, menu, owner, List.of());
        for (var click : scene.clicks()) { click(owner, menu, click.slot(), click.button(), click.type()); }
        var drops = FluidFuelLoaderFixtures.drops(level, owner.blockPosition());
        String where = role + (locked ? " escrow-held, " : ", ") + scene.name();
        try {
            int[] after = totals(device, menu, owner, drops);
            helper.assertTrue(Arrays.equals(before, after), where + ": the stone, dirt, diamond and bundle totals changed from "
                    + Arrays.toString(before) + " to " + Arrays.toString(after));
            if (locked) {
                helper.assertTrue(device.escrowLocked() && raw.equals(device.heldRoot().orElseThrow()), where + ": the held device changed");
                helper.assertTrue(!scene.deviceOnly() || ItemStack.matches(carried, menu.getCarried()) && same(inventory, snapshot(owner))
                        && drops.isEmpty(), where + ": items moved although the device refused");
            } else {
                helper.assertTrue(scene.outcome().holds(device, menu.getCarried(), owner.getInventory(), drops), where + ": wrong outcome");
            }
        } finally { drops.forEach(ItemEntity::discard); close(menu, owner); clear(level, at); }
    }

    private RocketItemCargoMenuGameTests() { }
}
