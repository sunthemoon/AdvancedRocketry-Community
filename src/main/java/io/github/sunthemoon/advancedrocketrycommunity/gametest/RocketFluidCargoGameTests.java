package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFluidCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.clear;
import static net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The two native device roles, ordinary local exchange, placement ownership and recipe boundaries. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFluidCargoGameTests {
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void allSidesSimulationReentryThreadAndLifecycleAreBounded(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        var spare = new BlockPos(8, 2, 4);
        var level = helper.getLevel();
        try {
            for (var role : RocketCargoRole.values()) {
                var device = place(helper, pos, role, owned(UUID.randomUUID(), role, 2_000));
                var handler = port(device);
                for (Direction side : Direction.values()) {
                    helper.assertTrue(device.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElseThrow() == handler,
                            role + " does not expose one shared all-side port");
                }
                var raw = device.heldRoot().orElseThrow();
                helper.assertTrue(handler.fill(water(1_000), SIMULATE) == (role == RocketCargoRole.LOAD ? 1_000 : 0)
                        && handler.drain(1_000, SIMULATE).getAmount() == (role == RocketCargoRole.UNLOAD ? 1_000 : 0)
                        && raw.equals(device.heldRoot().orElseThrow()), role + " simulation/direction mutated state");
                helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1_000), EXECUTE) == 0
                        && handler.drain(new FluidStack(Fluids.LAVA, 1_000), EXECUTE).isEmpty(), "Mixed Fluid");
                CompoundTag tagged = new CompoundTag(); tagged.putInt("batch", 1);
                helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1_000, tagged), EXECUTE) == 0
                        && handler.drain(new FluidStack(Fluids.WATER, 1_000, tagged), EXECUTE).isEmpty(), "Mixed native tags");
                helper.assertTrue(CompletableFuture.supplyAsync(() -> handler.fill(water(1_000), EXECUTE)).join() == 0
                        && CompletableFuture.supplyAsync(() -> handler.drain(1_000, EXECUTE)).join().isEmpty(), "Off-thread mutation");
                if (role == RocketCargoRole.LOAD) {
                    int[] inner = {-1};
                    // Intake reads native tags under the lock, without invoking an unbounded FluidStack.copy().
                    var offered = new FluidStack(Fluids.WATER, 1_000) {
                        @Override public CompoundTag getTag() {
                            if (inner[0] < 0) { inner[0] = handler.fill(water(1_000), EXECUTE); }
                            return super.getTag();
                        }
                    };
                    helper.assertTrue(handler.fill(offered, EXECUTE) == 1_000 && inner[0] == 0
                            && device.tank().amount() == 3_000, "Reentrant fill moved water");
                    helper.assertTrue(handler.fill(water(20_000), EXECUTE) == 13_000 && handler.fill(water(1), EXECUTE) == 0
                            && handler.drain(1, EXECUTE).isEmpty(), "LOAD capacity/direction failed");
                } else {
                    // Even the requested amount is read under the drain lock.
                    int[] inner = {-1};
                    var requested = new FluidStack(Fluids.WATER, 1_000) {
                        @Override public int getAmount() {
                            if (inner[0] < 0) { inner[0] = handler.drain(1_000, EXECUTE).getAmount(); }
                            return super.getAmount();
                        }
                    };
                    helper.assertTrue(handler.drain(requested, EXECUTE).getAmount() == 1_000 && inner[0] == 0
                            && device.tank().amount() == 1_000, "Reentrant drain moved water");
                    helper.assertTrue(handler.fill(water(1), EXECUTE) == 0 && handler.drain(20_000, EXECUTE).getAmount() == 1_000,
                            "UNLOAD capacity/direction failed");
                }
                device.invalidateCaps(); device.reviveCaps();
                helper.assertTrue(handler.fill(water(1), EXECUTE) == 0 && handler.drain(1, EXECUTE).isEmpty()
                        && handler.getFluidInTank(0).isEmpty() && port(device) != handler, "Stale handle survived invalidation");
                var fresh = port(device);
                var saved = device.saveWithFullMetadata();
                device.onChunkUnloaded(); level.removeBlockEntity(helper.absolutePos(pos));
                helper.assertTrue(fresh.fill(water(1), EXECUTE) == 0 && fresh.drain(1, EXECUTE).isEmpty(), "Unloaded handle works");
                var restored = BlockEntity.loadStatic(helper.absolutePos(pos), device.getBlockState(), saved.copy());
                helper.assertTrue(restored instanceof RocketFluidCargoBlockEntity, "Wrong reloaded BE type");
                level.setBlockEntity(restored);
                helper.assertTrue(saved.get(RocketFluidCargoRoot.KEY).equals(restored.saveWithoutMetadata().get(RocketFluidCargoRoot.KEY)),
                        "Unload/reload changed the root");
                clear(level, helper.absolutePos(pos));
                for (CompoundTag unclaimed : new CompoundTag[] {null, owned(null, role, 2_000)}) {
                    var bare = place(helper, spare, role, unclaimed);
                    helper.assertTrue(port(bare).fill(water(1), EXECUTE) == 0 && port(bare).drain(1, EXECUTE).isEmpty(),
                            "Unclaimed device moved water");
                    clear(level, helper.absolutePos(spare));
                }
            }
        } finally { clear(level, helper.absolutePos(pos)); clear(level, helper.absolutePos(spare)); }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void carriersApplyOwnerRulesAndContainersExchangeInBothDirections(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer();
            List<ServerPlayer> players = new ArrayList<>();
            try {
                var at = helper.absolutePos(pos);
                var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Owner", level, at.east(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Other", level, at.west(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var op = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Op", level, at.north(2),
                        new ArrayList<>(), true, new ArrayList<>(), false);
                players.addAll(List.of(owner, other, op));
                server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 4, false));
                for (var role : RocketCargoRole.values()) {
                    for (ServerPlayer placer : players) {
                        helper.assertTrue(FluidFuelLoaderFixtures.placeBy(helper, placer, at, carrier(role, owner.getUUID(), 2_000))
                                .consumesAction(), "Carrier placement refused");
                        var device = (RocketFluidCargoBlockEntity) level.getBlockEntity(at);
                        var root = device.root().orElseThrow();
                        helper.assertTrue(root.role() == role && root.tank().amount() == 2_000 && root.binding() == null
                                && (placer == other ? root.owner() == null : owner.getUUID().equals(root.owner())), "Wrong carrier owner rule");
                        var prior = root.device();
                        // Both roles allow the owner's bucket exchange in both directions, regardless of capability direction.
                        if (placer == owner) {
                            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
                            helper.assertTrue(device.exchange(owner, InteractionHand.MAIN_HAND) && device.tank().amount() == 3_000
                                    && owner.getMainHandItem().is(Items.BUCKET), "Owner could not fill with bucket");
                            helper.assertTrue(device.exchange(owner, InteractionHand.MAIN_HAND) && device.tank().amount() == 2_000
                                    && owner.getMainHandItem().is(Items.WATER_BUCKET), "Owner could not drain with bucket");
                            other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
                            helper.assertTrue(!device.exchange(other, InteractionHand.MAIN_HAND) && device.tank().amount() == 2_000,
                                    "Foreign exchange accepted");
                        }
                        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        helper.assertTrue(owner.gameMode.destroyBlock(at), "Ordinary break refused");
                        var drops = FluidFuelLoaderFixtures.drops(level, at);
                        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(item(role).getItem())
                                && drops.get(0).getItem().getCount() == 1, "Not exactly one carrier");
                        var carried = drops.get(0).getItem().copy(); drops.forEach(net.minecraft.world.entity.item.ItemEntity::discard);
                        helper.assertTrue(FluidFuelLoaderItem.carrier(carried).filter(new LoaderCarrier(root.owner(), tank(2_000))::equals).isPresent(),
                                "Dropped carrier changed contents or owner");
                        helper.assertTrue(FluidFuelLoaderFixtures.placeBy(helper, owner, at, carried).consumesAction(), "Carrier re-placement refused");
                        var again = ((RocketFluidCargoBlockEntity) level.getBlockEntity(at)).root().orElseThrow();
                        helper.assertTrue(owner.getUUID().equals(again.owner()) && !prior.equals(again.device())
                                && again.binding() == null && !again.marked(), "Carrier restored an identity/binding or lost ownerless claim");
                        clear(level, at);
                    }
                    helper.assertTrue(FluidFuelLoaderFixtures.placeBy(helper, owner, at, item(role)).consumesAction(), "Plain placement refused");
                    var plain = (RocketFluidCargoBlockEntity) level.getBlockEntity(at);
                    helper.assertTrue(plain.owner().filter(owner.getUUID()::equals).isPresent() && plain.tank().isEmpty(), "Plain item root wrong");
                    clear(level, at);
                }
            } finally {
                clear(level, helper.absolutePos(pos)); FluidFuelLoaderFixtures.discardDrops(level, helper.absolutePos(pos));
                players.forEach(p -> { server.getPlayerList().getOps().remove(p.getGameProfile()); server.getPlayerList().remove(p); });
            }
        }, pos);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void nonOperatorCannotInjectBlockEntityNbt(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer();
            var at = helper.absolutePos(pos);
            var player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Nbt", level, at.east(2),
                    new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                player.setGameMode(GameType.CREATIVE);
                helper.assertTrue(!player.hasPermissions(2), "NBT fixture is an operator");
                for (var role : RocketCargoRole.values()) {
                    var device = place(helper, pos, role, owned(player.getUUID(), role, 2_000));
                    var before = device.heldRoot().orElseThrow();
                    var forged = item(role); forged.getOrCreateTag().put("BlockEntityTag", outer(owned(UUID.randomUUID(), role, 16_000)));
                    helper.assertTrue(device.onlyOpCanSetNbt()
                            && !BlockItem.updateCustomBlockEntityTag(level, player, at, forged)
                            && before.equals(device.heldRoot().orElseThrow()) && !FluidFuelLoaderItem.placeable(forged), "NBT injection accepted");
                    clear(level, at);
                    helper.assertTrue(!FluidFuelLoaderFixtures.placeBy(helper, player, at, forged).consumesAction()
                            && level.getBlockEntity(at) == null, "Forged block item placed");
                }
            } finally { clear(level, at); server.getPlayerList().remove(player); }
        }, pos);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void shapedRecipesAcceptOnlyEmptyTanks(GameTestHelper helper) {
        var level = helper.getLevel();
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        var filled = new ItemStack(ModItems.PRESSURIZED_TANK.get());
        filled.getOrCreateTag().put(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT,
                io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.encode(water(1_000)));
        var bad = filled.copy(); bad.getOrCreateTag().getCompound(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT)
                .putInt("schema", 999);
        var empty = filled.copy(); empty.getOrCreateTag().put(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT,
                io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.encode(FluidStack.EMPTY));
        var candidates = List.of(new ItemStack(ModItems.PRESSURIZED_TANK.get()), empty, filled, bad);
        for (var role : RocketCargoRole.values()) {
            for (int i = 0; i < candidates.size(); i++) {
                grid.clearContent(); int row = role == RocketCargoRole.LOAD ? 3 : 0;
                grid.setItem(row, new ItemStack(ModItems.LIQUID_IO_BOARD.get()));
                grid.setItem(row + 1, new ItemStack(ModItems.MACHINE_CASING.get()));
                grid.setItem(row + 2, candidates.get(i).copy());
                grid.setItem(role == RocketCargoRole.LOAD ? 1 : 4, new ItemStack(Items.HOPPER));
                var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
                helper.assertTrue(i < 2 ? recipe.filter(r -> r.getId().equals(ModIdentity.id(role.id()))
                        && r.assemble(grid, level.registryAccess()).is(item(role).getItem())).isPresent() : recipe.isEmpty(),
                        role + " tank case " + i + " crafted incorrectly");
            }
            helper.assertTrue(level.getServer().getAdvancements().getAdvancement(ModIdentity.id("recipes/misc/" + role.id())) != null,
                    "Missing unlock advancement");
        }
        helper.succeed();
    }
    private RocketFluidCargoGameTests() { }
}
