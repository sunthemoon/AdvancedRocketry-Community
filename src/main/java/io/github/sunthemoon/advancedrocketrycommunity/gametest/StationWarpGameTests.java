package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManager;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationRocketAuthority;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpResult;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-044 warp on the real server: stateless cores charging their station, owner request and
 * confirmation, the countdown and a checked commit. Each test has its own batch so the rocket port
 * it installs (WARP-04 wires the real one) never overlaps another warp test.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationWarpGameTests {
    private static final ResourceLocation MOON = CelestialIds.MOON_ID;
    private static final StationRocketAuthority NOTHING_IN_MOTION = (server, station) -> false;
    private static final int COMMIT_DELAY = (int) StationLimits.WARP_COUNTDOWN_TICKS + 3;
    /** Keeps a fixture's pad chunk loaded across ticks (test setup only; production never loads chunks). */
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_station_warp", java.util.Comparator.comparing(UUID::toString), 0);

    private StationWarpGameTests() {
    }

    @GameTest(template = "empty", batch = "station_warp_charge", timeoutTicks = 100)
    public static void coresChargeOnlyTheStationTheyStandInWithinBounds(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Charging", CelestialIds.EARTH_ID, 0);
        StationWarpService warp = StationWarpRuntime.service().orElseThrow();
        try {
            IEnergyStorage core = fixture.core(fixture.pad.east(2));
            IEnergyStorage second = fixture.core(fixture.pad.west(2));
            BlockPos gapPosition = fixture.pad.east(StationLimits.REGION_SIZE / 2 + 32);
            IEnergyStorage gap = fixture.core(gapPosition);
            BlockPos overworldPosition = helper.absolutePos(new BlockPos(1, 2, 1));
            helper.getLevel().setBlockAndUpdate(overworldPosition, ModBlocks.WARP_CORE.get().defaultBlockState());
            IEnergyStorage overworld = energy(helper.getLevel(), overworldPosition);

            helper.assertTrue(core.receiveEnergy(1_000_000, true) == StationLimits.WARP_CREDIT_PER_TICK,
                    "Simulation must report the per-tick allowance");
            helper.assertTrue(warp.pendingCredit(fixture.id()) == 0, "Simulation changed the pending credit");
            // WARP review R2 (M16, M27): with the whole allowance unused, only the Level and thread checks
            // refuse. The Overworld call names a position inside this station's region.
            helper.assertTrue(warp.receiveEnergy(helper.getLevel(), fixture.pad.east(2), 1_000, false) == 0,
                    "An Overworld position at the station's x/z was accepted");
            int offThreadFirst = CompletableFuture.supplyAsync(
                    () -> warp.receiveEnergy(fixture.space, fixture.pad.east(2), 1_000, false)).join();
            helper.assertTrue(offThreadFirst == 0, "An off-thread call with allowance left was accepted");
            helper.assertTrue(warp.pendingCredit(fixture.id()) == 0, "A refused call changed the pending credit");
            helper.assertTrue(warp.receiveEnergy(fixture.space, fixture.pad.east(2), 1_000, true) == 1_000,
                    "The same call on the server thread in Space must be accepted");
            helper.assertTrue(core.receiveEnergy(150_000, false) == 150_000
                    && second.receiveEnergy(150_000, false) == 50_000
                    && core.receiveEnergy(1, false) == 0, "Cores of one station share the per-tick allowance");
            helper.assertTrue(gap.receiveEnergy(1_000, false) == 0, "A core in the gap charged something");
            helper.assertTrue(overworld.receiveEnergy(1_000, false) == 0, "A core outside Space charged something");
            int offThread = CompletableFuture.supplyAsync(() -> second.receiveEnergy(1_000, false)).join();
            helper.assertTrue(offThread == 0, "An off-thread call was accepted");
            helper.assertTrue(core.getEnergyStored() == 0 && core.extractEnergy(1_000, false) == 0
                    && !core.canExtract() && core.canReceive()
                    && core.getMaxEnergyStored() == StationLimits.MAX_WARP_ENERGY, "Core energy view differs");
            helper.assertTrue(warp.pendingCredit(fixture.id()) == StationLimits.WARP_CREDIT_PER_TICK,
                    "Pending credit differs");
            helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == 0, "Credit reached the registry before a fold");
            helper.assertTrue(warp.fold(fixture.server).credited() == StationLimits.WARP_CREDIT_PER_TICK,
                    "The fold did not credit the pending energy");
            helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == StationLimits.WARP_CREDIT_PER_TICK,
                    "Folded balance differs");
            helper.assertTrue(fixture.data.isDirty(), "A fold is an ordinary mutation that marks the registry dirty");
            fixture.space.setBlockAndUpdate(gapPosition, Blocks.AIR.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(overworldPosition, Blocks.AIR.defaultBlockState());
        } finally {
            fixture.close();
        }
        helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == 0, "Deleting the station kept its balance");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_warp_commit", timeoutTicks = 400)
    public static void ownerWarpsInSystemAfterConfirmationAndCountdown(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "In-system warp", CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            fixture.core(fixture.pad.east(2));
            List<String> owner = fixture.join(fixture.ownerId, "warpOwner");
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            List<String> member = fixture.join(memberId, "warpMember");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + MOON);
            String quote = last(owner);
            helper.assertTrue(quote.contains("in-system warp, cost 2000000 FE, warp energy 2500000 FE")
                            && quote.contains("Docked rockets move with the station")
                            && !quote.contains("no rocket routes")
                            && quote.contains("/arce station warp confirm " + fixture.id()),
                    "Quote differs: " + quote);
            // WARP review R2 (M11): a non-local source is refused before the confirmation is taken, so the
            // owner's own confirmation still works afterwards.
            List<String> console = new ArrayList<>();
            fixture.run(fixture.server.createCommandSourceStack().withSource(ConnectedTestPlayers.capture(console)),
                    "execute as " + fixture.ownerId + " run arce station warp confirm " + fixture.id());
            expect(helper, console, StationManagementCode.NOT_LOCAL_PLAYER, "/execute confirm");
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            helper.assertTrue(last(owner).startsWith("Warp countdown started; station=" + fixture.id()),
                    "Confirmation differs: " + owner);
            helper.assertTrue(member.stream().anyMatch(line -> line.startsWith("Station warp countdown started by warpOwner")),
                    "Members were not told: " + member);
            StationState before = fixture.station();
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    StationState after = fixture.station();
                    helper.assertTrue(after.equals(before.withOrbitBody(MOON)), "Warp did not relocate only the orbit");
                    helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == 500_000, "Balance was not debited once");
                    StationRegistrySavedData onDisk = StationRegistrySavedData.load(fixture.stationFile());
                    helper.assertTrue(onDisk.find(fixture.id()).filter(after::equals).isPresent()
                            && onDisk.warpEnergy(fixture.id()) == 500_000, "The checked write is not on disk");
                    List<String> countdown = member.stream().filter(line -> line.startsWith(ConnectedTestPlayers.ACTION_BAR))
                            .toList();
                    helper.assertTrue(countdown.equals(List.of(10, 5, 3, 2, 1).stream().map(seconds ->
                                    ConnectedTestPlayers.ACTION_BAR + "Station warp to " + MOON + " in " + seconds + " s")
                            .toList()), "Countdown announcements differ: " + countdown);
                    helper.assertTrue(member.stream().anyMatch(line -> line.equals("Station warped: "
                            + CelestialIds.EARTH_ID + " -> " + MOON
                            + " (in-system, 2000000 FE); warp energy left 500000 FE.")), "Commit notice differs: " + member);
                    fixture.run(fixture.ownerId, "arce station warp " + CelestialIds.EARTH_ID);
                    expect(helper, owner, StationManagementCode.WARP_COOLDOWN, "warp right after the commit");
                } finally {
                    fixture.warp.installRocketAuthority(previous);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.warp.installRocketAuthority(previous);
                fixture.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_warp_interstellar", timeoutTicks = 400)
    public static void interstellarWarpCostsMoreWarnsAboutRoutesAndChangesTheContext(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Interstellar warp", CelestialIds.EARTH_ID, 9_000_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            CelestialSavedData discoveries = CelestialSavedData.get(fixture.server);
            if (discoveries.get(StarSystemContent.TAU_CETI_E).isEmpty()) {
                discoveries.discover(StarSystemContent.TAU_CETI_E, helper.getLevel().getGameTime());
            }
            fixture.core(fixture.pad.east(2));
            List<String> owner = fixture.join(fixture.ownerId, "starOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + StarSystemContent.TAU_CETI_E);
            helper.assertTrue(last(owner).contains("interstellar warp, cost 8000000 FE, warp energy 9000000 FE")
                    && last(owner).contains("The target's star system has no rocket routes."),
                    "Interstellar quote differs: " + last(owner));
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(fixture.station().orbitBody().equals(StarSystemContent.TAU_CETI_E)
                            && fixture.data.warpEnergy(fixture.id()) == 1_000_000, "Interstellar warp differs");
                    fixture.run(fixture.ownerId, "arce station environment");
                    helper.assertTrue(last(owner).contains("orbit=" + StarSystemContent.TAU_CETI_E)
                            && last(owner).contains("solar=0.50"), "Context did not follow: " + last(owner));
                } finally {
                    fixture.warp.installRocketAuthority(previous);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.warp.installRocketAuthority(previous);
                fixture.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_warp_rejections", timeoutTicks = 100)
    public static void requestsWithoutAuthorityCoreTargetEnergyOrKnownRocketStateAreRejected(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Rejections", CelestialIds.EARTH_ID, 0);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(StationRocketAuthority.FAIL_CLOSED);
        try {
            fixture.core(fixture.pad.east(2));
            List<String> owner = fixture.join(fixture.ownerId, "rejectOwner");
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            List<String> member = fixture.join(memberId, "rejectMember");
            UUID outsiderId = UUID.randomUUID();
            List<String> outsider = fixture.join(outsiderId, "rejectOutsider");
            String warpMoon = "arce station warp " + MOON;

            List<String> console = new ArrayList<>();
            fixture.run(fixture.server.createCommandSourceStack().withSource(ConnectedTestPlayers.capture(console)),
                    "execute as " + fixture.ownerId + " run " + warpMoon);
            expect(helper, console, StationManagementCode.NOT_LOCAL_PLAYER, "/execute as the owner");
            fixture.look(memberId, fixture.pad.east(2));
            fixture.run(memberId, warpMoon);
            expect(helper, member, StationManagementCode.UNAUTHORIZED, "member request");
            fixture.look(fixture.ownerId, fixture.pad.east(2).above(8));
            fixture.run(fixture.ownerId, warpMoon);
            expect(helper, owner, StationManagementCode.NO_WARP_CORE, "request without looking at a core");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + StarSystemContent.TAU_CETI);
            expect(helper, owner, StationManagementCode.WARP_TARGET_UNAVAILABLE, "star target");
            fixture.run(fixture.ownerId, "arce station warp advancedrocketrycommunity:no_such_body");
            expect(helper, owner, StationManagementCode.WARP_TARGET_UNAVAILABLE, "missing target");
            fixture.run(fixture.ownerId, "arce station warp " + CelestialIds.EARTH_ID);
            expect(helper, owner, StationManagementCode.WARP_SAME_ORBIT, "current orbit");
            fixture.run(fixture.ownerId, warpMoon);
            expect(helper, owner, StationManagementCode.WARP_INSUFFICIENT_ENERGY, "empty balance");
            helper.assertTrue(last(owner).contains("(in-system warp costs 2000000 FE; warp energy 0 FE)"),
                    "Rejection does not show the numbers: " + last(owner));
            fixture.data.foldWarpCredits(Map.of(fixture.id(), 3_000_000));
            fixture.run(fixture.ownerId, warpMoon);
            expect(helper, owner, StationManagementCode.WARP_ROCKETS_IN_MOTION, "unwired rocket authority");
            // The kill switch through a private service: setting the shared config value rewrites its file,
            // and Forge's file watcher can re-apply a stale value on another thread after it is restored.
            StationWarpService disabled = new StationWarpService(new CelestialCatalogManager(), system -> false,
                    () -> new WarpSettings(false, WarpSettings.DEFAULTS.inSystemCost(),
                            WarpSettings.DEFAULTS.interstellarCost()));
            helper.assertTrue(disabled.request(fixture.server.getPlayerList().getPlayer(fixture.ownerId), true, MOON)
                    .code() == StationManagementCode.WARP_DISABLED, "A disabled warp was not refused");
            helper.assertTrue(CommonConfig.warpSettings().equals(WarpSettings.DEFAULTS),
                    "The server's warp settings do not come from the default common config");
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            expect(helper, owner, StationManagementCode.WARP_NO_CONFIRMATION, "confirm without request");
            fixture.run(fixture.ownerId, "arce station warp cancel");
            expect(helper, owner, StationManagementCode.WARP_NO_COUNTDOWN, "cancel without countdown");
            // WARP review R6: status shows a pending credit without folding it and dirties nothing.
            helper.assertTrue(fixture.warp.receiveEnergy(fixture.space, fixture.pad.east(2), 1_000, false) == 1_000,
                    "The core fixture did not charge");
            fixture.data.flush(fixture.server);
            fixture.run(memberId, "arce station warp status");
            helper.assertTrue(last(member).startsWith("Warp status; station=" + fixture.id())
                    && last(member).contains("energy=3000000 FE pending=1000 FE")
                    && last(member).contains("countdown=none"), "Member status differs: " + last(member));
            helper.assertTrue(!fixture.data.isDirty() && fixture.warp.pendingCredit(fixture.id()) == 1_000,
                    "Status folded or dirtied the registry");
            fixture.run(outsiderId, "arce station warp status");
            expect(helper, outsider, StationManagementCode.UNAUTHORIZED, "outsider status");
            helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                    && fixture.data.warpEnergy(fixture.id()) == 3_000_000, "A rejected request changed the station");
            fixture.warp.fold(fixture.server);
            helper.assertTrue(fixture.data.warpEnergy(fixture.id()) == 3_001_000, "The pending credit was lost");
        } finally {
            fixture.warp.installRocketAuthority(previous);
            fixture.close();
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "station_warp_abort", timeoutTicks = 400)
    public static void cancelledChangedOrRepricedCountdownsNeverWarpOrCharge(GameTestHelper helper) {
        Fixture cancelled = new Fixture(helper, "Cancelled warp", CelestialIds.EARTH_ID, 5_000_000);
        Fixture changed = new Fixture(helper, "Changed warp", CelestialIds.EARTH_ID, 5_000_000);
        Fixture repriced = new Fixture(helper, "Repriced warp", CelestialIds.EARTH_ID, 5_000_000);
        StationRocketAuthority previous = cancelled.warp.installRocketAuthority(NOTHING_IN_MOTION);
        int configuredCost = CommonConfig.WARP_COST_IN_SYSTEM.get();
        boolean scheduled = false;
        try {
            List<List<String>> owners = new ArrayList<>();
            for (Fixture fixture : List.of(cancelled, changed, repriced)) {
                fixture.core(fixture.pad.east(2));
                owners.add(fixture.join(fixture.ownerId, "abortOwner" + owners.size()));
                fixture.look(fixture.ownerId, fixture.pad.east(2));
                fixture.run(fixture.ownerId, "arce station warp " + MOON);
                fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
                helper.assertTrue(last(owners.get(owners.size() - 1)).startsWith("Warp countdown started"),
                        "Countdown did not start: " + owners);
            }
            cancelled.run(cancelled.ownerId, "arce station warp cancel");
            helper.assertTrue(owners.get(0).stream().anyMatch(line -> line.startsWith("Station warp cancelled by"))
                    && last(owners.get(0)).startsWith("Warp countdown cancelled"), "Cancel differs: " + owners.get(0));
            changed.data.addMember(changed.id(), UUID.randomUUID());
            // A config change after consent changes the price: the commit must ask for new consent.
            CommonConfig.WARP_COST_IN_SYSTEM.set(configuredCost + 1_000_000);
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    for (Fixture fixture : List.of(cancelled, changed, repriced)) {
                        helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                                && fixture.data.warpEnergy(fixture.id()) == 5_000_000,
                                "An aborted warp moved or charged the station");
                    }
                    helper.assertTrue(owners.get(1).stream().anyMatch(line -> line.equals("Station warp aborted: "
                            + StationManagementCode.STATION_CHANGED.description() + ".")),
                            "Abort reason missing: " + owners.get(1));
                    helper.assertTrue(owners.get(0).stream().noneMatch(line -> line.startsWith("Station warp aborted")
                            || line.startsWith("Station warped")), "A cancelled countdown still committed");
                    helper.assertTrue(owners.get(2).stream().anyMatch(line -> line.equals("Station warp aborted: "
                            + StationManagementCode.WARP_QUOTE_CHANGED.description() + ".")),
                            "Repricing did not abort: " + owners.get(2));
                } finally {
                    CommonConfig.WARP_COST_IN_SYSTEM.set(configuredCost);
                    cancelled.warp.installRocketAuthority(previous);
                    cancelled.close();
                    changed.close();
                    repriced.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                CommonConfig.WARP_COST_IN_SYSTEM.set(configuredCost);
                cancelled.warp.installRocketAuthority(previous);
                cancelled.close();
                changed.close();
                repriced.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_warp_logout", timeoutTicks = 400)
    public static void countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Logout warp", CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority production = fixture.warp.installRocketAuthority(NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            helper.assertTrue(production != StationRocketAuthority.FAIL_CLOSED,
                    "The rocket module did not wire its in-motion rule (ADR-044 §5)");
            fixture.core(fixture.pad.east(2));
            UUID memberId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            List<String> member = fixture.join(memberId, "logoutMember");
            List<String> owner = fixture.join(fixture.ownerId, "logoutOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + MOON);
            // Logging out drops a pending confirmation.
            fixture.leave(fixture.ownerId);
            owner = fixture.join(fixture.ownerId, "logoutOwner");
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            expect(helper, owner, StationManagementCode.WARP_NO_CONFIRMATION, "confirmation after logout");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            fixture.run(fixture.ownerId, "arce station warp " + MOON);
            fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
            helper.assertTrue(last(owner).startsWith("Warp countdown started"), "Countdown did not start: " + owner);
            // ...but never cancels a running countdown.
            fixture.leave(fixture.ownerId);
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(fixture.station().orbitBody().equals(MOON)
                            && fixture.data.warpEnergy(fixture.id()) == 500_000, "The countdown did not survive logout");
                    helper.assertTrue(member.stream().anyMatch(line -> line.startsWith("Station warped: ")),
                            "The online member was not told: " + member);
                } finally {
                    fixture.warp.installRocketAuthority(production);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.warp.installRocketAuthority(production);
                fixture.close();
            }
        }
    }

    @GameTest(template = "empty", batch = "station_warp_delete", timeoutTicks = 100)
    public static void stationDeletionFailsClosedWhileTheTransferJournalIsBlocked(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Blocked journal", CelestialIds.EARTH_ID, 0);
        var storage = fixture.server.overworld().getDataStorage();
        RocketTransferSavedData journal = RocketTransferSavedData.get(fixture.server);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        RocketTransferSavedData blocked = RocketTransferSavedData.load(future);
        try {
            helper.assertTrue(journal.operational() && !blocked.operational() && !blocked.isDirty(),
                    "Journal fixture differs");
            storage.set(RocketTransferSavedData.DATA_NAME, blocked);
            StationManager manager = new StationManager(new CelestialCatalogManager());
            try {
                manager.delete(fixture.server, UUID.randomUUID(), true, fixture.id(), "confirm");
                helper.fail("A station was deleted while the transfer journal hid its records");
            } catch (IllegalStateException expected) {
                helper.assertTrue(expected.getMessage().contains("rocket authority"),
                        "Deletion failed for the wrong reason: " + expected.getMessage());
            }
            helper.assertTrue(fixture.data.find(fixture.id()).isPresent(), "The station was removed");
        } finally {
            storage.set(RocketTransferSavedData.DATA_NAME, journal);
            fixture.close();
        }
        helper.assertTrue(RocketTransferSavedData.get(fixture.server) == journal, "The journal was not restored");
        helper.succeed();
    }

    /**
     * WARP-05 (ADR-044 §2/§8, plan §13.3): ten stations charged every tick dirty the registry only when
     * credits are folded, at most once per 200-tick window; the operator diagnostics show the state.
     */
    @GameTest(template = "empty", batch = "station_warp_fold", timeoutTicks = 600)
    public static void tenChargingStationsDirtyTheRegistryOnlyOncePerFoldWindow(GameTestHelper helper) {
        List<Fixture> fixtures = new ArrayList<>();
        List<IEnergyStorage> cores = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            Fixture fixture = new Fixture(helper, "Charging " + index, CelestialIds.EARTH_ID, 0);
            fixtures.add(fixture);
            // Without a player, the chunk would unload and remove the core's block entity after a few ticks.
            fixture.keepLoaded();
            cores.add(fixture.core(fixture.pad.east(2)));
        }
        StationRegistrySavedData data = fixtures.get(0).data;
        StationWarpService warp = fixtures.get(0).warp;
        long[] accepted = {0L};
        int[] dirtyTicks = {0};
        data.setDirty(false);
        boolean scheduled = false;
        try {
            helper.onEachTick(() -> {
                if (data.isDirty()) {
                    dirtyTicks[0]++;
                    data.setDirty(false);
                }
                for (IEnergyStorage core : cores) {
                    accepted[0] += core.receiveEnergy(1_000, false);
                }
            });
            scheduled = true;
            helper.runAfterDelay(401, () -> {
                try {
                    long folded = fixtures.stream().mapToLong(fixture -> fixture.data.warpEnergy(fixture.id())).sum();
                    long pending = fixtures.stream().mapToLong(fixture -> warp.pendingCredit(fixture.id())).sum();
                    helper.assertTrue(accepted[0] >= 10L * 400L * 1_000L, "Cores stopped accepting: " + accepted[0]);
                    helper.assertTrue(folded + pending == accepted[0], "Credits were lost or duplicated: folded="
                            + folded + " pending=" + pending + " accepted=" + accepted[0]);
                    helper.assertTrue(folded > 0L, "Nothing was folded in 400 ticks");
                    helper.assertTrue(dirtyTicks[0] >= 1 && dirtyTicks[0] <= 3,
                            "The registry was dirtied " + dirtyTicks[0] + " times in 400 ticks of charging");
                    List<String> console = new ArrayList<>();
                    UUID first = fixtures.get(0).id();
                    fixtures.get(0).run(fixtures.get(0).server.createCommandSourceStack()
                            .withSource(ConnectedTestPlayers.capture(console)), "arce station admin warp " + first);
                    helper.assertTrue(console.size() == 3 && console.get(0).startsWith("warp enabled=true")
                                    && console.get(1).startsWith("transfer_journal=")
                                    && console.get(2).startsWith("station=" + first + " orbit=" + CelestialIds.EARTH_ID
                                    + " balance=" + fixtures.get(0).data.warpEnergy(first)),
                            "Operator diagnostics differ: " + console);
                } finally {
                    fixtures.forEach(Fixture::close);
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixtures.forEach(Fixture::close);
            }
        }
    }

    /** WARP review R2 (M10): the kill switch is rechecked at commit; a countdown aborts when it is off. */
    @GameTest(template = "empty", batch = "station_warp_killswitch", timeoutTicks = 400)
    public static void theKillSwitchAbortsARunningCountdown(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Kill switch", CelestialIds.EARTH_ID, 2_500_000);
        // A private service whose settings the test switches; the shared config file is never written
        // (Forge's file watcher can re-apply a stale value on another thread).
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        java.util.concurrent.atomic.AtomicReference<WarpSettings> settings =
                new java.util.concurrent.atomic.AtomicReference<>(WarpSettings.DEFAULTS);
        StationWarpService isolated = new StationWarpService(catalogs, system -> false, settings::get);
        isolated.installRocketAuthority(NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies(false))),
                    "The private catalog was rejected");
            fixture.core(fixture.pad.east(2));
            List<String> owner = fixture.join(fixture.ownerId, "killOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            ServerPlayer player = fixture.server.getPlayerList().getPlayer(fixture.ownerId);
            helper.assertTrue(isolated.request(player, true, MOON).code() == StationManagementCode.WARP_ISSUED,
                    "Request differs: " + owner);
            helper.assertTrue(isolated.confirm(player, true, fixture.id()).code() == StationManagementCode.WARP_STARTED,
                    "Countdown did not start: " + owner);
            settings.set(new WarpSettings(false, WarpSettings.DEFAULTS.inSystemCost(),
                    WarpSettings.DEFAULTS.interstellarCost()));
            helper.onEachTick(() -> isolated.onServerTick(
                    new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, fixture.server)));
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(isolated.countdown(fixture.id()).isEmpty(), "The countdown never ended");
                    helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                            && fixture.data.warpEnergy(fixture.id()) == 2_500_000, "A disabled warp committed");
                    helper.assertTrue(owner.contains("Station warp aborted: "
                            + StationManagementCode.WARP_DISABLED.description() + "."), "Abort differs: " + owner);
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.close();
            }
        }
    }

    /** WARP review R2 (M02): an operator who confirmed and is de-opped before the commit cannot warp. */
    @GameTest(template = "empty", batch = "station_warp_deop", timeoutTicks = 400)
    public static void aDeoppedOperatorsCountdownAborts(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "De-op", CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = fixture.warp.installRocketAuthority(NOTHING_IN_MOTION);
        UUID operatorId = UUID.randomUUID();
        com.mojang.authlib.GameProfile profile = new com.mojang.authlib.GameProfile(operatorId, "warpOperator");
        boolean scheduled = false;
        try {
            // PlayerList.op uses the GameTest server's operator level (0); an explicit level-4 entry is an operator.
            fixture.server.getPlayerList().getOps().add(new ServerOpListEntry(profile, 4, false));
            fixture.core(fixture.pad.east(2));
            List<String> operator = fixture.join(operatorId, "warpOperator", true);
            fixture.look(operatorId, fixture.pad.east(2));
            fixture.run(operatorId, "arce station warp " + MOON);
            fixture.run(operatorId, "arce station warp confirm " + fixture.id());
            helper.assertTrue(last(operator).startsWith("Warp countdown started"),
                    "An operator could not start the countdown: " + operator);
            fixture.server.getPlayerList().getOps().remove(profile);
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                            && fixture.data.warpEnergy(fixture.id()) == 2_500_000, "A de-opped operator warped");
                    helper.assertTrue(operator.contains("Station warp aborted: "
                            + StationManagementCode.WARP_ACTOR_CHANGED.description() + "."),
                            "Abort differs: " + operator);
                } finally {
                    fixture.server.getPlayerList().getOps().remove(profile);
                    fixture.warp.installRocketAuthority(previous);
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.server.getPlayerList().getOps().remove(profile);
                fixture.warp.installRocketAuthority(previous);
                fixture.close();
            }
        }
    }

    /** WARP review R2 (M03): two countdowns due in the same tick commit on consecutive ticks. */
    @GameTest(template = "empty", batch = "station_warp_serial", timeoutTicks = 400)
    public static void countdownsDueTogetherCommitOnePerTick(GameTestHelper helper) {
        Fixture first = new Fixture(helper, "Serial one", CelestialIds.EARTH_ID, 2_500_000);
        Fixture second = new Fixture(helper, "Serial two", CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = first.warp.installRocketAuthority(NOTHING_IN_MOTION);
        java.util.Map<UUID, Long> committedAt = new java.util.HashMap<>();
        boolean scheduled = false;
        try {
            int index = 0;
            for (Fixture fixture : List.of(first, second)) {
                fixture.core(fixture.pad.east(2));
                List<String> owner = fixture.join(fixture.ownerId, "serialOwner" + index++);
                fixture.look(fixture.ownerId, fixture.pad.east(2));
                fixture.run(fixture.ownerId, "arce station warp " + MOON);
                fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
                helper.assertTrue(last(owner).startsWith("Warp countdown started"), "Countdown did not start: " + owner);
            }
            helper.onEachTick(() -> {
                for (Fixture fixture : List.of(first, second)) {
                    if (!committedAt.containsKey(fixture.id()) && fixture.data.find(fixture.id())
                            .filter(state -> state.orbitBody().equals(MOON)).isPresent()) {
                        committedAt.put(fixture.id(), fixture.server.overworld().getGameTime());
                    }
                }
            });
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY + 3, () -> {
                try {
                    helper.assertTrue(committedAt.size() == 2, "Both warps must commit: " + committedAt);
                    long a = committedAt.get(first.id());
                    long b = committedAt.get(second.id());
                    helper.assertTrue(Math.abs(a - b) == 1,
                            "Due countdowns must commit one per tick, on consecutive ticks: " + committedAt);
                } finally {
                    first.warp.installRocketAuthority(previous);
                    first.close();
                    second.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                first.warp.installRocketAuthority(previous);
                first.close();
                second.close();
            }
        }
    }

    /**
     * WARP review R2 (ADR-044 §8): a target removed from the catalog during the countdown aborts the
     * commit. A private service on a private catalog, so the server's catalogs are never replaced.
     */
    @GameTest(template = "empty", batch = "station_warp_target_removed", timeoutTicks = 400)
    public static void aTargetRemovedDuringTheCountdownAborts(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, "Removed target", CelestialIds.EARTH_ID, 9_000_000);
        CelestialCatalogManager catalogs = new CelestialCatalogManager();
        StationWarpService isolated = new StationWarpService(catalogs, system -> false, () -> WarpSettings.DEFAULTS);
        isolated.installRocketAuthority(NOTHING_IN_MOTION);
        boolean scheduled = false;
        try {
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies(true))),
                    "The private catalog with the target was rejected");
            CelestialSavedData discoveries = CelestialSavedData.get(fixture.server);
            if (discoveries.get(StarSystemContent.TAU_CETI_E).isEmpty()) {
                discoveries.discover(StarSystemContent.TAU_CETI_E, helper.getLevel().getGameTime());
            }
            fixture.core(fixture.pad.east(2));
            List<String> owner = fixture.join(fixture.ownerId, "removedOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            ServerPlayer player = fixture.server.getPlayerList().getPlayer(fixture.ownerId);
            StationWarpResult issued = isolated.request(player, true, StarSystemContent.TAU_CETI_E);
            helper.assertTrue(issued.code() == StationManagementCode.WARP_ISSUED, "Request differs: " + issued.code());
            StationWarpResult started = isolated.confirm(player, true, fixture.id());
            helper.assertTrue(started.code() == StationManagementCode.WARP_STARTED, "Confirm differs: " + started.code());
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(bodies(false))),
                    "The private catalog without the target was rejected");
            helper.onEachTick(() -> isolated.onServerTick(
                    new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, fixture.server)));
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(isolated.countdown(fixture.id()).isEmpty(), "The countdown never ended");
                    helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                            && fixture.data.warpEnergy(fixture.id()) == 9_000_000, "A removed target was warped to");
                    helper.assertTrue(owner.contains("Station warp aborted: "
                            + StationManagementCode.WARP_TARGET_UNAVAILABLE.description() + "."),
                            "Abort differs: " + owner);
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.close();
            }
        }
    }

    private static List<CelestialBodyDefinition> bodies(boolean withStarSystem) {
        List<CelestialBodyDefinition> bodies = new ArrayList<>(CelestialDefaults.definitions());
        if (withStarSystem) {
            bodies.addAll(StarSystemContent.definitions());
        }
        return bodies;
    }

    private static void expect(GameTestHelper helper, List<String> replies, StationManagementCode code, String label) {
        helper.assertTrue(!replies.isEmpty() && last(replies).contains(code.description()),
                label + ": expected " + code + " but got " + replies);
    }

    private static String last(List<String> replies) {
        for (int index = replies.size() - 1; index >= 0; index--) {
            if (!replies.get(index).startsWith(ConnectedTestPlayers.ACTION_BAR)) {
                return replies.get(index);
            }
        }
        return "";
    }

    private static IEnergyStorage energy(ServerLevel level, BlockPos position) {
        var entity = level.getBlockEntity(position);
        if (entity == null) {
            throw new AssertionError("No warp core block entity at " + position);
        }
        return entity.getCapability(ForgeCapabilities.ENERGY, null)
                .orElseThrow(() -> new AssertionError("Warp core exposes no energy capability"));
    }

    /** One station in Space with its owner UUID, connected players, placed cores and cleanup. */
    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerLevel space;
        final StationRegistrySavedData data;
        final StationWarpService warp = StationWarpRuntime.service().orElseThrow();
        final StationPlatformGenerator platforms = new StationPlatformGenerator();
        final UUID ownerId = UUID.randomUUID();
        final StationState created;
        final BlockPos pad;
        final List<ServerPlayer> online = new ArrayList<>();
        final List<BlockPos> cores = new ArrayList<>();
        net.minecraft.world.level.ChunkPos ticket;

        Fixture(GameTestHelper helper, String name, ResourceLocation orbit, int balance) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.space = server.getLevel(CelestialIds.SPACE_LEVEL);
            this.data = StationRegistrySavedData.get(server);
            helper.assertTrue(space != null && data.operational() && !data.updatesQuarantined(),
                    "Station authority or Space is unavailable");
            created = new StationCreationService(platforms, body -> true)
                    .create(server, ownerId, name, orbit, false).station().orElseThrow();
            if (balance > 0) {
                data.foldWarpCredits(Map.of(created.stationId(), balance));
            }
            data.flush(server);
            pad = new BlockPos(created.landingPad().x(), StationLimits.LANDING_Y, created.landingPad().z());
            space.getChunkAt(pad); // Test setup only; production checks never load chunks.
        }

        UUID id() {
            return created.stationId();
        }

        void keepLoaded() {
            ticket = new net.minecraft.world.level.ChunkPos(pad);
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, ticket, 2, id());
        }

        void leave(UUID playerId) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            online.remove(player);
            server.getPlayerList().remove(player);
        }

        StationState station() {
            return data.find(id()).orElseThrow();
        }

        IEnergyStorage core(BlockPos position) {
            space.getChunkAt(position); // Test setup only.
            space.setBlockAndUpdate(position, ModBlocks.WARP_CORE.get().defaultBlockState());
            cores.add(position);
            return energy(space, position);
        }

        List<String> join(UUID playerId, String name) {
            return join(playerId, name, false);
        }

        List<String> join(UUID playerId, String name, boolean serverPermissions) {
            List<String> replies = new ArrayList<>();
            online.add(ConnectedTestPlayers.join(server, playerId, name, space, pad, replies, serverPermissions));
            return replies;
        }

        void look(UUID playerId, BlockPos target) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(target));
        }

        void run(UUID playerId, String command) {
            run(server.getPlayerList().getPlayer(playerId).createCommandSourceStack(), command);
        }

        void run(CommandSourceStack source, String command) {
            CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
            try {
                dispatcher.execute(command, source);
            } catch (CommandSyntaxException exception) {
                throw new AssertionError("Command syntax rejected: " + command, exception);
            }
        }

        CompoundTag stationFile() {
            return CheckedSavedDataFile.readPayload(server.getWorldPath(LevelResource.ROOT).resolve("data")
                    .resolve(ManagedSavedDataType.STATIONS.fileName()), ManagedSavedDataType.STATIONS).orElseThrow();
        }

        void close() {
            online.forEach(server.getPlayerList()::remove);
            online.clear();
            // A countdown left by a failed assertion aborts at its commit: the station no longer exists.
            cores.forEach(position -> space.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState()));
            cores.clear();
            data.delete(id());
            platforms.removeTemplate(space, created.cell());
            data.flush(server);
            if (ticket != null) {
                space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, ticket, 2, id());
                ticket = null;
            }
        }
    }
}
