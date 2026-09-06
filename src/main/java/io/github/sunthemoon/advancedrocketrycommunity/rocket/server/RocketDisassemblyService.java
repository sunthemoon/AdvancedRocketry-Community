package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightEvent;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightStateMachine;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Validates and executes player or release-test rocket disassembly requests. */
final class RocketDisassemblyService {
    private final RocketTransactionExecutor transactions;
    private final RocketFlightService flights;

    RocketDisassemblyService(RocketTransactionExecutor transactions, RocketFlightService flights) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.flights = Objects.requireNonNull(flights, "flights");
    }

    void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rocket, "rocket");
        if (!(player.level() instanceof ServerLevel level)
                || rocket.level() != level
                || !rocket.isAlive()
                || !level.hasChunkAt(rocket.blockPosition())) {
            RocketInteraction.notify(player, RocketValidationCode.ENTITY_STATE_INVALID, "rocket is unavailable");
            return;
        }
        if (!RocketInteraction.withinRange(player, rocket.getX(), rocket.getY(), rocket.getZ())) {
            RocketInteraction.notify(player, RocketValidationCode.OUT_OF_RANGE, "rocket is beyond interaction range");
            return;
        }
        if (!rocket.operational()) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.UNSUPPORTED_SCHEMA,
                    "rocket data is unavailable or unsupported"
            );
            return;
        }
        UUID owner = rocket.ownerId().orElseThrow();
        if (!owner.equals(player.getUUID()) && !player.isCreative() && !player.hasPermissions(2)) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.UNAUTHORIZED,
                    "only the owner or an operator may disassemble this rocket"
            );
            return;
        }
        if (!RocketFlightStateMachine.isLegal(
                rocket.flightData().orElseThrow().state(),
                RocketFlightEvent.DISASSEMBLE
        )) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.ENTITY_STATE_INVALID,
                    "rocket cannot disassemble during flight"
            );
            return;
        }
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        if (!snapshot.sourceDimension().equals(level.dimension().location())) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.ENTITY_STATE_INVALID,
                    "rocket is outside its captured dimension"
            );
            return;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        if (!savedData.operational()) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.UNSUPPORTED_SCHEMA,
                    "transaction journal is blocked by unsupported data"
            );
            return;
        }
        if (transactions.hasPendingRecovery(savedData, snapshot)) {
            RocketInteraction.notify(
                    player,
                    RocketValidationCode.REGION_BUSY,
                    "an unfinished transaction still owns this region"
            );
            return;
        }

        RocketTransactionResult result = transactions.disassemble(level, owner, rocket, snapshot);
        if (result.success()) {
            flights.releaseLandedReservation(rocket);
        }
        reportTransaction(level, snapshot, player, result);
    }

    RocketValidationCode disassembleForReleaseTest(RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        if (!(rocket.level() instanceof ServerLevel level)
                || !rocket.isAlive()
                || !rocket.operational()
                || !level.hasChunkAt(rocket.blockPosition())
                || !rocket.snapshot().orElseThrow().sourceDimension().equals(level.dimension().location())
                || !RocketFlightStateMachine.isLegal(
                        rocket.flightData().orElseThrow().state(),
                        RocketFlightEvent.DISASSEMBLE
                )) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        if (!savedData.operational()) {
            return RocketValidationCode.UNSUPPORTED_SCHEMA;
        }
        if (transactions.hasPendingRecovery(savedData, snapshot)) {
            return RocketValidationCode.REGION_BUSY;
        }
        UUID owner = rocket.ownerId().orElseThrow();
        RocketTransactionResult result = transactions.disassemble(level, owner, rocket, snapshot);
        if (result.success()) {
            flights.releaseLandedReservation(rocket);
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_DISASSEMBLY entity={} logical={} code={} blocks={} rolled_back={}",
                rocket.getUUID(),
                rocket.assemblyTransactionId().orElse(null),
                result.code(),
                result.changedBlocks(),
                result.rolledBackBlocks()
        );
        return result.code();
    }

    private static void reportTransaction(
            ServerLevel level,
            RocketStructureSnapshot snapshot,
            ServerPlayer player,
            RocketTransactionResult result
    ) {
        var assembler = RocketInteraction.assembler(
                level,
                new net.minecraft.core.BlockPos(
                        snapshot.sourceOrigin().x(),
                        snapshot.sourceOrigin().y() - 1,
                        snapshot.sourceOrigin().z()
                )
        );
        String detail = result.success()
                ? "disassembly committed blocks=" + result.changedBlocks()
                : RocketInteraction.issueDetail(result.issue().orElseThrow());
        if (assembler != null) {
            RocketInteraction.update(assembler, result.code(), snapshot.stats(), detail, level);
        }
        if (result.success()) {
            RocketInteraction.notifyStats(player, snapshot, "disassembly committed");
        } else {
            RocketInteraction.notify(player, result.code(), detail);
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_ROCKET_TRANSACTION operation=disassembly code={} blocks={} snapshot={} entity={}",
                result.code(),
                result.changedBlocks(),
                snapshot.contentHash(),
                result.rocketEntityId().map(UUID::toString).orElse("none")
        );
    }
}
