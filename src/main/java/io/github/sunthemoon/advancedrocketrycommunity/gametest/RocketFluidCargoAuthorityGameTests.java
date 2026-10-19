package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFluidCargoFixtures.*;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.FluidFuelLoaderFixtures.clear;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
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
public final class RocketFluidCargoAuthorityGameTests {
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
                var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Bind", level, at.east(2),
                        new ArrayList<>(), false, new ArrayList<>(), false);
                var op = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109BindOp", level, at.west(2),
                        new ArrayList<>(), true, new ArrayList<>(), false);
                players.addAll(List.of(owner, op)); server.getPlayerList().getOps().add(new ServerOpListEntry(op.getGameProfile(), 4, false));
                var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner.getUUID()); rockets.add(rocket);
                var foreign = FluidFuelLoaderFixtures.rocket(helper, new BlockPos(10, 2, 3), op.getUUID()); rockets.add(foreign);
                var farRocket = FluidFuelLoaderFixtures.rocket(helper, new BlockPos(3, 2, 14), owner.getUUID()); rockets.add(farRocket);
                for (var role : RocketCargoRole.values()) {
                    var device = place(helper, pos, role, owned(owner.getUUID(), role, 2_000));
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
                    helper.assertTrue(twin.duplicate() && twin.displayStatus() == RocketCargoStatus.INVALID_DATA
                            && port(twin).fill(water(1), net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0
                            && port(twin).drain(1, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Duplicate device was active");
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
    public static void ownerMenuScalarsRangeAndPacketReplayCannotMutateResources(GameTestHelper helper) {
        var pos = new BlockPos(4, 2, 4);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109Menu", level, at.east(2), new ArrayList<>(), false, new ArrayList<>(), false);
            var other = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109MenuOther", level, at.west(2), new ArrayList<>(), false, new ArrayList<>(), false);
            try {
                for (var role : RocketCargoRole.values()) {
                    var device = place(helper, pos, role, owned(owner.getUUID(), role, 16_000));
                    helper.assertTrue(device.createMenu(1, other.getInventory(), other) == null, "Foreign menu opened");
                    owner.setPos(at.getX() + 9.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(device.createMenu(1, owner.getInventory(), owner) == null, "Distant menu opened");
                    owner.setPos(at.getX() + 2.5, at.getY(), at.getZ() + .5);
                    helper.assertTrue(device.getBlockState().use(level, owner, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)).consumesAction()
                            && owner.containerMenu instanceof RocketFluidCargoMenu, "Owner menu did not open");
                    var menu = (RocketFluidCargoMenu) owner.containerMenu;
                    helper.assertTrue(menu.view().amount() == 16_000 && menu.view().role() == role && menu.view().cursor() == null
                            && menu.view().status() == RocketCargoStatus.UNBOUND, "Wrong scalar view");
                    var open = new FriendlyByteBuf(Unpooled.buffer());
                    try {
                        RocketFluidCargoMenu.writeOpenData(open);
                        var client = new RocketFluidCargoMenu(9, owner.getInventory(), open);
                        var data = RocketFluidCargoMenu.liveData(device);
                        for (int i = 0; i < RocketFluidCargoMenu.DATA_COUNT; i++) { client.setData(i, (short) data.get(i)); }
                        helper.assertTrue(client.view().equals(menu.view()), "Scalar short encoding changed data");
                        client.setData(7, 1); client.setData(8, (short) -128); client.setData(9, (short) 127); client.setData(10, (short) -3);
                        helper.assertTrue(new BlockPos(-128, 127, -3).equals(client.view().cursor()), "Relative cursor lost sign");
                        client.setData(2, (short) -1); client.setData(3, (short) -1);
                        helper.assertTrue(client.view().fluidId() == -1, "Empty fluid sentinel changed");
                    } finally { open.release(); }
                    var before = device.heldRoot().orElseThrow();
                    replay(owner, menu);
                    helper.assertTrue(before.equals(device.heldRoot().orElseThrow()) && menu.getCarried().isEmpty(), "Packet mutated root");
                    clear(level, at); var replacement = place(helper, pos, role, owned(owner.getUUID(), role, 1_000));
                    helper.assertTrue(!menu.stillValid(owner), "Replaced BE retained menu authority"); replay(owner, menu);
                    helper.assertTrue(replacement.tank().amount() == 1_000, "Stale replay mutated replacement");
                    var reopened = (RocketFluidCargoMenu) replacement.createMenu(3, owner.getInventory(), owner);
                    replacement.load(outer(owned(owner.getUUID(), role, 2_000)));
                    helper.assertTrue(!reopened.stillValid(owner), "Changed placement UUID retained menu authority");
                    owner.closeContainer(); clear(level, at);
                }
            } finally { owner.closeContainer(); clear(level, at); server.getPlayerList().remove(owner); server.getPlayerList().remove(other); }
        }, pos);
    }

    private static void replay(ServerPlayer owner, RocketFluidCargoMenu menu) {
        owner.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), 0, 0,
                ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
        owner.connection.handleContainerButtonClick(new ServerboundContainerButtonClickPacket(menu.containerId, 0));
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 80)
    public static void cutG2OccupancyReconnectRideTeleportTravelAndBypassKeepCargoIdle(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 7); var origin = new BlockPos(3, 2, 3);
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var at = helper.absolutePos(pos);
            var owner = ConnectedTestPlayers.join(server, UUID.randomUUID(), "w109G2", level, at.east(2), new ArrayList<>(), false, new ArrayList<>(), false);
            List<RocketEntity> rockets = new ArrayList<>();
            var vehicle = new ArmorStand(level, at.getX(), at.getY(), at.getZ());
            helper.assertTrue(level.addFreshEntity(vehicle), "Vehicle fixture not admitted");
            try {
                for (var role : RocketCargoRole.values()) {
                    var rocket = FluidFuelLoaderFixtures.rocket(helper, origin, owner.getUUID()); rockets.add(rocket);
                    var root = RocketFluidCargoRoot.placed(owner.getUUID(), UUID.randomUUID(), role, tank(2_000))
                            .withBinding(binding(helper, rocket, origin));
                    var device = place(helper, pos, role, root.encode());
                    var flight = rocket.flightData().orElseThrow(); var cargo = rocket.snapshot().orElseThrow().contentHash();
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
    private RocketFluidCargoAuthorityGameTests() { }
}
