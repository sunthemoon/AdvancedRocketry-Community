package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowCargoTransferFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowNativeEnvelope;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;

/** C-I recovery consumes the real cargo result, not a synthetic after-payload mutation. */
public final class RocketCargoRecoveryGameTestSupport {
    private RocketCargoRecoveryGameTestSupport() { }
    public static void verify(GameTestHelper helper, RocketEntity rocket, RocketStructureSnapshot before) {
        var server = helper.getLevel().getServer(); EscrowTestEndpoints.requireEnabled(server);
        helper.assertTrue(!before.contentHash().equals(rocket.snapshot().orElseThrow().contentHash())
                && before.snapshotId().equals(rocket.snapshot().orElseThrow().snapshotId()) && rocket.escrowSection().epoch() > 0,
                "C-I fixture must use an actual completed cargo publication");
        var committed = EscrowCargoTransferFixtures.record(rocket, before, false);
        helper.assertTrue(RocketTransferEntities.isCommittedAuthority(rocket, committed)
                && RocketTransferEntities.isReplaceableLandedAuthority(rocket, committed)
                && RocketTransferEntities.loadedMatches(server, committed, true).equals(List.of(rocket)), "Changed native arrival was not recognized");
        var stale = EscrowCargoTransferFixtures.record(rocket, before, true);
        var journal = new RocketTransferSavedData(); var recovery = new RocketTransferRecoveryService(new HashSet<>(), new HashSet<>());
        var after = EscrowNativeEnvelope.rocket(rocket);
        RocketEntity duplicate = null; net.minecraft.server.level.ServerPlayer player = null;
        java.nio.file.Path directory = null;
        try {
            directory = Files.createTempDirectory("arce-w124-cargo-identity-");
            journal.put(stale); RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve(
                    io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
            var result = recovery.recoverById(server, journal, stale.transferId());
            helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER
                    && recovery.fencedIds().contains(stale.transferId()) && journal.find(stale.transferId()).orElseThrow() == stale,
                    "Stale PREPARED replaced or retired the changed source");
            duplicate = ModEntities.ROCKET.get().create(helper.getLevel());
            helper.assertTrue(duplicate != null, "No destination fixture entity");
            duplicate.initializeTransferred(stale.destinationSnapshot(), stale.logicalRocketId(), stale.ownerId(),
                    stale.destinationFlightData().land(200));
            helper.assertTrue(helper.getLevel().addFreshEntity(duplicate), "Both-entity fixture publication failed");
            helper.assertTrue(RocketTransferEntities.loadedMatches(server, stale, false).equals(List.of(rocket))
                    && RocketTransferEntities.loadedMatches(server, stale, true).equals(List.of(duplicate)), "Native recovery election lost either end");
            result = recovery.recoverById(server, journal, stale.transferId());
            helper.assertTrue(result.status() == RocketTransferRecoveryService.Status.RETRY_LATER && duplicate.isAlive() && rocket.isAlive(),
                    "Both-entity recovery discarded a protected cargo authority");
            player = FirstEventGameTestPlayers.join(server, UUID.randomUUID(), "CargoReconnect", helper.getLevel(), rocket.blockPosition().above());
            var location = player.position(); recovery.onPlayerLoggedIn(player, journal); recovery.tickReconnects(server, journal);
            helper.assertTrue(player.getVehicle() == null && player.position().equals(location) && after.equals(EscrowNativeEnvelope.rocket(rocket))
                    && duplicate.isAlive() && journal.find(stale.transferId()).orElseThrow() == stale,
                    "Reconnect bypassed the stale-record fence or changed cargo");
        } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        finally {
            if (player != null) { server.getPlayerList().remove(player); }
            if (duplicate != null) { duplicate.discard(); }
            recovery.clear(); journal.abort(); RocketTransferEntities.releaseFlightTickets(server, stale);
            if (directory != null) {
                try { try (var files = Files.list(directory)) { for (var file : files.toList()) { Files.delete(file); } } Files.delete(directory); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
            }
        }
    }
}
