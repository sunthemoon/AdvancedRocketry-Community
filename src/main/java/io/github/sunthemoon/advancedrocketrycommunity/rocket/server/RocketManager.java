package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketScanWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.ServerLevelRocketTransactionWorld;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketScanResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.scan.RocketStructureScanTask;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketAssemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketDisassemblyTransaction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegionLockManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
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
import net.minecraftforge.event.TickEvent;

/** Lifecycle-owned, main-thread authority for v0.5 scan and transaction intents. */
public final class RocketManager implements RocketOperationService {
    public static final double MAX_INTERACTION_DISTANCE_SQUARED = 64.0D;

    private final RocketBlockEntityAdapters adapters;
    private final RocketRegionLockManager locks = new RocketRegionLockManager();
    private final RocketOperationLedger ledger = new RocketOperationLedger();
    private final RocketTransactionRecoveryService recovery;
    private final Map<AssemblerKey, PendingScan> pending = new LinkedHashMap<>();
    private final ArrayDeque<AssemblerKey> scanOrder = new ArrayDeque<>();
    private boolean recoverySuppressedForReleaseTest;

    public RocketManager() {
        this(RocketBlockEntityAdapters.defaults());
    }

    public RocketManager(RocketBlockEntityAdapters adapters) {
        this.adapters = Objects.requireNonNull(adapters, "adapters");
        recovery = new RocketTransactionRecoveryService(adapters);
    }

    @Override
    public void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(assemblerPosition, "assemblerPosition");
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos immutablePosition = assemblerPosition.immutable();
        RocketValidationCode requestFailure = validateAssemblerRequest(player, level, immutablePosition);
        if (requestFailure != null) {
            notify(player, requestFailure, "assembler request rejected");
            return;
        }
        RocketAssemblerBlockEntity assembler = assembler(level, immutablePosition);
        if (assembler == null) {
            notify(player, RocketValidationCode.ENTITY_STATE_INVALID, "assembler is unavailable");
            return;
        }
        if (assembler.blockedByFutureData()) {
            update(assembler, RocketValidationCode.UNSUPPORTED_SCHEMA, null, "assembler data uses a future schema", level);
            notify(player, RocketValidationCode.UNSUPPORTED_SCHEMA, "assembler data uses a future schema");
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
            notify(player, queued, "assembler scan was not queued");
            return;
        }
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.scan_started"),
                true
        );
    }

    /** Queues an operator-authorized scan without inventing a fake player identity. */
    public RocketValidationCode requestAdminAssembler(
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
        RocketAssemblerBlockEntity assembler = assembler(level, immutablePosition);
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

    @Override
    public void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rocket, "rocket");
        if (!(player.level() instanceof ServerLevel level)
                || rocket.level() != level
                || !rocket.isAlive()
                || !level.hasChunkAt(rocket.blockPosition())) {
            notify(player, RocketValidationCode.ENTITY_STATE_INVALID, "rocket is unavailable");
            return;
        }
        if (!withinRange(player, rocket.getX(), rocket.getY(), rocket.getZ())) {
            notify(player, RocketValidationCode.OUT_OF_RANGE, "rocket is beyond interaction range");
            return;
        }
        if (!rocket.operational()) {
            notify(player, RocketValidationCode.UNSUPPORTED_SCHEMA, "rocket data is unavailable or unsupported");
            return;
        }
        UUID owner = rocket.ownerId().orElseThrow();
        if (!owner.equals(player.getUUID()) && !player.isCreative() && !player.hasPermissions(2)) {
            notify(player, RocketValidationCode.UNAUTHORIZED, "only the owner or an operator may disassemble this rocket");
            return;
        }
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        if (!snapshot.sourceDimension().equals(level.dimension().location())) {
            notify(player, RocketValidationCode.ENTITY_STATE_INVALID, "rocket is outside its captured dimension");
            return;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        if (!savedData.operational()) {
            notify(player, RocketValidationCode.UNSUPPORTED_SCHEMA, "transaction journal is blocked by unsupported data");
            return;
        }
        if (hasPendingRecovery(savedData, snapshot)) {
            notify(player, RocketValidationCode.REGION_BUSY, "an unfinished transaction still owns this region");
            return;
        }

        UUID transactionId = UUID.randomUUID();
        RocketTransactionResult result = new RocketDisassemblyTransaction(
                new ServerLevelRocketTransactionWorld(level, adapters, owner),
                locks,
                ledger,
                savedData.journalFor(snapshot, owner)
        ).execute(transactionId, rocket.getUUID(), snapshot);
        reportTransaction(level, snapshot, player, result, "disassembly");
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick(event.getServer());
        }
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (!recoverySuppressedForReleaseTest) {
            RocketTransactionRecoveryService.Outcome outcome = recovery.recoverOne(server);
            if (outcome == RocketTransactionRecoveryService.Outcome.RECOVERED
                    || outcome == RocketTransactionRecoveryService.Outcome.CONFLICT) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_ROCKET_RECOVERY outcome={}", outcome);
            }
        }
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

    public void clear() {
        pending.clear();
        scanOrder.clear();
        locks.clear();
        ledger.clear();
        recoverySuppressedForReleaseTest = false;
    }

    /** Prevents the deliberately staged release-test record from recovering before shutdown. */
    public void suppressRecoveryUntilStopForReleaseTest() {
        if (!Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")) {
            throw new IllegalStateException("Rocket release-test hooks are disabled");
        }
        recoverySuppressedForReleaseTest = true;
    }

    public int pendingScans() {
        return pending.size();
    }

    private boolean tickScan(MinecraftServer server, AssemblerKey key, PendingScan active) {
        ServerLevel level = server.getLevel(key.dimension());
        if (level == null || !level.hasChunkAt(key.position())) {
            return false;
        }
        RocketAssemblerBlockEntity assembler = assembler(level, key.position());
        if (assembler == null || assembler.blockedByFutureData()) {
            return false;
        }
        ServerPlayer player = null;
        if (active.playerId().isPresent()) {
            player = server.getPlayerList().getPlayer(active.playerId().orElseThrow());
            if (player == null || player.level() != level) {
                update(assembler, RocketValidationCode.UNAUTHORIZED, null, "requesting player disconnected or changed dimension", level);
                return false;
            }
            if (!withinRange(player, key.position())) {
                update(assembler, RocketValidationCode.OUT_OF_RANGE, null, "requesting player left interaction range", level);
                notify(player, RocketValidationCode.OUT_OF_RANGE, "assembler scan cancelled");
                return false;
            }
        }

        RocketScanResult result = active.task().step(RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK);
        if (result.status() == RocketScanResult.Status.RUNNING) {
            update(
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
            String detail = issueDetail(issue);
            update(assembler, issue.code(), result.stats().orElse(null), detail, level);
            if (player != null) {
                notify(player, issue.code(), detail);
            }
            logScanResult(level, key.position(), active, issue.code(), detail, null);
            return false;
        }

        RocketStructureSnapshot snapshot = result.snapshot().orElseThrow();
        update(assembler, RocketValidationCode.SUCCESS, snapshot.stats(), "validated " + snapshot.contentHash(), level);
        if (!active.assemble()) {
            if (player != null) {
                notifyStats(player, snapshot, "validated");
            }
            logScanResult(level, key.position(), active, RocketValidationCode.SUCCESS, "validated", snapshot);
            return false;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(server);
        if (!savedData.operational()) {
            update(assembler, RocketValidationCode.UNSUPPORTED_SCHEMA, snapshot.stats(), "transaction journal is blocked", level);
            if (player != null) {
                notify(player, RocketValidationCode.UNSUPPORTED_SCHEMA, "transaction journal is blocked by unsupported data");
            }
            return false;
        }
        if (hasPendingRecovery(savedData, snapshot)) {
            update(assembler, RocketValidationCode.REGION_BUSY, snapshot.stats(), "unfinished transaction owns the region", level);
            if (player != null) {
                notify(player, RocketValidationCode.REGION_BUSY, "an unfinished transaction still owns this region");
            }
            return false;
        }

        UUID transactionId = UUID.randomUUID();
        RocketTransactionResult transaction = new RocketAssemblyTransaction(
                new ServerLevelRocketTransactionWorld(level, adapters, active.ownerId()),
                locks,
                ledger,
                savedData.journalFor(snapshot, active.ownerId())
        ).execute(transactionId, snapshot);
        reportTransaction(level, snapshot, player, transaction, "assembly");
        return false;
    }

    private void reportTransaction(
            ServerLevel level,
            RocketStructureSnapshot snapshot,
            ServerPlayer player,
            RocketTransactionResult result,
            String operation
    ) {
        RocketAssemblerBlockEntity assembler = assembler(
                level,
                new BlockPos(
                        snapshot.sourceOrigin().x(),
                        snapshot.sourceOrigin().y() - 1,
                        snapshot.sourceOrigin().z()
                )
        );
        String detail = result.success()
                ? operation + " committed blocks=" + result.changedBlocks()
                : issueDetail(result.issue().orElseThrow());
        if (assembler != null) {
            update(assembler, result.code(), snapshot.stats(), detail, level);
        }
        if (result.success()) {
            if (player != null) {
                notifyStats(player, snapshot, operation + " committed");
            }
        } else if (player != null) {
            notify(player, result.code(), detail);
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
                new ServerLevelRocketScanWorld(level, adapters),
                level.dimension().location(),
                toRocketPosition(assemblerPosition.above()),
                UUID.randomUUID(),
                level.getGameTime()
        );
        pending.put(key, new PendingScan(task, ownerId, Optional.ofNullable(playerId), assemble));
        scanOrder.addLast(key);
        RocketAssemblerBlockEntity assembler = assembler(level, assemblerPosition);
        if (assembler != null) {
            update(assembler, RocketValidationCode.SCAN_IN_PROGRESS, null, "queued", level);
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
        if (!withinRange(player, position)) {
            return RocketValidationCode.OUT_OF_RANGE;
        }
        if (!level.hasChunkAt(position) || !level.hasChunkAt(position.above())) {
            return RocketValidationCode.UNLOADED_CHUNK;
        }
        return assembler(level, position) == null
                ? RocketValidationCode.ENTITY_STATE_INVALID
                : null;
    }

    private static RocketAssemblerBlockEntity assembler(ServerLevel level, BlockPos position) {
        return level.getBlockEntity(position) instanceof RocketAssemblerBlockEntity assembler
                ? assembler
                : null;
    }

    private static boolean withinRange(ServerPlayer player, BlockPos position) {
        return withinRange(
                player,
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D
        );
    }

    private static boolean withinRange(ServerPlayer player, double x, double y, double z) {
        return player.distanceToSqr(x, y, z) <= MAX_INTERACTION_DISTANCE_SQUARED;
    }

    private static void update(
            RocketAssemblerBlockEntity assembler,
            RocketValidationCode code,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats stats,
            String detail,
            ServerLevel level
    ) {
        assembler.setReport(new RocketAssemblerReport(code, stats, detail, level.getGameTime()));
    }

    private static void notify(ServerPlayer player, RocketValidationCode code, String detail) {
        player.displayClientMessage(
                Component.translatable(code.translationKey())
                        .append(Component.literal(": " + detail)),
                true
        );
    }

    private static void notifyStats(
            ServerPlayer player,
            RocketStructureSnapshot snapshot,
            String action
    ) {
        var stats = snapshot.stats();
        player.displayClientMessage(
                Component.literal(
                        action + ": blocks=" + stats.blockCount()
                                + ", mass=" + stats.mass()
                                + ", thrust=" + stats.thrust()
                                + ", fuel=" + stats.fuelCapacity()
                                + ", seats=" + stats.seatCount()
                ),
                true
        );
    }

    private static String issueDetail(RocketValidationIssue issue) {
        String position = issue.position()
                .map(value -> " at " + value.x() + "," + value.y() + "," + value.z())
                .orElse("");
        String parameters = issue.parameters().isEmpty() ? "" : " " + issue.parameters();
        return issue.code().name().toLowerCase(java.util.Locale.ROOT) + position + parameters;
    }

    private static RocketPosition toRocketPosition(BlockPos position) {
        return new RocketPosition(position.getX(), position.getY(), position.getZ());
    }

    private static boolean hasPendingRecovery(
            RocketTransactionSavedData savedData,
            RocketStructureSnapshot snapshot
    ) {
        RocketRegion requested = RocketRegion.fromSnapshot(snapshot);
        return savedData.entries().stream()
                .anyMatch(entry -> entry.record().region().overlaps(requested));
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
