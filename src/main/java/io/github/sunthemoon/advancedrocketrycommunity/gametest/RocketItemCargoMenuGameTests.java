package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketItemCargoFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoMenu;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
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

    private RocketItemCargoMenuGameTests() { }
}
