package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Executes assembly transactions through one shared lock, ledger and release probe. */
final class RocketTransactionExecutor {
    private final RocketBlockEntityAdapters adapters;
    private final RocketRegionLockManager locks = new RocketRegionLockManager();
    private final RocketOperationLedger ledger = new RocketOperationLedger();
    private final RocketTransactionReleaseProbe releaseProbe = new RocketTransactionReleaseProbe(
            Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks"),
            System.getProperty(RocketTransactionReleaseProbe.PROPERTY));

    RocketTransactionExecutor(RocketBlockEntityAdapters adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters");
    }

    RocketBlockEntityAdapters adapters() {
        return adapters;
    }

    RocketTransactionResult assemble(
            MinecraftServer server,
            ServerLevel level,
            UUID ownerId,
            RocketStructureSnapshot snapshot
    ) {
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(server);
        var observed = releaseProbe.bind(
                server,
                RocketTransactionType.ASSEMBLY,
                snapshot,
                new ServerLevelRocketTransactionWorld(level, adapters, ownerId),
                savedData.journalFor(snapshot, ownerId)
        );
        return new RocketAssemblyTransaction(observed.world(), locks, ledger, observed.journal())
                .execute(UUID.randomUUID(), snapshot);
    }

    RocketTransactionResult disassemble(
            ServerLevel level,
            UUID ownerId,
            RocketEntity rocket,
            RocketStructureSnapshot snapshot
    ) {
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        var observed = releaseProbe.bind(
                level.getServer(),
                RocketTransactionType.DISASSEMBLY,
                snapshot,
                new ServerLevelRocketTransactionWorld(level, adapters, ownerId),
                savedData.journalFor(snapshot, ownerId)
        );
        return new RocketDisassemblyTransaction(observed.world(), locks, ledger, observed.journal())
                .execute(UUID.randomUUID(), rocket.getUUID(), snapshot);
    }

    boolean hasPendingRecovery(
            RocketTransactionSavedData savedData,
            RocketStructureSnapshot snapshot
    ) {
        RocketRegion requested = RocketRegion.fromSnapshot(snapshot);
        return savedData.entries().stream()
                .anyMatch(entry -> entry.record().region().overlaps(requested));
    }

    void clear() {
        locks.clear();
        ledger.clear();
        releaseProbe.clear();
    }
}
