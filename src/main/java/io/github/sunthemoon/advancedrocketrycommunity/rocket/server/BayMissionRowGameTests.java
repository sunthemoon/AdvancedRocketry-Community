package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W333 F2. One serial matrix avoids sharing the registry clock/retention fixture between concurrent variants. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BayMissionRowGameTests {
    private BayMissionRowGameTests() { }
    private enum Change { NONE, CANCEL, CLAIM, PRUNE, REPLACE }
    private enum Entry { BEFORE_PROOF, REPLAY, PROMOTION }

    @GameTest(template = TEMPLATE, batch = "w333_mission_rows", timeoutTicks = 12000)
    public static void initialMissionProofReplayAndPromotionMatrix(GameTestHelper h) {
        var queue = new ArrayDeque<Variant>();
        queue.add(new Variant(Change.NONE, Entry.BEFORE_PROOF));
        for (Change change : Change.values()) {
            if (change == Change.NONE) { continue; }
            for (Entry entry : Entry.values()) { queue.add(new Variant(change, entry)); }
        }
        var current = new Case[1];
        h.onEachTick(() -> {
            if (current[0] == null) {
                if (queue.isEmpty()) { h.succeed(); return; }
                current[0] = new Case(h, queue.remove());
            }
            try {
                if (current[0].tick()) { current[0].close(); current[0] = null; }
            } catch (Exception failure) {
                current[0].close(); throw new IllegalStateException("W333 " + current[0].variant, failure);
            } catch (Error failure) { current[0].close(); throw failure; }
        });
    }

    private record Variant(Change change, Entry entry) { }
    private static final class Case {
        final GameTestHelper h;
        final Variant variant;
        final UUID owner = UUID.randomUUID();
        final ItemStack pkg;
        final CountDownLatch release = new CountDownLatch(1);
        Flight flight;
        BayClaim claim;
        BayReceipt receipt;
        List<MissionState> expected;
        int stage, waits;
        boolean closed;
        Case(GameTestHelper h, Variant variant) { this.h = h; this.variant = variant; pkg = dataPackage(owner); }

        boolean tick() throws Exception {
            h.assertTrue(++waits < 850, "W333 mission variant exceeded bounded wait: " + variant + " stage=" + stage);
            var server = h.getLevel().getServer();
            var data = SatelliteMissionSavedData.get(server);
            if (stage == 0) {
                var origin = h.absolutePos(new BlockPos(3, 2, 3));
                var target = h.absolutePos(new BlockPos(14, 2, 3));
                if (!h.getLevel().isPositionEntityTicking(origin) || !h.getLevel().isPositionEntityTicking(target) || !storeIdle(server)) { return false; }
                flight = transit(h, origin, target, owner, List.of(pkg), RocketEscrowSectionPatch.NEUTRAL);
                if (variant.entry != Entry.BEFORE_PROOF) {
                    var identity = io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData.read(pkg).identity().orElseThrow();
                    h.assertTrue(data.launch(identity.satelliteId(), SatelliteManager.initialMissionId(identity.satelliteId()), owner,
                            definition(), ModIdentity.id("earth"), data.logicalGameTime(), false).success(), "Preexisting DATA seed refused");
                    mutate(data); expected = missions(data); data.flush(server);
                } else if (variant.change != Change.NONE) {
                    // Existing writer-policy injection, fixture-only reflection; the capture is held until the owner mutates history.
                    policy(data, CheckedSavedDataPolicy.atomic(ManagedSavedDataType.SATELLITE_MISSIONS).withCommitter((staged, targetFile) -> {
                        try {
                            if (!release.await(10, TimeUnit.SECONDS)) { throw new java.io.IOException("W333 history mutation did not release checkpoint"); }
                        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new java.io.IOException(interrupted); }
                        CheckedSavedDataFile.atomicMove(staged, targetFile);
                    }));
                }
                var journal = RocketTransferSavedData.get(server); journal.put(flight.record());
                if (variant.entry == Entry.PROMOTION) {
                    receipt = intent(server, flight.record(), pkg, 0);
                    h.assertTrue(BayReceiptTestHooks.put(server, receipt, outcome -> { }).queued(), "Restart INTENT refused");
                    stage = 1;
                } else {
                    bays(h).ascentComplete(server, journal, flight.record(), flight.source());
                    claim = claim(h, flight.record().logicalRocketId()).orElseThrow(); stage = 2;
                }
                return false;
            }
            if (stage == 1) {
                if (!storeIdle(server)) { return false; }
                h.assertTrue(bays(h).sessionForTests().acknowledged(receipt.op()), "INTENT was not durable before restart promotion");
                var claims = bays(h).restartForTests(List.of(receipt));
                h.assertTrue(claims.size() == 1 && claims.get(0).stage() == BayClaim.Stage.OUTCOMES, "Equal INTENT did not enter promotion");
                claim = claims.get(0); stage = 3; return false;
            }
            if (stage == 2 && data.satellite(satellite(pkg)).isPresent()) {
                if (variant.entry == Entry.BEFORE_PROOF) {
                    var initial = data.mission(SatelliteManager.initialMissionId(satellite(pkg))).orElseThrow();
                    h.assertTrue(initial.satelliteId().equals(satellite(pkg)) && initial.ownerId().equals(owner)
                            && initial.definitionId().equals(definition().id()) && initial.targetBodyId().equals(ModIdentity.id("earth")),
                            "Fresh DATA did not atomically create its deterministic captured-body mission");
                    mutate(data); expected = missions(data); release.countDown();
                    policy(data, CheckedSavedDataPolicy.atomic(ManagedSavedDataType.SATELLITE_MISSIONS));
                }
                stage = 3;
            }
            if (stage != 3 || claim(h, flight.record().logicalRocketId()).isPresent()) { return false; }
            h.assertTrue(claim.stage() == BayClaim.Stage.ENDED && claim.handoff().orElseThrow() == BayClaim.Handoff.READY_SOURCE,
                    "Mission history held/refused registration: " + variant + " " + claim.reason());
            h.assertTrue(missions(data).equals(expected) && data.satellites().stream().filter(s -> s.satelliteId().equals(satellite(pkg))).count() == 1,
                    "D3/D4/replay/promotion queried or recreated initial/current/history: " + variant);
            var current = RocketTransferSavedData.get(server).find(flight.record().transferId()).orElseThrow();
            var ops = claim.group().orElseThrow().operations();
            h.assertTrue(consumedOps(current.sourceSnapshot()).size() == 1 && ops.contains(consumedOps(current.sourceSnapshot()).get(0))
                    && ops.contains(consumedOps(current.destinationSnapshot()).get(0)) && bays(h).sessionForTests().find(satellite(pkg)).isEmpty(),
                    "Mission proof did not consume/retire its own operation");
            com.mojang.logging.LogUtils.getLogger().info("ARCE_W333_MISSION_ROW_PASS change={} entry={}", variant.change, variant.entry);
            return true;
        }

        SatelliteDefinition definition() { return new SatelliteDefinition(1, ModIdentity.id("data_satellite"), 200, 120, 100, List.of(ModIdentity.id("earth"))); }
        List<MissionState> missions(SatelliteMissionSavedData data) { return data.missions().stream().filter(m -> m.satelliteId().equals(satellite(pkg))).toList(); }
        void mutate(SatelliteMissionSavedData data) {
            UUID initial = SatelliteManager.initialMissionId(satellite(pkg));
            long now = data.logicalGameTime();
            if (variant.change == Change.NONE) { h.assertTrue(missions(data).size() == 1, "Fresh launch created extra missions"); return; }
            if (variant.change == Change.CLAIM) {
                data.completeDue(Math.max(now, data.mission(initial).orElseThrow().completesAtLogicalTime()));
                h.assertTrue(data.claim(initial, owner, data.logicalGameTime()).success(), "Initial claim refused");
                data.mission(initial).filter(m -> m.status() == MissionStatus.CLAIM_PENDING_DISCOVERY).ifPresent(m -> data.finishDiscovery(initial));
                h.assertTrue(data.mission(initial).orElseThrow().status() == MissionStatus.CLAIMED, "Initial claim did not finish");
                return;
            }
            h.assertTrue(data.cancel(initial, owner, false, now).success(), "Initial cancellation refused");
            if (variant.change == Change.REPLACE || variant.change == Change.PRUNE) {
                UUID later = UUID.randomUUID();
                h.assertTrue(data.startMission(satellite(pkg), later, owner, definition(), ModIdentity.id("earth"), now + 1, false).success(),
                        "Successor mission refused");
                if (variant.change == Change.REPLACE) {
                    h.assertTrue(data.satellite(satellite(pkg)).orElseThrow().currentMissionId().orElseThrow().equals(later), "Successor not current");
                } else {
                    h.assertTrue(data.cancel(later, owner, false, now + 2).success(), "Prune fixture cancellation refused");
                    var d = RegistryLimits.DEFAULTS;
                    data.applyLimits(new RegistryLimits(d.unfinishedGlobal(), d.unfinishedPerOwner(), 1, d.missionsTotal(), d.satellitesGlobal(),
                            d.satellitesPerOwner(), d.instancesGlobal(), d.instancesPerOwner(), d.intentTicks(), d.selectionTicks()));
                    data.completeDue(now + 1300);
                    h.assertTrue(data.mission(initial).isEmpty(), "Initial history was not actually pruned");
                }
            }
        }

        void close() {
            if (closed) { return; } closed = true; release.countDown();
            var server = h.getLevel().getServer();
            try { policy(SatelliteMissionSavedData.get(server), CheckedSavedDataPolicy.atomic(ManagedSavedDataType.SATELLITE_MISSIONS)); }
            catch (Exception failure) { throw new IllegalStateException(failure); }
            if (flight != null) {
                var journal = RocketTransferSavedData.get(server);
                journal.find(flight.record().transferId()).ifPresent(record -> {
                    record.destinationEntityId().map(h.getLevel()::getEntity).ifPresent(net.minecraft.world.entity.Entity::discard);
                    journal.remove(record.transferId());
                });
                RocketTransferEntities.releaseFlightTickets(server, flight.record());
                flight.source().discard(); bays(h).forgetForTests(flight.record().logicalRocketId());
            }
            bays(h).sessionForTests().rejections().remove(satellite(pkg));
            bays(h).sessionForTests().find(satellite(pkg)).ifPresent(row -> BayReceiptTestHooks.remove(server, row.op(), outcome -> { }));
        }
    }

    private static void policy(SatelliteMissionSavedData data, CheckedSavedDataPolicy policy) throws Exception {
        var method = SatelliteMissionSavedData.class.getDeclaredMethod("writePolicy", CheckedSavedDataPolicy.class);
        method.setAccessible(true); method.invoke(data, policy);
    }
}
