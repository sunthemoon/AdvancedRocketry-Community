package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.*;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W82, leaf B3-A: the linker's rocket binding, the owner's menu and duplicate device identities, with real rockets. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FluidFuelLoaderLinkGameTests {
    private static final ResourceLocation ROCKET_FUEL = ModIdentity.id("rocket_fuel");

    private FluidFuelLoaderLinkGameTests() {
    }

    private static UseOnContext useOn(ServerPlayer player, BlockPos absolute) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }

    private static InteractionResult select(ServerPlayer player, BlockPos absolute) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return player.getItemInHand(InteractionHand.MAIN_HAND).useOn(useOn(player, absolute));
    }

    private static InteractionResult bind(ServerPlayer player, RocketEntity rocket) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return LinkerItem.bindRocket(player, InteractionHand.MAIN_HAND, rocket);
    }

    private static boolean said(List<String> tokens, String key) {
        return tokens.stream().anyMatch(token -> token.equals(ConnectedTestPlayers.ACTION_BAR + key));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void theLinkerBindsAnOwnedStationaryRocketAndAFillStopsAtAdmissionDisabled(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        BlockPos device = new BlockPos(3, 2, 7);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<String> tokens = new ArrayList<>();
            List<ServerPlayer> joined = new ArrayList<>();
            RocketEntity rocket = null;
            try {
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82Linker", level,
                        helper.absolutePos(new BlockPos(6, 2, 7)), new ArrayList<>(), false, tokens, false);
                joined.add(owner);
                rocket = rocket(helper, origin, ownerId);
                BlockPos at = helper.absolutePos(device);
                helper.assertTrue(placeBy(helper, owner, at, carrier(ownerId, LoaderTank.of(ROCKET_FUEL, null, 3_000)))
                        .consumesAction(), "Loader placement was refused");
                FluidFuelLoaderBlockEntity loader = (FluidFuelLoaderBlockEntity) level.getBlockEntity(at);
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.UNBOUND, "An unbound loader attempted");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                ItemStack linker = owner.getItemInHand(InteractionHand.MAIN_HAND);

                // Use on the loader selects it; the change starts the 10-tick cooldown, which refuses the next one.
                helper.assertTrue(select(owner, at) == InteractionResult.CONSUME && LinkerItem.selection(linker)
                        .filter(new LinkerSelection(loader.root().orElseThrow().device(), level.dimension().location(), at)::equals)
                        .isPresent() && owner.getCooldowns().isOnCooldown(ModItems.LINKER.get()), "Selection failed");
                helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.FAIL
                        && loader.root().orElseThrow().binding() == null, "A change inside the cooldown was accepted");

                // Use on the rocket binds through the real entity interaction, not the rocket's own menu.
                owner.getCooldowns().removeCooldown(ModItems.LINKER.get());
                helper.assertTrue(owner.interactOn(rocket, InteractionHand.MAIN_HAND).consumesAction(), "The bind was refused");
                var flight = rocket.flightData().orElseThrow();
                LoaderBinding binding = loader.root().orElseThrow().binding();
                helper.assertTrue(binding != null && binding.logicalRocket().equals(flight.logicalRocketId())
                        && binding.physicalRocket().equals(rocket.getUUID())
                        && binding.level().equals(level.dimension().location())
                        && binding.locator().equals(helper.absolutePos(origin)) && binding.station() == null
                        && !(owner.containerMenu instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightMenu),
                        "The binding is wrong: " + binding);
                helper.assertTrue(bind(owner, rocket) == InteractionResult.FAIL && said(tokens, LinkerItem.ALREADY_BOUND),
                        "A replayed bind was accepted");

                // Where a fill would start, admission is disabled: nothing moves.
                var fuelBefore = rocket.flightData().orElseThrow().fuel();
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED, "Attempt: " + loader.attempt());
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(fuelBefore)
                        && loader.tank().amount() == 3_000, "An attempt moved fuel");
                port(loader).drain(3_000, FluidAction.EXECUTE);
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.EMPTY, "An empty tank was not EMPTY");
                helper.assertTrue(port(loader).fill(new net.minecraftforge.fluids.FluidStack(Fluids.WATER, 1_000),
                        FluidAction.EXECUTE) == 1_000 && loader.attempt() == FluidFuelLoaderStatus.INCOMPATIBLE_FLUID,
                        "Water was not INCOMPATIBLE_FLUID");

                // Sneak-use on the loader clears its idle binding; sneak-use in the air clears the selection.
                owner.setShiftKeyDown(true);
                helper.assertTrue(select(owner, at) == InteractionResult.CONSUME && loader.root().orElseThrow().binding() == null
                        && loader.attempt() == FluidFuelLoaderStatus.UNBOUND, "Sneak-use kept the binding");
                owner.getCooldowns().removeCooldown(ModItems.LINKER.get());
                helper.assertTrue(linker.use(level, owner, InteractionHand.MAIN_HAND).getResult().consumesAction()
                        && !linker.hasTag(), "Sneak-use kept the selection");
                owner.setShiftKeyDown(false);

                // A bound rocket that leaves makes the binding stale; nothing searches for another rocket.
                helper.assertTrue(select(owner, at) == InteractionResult.CONSUME && bind(owner, rocket) == InteractionResult.CONSUME,
                        "Rebinding failed");
                rocket.discard();
                RocketEntity successor = rocket(helper, new BlockPos(9, 2, 7), ownerId);
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.STALE_BINDING, "A vanished rocket was not stale");
                helper.assertTrue(loader.root().orElseThrow().binding().physicalRocket().equals(rocket.getUUID()),
                        "The binding moved to another rocket");
                successor.discard();
            } finally {
                if (rocket != null) {
                    rocket.discard();
                }
                clear(level, helper.absolutePos(device));
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, origin, device);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void bindingRefusesForeignFarUnloadedStaleAndDuplicateTargets(GameTestHelper helper) {
        BlockPos mineOrigin = new BlockPos(3, 2, 3);
        BlockPos theirsOrigin = new BlockPos(9, 2, 6);
        BlockPos farOrigin = new BlockPos(3, 2, 14);
        BlockPos device = new BlockPos(3, 2, 7);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            UUID otherId = UUID.randomUUID();
            List<String> tokens = new ArrayList<>();
            List<String> otherTokens = new ArrayList<>();
            List<ServerPlayer> joined = new ArrayList<>();
            List<RocketEntity> rockets = new ArrayList<>();
            try {
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82Binder", level,
                        helper.absolutePos(new BlockPos(5, 2, 9)), new ArrayList<>(), false, tokens, false);
                ServerPlayer other = ConnectedTestPlayers.join(server, otherId, "w82Stranger", level,
                        helper.absolutePos(new BlockPos(6, 2, 8)), new ArrayList<>(), true, otherTokens, false);
                joined.add(owner);
                joined.add(other);
                RocketEntity mine = rocket(helper, mineOrigin, ownerId);
                RocketEntity theirs = rocket(helper, theirsOrigin, otherId);
                RocketEntity far = rocket(helper, farOrigin, ownerId);
                rockets.addAll(List.of(mine, theirs, far));
                BlockPos at = helper.absolutePos(device);
                FluidFuelLoaderBlockEntity loader = place(helper, device, owned(ownerId, LoaderTank.of(ROCKET_FUEL, null, 2_000)));
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));

                // Another player, operators included, cannot select or change the owner's device.
                helper.assertTrue(select(other, at) == InteractionResult.FAIL && !other.getMainHandItem().hasTag()
                        && said(otherTokens, FluidFuelLoaderStatus.NOT_OWNER.translationKey()), "A stranger selected the loader");
                server.getPlayerList().getOps().add(new ServerOpListEntry(other.getGameProfile(), 4, false));
                helper.assertTrue(other.hasPermissions(2) && select(other, at) == InteractionResult.FAIL
                        && !other.getMainHandItem().hasTag(), "An operator bypassed ownership");
                server.getPlayerList().getOps().remove(other.getGameProfile());

                // The owner's selection binds neither a foreign rocket nor one beyond 6 blocks.
                helper.assertTrue(select(owner, at) == InteractionResult.CONSUME, "The owner's selection failed");
                helper.assertTrue(bind(owner, theirs) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.NOT_OWNER.translationKey()), "A foreign rocket was bound");
                owner.teleportTo(level, at.getX() + 0.5D, at.getY(), at.getZ() + 4.5D, 0.0F, 0.0F);
                helper.assertTrue(bind(owner, far) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.OUT_OF_RANGE.translationKey()), "A far rocket was bound");
                helper.assertTrue(loader.root().orElseThrow().binding() == null, "A refused bind changed the device");

                // A duplicated rocket identity refuses until the copy is gone.
                RocketEntity copy = duplicate(helper, mine);
                rockets.add(copy);
                owner.teleportTo(level, at.getX() + 2.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                helper.assertTrue(bind(owner, mine) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.STALE_BINDING.translationKey()), "A duplicated rocket was bound");
                copy.discard();
                helper.assertTrue(bind(owner, mine) == InteractionResult.CONSUME, "Binding after the copy left failed");

                // The owner far from the device cannot change it.
                owner.teleportTo(level, at.getX() + 12.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                helper.assertTrue(select(owner, at) == InteractionResult.FAIL && said(tokens, LinkerItem.TOO_FAR),
                        "A distant owner changed the device");
                owner.teleportTo(level, at.getX() + 2.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);

                // A selection in an unloaded chunk or another Level refuses without loading anything.
                BlockPos unloaded = at.offset(8_192, 0, 8_192);
                helper.assertTrue(level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                        "The far chunk is loaded");
                ItemStack linker = owner.getMainHandItem();
                linker.getOrCreateTag().put(LinkerSelection.KEY,
                        new LinkerSelection(UUID.randomUUID(), level.dimension().location(), unloaded).encode());
                helper.assertTrue(bind(owner, mine) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.WAITING_UNLOADED.translationKey())
                        && level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                        "An unloaded selection was resolved or loaded");
                linker.getOrCreateTag().put(LinkerSelection.KEY, new LinkerSelection(loader.root().orElseThrow().device(),
                        ResourceLocation.tryParse("minecraft:the_nether"), at).encode());
                int before = tokens.size();
                helper.assertTrue(bind(owner, mine) == InteractionResult.FAIL && tokens.size() == before + 1
                        && said(tokens, FluidFuelLoaderStatus.STALE_BINDING.translationKey()), "Another Level's selection bound");
                // An invalid or future selection is inert.
                var future = new LinkerSelection(loader.root().orElseThrow().device(), level.dimension().location(), at).encode();
                future.putInt("schema_version", 2);
                linker.getOrCreateTag().put(LinkerSelection.KEY, future);
                helper.assertTrue(LinkerItem.selection(linker).isEmpty() && bind(owner, mine) == InteractionResult.FAIL
                        && said(tokens, LinkerItem.NO_SELECTION), "A future selection was used");

                // A binding whose locator chunk is unloaded waits without loading it; a moved locator is stale.
                LoaderRoot bound = loader.root().orElseThrow();
                LoaderBinding real = bound.binding();
                loader.load(outer(new LoaderRoot(ownerId, bound.device(), new LoaderBinding(real.logicalRocket(),
                        real.level(), real.physicalRocket(), unloaded, null), bound.tank(), LoaderEscrowSection.neutral()).encode()));
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.WAITING_UNLOADED
                        && level.getChunkSource().getChunkNow(unloaded.getX() >> 4, unloaded.getZ() >> 4) == null,
                        "An unloaded locator was loaded or resolved");
                loader.load(outer(new LoaderRoot(ownerId, bound.device(), new LoaderBinding(real.logicalRocket(),
                        real.level(), real.physicalRocket(), real.locator().east(), null), bound.tank(),
                        LoaderEscrowSection.neutral()).encode()));
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.STALE_BINDING, "A moved rocket was not stale");
                loader.load(outer(bound.encode()));
                helper.assertTrue(loader.attempt() == FluidFuelLoaderStatus.ADMISSION_DISABLED, "The exact binding failed");

                // A selection of a replaced device is stale: the new device has a fresh identity.
                linker.removeTagKey(LinkerSelection.KEY);
                helper.assertTrue(select(owner, at) == InteractionResult.CONSUME, "Reselection failed");
                clear(level, at);
                place(helper, device, owned(ownerId, LoaderTank.EMPTY));
                helper.assertTrue(bind(owner, mine) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.STALE_BINDING.translationKey()), "A replaced device was bound");

                // Fake players never select, so automation has no linker authority.
                var fake = FakePlayerFactory.get(level, new GameProfile(ownerId, "w82Fake"));
                fake.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                fake.setPos(at.getX() + 1.5D, at.getY(), at.getZ() + 0.5D);
                helper.assertTrue(fake.getMainHandItem().useOn(useOn(fake, at)) != InteractionResult.CONSUME
                        && !fake.getMainHandItem().hasTag(), "A fake player selected the loader");
                fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            } finally {
                rockets.forEach(RocketEntity::discard);
                clear(level, helper.absolutePos(device));
                server.getPlayerList().getOps().remove(new GameProfile(otherId, "w82Stranger"));
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, mineOrigin, theirsOrigin, farOrigin, device);
    }

    /** Only the owner within 8 blocks opens the menu; its values travel as shorts and a stale menu ignores replays. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 60)
    public static void theMenuOpensForTheNearOwnerOnlyAndIgnoresReplays(GameTestHelper helper) {
        BlockPos device = new BlockPos(4, 2, 4);
        BlockPos spare = new BlockPos(10, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<String> otherTokens = new ArrayList<>();
            List<ServerPlayer> joined = new ArrayList<>();
            ServerPlayer operator = null;
            try {
                BlockPos at = helper.absolutePos(device);
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82MenuOwner", level, at.east(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                ServerPlayer other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82MenuOther", level, at.west(2),
                        new ArrayList<>(), false, otherTokens, false);
                operator = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w82MenuOperator", level, at.north(2),
                        new ArrayList<>(), true, new ArrayList<>(), false);
                joined.addAll(List.of(owner, other, operator));
                server.getPlayerList().getOps().add(new ServerOpListEntry(operator.getGameProfile(), 4, false));
                FluidFuelLoaderBlockEntity loader = place(helper, device, owned(ownerId, LoaderTank.of(WATER, null, 16_000)));
                helper.assertTrue(loader.createMenu(1, owner.getInventory(), owner) != null
                        && loader.createMenu(2, other.getInventory(), other) == null
                        && loader.createMenu(3, operator.getInventory(), operator) == null, "The menu owner rule failed");
                owner.teleportTo(level, at.getX() + 9.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                helper.assertTrue(loader.createMenu(4, owner.getInventory(), owner) == null, "A distant owner opened the menu");
                owner.teleportTo(level, at.getX() + 2.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
                FluidFuelLoaderBlockEntity bare = place(helper, spare);
                helper.assertTrue(bare.createMenu(5, owner.getInventory(), owner) == null
                        && bare.createMenu(6, operator.getInventory(), operator) == null, "An unclaimed device opened its menu");

                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false);
                helper.assertTrue(loader.getBlockState().use(level, other, InteractionHand.MAIN_HAND, hit) == InteractionResult.FAIL
                        && said(otherTokens, FluidFuelLoaderStatus.NOT_OWNER.translationKey()), "A stranger was not refused");
                helper.assertTrue(loader.getBlockState().use(level, owner, InteractionHand.MAIN_HAND, hit).consumesAction()
                        && owner.containerMenu instanceof FluidFuelLoaderMenu, "The owner's use did not open the menu");
                FluidFuelLoaderMenu menu = (FluidFuelLoaderMenu) owner.containerMenu;
                FluidFuelLoaderMenu.View view = menu.view();
                helper.assertTrue(view.amount() == 16_000 && view.capacity() == 16_000
                        && view.fluidId() == BuiltInRegistries.FLUID.getId(Fluids.WATER)
                        && view.status() == FluidFuelLoaderStatus.UNBOUND && !view.bound(), "Menu values: " + view);

                // Each value survives vanilla's 16-bit data packets, including -1 for no Fluid.
                for (LoaderTank tank : List.of(LoaderTank.of(WATER, null, 16_000), LoaderTank.EMPTY)) {
                    FluidFuelLoaderBlockEntity shown = place(helper, spare, owned(ownerId, tank));
                    var live = FluidFuelLoaderMenu.liveData(shown);
                    FriendlyByteBuf open = new FriendlyByteBuf(Unpooled.buffer());
                    FluidFuelLoaderMenu.writeOpenData(open);
                    FluidFuelLoaderMenu client = new FluidFuelLoaderMenu(9, owner.getInventory(), open);
                    for (int index = 0; index < FluidFuelLoaderMenu.DATA_COUNT; index++) {
                        client.setData(index, (short) live.get(index));
                    }
                    FluidFuelLoaderMenu.View expected = new FluidFuelLoaderMenu.View(tank.amount(), 16_000,
                            tank.isEmpty() ? FluidFuelLoaderMenu.NO_FLUID : BuiltInRegistries.FLUID.getId(Fluids.WATER),
                            FluidFuelLoaderStatus.UNBOUND, false);
                    helper.assertTrue(client.view().equals(expected), "Short-encoded values differ: " + client.view());
                }

                // Clicks and button presses, replayed or not, change nothing.
                Tag root = loader.heldRoot().orElseThrow();
                owner.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                        0, 0, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
                owner.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, 0));
                helper.assertTrue(root.equals(loader.heldRoot().orElseThrow()) && owner.containerMenu.getCarried().isEmpty(),
                        "A click changed the device");

                // A replaced device invalidates the open menu; a replay against it is ignored.
                clear(level, at);
                FluidFuelLoaderBlockEntity replaced = place(helper, device, owned(ownerId, LoaderTank.of(WATER, null, 1_000)));
                helper.assertTrue(!menu.stillValid(owner), "A menu survived its device's replacement");
                owner.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(),
                        0, 0, ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
                helper.assertTrue(replaced.tank().amount() == 1_000, "A replay changed the new device");
                // So does an operator's root replacement with another identity.
                owner.closeContainer();
                helper.assertTrue(replaced.getBlockState().use(level, owner, InteractionHand.MAIN_HAND, hit).consumesAction(),
                        "Reopening failed");
                FluidFuelLoaderMenu reopened = (FluidFuelLoaderMenu) owner.containerMenu;
                replaced.load(outer(owned(ownerId, LoaderTank.EMPTY)));
                helper.assertTrue(!reopened.stillValid(owner), "A menu survived an identity change");
                owner.closeContainer();
            } finally {
                clear(level, helper.absolutePos(device));
                clear(level, helper.absolutePos(spare));
                if (operator != null) {
                    server.getPlayerList().getOps().remove(operator.getGameProfile());
                }
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, device, spare);
    }

    /** Two loaded loaders with one device UUID: the first keeps it, the copy is disabled until the first leaves. */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void aDuplicateDeviceIdentityIsDisabledWhileTheFirstIsLoaded(GameTestHelper helper) {
        BlockPos first = new BlockPos(3, 2, 3);
        BlockPos second = new BlockPos(7, 2, 3);
        EntitySections.whenLoaded(helper, () -> {
            ServerLevel level = helper.getLevel();
            MinecraftServer server = level.getServer();
            UUID ownerId = UUID.randomUUID();
            List<String> tokens = new ArrayList<>();
            List<ServerPlayer> joined = new ArrayList<>();
            try {
                ServerPlayer owner = ConnectedTestPlayers.join(server, ownerId, "w82Twins", level,
                        helper.absolutePos(new BlockPos(5, 2, 5)), new ArrayList<>(), false, tokens, false);
                joined.add(owner);
                var root = owned(ownerId, LoaderTank.of(WATER, null, 1_000));
                FluidFuelLoaderBlockEntity original = place(helper, first, root.copy());
                FluidFuelLoaderBlockEntity twin = place(helper, second, root.copy());
                helper.assertTrue(!original.duplicate() && twin.duplicate()
                        && twin.deviceRefusal() == FluidFuelLoaderStatus.INVALID_DATA, "The duplicate was not detected");
                helper.assertTrue(port(twin).fill(new net.minecraftforge.fluids.FluidStack(Fluids.WATER, 1_000), FluidAction.EXECUTE) == 0
                        && port(twin).drain(1_000, FluidAction.EXECUTE).isEmpty()
                        && twin.createMenu(1, owner.getInventory(), owner) == null, "The duplicate is usable");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                helper.assertTrue(select(owner, helper.absolutePos(second)) == InteractionResult.FAIL
                        && said(tokens, FluidFuelLoaderStatus.INVALID_DATA.translationKey()), "The duplicate was selected");
                helper.assertTrue(port(original).drain(500, FluidAction.EXECUTE).getAmount() == 500, "The original is disabled");
                clear(level, helper.absolutePos(first));
                helper.assertTrue(!twin.duplicate() && port(twin).drain(500, FluidAction.EXECUTE).getAmount() == 500,
                        "The copy did not take over the freed identity");
            } finally {
                clear(level, helper.absolutePos(first));
                clear(level, helper.absolutePos(second));
                joined.forEach(player -> server.getPlayerList().remove(player));
            }
        }, first, second);
    }
}
