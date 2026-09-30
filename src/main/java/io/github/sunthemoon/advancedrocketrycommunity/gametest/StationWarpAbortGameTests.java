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
 * Warp countdowns that must abort or wait: the kill switch, a de-opped operator, one commit per tick,
 * a removed target and a changed price (WARP review R2, final review A2). Split from
 * StationWarpGameTests (final review A8); it shares that class's fixture.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationWarpAbortGameTests {
    private static final ResourceLocation MOON = StationWarpGameTests.MOON;
    private static final StationRocketAuthority NOTHING_IN_MOTION = StationWarpGameTests.NOTHING_IN_MOTION;
    private static final int COMMIT_DELAY = StationWarpGameTests.COMMIT_DELAY;

    private StationWarpAbortGameTests() {
    }

    /** WARP review R2 (M10): the kill switch is rechecked at commit; a countdown aborts when it is off. */
    @GameTest(template = "empty", batch = "station_warp_killswitch", timeoutTicks = 400)
    public static void theKillSwitchAbortsARunningCountdown(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Kill switch", CelestialIds.EARTH_ID, 2_500_000);
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
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "De-op", CelestialIds.EARTH_ID, 2_500_000);
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
        StationWarpGameTests.Fixture first = new StationWarpGameTests.Fixture(helper, "Serial one", CelestialIds.EARTH_ID, 2_500_000);
        StationWarpGameTests.Fixture second = new StationWarpGameTests.Fixture(helper, "Serial two", CelestialIds.EARTH_ID, 2_500_000);
        StationRocketAuthority previous = first.warp.installRocketAuthority(NOTHING_IN_MOTION);
        java.util.Map<UUID, Long> committedAt = new java.util.HashMap<>();
        boolean scheduled = false;
        try {
            int index = 0;
            for (StationWarpGameTests.Fixture fixture : List.of(first, second)) {
                fixture.core(fixture.pad.east(2));
                List<String> owner = fixture.join(fixture.ownerId, "serialOwner" + index++);
                fixture.look(fixture.ownerId, fixture.pad.east(2));
                fixture.run(fixture.ownerId, "arce station warp " + MOON);
                fixture.run(fixture.ownerId, "arce station warp confirm " + fixture.id());
                helper.assertTrue(last(owner).startsWith("Warp countdown started"), "Countdown did not start: " + owner);
            }
            helper.onEachTick(() -> {
                for (StationWarpGameTests.Fixture fixture : List.of(first, second)) {
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
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Removed target", CelestialIds.EARTH_ID, 9_000_000);
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


    /**
     * ADR-044 §4: a price change after consent (a config reload) makes the commit ask for new consent.
     * A private service with switchable settings, so the shared config file is never written (final
     * review A2: Forge's file watcher can re-apply a stale value on another thread).
     */
    @GameTest(template = "empty", batch = "station_warp_repriced", timeoutTicks = 400)
    public static void aRepricedCountdownAborts(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Repriced warp", CelestialIds.EARTH_ID, 5_000_000);
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
            List<String> owner = fixture.join(fixture.ownerId, "repricedOwner");
            fixture.look(fixture.ownerId, fixture.pad.east(2));
            ServerPlayer player = fixture.server.getPlayerList().getPlayer(fixture.ownerId);
            helper.assertTrue(isolated.request(player, true, MOON).code() == StationManagementCode.WARP_ISSUED,
                    "Request differs: " + owner);
            helper.assertTrue(isolated.confirm(player, true, fixture.id()).code() == StationManagementCode.WARP_STARTED,
                    "Countdown did not start: " + owner);
            settings.set(new WarpSettings(true, WarpSettings.DEFAULTS.inSystemCost() + 1_000_000,
                    WarpSettings.DEFAULTS.interstellarCost()));
            helper.onEachTick(() -> isolated.onServerTick(
                    new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, fixture.server)));
            scheduled = true;
            helper.runAfterDelay(COMMIT_DELAY, () -> {
                try {
                    helper.assertTrue(fixture.station().orbitBody().equals(CelestialIds.EARTH_ID)
                            && fixture.data.warpEnergy(fixture.id()) == 5_000_000, "A repriced warp moved or charged");
                    helper.assertTrue(owner.stream().anyMatch(line -> line.equals("Station warp aborted: "
                                    + StationManagementCode.WARP_QUOTE_CHANGED.description() + ".")),
                            "Repricing did not abort: " + owner);
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

    private static String last(List<String> replies) {
        return StationWarpGameTests.last(replies);
    }

    private static void expect(GameTestHelper helper, List<String> replies, StationManagementCode code, String label) {
        StationWarpGameTests.expect(helper, replies, code, label);
    }
}
