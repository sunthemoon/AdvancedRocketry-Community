package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** FM12a's internal plain hook, not player-disassembly coverage. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketI6PlainGameTests {
    private static final List<Scenario> RUNNING = new ArrayList<>();
    private RocketI6PlainGameTests() { }

    @GameTest(template = "rocket_test", batch = "w164_plain_proof", timeoutTicks = 1200)
    public static void plainHookKeepsTUntilCurrentDAndOldOriginAreProved(GameTestHelper helper) { start(helper, false); }

    @GameTest(template = "rocket_test", batch = "w164_plain_crew", timeoutTicks = 1200)
    public static void emptyCurrentManifestDoesNotEraseUnresolvedOldCrew(GameTestHelper helper) { start(helper, true); }

    private static void start(GameTestHelper helper, boolean crew) {
        var scenario = new Scenario(helper, crew); RUNNING.add(scenario); helper.onEachTick(scenario::tick);
    }
    @AfterBatch(batch = "w164_plain_proof")
    public static void cleanup(ServerLevel level) { RUNNING.forEach(Scenario::close); RUNNING.clear(); }
    @AfterBatch(batch = "w164_plain_crew")
    public static void cleanupCrew(ServerLevel level) { cleanup(level); }

    private static final class Scenario {
        final GameTestHelper helper;
        final ServerLevel source, destination;
        final GameTestTickPacer pacer;
        final boolean crew;
        RocketSourceRetirementFixtures.Flight fixture;
        RocketTransferRecord record;
        RocketEntity rocket;
        CompoundTag frozen;
        int step;
        int acknowledgedAt = -1;
        boolean observationTicket;
        boolean closed;

        Scenario(GameTestHelper helper, boolean crew) {
            this.helper = helper; this.crew = crew; source = helper.getLevel();
            destination = source.getServer().getLevel(CelestialIds.MOON_LEVEL);
            pacer = new GameTestTickPacer(source.getServer());
        }
        void tick() {
            if (closed) { return; }
            pacer.pace();
            var server = source.getServer(); var journal = RocketTransferSavedData.get(server);
            var transfers = RocketRuntime.installedTransfers();
            if (step == 0) {
                if (!journal.retryChecked(server)) { return; }
                fixture = RocketSourceRetirementFixtures.countdown(source, helper.absolutePos(new BlockPos(3, 2, 3)),
                        destination, new BlockPos(890008 + (crew ? 128 : 0), 100, 890008),
                        crew ? List.of(UUID.randomUUID()) : List.of(), 0);
                RocketTransferEntities.keepLoaded(source, fixture.record().sourceSnapshot(), fixture.record().transferId());
                RocketTransferEntities.keepLoaded(destination, fixture.record().destinationSnapshot(), fixture.record().transferId());
                // This observer outlives the production flight ticket, which ends on acknowledged release.
                // Without it a complete run can unload D between acknowledgement and this fixture's assertion.
                var slot = fixture.destinationSlot();
                observationTicket = !destination.getForcedChunks().contains(slot.toLong());
                if (observationTicket) { destination.setChunkForced(slot.x, slot.z, true); }
                step++; return;
            }
            if (step == 1) {
                if (!RocketTransferEntities.recoveryEndsAlreadyLoaded(server, fixture.record())) { return; }
                rocket = fixture.spawnDestination(helper, destination);
                rocket.updateFlightData(rocket.flightData().orElseThrow().land(destination.getGameTime())
                        .withPassengers(RocketPassengerManifest.empty(1)));
                RocketTransferEntities.positionAtOrigin(rocket, fixture.record().destinationSnapshot().sourceOrigin());
                record = fixture.record().destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                        .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
                journal.put(record); journal.flushChecked(server); step++; return;
            }
            if (step == 2) {
                if (!RocketRuntime.resourceMutationAllowed(rocket)) { return; }
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().fill(123).state(), destination.getGameTime()));
                frozen = new CompoundTag(); helper.assertTrue(rocket.save(frozen), "Cannot freeze D1");
                helper.assertTrue(!transfers.releaseLandedReservation(rocket), "Plain hook must first answer pending");
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "Plain hook removed T before proof");
                helper.assertTrue(!RocketRuntime.resourceMutationAllowed(rocket), "Pending proof permits fuel mutation");
                step++; return;
            }
            if (crew) {
                helper.assertTrue(journal.find(record.transferId()).orElse(null) == record, "Unresolved crew lost T");
                if (!transfers.ordinaryRetirements().pending(record.logicalRocketId())) {
                    helper.assertTrue(record.sourceFlightData().passengers().assignments().size() == 1, "Old obligation lost");
                    close(); helper.succeed();
                }
                return;
            }
            if (journal.find(record.transferId()).isPresent()) { return; }
            helper.assertTrue(RocketRetirementProof.matches(
                    RocketSourceRetirementFixtures.diskImage(destination, fixture.destinationSlot(), rocket.getUUID()), frozen),
                    "T disappeared without current D1 on disk");
            helper.assertTrue(RocketSourceRetirementFixtures.diskCopies(source, fixture.originSlot(), record.logicalRocketId()) == 0,
                    "T disappeared without old-origin exclusion");
            if (!transfers.releaseLandedReservation(rocket)) { return; }
            helper.assertTrue(journal.logicalAcknowledged(record.logicalRocketId()), "Completion preceded checked absence");
            helper.assertTrue(rocket.isAlive(), "Plain hook restored or removed D");
            helper.assertTrue(destination.getEntity(rocket.getUUID()) == rocket, "Release replaced the observed D");
            // Observe another real tick after the removal callback released the production ticket.
            if (acknowledgedAt < 0) { acknowledgedAt = server.getTickCount(); return; }
            if (server.getTickCount() == acknowledgedAt) { return; }
            close(); helper.succeed();
        }
        void close() {
            if (closed) { return; } closed = true;
            if (rocket != null) { rocket.discard(); }
            var server = source.getServer(); var journal = RocketTransferSavedData.get(server);
            if (record != null) { journal.remove(record.transferId()); RocketTransferEntities.releaseFlightTickets(server, record); }
            if (fixture != null) { RocketTransferEntities.releaseFlightTickets(server, fixture.record()); }
            if (observationTicket) {
                var slot = fixture.destinationSlot(); destination.setChunkForced(slot.x, slot.z, false);
            }
            journal.flushChecked(server);
        }
    }
}
