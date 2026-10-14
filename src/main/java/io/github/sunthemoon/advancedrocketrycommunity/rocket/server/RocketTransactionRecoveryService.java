package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketPersistedTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRecoveryDecision;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongPredicate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Idempotent loaded-region recovery for durable v0.5 transaction records. */
public final class RocketTransactionRecoveryService {
    public enum Outcome {
        NO_WORK,
        DEFERRED_UNLOADED,
        RECOVERED,
        CONFLICT
    }

    private final RocketBlockEntityAdapters adapters;
    private UUID lastAttempted;
    private final java.util.Set<UUID> fencedTransactions = new java.util.HashSet<>();
    private final java.util.Map<UUID, Long> fenceAttempts = new java.util.HashMap<>();

    public RocketTransactionRecoveryService(RocketBlockEntityAdapters adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters");
    }

    /** Attempts at most one loaded transaction, so restart work remains bounded per tick. */
    public Outcome recoverOne(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RocketTransactionSavedData data = RocketTransactionSavedData.get(server);
        if (!data.operational()) {
            lastAttempted = null;
            return Outcome.NO_WORK;
        }
        List<RocketPersistedTransaction> entries = data.entries();
        var present = entries.stream().map(entry -> entry.record().transactionId()).collect(java.util.stream.Collectors.toSet());
        fencedTransactions.retainAll(present); fenceAttempts.keySet().retainAll(present);
        if (entries.isEmpty()) {
            lastAttempted = null;
            return Outcome.NO_WORK;
        }
        int start = 0;
        if (lastAttempted != null) {
            while (start < entries.size()
                    && entries.get(start).record().transactionId().compareTo(lastAttempted) <= 0) {
                start++;
            }
        }
        boolean sawUnloaded = false;
        int fencedRetries = 0;
        for (int offset = 0; offset < entries.size(); offset++) {
            RocketPersistedTransaction entry = entries.get((start + offset) % entries.size());
            UUID transaction = entry.record().transactionId();
            long now = server.getTickCount();
            if (fencedTransactions.contains(transaction)
                    && now - fenceAttempts.getOrDefault(transaction, now) < RocketTransferFences.RETRY_TICKS) { continue; }
            if (fencedTransactions.contains(transaction)) {
                if (fencedRetries++ >= 1) { continue; }
                fenceAttempts.put(transaction, now);
            }
            var escrow = io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.ResourceEscrowSavedData.existing(server);
            var ledger = escrow == null ? null : escrow.ledger();
            boolean knownPending = ledger != null && ledger.records().stream().anyMatch(op ->
                    op.guard().rocket().equals(transaction)
                    || entry.record().rocketEntityIdOptional().filter(op.destination().identity()::equals).isPresent());
            if (knownPending || io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.logicalLocked(server, transaction)) {
                fence(transaction, now); continue;
            }
            ServerLevel level = levelFor(server, entry);
            if (level == null) {
                sawUnloaded = true;
                continue;
            }
            ServerLevelRocketTransactionWorld world = new ServerLevelRocketTransactionWorld(
                    level,
                    adapters,
                    entry.ownerId()
            );
            if (!readyForRecovery(world.isRegionLoaded(entry.record().region()),
                    entry.snapshot().sourceOrigin(), level::areEntitiesLoaded)) {
                sawUnloaded = true;
                continue;
            }
            lastAttempted = entry.record().transactionId();
            try {
                List<UUID> matches = matchingRockets(world, entry);
                var candidates = matches.stream().map(level::getEntity)
                        .filter(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class::isInstance)
                        .map(io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity.class::cast).toList();
                boolean assembly = entry.record().type() == RocketTransactionType.ASSEMBLY;
                boolean protectedCandidate = candidates.stream().anyMatch(rocket ->
                        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.logicalLocked(server,
                                rocket.assemblyTransactionId().orElse(transaction))
                        || io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence.transaction(ledger,
                                rocket.assemblyTransactionId().orElse(transaction), assembly, List.of(RocketTransferFences.state(rocket))));
                if (protectedCandidate || io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRecoveryFence.transaction(
                        ledger, transaction, assembly, List.of())) { fence(transaction, now); continue; }
                fencedTransactions.remove(transaction); fenceAttempts.remove(transaction);
                return recoverLoaded(data, world, entry, matches) ? Outcome.RECOVERED : Outcome.CONFLICT;
            } catch (RuntimeException exception) {
                // Keep the full journal snapshot and allow other entries next tick.
                return Outcome.CONFLICT;
            }
        }
        return sawUnloaded ? Outcome.DEFERRED_UNLOADED : Outcome.NO_WORK;
    }

    public void clear() {
        lastAttempted = null;
        fencedTransactions.clear(); fenceAttempts.clear();
    }

    private void fence(UUID transaction, long tick) {
        fenceAttempts.put(transaction, tick);
        if (fencedTransactions.add(transaction)) {
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(
                    "ARCE_ROCKET_RECOVERY_FENCED transaction={} repair=offline-whole-world", transaction);
        }
    }

    static boolean readyForRecovery(
            boolean regionLoaded, RocketPosition origin, LongPredicate entityChunksLoaded
    ) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(entityChunksLoaded, "entityChunksLoaded");
        // Block chunks can arrive before their persisted entities. A missing
        // entity is authoritative only after the origin's entity read finishes.
        // Assembly/disassembly entities are anchored at this snapshot origin;
        // this query neither loads a chunk nor creates a recovery ticket.
        return regionLoaded && entityChunksLoaded.test(ChunkPos.asLong(origin.x() >> 4, origin.z() >> 4));
    }

    private boolean recoverLoaded(
            RocketTransactionSavedData data,
            ServerLevelRocketTransactionWorld world,
            RocketPersistedTransaction entry,
            List<UUID> matchingRockets
    ) {
        if (!world.canRestoreSnapshot(entry.snapshot())) {
            return false;
        }
        RocketRecoveryDecision.Authority authority = RocketRecoveryDecision.authority(
                entry.record().type(),
                entry.record().phase(),
                !matchingRockets.isEmpty()
        );
        boolean recovered = authority == RocketRecoveryDecision.Authority.BLOCKS
                ? recoverBlocks(world, entry.snapshot(), matchingRockets)
                : recoverEntity(world, entry.snapshot(), matchingRockets);
        if (recovered) {
            data.journalFor(entry.snapshot(), entry.ownerId())
                    .remove(entry.record().transactionId());
        }
        return recovered;
    }

    private static List<UUID> matchingRockets(
            ServerLevelRocketTransactionWorld world,
            RocketPersistedTransaction entry
    ) {
        ArrayList<UUID> matches = new ArrayList<>();
        if (entry.record().type() == RocketTransactionType.ASSEMBLY) {
            matches.addAll(world.matchingAssemblyRockets(
                    entry.record().transactionId(),
                    entry.snapshot()
            ));
        }
        entry.record().rocketEntityIdOptional()
                .filter(id -> world.rocketMatches(
                        id,
                        entry.snapshot().snapshotId(),
                        entry.snapshot().contentHash()
                ))
                .filter(id -> !matches.contains(id))
                .ifPresent(matches::add);
        matches.sort(Comparator.naturalOrder());
        return List.copyOf(matches);
    }

    static boolean recoverBlocks(
            RocketTransactionWorld world,
            RocketStructureSnapshot snapshot,
            List<UUID> matchingRockets
    ) {
        ArrayList<RocketBlock> missing = new ArrayList<>();
        // Inspect the complete bounded target before changing either authority.
        for (RocketBlock block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            RocketWorldBlock expected = RocketWorldBlock.fromSnapshotBlock(block);
            Optional<RocketWorldBlock> current;
            try {
                current = world.readBlock(absolute);
            } catch (RuntimeException exception) {
                return false;
            }
            if (current.isPresent()) {
                if (!current.orElseThrow().equals(expected)) {
                    return false;
                }
            } else {
                missing.add(block);
            }
        }
        // Availability/conflicts have been checked without mutation. Retire
        // entity authority before exposing restored inventories. The complete
        // journal remains authoritative until every restoration succeeds.
        for (UUID rocketId : matchingRockets) {
            if (!world.removeRocket(rocketId, snapshot.snapshotId())) {
                return false;
            }
        }
        for (RocketBlock block : missing) {
            if (!world.placeBlockIfEmpty(snapshot.sourceOrigin().add(block.position()),
                    RocketWorldBlock.fromSnapshotBlock(block))) {
                return false;
            }
        }
        return true;
    }

    private static boolean recoverEntity(
            ServerLevelRocketTransactionWorld world,
            RocketStructureSnapshot snapshot,
            List<UUID> matchingRockets
    ) {
        if (matchingRockets.isEmpty()) {
            return recoverBlocks(world, snapshot, List.of());
        }
        UUID keeper = matchingRockets.get(0);
        for (int index = 1; index < matchingRockets.size(); index++) {
            if (!world.removeRocket(matchingRockets.get(index), snapshot.snapshotId())) {
                return false;
            }
        }
        if (!world.rocketMatches(keeper, snapshot.snapshotId(), snapshot.contentHash())) {
            return false;
        }
        for (RocketBlock block : snapshot.blocks()) {
            RocketPosition absolute = snapshot.sourceOrigin().add(block.position());
            RocketWorldBlock expected = RocketWorldBlock.fromSnapshotBlock(block);
            Optional<RocketWorldBlock> current;
            try {
                current = world.readBlock(absolute);
            } catch (RuntimeException exception) {
                return false;
            }
            if (current.isPresent()
                    && (!current.orElseThrow().equals(expected)
                    || !world.removeBlockNoDrops(absolute, expected))) {
                return false;
            }
        }
        return true;
    }

    private static ServerLevel levelFor(
            MinecraftServer server,
            RocketPersistedTransaction entry
    ) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().location().equals(entry.record().region().dimension())) {
                return level;
            }
        }
        return null;
    }
}
