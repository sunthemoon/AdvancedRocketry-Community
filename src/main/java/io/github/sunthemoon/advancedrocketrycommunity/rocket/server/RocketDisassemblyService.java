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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** Validates disassembly and binds deliberate fuel disposal to transient player consent. */
final class RocketDisassemblyService {
    private static final String MESSAGE = "message.advancedrocketrycommunity.rocket.";
    private final RocketTransactionExecutor transactions;
    private final RocketFlightService flights;
    private final RocketDisassemblyConfirmations confirmations = new RocketDisassemblyConfirmations();
    private final RocketIntentRateLimiter limiter = new RocketIntentRateLimiter();

    RocketDisassemblyService(RocketTransactionExecutor transactions, RocketFlightService flights) {
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.flights = Objects.requireNonNull(flights, "flights");
    }

    void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        if (!allowRequest(player) || !validatePlayer(player, rocket)) {
            return;
        }
        long fuel = rocket.flightData().orElseThrow().fuel().amount();
        if (fuel > 0L) {
            long now = now(player);
            var offer = confirmations.offer(player.getUUID(), quote(rocket), now);
            if (offer.isEmpty()) {
                reject(player, RocketValidationCode.OPERATION_LEDGER_FULL);
                return;
            }
            String command = "/arce rocket-disassembly confirm " + offer.orElseThrow().token();
            long remainingTicks = RocketDisassemblyConfirmations.LIFETIME_TICKS - (now - offer.orElseThrow().issuedAt());
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_fuel_warning", fuel,
                    (remainingTicks + 19L) / 20L));
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_discard_action", fuel)
                    .withStyle(style -> style.withColor(ChatFormatting.RED).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))));
            return;
        }
        confirmations.forget(player.getUUID());
        executePlayer(player, rocket);
    }

    boolean confirmDisassembly(ServerPlayer player, UUID token) {
        if (!allowRequest(player)) {
            return false;
        }
        var offered = confirmations.take(player.getUUID(), token, now(player));
        if (offered.isEmpty()) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        var expected = offered.orElseThrow();
        // This lookup never loads a saved target region.
        if (!(player.serverLevel().getEntity(expected.entityId()) instanceof RocketEntity rocket)
                || !validatePlayer(player, rocket)) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        if (rocket.flightData().orElseThrow().fuel().amount() <= 0L || !expected.equals(quote(rocket))) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        return executePlayer(player, rocket);
    }

    private boolean allowRequest(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return player.serverLevel().getServer().isSameThread()
                && limiter.check(player.getUUID(), now(player)) == RocketIntentRateLimiter.Decision.ALLOWED;
    }

    private boolean validatePlayer(ServerPlayer player, RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        if (!player.isAlive() || player.isSpectator() || player.level() != rocket.level()) {
            return reject(player, RocketValidationCode.ENTITY_STATE_INVALID);
        }
        if (!RocketInteraction.withinRange(player, rocket.getX(), rocket.getY(), rocket.getZ())) {
            return reject(player, RocketValidationCode.OUT_OF_RANGE);
        }
        RocketValidationCode code = validateRocket(rocket);
        if (code != RocketValidationCode.SUCCESS) {
            return reject(player, code);
        }
        if (!rocket.ownerId().orElseThrow().equals(player.getUUID())
                && !player.isCreative() && !player.hasPermissions(2)) {
            return reject(player, RocketValidationCode.UNAUTHORIZED);
        }
        return true;
    }

    private RocketValidationCode validateRocket(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.isAlive() || !level.hasChunkAt(rocket.blockPosition())) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        if (!rocket.operational()) {
            return RocketValidationCode.UNSUPPORTED_SCHEMA;
        }
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        if (!snapshot.sourceDimension().equals(level.dimension().location())
                || !RocketFlightStateMachine.isLegal(rocket.flightData().orElseThrow().state(),
                RocketFlightEvent.DISASSEMBLE)) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        if (!savedData.operational()) {
            return RocketValidationCode.UNSUPPORTED_SCHEMA;
        }
        return transactions.hasPendingRecovery(savedData, snapshot)
                ? RocketValidationCode.REGION_BUSY : RocketValidationCode.SUCCESS;
    }

    private boolean executePlayer(ServerPlayer player, RocketEntity rocket) {
        ServerLevel level = player.serverLevel();
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        long fuel = rocket.flightData().orElseThrow().fuel().amount();
        RocketTransactionResult result = execute(level, rocket, snapshot, fuel);
        reportTransaction(level, snapshot, player, result);
        if (result.success() && fuel > 0L) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_fuel_discarded", fuel));
        }
        return result.success();
    }

    RocketValidationCode disassembleForReleaseTest(RocketEntity rocket, long expectedDiscardFuel) {
        Objects.requireNonNull(rocket, "rocket");
        RocketValidationCode code = validateRocket(rocket);
        if (code != RocketValidationCode.SUCCESS) {
            return code;
        }
        long fuel = rocket.flightData().orElseThrow().fuel().amount();
        if (fuel > 0L && expectedDiscardFuel < 0L) {
            return RocketValidationCode.FUEL_DISPOSAL_REQUIRED;
        }
        if (expectedDiscardFuel >= 0L && expectedDiscardFuel != fuel) {
            return RocketValidationCode.WORLD_CHANGED;
        }
        RocketTransactionResult result = execute((ServerLevel) rocket.level(), rocket,
                rocket.snapshot().orElseThrow(), fuel);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_DISASSEMBLY entity={} logical={} code={} blocks={} rolled_back={}",
                rocket.getUUID(), rocket.assemblyTransactionId().orElse(null), result.code(),
                result.changedBlocks(), result.rolledBackBlocks());
        return result.code();
    }

    private RocketTransactionResult execute(ServerLevel level, RocketEntity rocket,
                                            RocketStructureSnapshot snapshot, long discardedFuel) {
        // Never zero fuel first: unsuccessful restoration must leave entity fuel intact.
        RocketTransactionResult result = transactions.disassemble(level, rocket.ownerId().orElseThrow(), rocket, snapshot);
        if (result.success()) {
            flights.releaseLandedReservation(rocket);
            if (discardedFuel > 0L) {
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_ROCKET_FUEL_DISCARDED entity={} logical={} amount={} reason=confirmed_disassembly",
                        rocket.getUUID(), rocket.assemblyTransactionId().orElseThrow(), discardedFuel);
            }
        }
        return result;
    }

    private static RocketDisassemblyConfirmations.Quote quote(RocketEntity rocket) {
        var snapshot = rocket.snapshot().orElseThrow();
        return new RocketDisassemblyConfirmations.Quote(rocket.getUUID(), rocket.ownerId().orElseThrow(),
                snapshot.snapshotId(), snapshot.contentHash(), rocket.flightData().orElseThrow(),
                rocket.getX(), rocket.getY(), rocket.getZ());
    }

    private static long now(ServerPlayer player) {
        return player.serverLevel().getServer().overworld().getGameTime();
    }

    private static boolean reject(ServerPlayer player, RocketValidationCode code) {
        player.displayClientMessage(Component.translatable(code.translationKey()), true);
        return false;
    }

    void onLogout(UUID playerId) {
        confirmations.forget(playerId);
    }

    void clear() {
        confirmations.clear();
        limiter.clear();
    }

    private static void reportTransaction(ServerLevel level, RocketStructureSnapshot snapshot,
                                          ServerPlayer player, RocketTransactionResult result) {
        var assembler = RocketInteraction.assembler(level, new net.minecraft.core.BlockPos(
                snapshot.sourceOrigin().x(), snapshot.sourceOrigin().y() - 1, snapshot.sourceOrigin().z()));
        String detail = result.success() ? "disassembly committed blocks=" + result.changedBlocks()
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
                result.code(), result.changedBlocks(), snapshot.contentHash(),
                result.rocketEntityId().map(UUID::toString).orElse("none"));
    }
}
