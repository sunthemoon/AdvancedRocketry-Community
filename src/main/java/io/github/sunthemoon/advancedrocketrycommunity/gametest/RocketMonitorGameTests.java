package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LinkerItem;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBinding;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketMonitorGameTests {
    private RocketMonitorGameTests() { }
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3), DEVICE = new BlockPos(3, 2, 7);
    private static final TravelTarget MOON = new TravelTarget.BodySurface(CelestialIds.MOON_ID);

    static RocketMonitorBlockEntity monitor(GameTestHelper helper, UUID owner) {
        helper.setBlock(DEVICE, ModBlocks.ROCKET_MONITORING_STATION.get());
        var monitor = (RocketMonitorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE));
        var tag = new CompoundTag();
        tag.put(RocketMonitorRoot.KEY, new RocketMonitorRoot(owner, UUID.randomUUID(), null, 0, false, false, null, "").encode());
        monitor.load(tag); return monitor;
    }
    private static InteractionResult select(ServerPlayer player, BlockPos at) {
        player.getCooldowns().removeCooldown(ModItems.LINKER.get());
        return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
    }

    @GameTest(template = "rocket_test", batch = "monitor_menu", timeoutTicks = 120)
    public static void containerOnlyLaunchRequiresTheHoldersCurrentQuote(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
        var level = helper.getLevel(); var ownerId = UUID.randomUUID();
        RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, ORIGIN, ownerId, rocket -> {
            var player = ConnectedTestPlayers.join(level.getServer(), ownerId, "monitorMenu", level, helper.absolutePos(DEVICE), new ArrayList<>());
            var monitor = monitor(helper, ownerId); monitor.bind(RocketMonitorBinding.capture(rocket));
            var menu = new io.github.sunthemoon.advancedrocketrycommunity.rocket.monitor.RocketMonitorMenu(1, player, monitor,
                    java.util.List.of(new io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketMonitorFlights.Target(MOON, "Moon")));
            player.containerMenu = menu;
            var kind = io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind.ROCKET_MONITOR;
            menu.acceptClassicControl(player, new io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket(2, kind, 3, 0, 0));
            helper.assertTrue(menu.lastView().isEmpty(), "Wrong container acquired a quote");
            menu.acceptClassicControl(player, new io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket(1, kind, 3, 0, 0));
            var shown = menu.lastView().orElseThrow();
            helper.assertTrue(shown.token() > 0 && shown.values().size() == 21, "No bounded monitor quote was displayed");
            long wrong = shown.token() == Long.MAX_VALUE ? 1 : shown.token() + 1;
            driver.after(11, () -> {
                menu.acceptClassicControl(player, new io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket(1, kind, 6, 0, wrong));
                helper.assertTrue(monitor.lastFlight() == null, "A wrong token launched");
                helper.assertTrue(menu.lastView().orElseThrow().status().orElseThrow()
                        == io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode.CONFIRMATION_MISMATCH, "Wrong token was not rejected");
                var full = rocket.flightData().orElseThrow(); var low = full.fuel().debit(UUID.randomUUID(), full.fuel().amount() - 1).state();
                rocket.updateFlightData(full.withFuel(low, level.getGameTime()));
                driver.after(11, () -> {
                    menu.acceptClassicControl(player, new io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket(1, kind, 6, 0, shown.token()));
                    helper.assertTrue(menu.lastView().orElseThrow().token() == 0 && monitor.lastFlight() == null
                            && rocket.flightData().orElseThrow().fuel().equals(low), "A stale quote launched or changed resources");
                    driver.after(11, () -> {
                    menu.acceptClassicControl(player, new io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket(1, kind, 7, 0, 0));
                    helper.assertTrue(monitor.root().mode() == 1 && !monitor.root().armed() && menu.lastView().orElseThrow().token() == 0,
                            "Selecting fuel display mode required launchable fuel or created a launch token");
                    player.teleportTo(level, monitor.getBlockPos().getX() + 20, monitor.getBlockPos().getY(), monitor.getBlockPos().getZ(), 0, 0);
                    helper.assertTrue(!menu.stillValid(player), "Distant menu remained valid");
                    player.closeContainer(); level.getServer().getPlayerList().remove(player); rocket.discard();
                    FluidFuelLoaderFixtures.clear(level, monitor.getBlockPos()); helper.succeed();
                    });
                });
            });
        });
    }
    @GameTest(template = "rocket_test", batch = "monitor_binding", timeoutTicks = 100)
    public static void linkerChecksOwnerDistancePhysicalIdentityAndRelativeTelemetry(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var server = level.getServer(); var ownerId = UUID.randomUUID();
            var owner = ConnectedTestPlayers.join(server, ownerId, "monitorLink", level, helper.absolutePos(DEVICE), new ArrayList<>());
            var stranger = ConnectedTestPlayers.join(server, UUID.randomUUID(), "monitorOther", level, helper.absolutePos(DEVICE), new ArrayList<>());
            var rocket = FluidFuelLoaderFixtures.rocket(helper, ORIGIN, ownerId);
            var monitor = monitor(helper, ownerId);
            try {
                helper.assertTrue(monitor.onlyOpCanSetNbt(), "Monitor identity accepts non-operator item NBT");
                helper.assertTrue(!monitor.admitted(stranger), "A stranger accessed the monitor");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LINKER.get()));
                helper.assertTrue(select(owner, monitor.getBlockPos()) == InteractionResult.CONSUME, "Monitor was not selected");
                helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.FAIL,
                        "Linker ignored its change cooldown");
                owner.getCooldowns().removeCooldown(ModItems.LINKER.get());
                helper.assertTrue(LinkerItem.bindRocket(owner, InteractionHand.MAIN_HAND, rocket) == InteractionResult.CONSUME,
                        "Owner's docked rocket was not bound");
                helper.assertTrue(monitor.root().binding().equals(RocketMonitorBinding.capture(rocket)), "Wrong logical/physical binding");
                monitor.tick(); helper.assertTrue(monitor.height() == 0 && monitor.status() == RocketMonitorStatus.READY, "Initial telemetry is not relative");
                rocket.setPos(rocket.getX(), rocket.getY() + 24, rocket.getZ());
                monitor.tick(); helper.assertTrue(monitor.height() == 24000 && monitor.comparator() == 7, "Ascent telemetry/comparator is wrong");
                helper.assertTrue(RocketMonitorBinding.resolve(monitor, true).rocket() == null, "Moving rocket passed docked admission");
                owner.teleportTo(level, monitor.getBlockPos().getX() + 20, monitor.getBlockPos().getY(), monitor.getBlockPos().getZ(), 0, 0);
                helper.assertTrue(!monitor.admitted(owner), "Distant owner accessed the monitor");
                var saved = monitor.saveWithoutMetadata(); monitor.load(saved);
                helper.assertTrue(monitor.root().binding().physicalRocket().equals(rocket.getUUID()), "Reload lost the binding");
                rocket.discard(); monitor.tick();
                helper.assertTrue(monitor.status() == RocketMonitorStatus.STALE_BINDING && monitor.comparator() == 0, "Missing rocket was not paused");
                var future = saved.copy(); future.getCompound(RocketMonitorRoot.KEY).putInt("schema", 2); monitor.load(future);
                helper.assertTrue(monitor.root() == null && monitor.status() == RocketMonitorStatus.UNSUPPORTED_DATA
                        && future.get(RocketMonitorRoot.KEY).equals(monitor.saveWithoutMetadata().get(RocketMonitorRoot.KEY)), "Future metadata was not retained inert");
            } finally {
                rocket.discard(); FluidFuelLoaderFixtures.clear(level, monitor.getBlockPos());
                server.getPlayerList().remove(owner); server.getPlayerList().remove(stranger);
            }
        }, ORIGIN, DEVICE);
    }

    @GameTest(template = "rocket_test", batch = "monitor_refusal", timeoutTicks = 100)
    public static void refusedRisingEdgeRetainsResourcesAndAPoweredReloadDoesNotRetry(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            var owner = UUID.randomUUID(); var rocket = FluidFuelLoaderFixtures.rocket(helper, ORIGIN, owner);
            var monitor = monitor(helper, owner);
            try {
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), helper.getLevel().getGameTime()));
                helper.assertTrue(monitor.bind(RocketMonitorBinding.capture(rocket)), "Fixture binding failed");
                var service = RocketRuntime.monitorFlights().orElseThrow(); var quote = service.quote(monitor, MOON);
                helper.assertTrue(quote.ready() && monitor.configure(quote, true, 1), "Monitor could not arm: " + quote.code());
                var full = rocket.flightData().orElseThrow();
                var low = full.fuel().debit(UUID.randomUUID(), full.fuel().amount() - 1).state();
                rocket.updateFlightData(full.withFuel(low, helper.getLevel().getGameTime()));
                helper.setBlock(DEVICE.east(), Blocks.REDSTONE_BLOCK);
                monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.INSUFFICIENT_FUEL, "Missing refused-launch reason: " + monitor.lastFlight());
                helper.assertTrue(monitor.lastCost() > low.amount(), "Refused launch lost its required cost");
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(low), "Refusal changed fuel");
                var saved = monitor.saveWithoutMetadata(); monitor.load(saved); monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == null && monitor.root().powered(), "Powered reload retried the edge");
                helper.assertTrue(RocketTransferSavedData.get(helper.getLevel().getServer()).findByLogicalRocket(full.logicalRocketId()).isEmpty(),
                        "Refused edge created a transfer");
                helper.setBlock(DEVICE.east(), Blocks.AIR); monitor.signal(false);
                helper.setBlock(DEVICE.east(), Blocks.REDSTONE_BLOCK); monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.INSUFFICIENT_FUEL, "A fresh edge did not re-evaluate");
                for (int edge = 0; edge < 7; edge++) { monitor.signal(false); monitor.signal(true); }
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.RATE_LIMITED, "Repeated edges bypassed the shared limiter");
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().equals(low), "Retry changed fuel");
                var changedAuthority = new CompoundTag();
                changedAuthority.put(RocketMonitorRoot.KEY, new RocketMonitorRoot(owner, monitor.root().device(),
                        monitor.root().binding(), 1, false, true, MOON, "a".repeat(64)).encode());
                monitor.load(changedAuthority); monitor.signal(true);
                helper.assertTrue(!monitor.root().armed() && monitor.root().mode() == 0
                        && monitor.lastFlight() == RocketFlightRequestCode.UNAUTHORIZED
                        && rocket.flightData().orElseThrow().fuel().equals(low), "Changed authority did not visibly refuse and disarm");
                monitor.bind(null); helper.assertTrue(!monitor.root().armed() && monitor.root().mode() == 0, "Rebinding retained arming/mode");
            } finally {
                rocket.discard(); helper.setBlock(DEVICE.east(), Blocks.AIR);
                FluidFuelLoaderFixtures.clear(helper.getLevel(), monitor.getBlockPos());
            }
        }, ORIGIN, DEVICE);
    }

    @GameTest(template = "rocket_test", batch = "monitor_unloaded", timeoutTicks = 80)
    public static void anUnloadedEndpointPausesWithoutLoadingOrForgettingThePersistedEdge(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            var level = helper.getLevel(); var owner = UUID.randomUUID(); var monitor = monitor(helper, owner);
            var distant = new BlockPos(2345678, 100, 2345678);
            helper.assertTrue(level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null, "Fixture chunk is loaded");
            var binding = new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding(UUID.randomUUID(),
                    level.dimension().location(), UUID.randomUUID(), distant, null);
            var root = new RocketMonitorRoot(owner, monitor.root().device(), binding, 1, false, true, MOON, "a".repeat(64));
            var tag = new CompoundTag(); tag.put(RocketMonitorRoot.KEY, root.encode());
            helper.setBlock(DEVICE.east(), Blocks.REDSTONE_BLOCK); monitor.load(tag);
            try {
                monitor.tick();
                helper.assertTrue(monitor.status() == RocketMonitorStatus.WAITING_UNLOADED && monitor.root().armed()
                        && monitor.root().powered() && monitor.root().mode() == 1 && monitor.comparator() == 0,
                        "An unavailable endpoint lost its arming or produced telemetry");
                helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.ENTITY_UNAVAILABLE, "Unloaded edge did not refuse");
                monitor.load(monitor.saveWithoutMetadata()); monitor.signal(true);
                helper.assertTrue(monitor.lastFlight() == null && monitor.root().armed(), "Powered reload submitted again");
                helper.assertTrue(level.getChunkSource().getChunkNow(distant.getX() >> 4, distant.getZ() >> 4) == null, "Monitor loaded the locator chunk");
            } finally { helper.setBlock(DEVICE.east(), Blocks.AIR); FluidFuelLoaderFixtures.clear(level, monitor.getBlockPos()); }
        }, DEVICE);
    }

    @GameTest(template = "rocket_test", batch = "monitor_catalog", timeoutTicks = 100)
    public static void poweredArmingReloadKeepsEquivalentFuelCatalogButDisarmsOnFuelOnlyChange(GameTestHelper helper) {
        helper.startSequence().thenWaitUntil(() -> {
            var level = helper.getLevel();
            helper.assertTrue(level.getGameTime() % 20 == 0, "Waiting for the authority sampling tick");
            helper.assertTrue(level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(helper.absolutePos(ORIGIN)).toLong()),
                    "Waiting for the source entity section");
        }).thenExecute(() -> {
            var previous = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.capture();
            var owner = UUID.randomUUID(); var rocket = FluidFuelLoaderFixtures.rocket(helper, ORIGIN, owner);
            var monitor = monitor(helper, owner);
            try {
                var field = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.class.getDeclaredField("catalog");
                field.setAccessible(true);
                var baseline = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.defaults();
                field.set(null, baseline);
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().fill(flight.fuel().capacity()).state(), helper.getLevel().getGameTime()));
                helper.assertTrue(monitor.bind(RocketMonitorBinding.capture(rocket)), "Cannot bind catalog fixture");
                helper.setBlock(DEVICE.east(), Blocks.REDSTONE_BLOCK); monitor.signal(true);
                var service = RocketRuntime.monitorFlights().orElseThrow(); var quote = service.quote(monitor, MOON);
                helper.assertTrue(quote.ready() && monitor.configure(quote, true, 1), "Cannot arm catalog fixture");
                var saved = monitor.saveWithoutMetadata(); var before = rocket.flightData().orElseThrow();
                field.set(null, io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.defaults());
                monitor.load(saved); monitor.tick();
                helper.assertTrue(monitor.root().armed() && monitor.root().powered() && monitor.lastFlight() == null,
                        "Equivalent reload invalidated arming or retried the held edge");
                var entries = new java.util.HashMap<io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.Key,
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.Efficiency>();
                var role = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsion.Role.class;
                for (var kind : role.getEnumConstants()) {
                    var id = ModIdentity.id(kind.name().equals("MONO") ? "rocket_fuel" : kind.name().equals("OXIDIZER") ? "oxygen" : "hydrogen");
                    entries.put(new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.Key(kind, id),
                            kind.name().equals("MONO") ? new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog.Efficiency(3, 1)
                                    : baseline.find(kind, id));
                }
                field.set(null, new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelCatalog(entries));
                monitor.load(saved); monitor.tick();
                helper.assertTrue(!monitor.root().armed() && monitor.root().mode() == 0 && monitor.root().powered()
                        && monitor.lastFlight() == null && before.equals(rocket.flightData().orElseThrow()),
                        "Fuel-only reload retained powered arming or launched");
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            finally {
                try {
                    var field = io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.class.getDeclaredField("catalog");
                    field.setAccessible(true); field.set(null, previous);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                rocket.discard(); helper.setBlock(DEVICE.east(), Blocks.AIR); FluidFuelLoaderFixtures.clear(helper.getLevel(), monitor.getBlockPos());
            }
        }).thenSucceed();
    }

    @GameTest(template = "rocket_test", batch = "monitor_flight", timeoutTicks = 800)
    public static void offlineOwnersRedstoneLaunchUsesTheOrdinaryJournalAndDebitsOnce(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper); var level = helper.getLevel();
        var moon = level.getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon is unavailable");
        RocketFlightGameTestFixtures.primePadChunks(moon);
        RocketFlightGameTestFixtures.assembleFueledRocket(helper, driver, ORIGIN, UUID.randomUUID(), rocket -> {
            var monitor = monitor(helper, rocket.ownerId().orElseThrow());
            helper.assertTrue(monitor.bind(RocketMonitorBinding.capture(rocket)), "Fixture binding failed");
            var quote = RocketRuntime.monitorFlights().orElseThrow().quote(monitor, MOON);
            helper.assertTrue(quote.ready() && monitor.configure(quote, true, 0), "Cannot arm: " + quote.code());
            long before = rocket.flightData().orElseThrow().fuel().amount(); var logical = quote.source().logicalRocketId();
            helper.setBlock(DEVICE.east(), Blocks.REDSTONE_BLOCK); monitor.signal(true);
            helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.JOURNAL_PENDING || monitor.lastFlight() == RocketFlightRequestCode.SUCCESS,
                    "Offline launch refused: " + monitor.lastFlight());
            var accepted = monitor.lastFlight(); monitor.signal(true);
            helper.assertTrue(monitor.lastFlight() == accepted, "Held signal resubmitted");
            RocketFlightGameTestFixtures.awaitFlightState(helper, driver, moon, logical, RocketFlightState.LANDED, 400, () -> {
                var landed = RocketFlightGameTestFixtures.findLogicalRocket(moon, logical);
                helper.assertTrue(landed != null && landed.flightData().orElseThrow().fuel().amount() == before - quote.cost(), "Monitor flight did not debit once");
                var debits = landed.flightData().orElseThrow().fuel().committedDebits();
                helper.assertTrue(debits.size() == 1 && !debits.get(0).equals(quote.plan().requestId()), "Launch reused the preview request ID");
                helper.assertTrue(!rocket.isAlive(), "Source rocket survived the committed flight");
                monitor.tick(); helper.assertTrue(monitor.lastFlight() == RocketFlightRequestCode.SUCCESS, "Accepted flight still displays pending");
                landed.discard(); helper.setBlock(DEVICE.east(), Blocks.AIR); FluidFuelLoaderFixtures.clear(level, monitor.getBlockPos());
                RocketFlightGameTestFixtures.clearTransferJournal(level); helper.succeed();
            });
        });
    }

    @GameTest(template = "rocket_test", batch = "monitor_nested_metadata", timeoutTicks = 80)
    public static void malformedNestedDestinationsStayRawAndInertAcrossBlockEntitySaveReload(GameTestHelper helper) {
        var monitor = monitor(helper, UUID.randomUUID());
        try {
            var binding = new io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderBinding(UUID.randomUUID(),
                    helper.getLevel().dimension().location(), UUID.randomUUID(), helper.absolutePos(ORIGIN), null);
            var valid = new RocketMonitorRoot(monitor.root().owner(), monitor.root().device(), binding, 1, true, true, MOON, "a".repeat(64)).encode();
            var malformed = new ArrayList<CompoundTag>();
            for (var schema : new net.minecraft.nbt.Tag[] {net.minecraft.nbt.DoubleTag.valueOf(1.75),
                    net.minecraft.nbt.LongTag.valueOf(1), net.minecraft.nbt.ByteTag.valueOf((byte) 1)}) {
                var root = valid.copy(); root.getCompound("destination").put("schema_version", schema); malformed.add(root);
            }
            var unknown = valid.copy(); unknown.getCompound("destination").putInt("unknown", 1); malformed.add(unknown);
            var shorthand = valid.copy(); shorthand.getCompound("destination").putString("body_id", "moon"); malformed.add(shorthand);
            for (var raw : malformed) {
                var outer = new CompoundTag(); outer.put(RocketMonitorRoot.KEY, raw.copy()); monitor.load(outer);
                for (int reload = 0; reload < 2; reload++) {
                    helper.assertTrue(monitor.root() == null && monitor.status() == RocketMonitorStatus.UNSUPPORTED_DATA, "Malformed target remained live");
                    monitor.signal(true); monitor.tick();
                    var saved = monitor.saveWithoutMetadata();
                    helper.assertTrue(raw.equals(saved.get(RocketMonitorRoot.KEY)) && monitor.lastFlight() == null,
                            "Save/reload normalized or launched malformed metadata");
                    monitor.load(saved);
                }
            }
        } finally { FluidFuelLoaderFixtures.clear(helper.getLevel(), monitor.getBlockPos()); }
        helper.succeed();
    }
}
