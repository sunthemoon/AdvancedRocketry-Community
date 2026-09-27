package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/** Owns the bounded assembler scan queue and commits successful scan results. */
final class RocketAssemblerScanService {
    private final RocketTransactionExecutor transactions;
    private final RocketComponentCatalog components;
    private final Map<AssemblerKey, PendingScan> pending = new LinkedHashMap<>();
    private final ArrayDeque<AssemblerKey> scanOrder = new ArrayDeque<>();

    RocketAssemblerScanService(RocketTransactionExecutor transactions, RocketComponentCatalog components) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.components = Objects.requireNonNull(components, "components");
    }

    void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(assemblerPosition, "assemblerPosition");
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos immutablePosition = assemblerPosition.immutable();
        RocketValidationCode requestFailure = validateAssemblerRequest(player, level, immutablePosition);
        if (requestFailure != null) {
            RocketInteraction.notify(player, requestFailure, "assembler request rejected");
            return;
        }
        RocketAssemblerBlockEntity assembler = RocketInteraction.assembler(level, immutablePosition);
        if (assembler == null) {
            RocketInteraction.notify(player, RocketValidationCode.ENTITY_STATE_INVALID, "assembler is unavailable");
            return;
        }
        if (assembler.blockedByFutureData()) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.UNSUPPORTED_SCHEMA,
                    null,
                    "assembler data uses a future schema",
                    level
            );
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.UNSUPPORTED_SCHEMA,
                    "assembler data uses a future schema"
            );
            return;
        }

        RocketValidationCode queued = enqueueScan(
                level,
                immutablePosition,
                player.getUUID(),
                player.getUUID(),
                assemble
        );
        if (queued != RocketValidationCode.SCAN_IN_PROGRESS) {
            RocketInteraction.notify(player, queued, "assembler scan was not queued");
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.scan_started"),
                true
        );
    }

    RocketValidationCode requestAdminAssembler(
            ServerLevel level,
            BlockPos assemblerPosition,
            UUID ownerId,
            boolean assemble
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(assemblerPosition, "assemblerPosition");
        Objects.requireNonNull(ownerId, "ownerId");
        BlockPos immutablePosition = assemblerPosition.immutable();
        if (!level.hasChunkAt(immutablePosition) || !level.hasChunkAt(immutablePosition.above())) {
            return RocketValidationCode.UNLOADED_CHUNK;
        }
        RocketAssemblerBlockEntity assembler = RocketInteraction.assembler(level, immutablePosition);
        if (assembler == null) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        if (assembler.blockedByFutureData()) {
            return RocketValidationCode.UNSUPPORTED_SCHEMA;
        }
        RocketValidationCode result = enqueueScan(level, immutablePosition, ownerId, null, assemble);
        if (result == RocketValidationCode.SCAN_IN_PROGRESS) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_ROCKET_SCAN_QUEUED operation={} dimension={} assembler={} owner={}",
                    assemble ? "assemble" : "validate",
                    level.dimension().location(),
                    immutablePosition.toShortString(),
                    ownerId
            );
        }
        return result;
    }

    void tick(MinecraftServer server) {
        while (!scanOrder.isEmpty()) {
            AssemblerKey key = scanOrder.removeFirst();
            PendingScan active = pending.get(key);
            if (active == null) {
                continue;
            }
            boolean keep = tickScan(server, key, active);
            if (keep) {
                scanOrder.addLast(key);
            } else {
                pending.remove(key);
            }
            // One task receives the entire fixed observation budget each server tick.
            break;
        }
    }

    int pendingScans() {
        return pending.size();
    }

    void clear() {
        pending.clear();
        scanOrder.clear();
    }

    private boolean tickScan(MinecraftServer server, AssemblerKey key, PendingScan active) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null || !level.hasChunkAt(key.position())) {
            return false;
        }
        RocketAssemblerBlockEntity assembler = RocketInteraction.assembler(level, key.position());
        if (assembler == null || assembler.blockedByFutureData()) {
            return false;
        }
        ServerPlayer player = requestingPlayer(server, level, key, active, assembler);
        if (active.playerId().isPresent() && player == null) {
            return false;
        }

        RocketScanResult result = active.task().step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        if (result.status() == RocketScanResult.Status.RUNNING) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.SCAN_IN_PROGRESS,
                    null,
                    "blocks=" + result.capturedBlocks()
                            + ", inspected=" + result.totalInspections()
                            + ", queued=" + result.queuedPositions(),
                    level
            );
            return true;
        }
        if (result.status() == RocketScanResult.Status.FAILED) {
            RocketValidationIssue issue = result.issues().get(0);
            String detail = RocketInteraction.issueDetail(issue);
            RocketInteraction.update(assembler, issue.code(), result.stats().orElse(null), detail, level);
            if (player != null) {
                RocketInteraction.notify(player, issue.code(), detail);
            }
            logScanResult(level, key.position(), active, issue.code(), detail, null);
            return false;
        }

        RocketStructureSnapshot snapshot = result.snapshot().orElseThrow();
        RocketInteraction.update(
                assembler,
                RocketValidationCode.SUCCESS,
                snapshot.stats(),
                "validated " + snapshot.contentHash(),
                level
        );
        if (!active.assemble()) {
            if (player != null) {
                RocketInteraction.notifyStats(player, snapshot, "validated");
            }
            logScanResult(level, key.position(), active, RocketValidationCode.SUCCESS, "validated", snapshot);
            return false;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(server);
        if (!savedData.operational()) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.UNSUPPORTED_SCHEMA,
                    snapshot.stats(),
                    "transaction journal is blocked",
                    level
            );
            if (player != null) {
                RocketInteraction.notify(
                        player,
                        RocketValidationCode.UNSUPPORTED_SCHEMA,
                        "transaction journal is blocked by unsupported data"
                );
            }
            return false;
        }
        if (transactions.hasPendingRecovery(savedData, snapshot)) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.REGION_BUSY,
                    snapshot.stats(),
                    "unfinished transaction owns the region",
                    level
            );
            if (player != null) {
                RocketInteraction.notify(
                        player,
                        RocketValidationCode.REGION_BUSY,
                        "an unfinished transaction still owns this region"
                );
            }
            return false;
        }

        RocketTransactionResult transaction = transactions.assemble(server, level, active.ownerId(), snapshot);
        reportTransaction(level, snapshot, player, transaction, "assembly");
        return false;
    }

    private static ServerPlayer requestingPlayer(
            MinecraftServer server,
            ServerLevel level,
            AssemblerKey key,
            PendingScan active,
            RocketAssemblerBlockEntity assembler
    ) {
        if (active.playerId().isEmpty()) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(active.playerId().orElseThrow());
        if (player == null || player.level() != level) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.UNAUTHORIZED,
                    null,
                    "requesting player disconnected or changed dimension",
                    level
            );
            return null;
        }
        if (!RocketInteraction.withinRange(player, key.position())) {
            RocketInteraction.update(
                    assembler,
                    RocketValidationCode.OUT_OF_RANGE,
                    null,
                    "requesting player left interaction range",
                    level
            );
            RocketInteraction.notify(player, RocketValidationCode.OUT_OF_RANGE, "assembler scan cancelled");
            return null;
        }
        return player;
    }

    private static void reportTransaction(
            ServerLevel level,
            RocketStructureSnapshot snapshot,
            ServerPlayer player,
            RocketTransactionResult result,
            String operation
    ) {
        RocketAssemblerBlockEntity assembler = RocketInteraction.assembler(
                level,
                new BlockPos(
                        snapshot.sourceOrigin().x(),
                        snapshot.sourceOrigin().y() - 1,
                        snapshot.sourceOrigin().z()
                )
        );
        String detail = result.success()
                ? operation + " committed blocks=" + result.changedBlocks()
                : RocketInteraction.issueDetail(result.issue().orElseThrow());
        if (assembler != null) {
            RocketInteraction.update(assembler, result.code(), snapshot.stats(), detail, level);
        }
        if (result.success()) {
            if (player != null) {
                RocketInteraction.notifyStats(player, snapshot, operation + " committed");
            }
        } else if (player != null) {
            RocketInteraction.notify(player, result.code(), detail);
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_ROCKET_TRANSACTION operation={} code={} blocks={} snapshot={} entity={}",
                operation,
                result.code(),
                result.changedBlocks(),
                snapshot.contentHash(),
                result.rocketEntityId().map(UUID::toString).orElse("none")
        );
    }

    private RocketValidationCode enqueueScan(
            ServerLevel level,
            BlockPos assemblerPosition,
            UUID ownerId,
            UUID playerId,
            boolean assemble
    ) {
        AssemblerKey key = new AssemblerKey(level.dimension(), assemblerPosition);
        if (pending.containsKey(key)) {
            return RocketValidationCode.REGION_BUSY;
        }
        if (pending.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
            return RocketValidationCode.OPERATION_LEDGER_FULL;
        }
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, transactions.adapters(), components),
                level.dimension().location(),
                toRocketPosition(assemblerPosition.above()),
                UUID.randomUUID(),
                level.getGameTime()
        );
        pending.put(key, new PendingScan(task, ownerId, Optional.ofNullable(playerId), assemble));
        scanOrder.addLast(key);
        RocketAssemblerBlockEntity assembler = RocketInteraction.assembler(level, assemblerPosition);
        if (assembler != null) {
            RocketInteraction.update(assembler, RocketValidationCode.SCAN_IN_PROGRESS, null, "queued", level);
        }
        return RocketValidationCode.SCAN_IN_PROGRESS;
    }

    private static void logScanResult(
            ServerLevel level,
            BlockPos assemblerPosition,
            PendingScan active,
            RocketValidationCode code,
            String detail,
            RocketStructureSnapshot snapshot
    ) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_ROCKET_SCAN operation={} code={} dimension={} assembler={} blocks={} snapshot={} detail={}",
                active.assemble() ? "assemble" : "validate",
                code,
                level.dimension().location(),
                assemblerPosition.toShortString(),
                snapshot == null ? 0 : snapshot.stats().blockCount(),
                snapshot == null ? "none" : snapshot.contentHash(),
                detail
        );
    }

    private static RocketValidationCode validateAssemblerRequest(
            ServerPlayer player,
            ServerLevel level,
            BlockPos position
    ) {
        if (!RocketInteraction.withinRange(player, position)) {
            return RocketValidationCode.OUT_OF_RANGE;
        }
        if (!level.hasChunkAt(position) || !level.hasChunkAt(position.above())) {
            return RocketValidationCode.UNLOADED_CHUNK;
        }
        return RocketInteraction.assembler(level, position) == null
                ? RocketValidationCode.ENTITY_STATE_INVALID
                : null;
    }

    private static RocketPosition toRocketPosition(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }

    private record AssemblerKey(ResourceKey<Level> dimension, BlockPos position) {
        private AssemblerKey {
            Objects.requireNonNull(dimension, "dimension");
            position = Objects.requireNonNull(position, "position").immutable();
        }
    }

    private record PendingScan(
            RocketStructureScanTask task,
            UUID ownerId,
            Optional<UUID> playerId,
            boolean assemble
    ) {
        private PendingScan {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(ownerId, "ownerId");
            Objects.requireNonNull(playerId, "playerId");
        }
    }
}
