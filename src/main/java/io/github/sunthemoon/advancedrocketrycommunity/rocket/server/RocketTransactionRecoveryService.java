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
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Idempotent loaded-region recovery for durable v0.5 transaction records. */
public final class RocketTransactionRecoveryService {
    public enum Outcome {
        NO_WORK,
        DEFERRED_UNLOADED,
        RECOVERED,
        CONFLICT
    }

    private final RocketBlockEntityAdapters adapters;

    public RocketTransactionRecoveryService(RocketBlockEntityAdapters adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters");
    }

    /** Attempts at most one loaded transaction, so restart work remains bounded per tick. */
    public Outcome recoverOne(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        RocketTransactionSavedData data = RocketTransactionSavedData.get(server);
        if (!data.operational() || data.entries().isEmpty()) {
            return Outcome.NO_WORK;
        }
        boolean sawUnloaded = false;
        for (RocketPersistedTransaction entry : data.entries()) {
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
            if (!world.isRegionLoaded(entry.record().region())) {
                sawUnloaded = true;
                continue;
            }
            return recoverLoaded(data, world, entry)
                    ? Outcome.RECOVERED
                    : Outcome.CONFLICT;
        }
        return sawUnloaded ? Outcome.DEFERRED_UNLOADED : Outcome.NO_WORK;
    }

    private boolean recoverLoaded(
            RocketTransactionSavedData data,
            ServerLevelRocketTransactionWorld world,
            RocketPersistedTransaction entry
    ) {
        List<UUID> matchingRockets = matchingRockets(world, entry);
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

    private static boolean recoverBlocks(
            ServerLevelRocketTransactionWorld world,
            RocketStructureSnapshot snapshot,
            List<UUID> matchingRockets
    ) {
        for (UUID rocketId : matchingRockets) {
            if (!world.removeRocket(rocketId, snapshot.snapshotId())) {
                return false;
            }
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
            if (current.isPresent()) {
                if (!current.orElseThrow().equals(expected)) {
                    return false;
                }
            } else if (!world.placeBlockIfEmpty(absolute, expected)) {
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
