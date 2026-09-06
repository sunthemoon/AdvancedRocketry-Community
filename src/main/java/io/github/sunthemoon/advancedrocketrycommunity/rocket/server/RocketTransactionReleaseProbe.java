package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.testing.RocketTransactionFaultProbe;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import java.util.concurrent.TimeUnit;
import net.minecraft.server.MinecraftServer;

/** Single-use diagnostic owned by one RocketManager, absent from ordinary gameplay. */
final class RocketTransactionReleaseProbe {
    static final String PROPERTY = "advancedrocketrycommunity.transactionCheckpoint";
    private RocketTransactionCheckpoint checkpoint;

    RocketTransactionReleaseProbe(boolean enabled, String selection) {
        checkpoint = enabled && selection != null ? RocketTransactionCheckpoint.parse(selection) : null;
    }

    Bindings bind(MinecraftServer server, RocketTransactionType type, RocketStructureSnapshot snapshot,
                  RocketTransactionWorld world, RocketTransactionJournal journal) {
        if (checkpoint == null || checkpoint.type() != type) {
            return new Bindings(world, journal);
        }
        if (!server.isDedicatedServer() || !server.isSameThread()
                || server.getPlayerList().getPlayerCount() != 0) {
            throw new IllegalStateException("Transaction checkpoint requires an empty dedicated test server");
        }
        RocketTransactionCheckpoint selected = checkpoint;
        selected.validateBlockCount(snapshot.blocks().size());
        checkpoint = null;
        RocketTransactionFaultProbe probe = new RocketTransactionFaultProbe(selected,
                record -> persistAndPause(server, snapshot, record));
        return new Bindings(probe.world(world), probe.journal(journal));
    }

    void clear() {
        checkpoint = null;
    }

    private static void persistAndPause(
            MinecraftServer server, RocketStructureSnapshot snapshot, RocketTransactionRecord record
    ) {
        if (!server.saveEverything(false, true, true)) {
            throw new IllegalStateException("Could not flush the actual transaction checkpoint");
        }
        var origin = snapshot.sourceOrigin();
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TRANSACTION_PAUSED type={} phase={} progress={} transaction={} entity={} "
                        + "snapshot={} blocks={} dimension={} origin={},{},{}",
                record.type(), record.phase(), record.progress(), record.transactionId(),
                record.rocketEntityIdOptional().map(Object::toString).orElse("none"),
                snapshot.contentHash(), snapshot.blocks().size(), snapshot.sourceDimension(),
                origin.x(), origin.y(), origin.z());
        try {
            TimeUnit.SECONDS.sleep(20L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        AdvancedRocketryCommunity.LOGGER.warn("ARCE_RELEASE_TRANSACTION_CHECKPOINT_EXPIRED transaction={}",
                record.transactionId());
        throw new IllegalStateException("External checkpoint kill did not arrive within the fixed wait");
    }

    record Bindings(RocketTransactionWorld world, RocketTransactionJournal journal) {
    }
}
