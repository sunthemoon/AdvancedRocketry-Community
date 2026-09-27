package io.github.sunthemoon.advancedrocketrycommunity.satellite.service;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.SatelliteIds;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Network-free lifecycle/claim adapter check; does not simulate a native process kill. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiscoveryRecoveryGameTests {
    private DiscoveryRecoveryGameTests() { }

    @GameTest(template = "empty", batch = "planetary_discovery_recovery", timeoutTicks = 40)
    public static void oldPaidReceiptAndNewRemovedTargetRecoverWithoutAnotherClaim(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var missions = SatelliteMissionSavedData.get(server);
        var progress = CelestialSavedData.get(server);
        var owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "RecoveryOwner"));
        var all = new ArrayList<>(CelestialDefaults.definitions()); all.addAll(PlanetaryContent.definitions());
        var catalogs = new CelestialCatalogManager();
        helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(all)), "Fixture catalog rejected");
        var satellites = new SatelliteCatalogManager();
        helper.assertTrue(satellites.applyCandidate(SatelliteCatalog.create(List.of(PlanetaryContent.surveySatellite()),
                all.stream().map(CelestialBodyDefinition::id).toList())), "Fixture satellite definition rejected");
        var notifications = new AtomicInteger();
        var manager = new SatelliteManager(satellites, catalogs, ignored -> notifications.incrementAndGet());
        var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, server);
        UUID oldMission = UUID.randomUUID();
        try {
            long time = server.overworld().getGameTime();
            missions.launch(UUID.randomUUID(), oldMission, owner.getUUID(), PlanetaryContent.surveySatellite(),
                    PlanetaryContent.MARS, time, true);
            missions.completeDue(time + 200);
            missions.claim(oldMission, owner.getUUID(), time + 201);
            missions.finishDiscovery(oldMission); missions.flush(server);
            var original = missions.mission(oldMission).orElseThrow();
            manager.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.START, () -> true, server));
            helper.assertTrue(progress.get(PlanetaryContent.MARS).isEmpty(), "START phase applied discovery");
            manager.onServerTick(tick);
            helper.assertTrue(progress.get(PlanetaryContent.MARS).isPresent()
                    && original.equals(missions.mission(oldMission).orElseThrow()), "Startup lost or rewrote the historical receipt");
            helper.assertTrue(missions.account(owner.getUUID()).balance() == 20, "Startup paid historical research twice");

            var identity = new SatelliteIdentity(UUID.randomUUID(), owner.getUUID(), SatelliteIds.DATA_SATELLITE);
            var obstacle = server.getWorldPath(LevelResource.ROOT).resolve("data")
                    .resolve(SatelliteMissionSavedData.DATA_NAME + ".dat.arce-pending");
            java.nio.file.Files.createDirectory(obstacle);
            try {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_DISCOVERY_EXPECTED_SAVE_FAILURE testing launch acknowledgment");
                helper.assertTrue(manager.launch(owner, identity, PlanetaryContent.VENUS).code()
                        == SatelliteOperationCode.UNSUPPORTED_DATA, "Unacknowledged launch was reported successful");
            } finally { java.nio.file.Files.delete(obstacle); }
            helper.assertTrue(manager.launch(owner, identity, PlanetaryContent.VENUS).success(), "Second mission did not launch");
            var acknowledgedLaunch = SatelliteMissionSavedData.load(NbtIo.readCompressed(server.getWorldPath(LevelResource.ROOT)
                    .resolve("data").resolve(SatelliteMissionSavedData.DATA_NAME + ".dat").toFile()).getCompound("data"));
            helper.assertTrue(acknowledgedLaunch.satellite(identity.satelliteId()).isPresent(),
                    "Idempotent launch retry skipped the failed persistence acknowledgment");
            UUID next = missions.satellite(identity.satelliteId()).orElseThrow().currentMissionId().orElseThrow();
            var started = missions.mission(next).orElseThrow();
            missions.completeDue(time + 402);
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(all.stream()
                    .filter(body -> !body.id().equals(PlanetaryContent.VENUS)).toList())), "Removed fixture catalog rejected");
            for (int retry = 0; retry < 3; retry++) {
                helper.assertTrue(manager.claimCurrent(owner, identity).code() == SatelliteOperationCode.CATALOG_UNAVAILABLE,
                        "Removed target was not retained for recovery");
            }
            helper.assertTrue(missions.account(owner.getUUID()).balance() == 40
                    && missions.mission(next).orElseThrow().status() == MissionStatus.CLAIM_PENDING_DISCOVERY,
                    "Unavailable claim lost its paid receipt or charged twice");
            manager.onServerTick(tick);
            helper.assertTrue(progress.get(PlanetaryContent.VENUS).isEmpty(), "Replay manufactured a removed target");
            helper.assertTrue(catalogs.applyCandidate(CelestialCatalog.create(all)), "Restored fixture catalog rejected");
            manager.onServerTick(tick); // No restart and no second player claim.
            helper.assertTrue(progress.get(PlanetaryContent.VENUS).isPresent()
                    && missions.mission(next).orElseThrow().status() == MissionStatus.CLAIMED,
                    "New runtime pending receipt was never enrolled for replay");
            helper.assertTrue(missions.account(owner.getUUID()).balance() == 40
                    && missions.mission(next).orElseThrow().completesAtLogicalTime() == started.completesAtLogicalTime(),
                    "Replay changed research or the captured mission deadline");
            var disk = server.getWorldPath(LevelResource.ROOT).resolve("data");
            var restored = SatelliteMissionSavedData.load(NbtIo.readCompressed(
                    disk.resolve(SatelliteMissionSavedData.DATA_NAME + ".dat").toFile()).getCompound("data"));
            var restoredProgress = CelestialSavedData.load(NbtIo.readCompressed(
                    disk.resolve(CelestialSavedData.DATA_NAME + ".dat").toFile()).getCompound("data"));
            helper.assertTrue(restored.account(owner.getUUID()).balance() == 40
                    && restored.mission(next).orElseThrow().status() == MissionStatus.CLAIMED
                    && restoredProgress.get(PlanetaryContent.MARS).isPresent()
                    && restoredProgress.get(PlanetaryContent.VENUS).isPresent(), "Acknowledged disk stores disagree");
            helper.assertTrue(notifications.get() == 2, "Discovery publication did not notify exactly once per new body");
            progress.recordVisit(PlanetaryContent.VENUS, time);
            missions.setDirty();
            server.overworld().getDataStorage().save();
            var autosaved = CelestialSavedData.load(NbtIo.readCompressed(
                    disk.resolve(CelestialSavedData.DATA_NAME + ".dat").toFile()).getCompound("data"));
            helper.assertTrue(!progress.isDirty() && !missions.isDirty()
                    && autosaved.get(PlanetaryContent.VENUS).orElseThrow().firstVisitAt().orElseThrow() == time,
                    "Ordinary DataStorage save did not dispatch to the checked authorities");
            helper.succeed();
        } catch (java.io.IOException exception) { throw new IllegalStateException("Fixture disk read failed", exception); }
        finally { manager.clear(); }
    }
}
