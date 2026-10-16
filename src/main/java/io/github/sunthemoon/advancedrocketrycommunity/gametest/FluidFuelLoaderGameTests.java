package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderCarrier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W82, leaf B3-A: the real fluid fuel loader's placement, carrier, port, persistence and protection. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderGameTests {
    private FluidFuelLoaderGameTests() {
    }

    private static FluidStack water(int amount) {
        return new FluidStack(Fluids.WATER, amount);
    }

    private static LoaderTank waterTank(int amount) {
        return LoaderTank.of(WATER, null, amount);
    }

    /** Exactly one dropped loader item, carrying exactly {@code expected}; it is removed from the world. */
    private static ItemStack oneCarrier(GameTestHelper helper, BlockPos absolute, LoaderCarrier expected, String label) {
        List<ItemEntity> drops = drops(helper.getLevel(), absolute);
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(ModItems.FLUID_FUEL_LOADER.get())
                && drops.get(0).getItem().getCount() == 1, label + ": not exactly one loader item: " + drops);
        ItemStack stack = drops.get(0).getItem().copy();
        helper.assertTrue(FluidFuelLoaderItem.carrier(stack).filter(expected::equals).isPresent(),
                label + ": the carrier differs: " + stack.getTag());
        drops.forEach(ItemEntity::discard);
        return stack;
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void oneCarrierBreakAndPlaceKeepsTheTankAndAppliesTheOwnerRule(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 2);
        BlockPos second = new BlockPos(2, 2, 13);
        BlockPos third = new BlockPos(13, 2, 2);
        BlockPos fourth = new BlockPos(13, 2, 13);
        BlockPos fifth = new BlockPos(8, 2, 8);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<ServerPlayer> joined = new ArrayList<>();
            ServerPlayer operator = null;
            try {
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82Owner", level,
                        helper.absolutePos(new BlockPos(5, 2, 5)), new ArrayList<>(), false, new ArrayList<>(), false);
                ServerPlayer other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82Other", level,
                        helper.absolutePos(new BlockPos(5, 2, 10)), new ArrayList<>(), false, new ArrayList<>(), false);
                operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82Operator", level,
                        helper.absolutePos(new BlockPos(10, 2, 10)), new ArrayList<>(), true, new ArrayList<>(), false);
                joined.add(owner);
                joined.add(other);
                joined.add(operator);
                server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
                helper.assertTrue(operator.hasPermissions(2) && !other.hasPermissions(2), "Permission fixture is wrong");

                // A plain loader item: the placer owns a fresh device with an empty tank and no binding.
                helper.assertTrue(placeBy(helper, owner, helper.absolutePos(first), new ItemStack(ModItems.FLUID_FUEL_LOADER.get()))
                        .consumesAction(), "Plain placement was refused");
                FluidFuelLoaderBlockEntity placed = (FluidFuelLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(first));
                LoaderRoot root = placed.root().orElseThrow();
                helper.assertTrue(ownerId.equals(root.owner()) && root.binding() == null && root.tank().isEmpty()
                        && !root.escrow().locked(), "Plain placement root is wrong: " + root);
                UUID firstDevice = root.device();
                helper.assertTrue(port(placed).fill(water(5_000), FluidAction.EXECUTE) == 5_000, "The owner's port refused water");

                // A survival break drops exactly one carrier with the owner and the tank, and nothing else.
                helper.assertTrue(owner.gameMode.destroyBlock(helper.absolutePos(first)), "Survival break was refused");
                ItemStack carried = oneCarrier(helper, helper.absolutePos(first), new LoaderCarrier(ownerId, waterTank(5_000)),
                        "owner break");

                // Placing it again restores the owner and the tank on a fresh identity with no binding.
                helper.assertTrue(placeBy(helper, owner, helper.absolutePos(first), carried.copy()).consumesAction(),
                        "Carrier placement was refused");
                LoaderRoot again = ((FluidFuelLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(first))).root().orElseThrow();
                helper.assertTrue(ownerId.equals(again.owner()) && again.tank().equals(waterTank(5_000))
                        && again.binding() == null && !again.device().equals(firstDevice), "Re-placement root is wrong: " + again);

                // A non-operator placing another player's carrier places an unclaimed device that keeps the tank.
                helper.assertTrue(placeBy(helper, other, helper.absolutePos(second), carried.copy()).consumesAction(),
                        "Foreign carrier placement was refused");
                FluidFuelLoaderBlockEntity foreign = (FluidFuelLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(second));
                helper.assertTrue(!foreign.claimed() && foreign.root().orElseThrow().tank().equals(waterTank(5_000)),
                        "A non-operator's foreign carrier kept its owner");
                IFluidHandler unclaimed = port(foreign);
                helper.assertTrue(unclaimed.fill(water(1_000), FluidAction.EXECUTE) == 0
                        && unclaimed.drain(1_000, FluidAction.EXECUTE).isEmpty()
                        && foreign.root().orElseThrow().tank().equals(waterTank(5_000)), "An unclaimed device moved Fluid");
                helper.assertTrue(foreign.createMenu(1, owner.getInventory(), owner) == null
                        && foreign.createMenu(2, other.getInventory(), other) == null
                        && foreign.createMenu(3, operator.getInventory(), operator) == null
                        && foreign.displayStatus() == FluidFuelLoaderStatus.NOT_OWNER, "An unclaimed device opened its menu");

                // Breaking it drops a carrier with no owner; whoever places that owns the device.
                helper.assertTrue(other.gameMode.destroyBlock(helper.absolutePos(second)), "Unclaimed break was refused");
                ItemStack ownerless = oneCarrier(helper, helper.absolutePos(second), new LoaderCarrier(null, waterTank(5_000)),
                        "unclaimed break");
                helper.assertTrue(placeBy(helper, other, helper.absolutePos(third), ownerless).consumesAction(),
                        "Ownerless carrier placement was refused");
                helper.assertTrue(((FluidFuelLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(third))).owner()
                        .filter(other.getUUID()::equals).isPresent(), "The ownerless carrier was not claimed by its placer");

                // An operator keeps the carried owner, so it can place a device for another player.
                helper.assertTrue(placeBy(helper, operator, helper.absolutePos(fourth), carried.copy()).consumesAction(),
                        "Operator carrier placement was refused");
                FluidFuelLoaderBlockEntity forOwner = (FluidFuelLoaderBlockEntity) level.getBlockEntity(helper.absolutePos(fourth));
                helper.assertTrue(forOwner.owner().filter(ownerId::equals).isPresent(), "The operator's placement changed the owner");

                // An explosion of an unprotected device drops exactly one carrier too.
                BlockPos blown = helper.absolutePos(fourth);
                Explosion explosion = new Explosion(level, null, blown.getX() + 0.5D, blown.getY() + 0.5D, blown.getZ() + 0.5D,
                        1.0F, false, Explosion.BlockInteraction.DESTROY);
                explosion.getToBlow().add(blown);
                MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                helper.assertTrue(explosion.getToBlow().contains(blown), "An unprotected loader left the explosion");
                explosion.finalizeExplosion(false);
                helper.assertTrue(level.getBlockState(blown).isAir(), "The explosion kept the loader");
                oneCarrier(helper, blown, new LoaderCarrier(ownerId, waterTank(5_000)), "explosion");

                // A bare block (as /setblock leaves it) is unclaimed and breaks into an empty, ownerless carrier.
                FluidFuelLoaderBlockEntity bare = place(helper, fifth);
                helper.assertTrue(bare.root().isEmpty() && !bare.claimed(), "A bare loader has a root");
                helper.assertTrue(owner.gameMode.destroyBlock(helper.absolutePos(fifth)), "Bare loader break was refused");
                oneCarrier(helper, helper.absolutePos(fifth), new LoaderCarrier(null, LoaderTank.EMPTY), "bare break");
            } finally {
                for (BlockPos relative : List.of(first, second, third, fourth, fifth)) {
                    clear(level, helper.absolutePos(relative));
                    discardDrops(level, helper.absolutePos(relative));
                }
                if (operator != null) {
                    server.getPlayerList().getOps().remove(operator.getGameProfile());
                }
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, first, second, third, fourth, fifth);
    }

    /** As {@code ForceFieldGameTests}: the root is operator-only, and the item refuses any {@code BlockEntityTag}. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void nonOperatorItemNbtCannotRestoreALoaderRoot(GameTestHelper helper) {
        BlockPos byBuilder = new BlockPos(3, 2, 10);
        BlockPos byOperator = new BlockPos(10, 2, 10);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            List<ServerPlayer> joined = new ArrayList<>();
            ServerPlayer operator = null;
            try {
                FluidFuelLoaderBlockEntity probe = new FluidFuelLoaderBlockEntity(helper.absolutePos(byBuilder),
                        ModBlocks.FLUID_FUEL_LOADER.get().defaultBlockState());
                helper.assertTrue(probe.onlyOpCanSetNbt(), "The loader accepts non-operator item NBT");
                ItemStack forged = new ItemStack(ModItems.FLUID_FUEL_LOADER.get());
                forged.getOrCreateTag().put(BlockItem.BLOCK_ENTITY_TAG, outer(owned(UUID.randomUUID(), waterTank(9_000))));
                helper.assertTrue(!FluidFuelLoaderItem.placeable(forged), "A BlockEntityTag item is placeable");
                ServerPlayer builder = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82Builder", level,
                        helper.absolutePos(byBuilder).north(3), new ArrayList<>());
                joined.add(builder);
                builder.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                helper.assertTrue(builder.getAbilities().instabuild && !builder.canUseGameMasterBlocks(),
                        "The builder is not a creative non-operator");
                helper.assertTrue(!placeBy(helper, builder, helper.absolutePos(byBuilder), forged.copy()).consumesAction()
                        && level.getBlockState(helper.absolutePos(byBuilder)).isAir(), "A non-operator placed item NBT");
                operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82OperatorNbt", level,
                        helper.absolutePos(byOperator).north(3), new ArrayList<>(), true);
                joined.add(operator);
                server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
                operator.gameMode.changeGameModeForPlayer(GameType.CREATIVE);
                helper.assertTrue(!placeBy(helper, operator, helper.absolutePos(byOperator), forged.copy()).consumesAction()
                        && level.getBlockState(helper.absolutePos(byOperator)).isAir(), "The item accepted a BlockEntityTag");
            } finally {
                clear(level, helper.absolutePos(byBuilder));
                clear(level, helper.absolutePos(byOperator));
                if (operator != null) {
                    server.getPlayerList().getOps().remove(operator.getGameProfile());
                }
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, byBuilder, byOperator);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void thePortSimulatesWithoutEffectAndRefusesStaleReentrantAndUncarriableCalls(GameTestHelper helper) {
        BlockPos main = new BlockPos(3, 2, 3);
        BlockPos bare = new BlockPos(3, 2, 8);
        BlockPos ownerless = new BlockPos(8, 2, 3);
        BlockPos stored = new BlockPos(8, 2, 8);
        ServerLevel level = helper.getLevel();
        UUID ownerId = UUID.randomUUID();
        try {
            FluidFuelLoaderBlockEntity loader = place(helper, main, owned(ownerId, LoaderTank.EMPTY));
            IFluidHandler handler = port(loader);
            for (Direction side : Direction.values()) {
                helper.assertTrue(loader.getCapability(ForgeCapabilities.FLUID_HANDLER, side).resolve().orElse(null) == handler,
                        "The port differs on side " + side);
            }
            helper.assertTrue(handler.getTanks() == 1 && handler.getTankCapacity(0) == LoaderTank.CAPACITY,
                    "The port shape is wrong");

            // Simulation has no side effects.
            Tag before = loader.heldRoot().orElseThrow();
            helper.assertTrue(handler.fill(water(1_000), FluidAction.SIMULATE) == 1_000
                    && before.equals(loader.heldRoot().orElseThrow()), "Simulated fill changed the root");
            helper.assertTrue(handler.fill(water(1_000), FluidAction.EXECUTE) == 1_000 && loader.tank().amount() == 1_000,
                    "Fill failed");
            Tag filled = loader.heldRoot().orElseThrow();
            helper.assertTrue(handler.drain(400, FluidAction.SIMULATE).getAmount() == 400
                    && handler.drain(water(400), FluidAction.SIMULATE).getAmount() == 400
                    && filled.equals(loader.heldRoot().orElseThrow()), "Simulated drain changed the root");
            helper.assertTrue(handler.fill(water(20_000), FluidAction.EXECUTE) == 15_000 && loader.tank().amount() == 16_000
                    && handler.drain(15_000, FluidAction.EXECUTE).getAmount() == 15_000, "Capacity is not 16,000 mB");
            CompoundTag otherTag = new CompoundTag();
            otherTag.putString("batch", "other");
            helper.assertTrue(handler.fill(new FluidStack(Fluids.LAVA, 1_000), FluidAction.EXECUTE) == 0
                    && handler.fill(new FluidStack(Fluids.WATER, 1_000, otherTag), FluidAction.EXECUTE) == 0
                    && !handler.isFluidValid(0, new FluidStack(Fluids.LAVA, 1)) && loader.tank().amount() == 1_000,
                    "Another Fluid or native tag was mixed in");

            // A call made while another is in progress returns zero or empty.
            int[] innerFill = {-1};
            FluidStack[] innerDrain = {null};
            FluidStack reentrant = new FluidStack(Fluids.WATER, 1_000) {
                @Override
                public FluidStack copy() {
                    if (innerFill[0] < 0) {
                        innerFill[0] = handler.fill(water(1_000), FluidAction.EXECUTE);
                        innerDrain[0] = handler.drain(1_000, FluidAction.EXECUTE);
                    }
                    return super.copy();
                }
            };
            helper.assertTrue(handler.fill(reentrant, FluidAction.EXECUTE) == 1_000 && innerFill[0] == 0
                    && innerDrain[0].isEmpty() && loader.tank().amount() == 2_000, "A reentrant call moved Fluid");

            // A call off the server thread returns zero or empty.
            helper.assertTrue(CompletableFuture.supplyAsync(() -> handler.fill(water(1_000), FluidAction.EXECUTE)).join() == 0
                    && CompletableFuture.supplyAsync(() -> handler.drain(1_000, FluidAction.EXECUTE)).join().isEmpty()
                    && loader.tank().amount() == 2_000, "An off-thread call moved Fluid");

            // Lifecycle events invalidate handles: stale ports refuse, fresh ones work.
            loader.invalidateCaps();
            helper.assertTrue(handler.fill(water(1_000), FluidAction.EXECUTE) == 0
                    && handler.drain(1_000, FluidAction.EXECUTE).isEmpty() && handler.getFluidInTank(0).isEmpty(),
                    "A handle survived invalidation");
            loader.reviveCaps();
            IFluidHandler fresh = port(loader);
            helper.assertTrue(fresh != handler && fresh.getFluidInTank(0).getAmount() == 2_000, "The revived port is wrong");
            loader.load(outer(owned(ownerId, waterTank(3_000))));
            helper.assertTrue(fresh.fill(water(1_000), FluidAction.EXECUTE) == 0
                    && port(loader).getFluidInTank(0).getAmount() == 3_000, "A handle survived a data replacement");

            // Unclaimed devices refuse fill and drain, with or without a root.
            IFluidHandler none = port(place(helper, bare));
            IFluidHandler unowned = port(place(helper, ownerless, LoaderRoot.placed(null, UUID.randomUUID(), waterTank(2_000)).encode()));
            helper.assertTrue(none.fill(water(1_000), FluidAction.EXECUTE) == 0
                    && unowned.fill(water(1_000), FluidAction.EXECUTE) == 0
                    && unowned.drain(1_000, FluidAction.EXECUTE).isEmpty(), "An unclaimed device moved Fluid");

            // Root's W82 decision: intake refuses a Fluid that no escrow operation could carry; stored data still loads.
            CompoundTag wide = new CompoundTag();
            ListTag elements = new ListTag();
            for (int index = 0; index < 400; index++) {
                elements.add(ByteTag.valueOf((byte) index));
            }
            wide.put("elements", elements);
            CompoundTag deep = new CompoundTag();
            CompoundTag cursor = deep;
            for (int depth = 0; depth < 12; depth++) {
                CompoundTag child = new CompoundTag();
                cursor.put("c", child);
                cursor = child;
            }
            FluidFuelLoaderBlockEntity empty = place(helper, bare, owned(ownerId, LoaderTank.EMPTY));
            for (CompoundTag tag : List.of(wide, deep)) {
                FluidStack offered = new FluidStack(Fluids.WATER, 1_000, tag);
                LoaderTank tank = LoaderTank.of(offered);
                helper.assertTrue(!tank.escrowCarriable(), "The fixture tag fits an escrow payload");
                helper.assertTrue(port(empty).fill(offered, FluidAction.EXECUTE) == 0 && !port(empty).isFluidValid(0, offered)
                        && empty.tank().isEmpty(), "Intake admitted a Fluid no escrow operation could carry");
            }
            FluidFuelLoaderBlockEntity keeps = place(helper, stored, owned(ownerId, LoaderTank.of(new FluidStack(Fluids.WATER, 1_000, wide))));
            helper.assertTrue(keeps.dataStatus() == LoaderRoot.Status.VALID && keeps.tank().amount() == 1_000
                    && port(keeps).fill(new FluidStack(Fluids.WATER, 1_000, wide), FluidAction.EXECUTE) == 0
                    && port(keeps).drain(250, FluidAction.EXECUTE).getAmount() == 250, "Stored data no longer loads or drains");
        } finally {
            for (BlockPos relative : List.of(main, bare, ownerless, stored)) {
                clear(level, helper.absolutePos(relative));
            }
        }
        helper.succeed();
    }

    /** Unload and reload keep the root byte for byte; the reloaded device waits one full interval before its attempt. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void unloadAndReloadKeepTheRootAndWaitAFullInterval(GameTestHelper helper) {
        BlockPos relative = new BlockPos(4, 2, 4);
        UUID ownerId = UUID.randomUUID();
        FluidFuelLoaderBlockEntity[] reloaded = {null};
        helper.startSequence().thenExecute(() -> {
            ServerLevel level = helper.getLevel();
            BlockPos at = helper.absolutePos(relative);
            var binding = new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding(
                    UUID.randomUUID(), level.dimension().location(), UUID.randomUUID(), at.east(3), null);
            LoaderRoot root = new LoaderRoot(ownerId, UUID.randomUUID(), binding, waterTank(6_000),
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection.neutral());
            FluidFuelLoaderBlockEntity loader = place(helper, relative, root.encode());
            IFluidHandler before = port(loader);
            CompoundTag saved = loader.saveWithFullMetadata();
            level.removeBlockEntity(at);
            helper.assertTrue(before.fill(water(1_000), FluidAction.EXECUTE) == 0 && loader.isRemoved(),
                    "The unloaded device's handle still works");
            BlockEntity restored = BlockEntity.loadStatic(at, level.getBlockState(at), saved.copy());
            helper.assertTrue(restored instanceof FluidFuelLoaderBlockEntity, "The saved loader did not reload");
            level.setBlockEntity(restored);
            FluidFuelLoaderBlockEntity again = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
            helper.assertTrue(again == restored && again.root().filter(root::equals).isPresent()
                    && saved.get(LoaderRoot.KEY).equals(again.saveWithoutMetadata().get(LoaderRoot.KEY))
                    && !again.duplicate() && again.lastAttempt() == null, "The reload changed the root");
            helper.assertTrue(port(again).fill(water(1_000), FluidAction.SIMULATE) == 1_000, "The reloaded port refuses");
            reloaded[0] = again;
        }).thenIdle(15).thenExecute(() -> helper.assertTrue(reloaded[0].lastAttempt() == null,
                "The reloaded device attempted before a full interval"))
                .thenIdle(10).thenExecute(() -> helper.assertTrue(reloaded[0].lastAttempt() == FluidFuelLoaderStatus.STALE_BINDING,
                        "The reloaded device made no attempt, or the wrong one: " + reloaded[0].lastAttempt()))
                .thenExecute(() -> clear(helper.getLevel(), helper.absolutePos(relative)))
                .thenSucceed();
    }

    /** R-038 for the loader: unsupported and escrow-locked roots refuse player and explosion removal and drop nothing. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void protectedLoadersRefuseRemovalDropNothingAndDetectCommandReplacement(GameTestHelper helper) {
        List<BlockPos> columns = List.of(new BlockPos(2, 2, 4), new BlockPos(5, 2, 4), new BlockPos(8, 2, 4),
                new BlockPos(11, 2, 4), new BlockPos(14, 2, 4));
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<ServerPlayer> joined = new ArrayList<>();
            try {
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82Guarded", level,
                        helper.absolutePos(new BlockPos(8, 2, 8)), new ArrayList<>(), false, new ArrayList<>(), false);
                joined.add(owner);
                CompoundTag future = owned(ownerId, waterTank(1_000));
                future.putInt("schema_version", 2);
                CompoundTag invalid = owned(ownerId, waterTank(1_000));
                invalid.putInt("unknown", 1);
                CompoundTag oversized = owned(ownerId, waterTank(1_000));
                oversized.putByteArray("payload", new byte[40 * 1_024]);
                CompoundTag unregistered = owned(ownerId, waterTank(1_000));
                unregistered.getCompound("tank").putString("FluidName", "advancedrocketrycommunity:no_such_fluid");
                List<Tag> roots = List.of(future, invalid, oversized, unregistered, locked(ownerId, waterTank(2_000)));
                List<LoaderRoot.Status> statuses = List.of(LoaderRoot.Status.FUTURE, LoaderRoot.Status.INVALID,
                        LoaderRoot.Status.INVALID, LoaderRoot.Status.UNREGISTERED_FLUID, LoaderRoot.Status.VALID);
                for (int index = 0; index < roots.size(); index++) {
                    BlockPos at = helper.absolutePos(columns.get(index));
                    Tag original = roots.get(index).copy();
                    FluidFuelLoaderBlockEntity loader = place(helper, columns.get(index), roots.get(index));
                    String label = "root " + index + " (" + statuses.get(index) + ")";
                    boolean escrow = statuses.get(index) == LoaderRoot.Status.VALID;
                    helper.assertTrue(loader.dataStatus() == statuses.get(index) && loader.protectedFromRemoval()
                            && (escrow ? loader.escrowLocked() : loader.unsupportedRoot()), label + " is not protected");
                    helper.assertTrue(original.equals(loader.saveWithoutMetadata().get(LoaderRoot.KEY)),
                            label + " was not kept unchanged");
                    helper.assertTrue(escrow ? port(loader).drain(1_000, FluidAction.EXECUTE).isEmpty()
                                    && port(loader).fill(water(1_000), FluidAction.EXECUTE) == 0
                                    && loader.displayStatus() == FluidFuelLoaderStatus.ESCROW_PENDING
                                    : !loader.getCapability(ForgeCapabilities.FLUID_HANDLER, null).isPresent()
                                    && loader.createMenu(1, owner.getInventory(), owner) == null,
                            label + " exposed its resources or menu");
                    // A live /data replacement keeps the protected root.
                    loader.load(outer(owned(ownerId, LoaderTank.EMPTY)));
                    helper.assertTrue(original.equals(loader.saveWithoutMetadata().get(LoaderRoot.KEY)),
                            label + " was replaced by live NBT");
                    BlockEvent.BreakEvent breaking = new BlockEvent.BreakEvent(level, at, loader.getBlockState(), owner);
                    MinecraftForge.EVENT_BUS.post(breaking);
                    helper.assertTrue(breaking.isCanceled(), label + " allowed player break");
                    helper.assertTrue(!owner.gameMode.destroyBlock(at) && level.getBlockEntity(at) == loader,
                            label + " was broken by a player");
                    Explosion explosion = new Explosion(level, null, at.getX() + 0.5D, at.getY() + 0.5D, at.getZ() + 0.5D,
                            1.0F, false, Explosion.BlockInteraction.DESTROY);
                    explosion.getToBlow().add(at);
                    MinecraftForge.EVENT_BUS.post(new ExplosionEvent.Detonate(level, explosion, new ArrayList<>()));
                    helper.assertTrue(explosion.getToBlow().isEmpty(), label + " stayed in the explosion");
                    loader.getBlockState().onBlockExploded(level, at, explosion);
                    helper.assertTrue(level.getBlockEntity(at) == loader
                            && Block.getDrops(loader.getBlockState(), level, at, loader).isEmpty()
                            && drops(level, at).isEmpty() && original.equals(loader.saveWithoutMetadata().get(LoaderRoot.KEY)),
                            label + " dropped or lost its root");
                }
                // Command replacement is a detectable residual, never salvage: one audit line and no drops.
                for (int index : new int[] {3, 4}) {
                    BlockPos at = helper.absolutePos(columns.get(index));
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " minecraft:air");
                    helper.assertTrue(level.getBlockState(at).isAir() && level.getBlockEntity(at) == null
                            && drops(level, at).isEmpty(), "Command replacement of root " + index + " salvaged or failed");
                }
            } finally {
                for (BlockPos relative : columns) {
                    clear(level, helper.absolutePos(relative));
                    discardDrops(level, helper.absolutePos(relative));
                }
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, columns.toArray(BlockPos[]::new));
    }

    /** The loader takes only an empty pressurized tank, and the linker has its own recipe. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void filledOrUnreadableTanksNeverCraftTheLoader(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var menu = new net.minecraft.world.inventory.AbstractContainerMenu(null, -1) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(menu, 3, 3);
        ItemStack emptyRoot = new ItemStack(ModItems.PRESSURIZED_TANK.get());
        emptyRoot.getOrCreateTag().put(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT,
                io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.encode(FluidStack.EMPTY));
        ItemStack filled = new ItemStack(ModItems.PRESSURIZED_TANK.get());
        filled.getOrCreateTag().put(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT,
                io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.encode(water(1_000)));
        ItemStack unreadable = new ItemStack(ModItems.PRESSURIZED_TANK.get());
        CompoundTag bad = new CompoundTag();
        bad.putInt("schema", 9);
        unreadable.getOrCreateTag().put(io.github.sunthemoon.advancedrocketrycommunity.machine.tank.TankSave.ROOT, bad);
        List<ItemStack> tanks = List.of(new ItemStack(ModItems.PRESSURIZED_TANK.get()), emptyRoot, filled, unreadable);
        List<Boolean> crafts = List.of(true, true, false, false);
        for (int index = 0; index < tanks.size(); index++) {
            grid.clearContent();
            grid.setItem(0, tanks.get(index).copy());
            grid.setItem(4, new ItemStack(ModItems.LIQUID_IO_BOARD.get()));
            grid.setItem(5, new ItemStack(ModItems.CONTROL_BOARD.get()));
            grid.setItem(8, new ItemStack(ModItems.MACHINE_CASING.get()));
            var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level);
            Optional<ItemStack> result = recipe.map(found -> found.assemble(grid, level.registryAccess()));
            helper.assertTrue(crafts.get(index) == result.filter(stack -> stack.is(ModItems.FLUID_FUEL_LOADER.get())
                    && stack.getCount() == 1 && !stack.hasTag()).isPresent()
                    && (crafts.get(index) || recipe.isEmpty()), "Tank " + index + " crafted wrongly: " + result);
            if (crafts.get(index)) {
                helper.assertTrue(ModIdentity.id("fluid_fuel_loader").equals(recipe.orElseThrow().getId()),
                        "Another recipe made the loader");
            }
        }
        grid.clearContent();
        grid.setItem(0, new ItemStack(ModItems.TRACKING_CIRCUIT.get()));
        grid.setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
        grid.setItem(2, new ItemStack(net.minecraft.world.item.Items.REDSTONE));
        helper.assertTrue(level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, grid, level)
                .filter(found -> found.getId().equals(ModIdentity.id("linker"))
                        && found.assemble(grid, level.registryAccess()).is(ModItems.LINKER.get())).isPresent(),
                "The linker recipe is missing");
        for (String id : List.of("recipes/misc/fluid_fuel_loader", "recipes/misc/linker")) {
            helper.assertTrue(level.getServer().getAdvancements().getAdvancement(ModIdentity.id(id)) != null,
                    "Missing recipe advancement " + id);
        }
        helper.succeed();
    }
}
