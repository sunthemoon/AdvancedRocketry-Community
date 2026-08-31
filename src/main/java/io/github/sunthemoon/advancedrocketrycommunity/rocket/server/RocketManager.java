package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

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

        AssemblerKey key = new AssemblerKey(level.dimension(), immutablePosition);
        if (pending.containsKey(key)) {
            notify(player, RocketValidationCode.REGION_BUSY, "an assembler scan is already active");
            return;
        }
        if (pending.size() >= RocketLimits.MAX_ACTIVE_TRANSACTIONS) {
            notify(player, RocketValidationCode.OPERATION_LEDGER_FULL, "too many rocket scans are active");
            return;
        }

        BlockPos seed = immutablePosition.above();
        RocketStructureScanTask task = new RocketStructureScanTask(
                new ServerLevelRocketScanWorld(level, adapters),
                level.dimension().location(),
                toRocketPosition(seed),
                UUID.randomUUID(),
                level.getGameTime()
        );
        pending.put(key, new PendingScan(task, player.getUUID(), assemble));
        scanOrder.addLast(key);
        update(assembler, RocketValidationCode.SCAN_IN_PROGRESS, null, "queued", level);
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.scan_started"),
                true
        );
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
        recovery.recoverOne(server);
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
        ServerPlayer player = server.getPlayerList().getPlayer(active.playerId());
        if (player == null || player.level() != level) {
            update(assembler, RocketValidationCode.UNAUTHORIZED, null, "requesting player disconnected or changed dimension", level);
            return false;
        }
        if (!withinRange(player, key.position())) {
            update(assembler, RocketValidationCode.OUT_OF_RANGE, null, "requesting player left interaction range", level);
            notify(player, RocketValidationCode.OUT_OF_RANGE, "assembler scan cancelled");
            return false;
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
            notify(player, issue.code(), detail);
            return false;
        }

        RocketStructureSnapshot snapshot = result.snapshot().orElseThrow();
        update(assembler, RocketValidationCode.SUCCESS, snapshot.stats(), "validated " + snapshot.contentHash(), level);
        if (!active.assemble()) {
            notifyStats(player, snapshot, "validated");
            return false;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(server);
        if (!savedData.operational()) {
            update(assembler, RocketValidationCode.UNSUPPORTED_SCHEMA, snapshot.stats(), "transaction journal is blocked", level);
            notify(player, RocketValidationCode.UNSUPPORTED_SCHEMA, "transaction journal is blocked by unsupported data");
            return false;
        }
        if (hasPendingRecovery(savedData, snapshot)) {
            update(assembler, RocketValidationCode.REGION_BUSY, snapshot.stats(), "unfinished transaction owns the region", level);
            notify(player, RocketValidationCode.REGION_BUSY, "an unfinished transaction still owns this region");
            return false;
        }

        UUID transactionId = UUID.randomUUID();
        RocketTransactionResult transaction = new RocketAssemblyTransaction(
                new ServerLevelRocketTransactionWorld(level, adapters, player.getUUID()),
                locks,
                ledger,
                savedData.journalFor(snapshot, player.getUUID())
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
            notifyStats(player, snapshot, operation + " committed");
        } else {
            notify(player, result.code(), detail);
        }
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

    private record PendingScan(RocketStructureScanTask task, UUID playerId, boolean assemble) {
        private PendingScan {
            Objects.requireNonNull(task, "task");
            Objects.requireNonNull(playerId, "playerId");
        }
    }
}
