package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketItemCargoFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoRole;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketCargoStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoMenu;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketItemCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketItemCargoAuthorityGameTests {
    private static InteractionResult select(ServerPlayer player, BlockPos at) {
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
    }
    private static void cooldown(ServerPlayer player) { player.getCooldowns().removeCooldown(ModItems.LINKER.get()); }
    private static LoaderBinding binding(GameTestHelper helper, RocketEntity rocket, BlockPos origin) {
        return new LoaderBinding(rocket.flightData().orElseThrow().logicalRocketId(), helper.getLevel().dimension().location(),
                rocket.getUUID(), helper.absolutePos(origin), null);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void ownerLinkerCooldownUnloadedDuplicateAndStaleIdentities(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 7); var origin = new BlockPos(3, 2, 3); var spare = new BlockPos(7, 2, 7);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            List<ServerPlayer> players = new ArrayList<>(); List<RocketEntity> rockets = new ArrayList<>();
            try {
                var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Bind", level, at.east(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var op = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120BindOp", level, at.west(2),
                        new ArrayList<>(), true, new ArrayList<>(), false);
                players.addAll(List.of(owner, op)); server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 4, false));
                var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner.getUUID()); rockets.add(rocket);
                var foreign = FluidFuelLoaderFixtures.rocket(helper, new BlockPos(10, 2, 3), op.getUUID()); rockets.add(foreign);
                var farRocket = FluidFuelLoaderFixtures.rocket(helper, new BlockPos(3, 2, 14), owner.getUUID()); rockets.add(farRocket);
                for (var role : RocketCargoRole.values()) {
                    var device = place(helper, pos, role, owned(owner.getUUID(), role, stone(20)));
                    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                    op.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get())); cooldown(owner); cooldown(op);
                    helper.assertTrue(select(op, at) == InteractionResult.FAIL && !op.getMainHandItem().hasTag(), "Operator bypassed ownership");
                    owner.setPos(at.getX() + 9.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(select(owner, at) == InteractionResult.FAIL && !owner.getMainHandItem().hasTag(), "Distant selection accepted");
                    owner.setPos(at.getX() + 2.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(select(owner, at) == InteractionResult.CONSUME && owner.getCooldowns().isOnCooldown(ModItems.LINKER.get()),
                            "Selection failed or no cooldown");
                    helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.FAIL
                            && device.root().orElseThrow().binding() == null, "Cooldown allowed second change");
                    cooldown(owner);
                    helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, foreign) == InteractionResult.FAIL, "Foreign rocket bound");
                    helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, farRocket) == InteractionResult.FAIL
                            && device.root().orElseThrow().binding() == null, "Out-of-range rocket bound");
                    helper.assertTrue(owner.interactOn(rocket, InteractionHand.MAIN_HAND).consumesAction()
                            && binding(helper, rocket, origin).equals(device.root().orElseThrow().binding()), "Real interaction did not bind");
                    var before = device.heldRoot().orElseThrow(); var snapshot = rocket.snapshot().orElseThrow().contentHash();
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.ADMISSION_DISABLED, "Unwired admission status: " + device.displayStatus());
                    cooldown(owner); helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.FAIL
                            && before.equals(device.heldRoot().orElseThrow()) && snapshot.equals(rocket.snapshot().orElseThrow().contentHash()), "Replay changed resources");
                    var copy = FluidFuelLoaderFixtures.duplicate(helper, rocket); rockets.add(copy);
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.STALE_BINDING, "Duplicate rocket was accepted"); copy.discard();
                    var twin = place(helper, spare, role, device.root().orElseThrow().encode());
                    var one = new ItemStack(Items.STONE, 1);
                    helper.assertTrue(twin.duplicate() && twin.displayStatus() == RocketCargoStatus.INVALID_DATA
                            && port(twin).insertItem(1, one, false) == one && port(twin).extractItem(0, 1, false).isEmpty(), "Duplicate device was active");
                    clear(level, helper.absolutePos(spare));
                    var root = device.root().orElseThrow();
                    var far = new BlockPos(29_000_000, 64, 29_000_000);
                    helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Far fixture loaded");
                    device.load(outer(root.withBinding(new LoaderBinding(UUID.randomUUID(), level.dimension().location(), UUID.randomUUID(), far, null)).encode()));
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.WAITING_UNLOADED
                            && level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "Status force-loaded a chunk");
                    device.load(outer(root.encode()));
                    var home = rocket.position(); rocket.setPos(home.add(20, 0, 0));
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.STALE_BINDING, "Moved physical rocket retained locator authority");
                    rocket.setPos(home);
                    owner.setShiftKeyDown(true); cooldown(owner);
                    helper.assertTrue(select(owner, at) == InteractionResult.CONSUME && device.root().orElseThrow().binding() == null, "Idle binding not cleared");
                    owner.setShiftKeyDown(false); clear(level, at);
                }
            } finally {
                rockets.forEach(RocketEntity::discard); clear(level, at); clear(level, helper.absolutePos(spare));
                players.forEach(p -> { server.getPlayerList().getOps().remove(p.getGameProfile()); server.getPlayerList().remove(p); });
            }
        }, pos, origin, spare, new BlockPos(10, 2, 3), new BlockPos(3, 2, 14));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void ownerMenuMovesItemsBothWaysAndReplayCannotMutateResources(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120Menu", level, at.east(2), new ArrayList<>(), false, new ArrayList<>(), false);
            var other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120MenuOther", level, at.west(2), new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                for (var role : RocketCargoRole.values()) {
                    owner.getInventory().clearContent();
                    var device = place(helper, pos, role, owned(owner.getUUID(), role, stone(20)));
                    helper.assertTrue(device.createMenu(1, other.getInventory(), other) == null, "Foreign menu opened");
                    owner.setPos(at.getX() + 9.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(device.createMenu(1, owner.getInventory(), owner) == null, "Distant menu opened");
                    owner.setPos(at.getX() + 2.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(device.getBlockState().use(level, owner, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)).consumesAction()
                            && owner.containerMenu instanceof RocketItemCargoMenu, "Owner menu did not open");
                    var menu = (RocketItemCargoMenu) owner.containerMenu;
                    helper.assertTrue(menu.view().role() == role && menu.view().cursor() == null && menu.view().cursorSlot() == -1
                            && menu.view().status() == RocketCargoStatus.UNBOUND && !menu.view().bound()
                            && menu.slots.size() == 40, "Wrong scalar view or slots");
                    var open = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        RocketItemCargoMenu.writeOpenData(open);
                        var client = new RocketItemCargoMenu(9, owner.getInventory(), open);
                        var data = RocketItemCargoMenu.liveData(device);
                        for (int i = 0; i < RocketItemCargoMenu.DATA_COUNT; i++) { client.setData(i, (short) data.get(i)); }
                        helper.assertTrue(client.view().equals(menu.view()), "Scalar short encoding changed data");
                        client.setData(3, 1); client.setData(4, (short) -128); client.setData(5, (short) 127); client.setData(6, (short) -3);
                        client.setData(7, 26);
                        helper.assertTrue(new BlockPos(-128, 127, -3).equals(client.view().cursor()) && client.view().cursorSlot() == 26,
                                "Relative cursor or slot lost its value");
                    } finally { open.release(); }
                    // The owner moves items both ways, whatever the device's role, through real click packets.
                    click(owner, menu, 0, ClickType.PICKUP);
                    helper.assertTrue(menu.getCarried().getCount() == 20 && device.stack(0).isEmpty(), role + " pickup failed");
                    click(owner, menu, 2, ClickType.PICKUP);
                    helper.assertTrue(menu.getCarried().isEmpty() && device.stack(2).getCount() == 20, role + " place failed");
                    click(owner, menu, 2, ClickType.QUICK_MOVE);
                    helper.assertTrue(device.stack(2).isEmpty() && owner.getInventory().countItem(Items.STONE) == 20, role + " quick move out failed");
                    int playerSlot = -1;
                    for (int index = RocketItemCargoMenu.DEVICE_SLOTS; index < menu.slots.size(); index++) {
                        if (menu.slots.get(index).getItem().is(Items.STONE)) { playerSlot = index; }
                    }
                    click(owner, menu, playerSlot, ClickType.QUICK_MOVE);
                    helper.assertTrue(device.stack(0).getCount() == 20 && owner.getInventory().countItem(Items.STONE) == 0, role + " quick move in failed");
                    // A buttonless menu, and a replayed click on a replaced device, change nothing.
                    var before = device.heldRoot().orElseThrow();
                    owner.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, 0));
                    helper.assertTrue(before.equals(device.heldRoot().orElseThrow()), "Button changed the root");
                    clear(level, at); var replacement = place(helper, pos, role, owned(owner.getUUID(), role, stone(5)));
                    helper.assertTrue(!menu.stillValid(owner), "Replaced BE retained menu authority");
                    click(owner, menu, 0, ClickType.PICKUP); click(owner, menu, 0, ClickType.QUICK_MOVE);
                    helper.assertTrue(replacement.stack(0).getCount() == 5 && menu.getCarried().isEmpty()
                            && owner.getInventory().countItem(Items.STONE) == 0, "Stale replay mutated the replacement");
                    var reopened = (RocketItemCargoMenu) replacement.createMenu(3, owner.getInventory(), owner);
                    replacement.load(outer(owned(owner.getUUID(), role, stone(5))));
                    helper.assertTrue(!reopened.stillValid(owner), "Changed placement UUID retained menu authority");
                    owner.closeContainer();
                    // An escrow-held device opens but refuses every slot change, in both directions.
                    var five = RocketItemCargoRoot.decode(owned(owner.getUUID(), role, stone(5)), role).buffer();
                    var held = new RocketItemCargoRoot(owner.getUUID(), UUID.randomUUID(), role, null, five,
                            new RocketEscrowSection(UUID.randomUUID(), 1, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, role.markerRole(), 1))));
                    clear(level, at); var locked = place(helper, pos, role, held.encode());
                    var lockedMenu = (RocketItemCargoMenu) locked.createMenu(4, owner.getInventory(), owner);
                    owner.getInventory().add(new ItemStack(Items.DIRT, 3));
                    var lockedRoot = locked.heldRoot().orElseThrow();
                    lockedMenu.clicked(0, 0, ClickType.PICKUP, owner);
                    for (int index = RocketItemCargoMenu.DEVICE_SLOTS; index < lockedMenu.slots.size(); index++) {
                        if (lockedMenu.slots.get(index).getItem().is(Items.DIRT)) { lockedMenu.clicked(index, 0, ClickType.QUICK_MOVE, owner); }
                    }
                    helper.assertTrue(locked.escrowLocked() && lockedMenu.getCarried().isEmpty() && lockedRoot.equals(locked.heldRoot().orElseThrow())
                            && owner.getInventory().countItem(Items.DIRT) == 3, "An escrow-held device changed through its menu");
                    owner.getInventory().clearContent(); clear(level, at);
                }
            } finally { owner.closeContainer(); clear(level, at); server.getPlayerList().remove(owner); server.getPlayerList().remove(other); }
        }, pos);
    }

    private static void click(ServerPlayer owner, RocketItemCargoMenu menu, int slot, ClickType type) {
        owner.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), slot, 0,
                type, menu.getCarried().copy(), new Int2ObjectOpenHashMap<>()));
    }

    /**
     * W120-R2: an unbound device filled as far as it accepts can still be bound through the real interaction, with
     * room left for an escrow marker. A root loaded fuller than that refuses the binding cleanly, with the size
     * message and the linker's cooldown, and changes nothing.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void aFilledUnboundDeviceStaysBindableAndAFullerRootRefusesCleanly(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 7); var origin = new BlockPos(3, 2, 3);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            List<String> tokens = new ArrayList<>();
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120fFull", level, at.east(2),
                    new ArrayList<>(), false, tokens, false);
            List<RocketEntity> rockets = new ArrayList<>();
            try {
                var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner.getUUID()); rockets.add(rocket);
                var large = stoneOfNodes(1, 512);
                for (var role : RocketCargoRole.values()) {
                    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    var device = place(helper, pos, role, owned(owner.getUUID(), role));
                    // What the four stacks may take of the bound: everything but the frame, where each empty slot is {}.
                    int stacks = RocketItemCargoRoot.MAX_NODES - (nodes(device.heldRoot().orElseThrow()) - RocketItemCargoMenu.DEVICE_SLOTS);
                    var menu = (RocketItemCargoMenu) device.createMenu(6, owner.getInventory(), owner);
                    for (int slot = 0; slot < 3; slot++) {
                        menu.setCarried(large.copy()); menu.clicked(slot, 0, ClickType.PICKUP, owner);
                        helper.assertTrue(menu.getCarried().isEmpty() && device.stack(slot).getCount() == 1, role + " refused a 512-node stack");
                    }
                    // The stack that brings the unbound root to exactly 2,048 nodes leaves no room to bind it.
                    var last = stoneOfNodes(1, stacks - 3 * 512);
                    var before = device.heldRoot().orElseThrow();
                    menu.setCarried(last.copy()); menu.clicked(3, 0, ClickType.PICKUP, owner);
                    helper.assertTrue(menu.getCarried().getCount() == 1 && device.stack(3).isEmpty() && before.equals(device.heldRoot().orElseThrow()),
                            role + " accepted a stack that leaves no room to bind the device");
                    if (role == RocketCargoRole.LOAD) {
                        helper.assertTrue(port(device).insertItem(3, last, false) == last && before.equals(device.heldRoot().orElseThrow()),
                                "The port accepted a stack that leaves no room to bind the device");
                    }
                    // The largest fourth stack the device accepts, searched below that within 64 nodes.
                    int accepted = 0;
                    for (int size = stacks - 3 * 512 - 1; size > stacks - 3 * 512 - 64 && accepted == 0; size--) {
                        menu.setCarried(stoneOfNodes(1, size)); menu.clicked(3, 0, ClickType.PICKUP, owner);
                        if (menu.getCarried().isEmpty()) { accepted = size; }
                    }
                    helper.assertTrue(accepted > 0 && device.stack(3).getCount() == 1, role + " accepted no fourth stack");
                    menu.setCarried(ItemStack.EMPTY);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get())); cooldown(owner);
                    helper.assertTrue(select(owner, at) == InteractionResult.CONSUME, role + " could not select the filled device");
                    cooldown(owner);
                    helper.assertTrue(owner.interactOn(rocket, InteractionHand.MAIN_HAND).consumesAction()
                            && binding(helper, rocket, origin).equals(device.root().orElseThrow().binding()), role + " could not bind the filled device");
                    var bound = device.root().orElseThrow();
                    var marked = new RocketItemCargoRoot(bound.owner(), bound.device(), role, bound.binding(), bound.buffer(),
                            new RocketEscrowSection(UUID.randomUUID(), Long.MAX_VALUE,
                                    List.of(new EscrowPrefix.Marker(UUID.randomUUID(), Long.MAX_VALUE, role.markerRole(), Long.MAX_VALUE))));
                    helper.assertTrue(RocketItemCargoRoot.bounded(marked.encodeUnchecked()), role + " left no room for an escrow marker");
                    // A root loaded fuller than the device accepts: the binding is refused cleanly and changes nothing.
                    var full = RocketItemCargoRoot.placed(owner.getUUID(), UUID.randomUUID(), role);
                    for (int slot = 0; slot < 3; slot++) { full = full.withSlot(slot, large.save(new CompoundTag())); }
                    device.load(outer(full.withSlot(3, last.save(new CompoundTag())).encode()));
                    var raw = device.heldRoot().orElseThrow();
                    cooldown(owner);
                    helper.assertTrue(select(owner, at) == InteractionResult.CONSUME, role + " could not select the full device");
                    cooldown(owner); tokens.clear();
                    helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.FAIL
                            && raw.equals(device.heldRoot().orElseThrow()) && device.root().orElseThrow().binding() == null
                            && owner.getCooldowns().isOnCooldown(ModItems.LINKER.get())
                            && tokens.contains(ConnectedTestPlayers.ACTION_BAR + RocketCargoStatus.CARGO_TOO_LARGE.translationKey()),
                            role + " did not refuse a binding that does not fit cleanly: " + tokens);
                    owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); clear(level, at);
                }
            } finally {
                rockets.forEach(RocketEntity::discard); owner.getInventory().clearContent(); clear(level, at);
                server.getPlayerList().remove(owner);
            }
        }, pos, origin);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void cutG2OccupancyReconnectRideTeleportTravelAndBypassKeepCargoIdle(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 7); var origin = new BlockPos(3, 2, 3);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w120G2", level, at.east(2), new ArrayList<>(), false, new ArrayList<>(), false);
            List<RocketEntity> rockets = new ArrayList<>();
            var vehicle = new ArmorStand(level, at.getX(), at.getY(), at.getZ());
            helper.assertTrue(level.addFreshEntity(vehicle), "Vehicle fixture not admitted");
            try {
                for (var role : RocketCargoRole.values()) {
                    var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner.getUUID()); rockets.add(rocket);
                    var root = RocketItemCargoRoot.placed(owner.getUUID(), UUID.randomUUID(), role).withSlot(0, stone(20))
                            .withBinding(binding(helper, rocket, origin));
                    var device = place(helper, pos, role, root.encode());
                    var flight = rocket.flightData().orElseThrow(); var cargo = rocket.snapshot().orElseThrow().contentHash();
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.ADMISSION_DISABLED, "Bound device not idle: " + device.displayStatus());
                    rocket.updateFlightData(flight.withPassengers(flight.passengers().assign(owner.getUUID()).orElseThrow()));
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.ROCKET_BUSY, "Assigned passenger ignored");
                    helper.assertTrue(owner.startRiding(rocket, true) && device.displayStatus() == RocketCargoStatus.ROCKET_BUSY, "Mount occupancy ignored");
                    owner.stopRiding();
                    new RocketManager().onPlayerLoggedIn(new PlayerEvent.PlayerLoggedInEvent(owner));
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.ROCKET_BUSY, "Reconnect removed assigned occupancy interlock");
                    owner.stopRiding(); rocket.updateFlightData(flight);
                    helper.assertTrue(rocket.startRiding(vehicle, true) && device.displayStatus() == RocketCargoStatus.ROCKET_BUSY, "Riding rocket accepted");
                    rocket.stopRiding();
                    var home = rocket.position();
                    var source = server.createCommandSourceStack().withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(source, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
                    helper.assertTrue(rocket.isPassenger() && device.displayStatus() == RocketCargoStatus.ROCKET_BUSY, "Neutral /ride control failed");
                    rocket.stopRiding();
                    // Load a persisted, held wrapper; no escrow operation is created by this fixture or device.
                    var saved = rocket.saveWithoutId(new CompoundTag());
                    saved.getCompound("RocketEntityData").put("resource_escrow", new RocketEscrowSection(UUID.randomUUID(), 1,
                            List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, role == RocketCargoRole.LOAD ? "DESTINATION" : "SOURCE", 1))).encode());
                    // A fresh native entity load, not live /data (which intentionally retains its existing wrapper).
                    rocket.discard();
                    rocket = io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities.ROCKET.get().create(level);
                    rocket.load(saved); rockets.add(rocket);
                    helper.assertTrue(level.addFreshEntity(rocket), "Held rocket reload refused");
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.ESCROW_PENDING, "Held rocket not blocked");
                    server.getCommands().performPrefixedCommand(source, "ride " + rocket.getUUID() + " mount " + vehicle.getUUID());
                    server.getCommands().performPrefixedCommand(source, "tp " + rocket.getUUID() + " " + (home.x + 2) + " " + home.y + " " + home.z);
                    var travel = new EntityTravelToDimensionEvent(rocket, Level.NETHER); MinecraftForge.EVENT_BUS.post(travel);
                    helper.assertTrue(!rocket.isPassenger() && rocket.position().equals(home) && travel.isCanceled(), "Held movement bypassed Forge interlocks");
                    // Direct mod movement is a residual, but the physical locator cannot become valid authority.
                    rocket.setPos(home.add(0.125, 0, 0));
                    helper.assertTrue(device.displayStatus() == RocketCargoStatus.STALE_BINDING && root.encode().equals(device.heldRoot().orElseThrow())
                            && cargo.equals(rocket.snapshot().orElseThrow().contentHash())
                            && flight.fuel().equals(rocket.flightData().orElseThrow().fuel()), "Bypass movement moved cargo/fuel or remained eligible");
                    rocket.discard(); clear(level, at);
                }
            } finally { owner.stopRiding(); rockets.forEach(RocketEntity::discard); vehicle.discard(); clear(level, at); server.getPlayerList().remove(owner); }
        }, pos, origin);
    }
    private RocketItemCargoAuthorityGameTests() { }
}
