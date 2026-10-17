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
import java.util.Optional;
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
        long fuel = rocket.flightData().orElseThrow().fuel().totalAmount();
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
            player.sendSystemMessage(Component.translatable("message.advancedrocketrycommunity.propulsion.disposal_vector",
                    rocket.flightData().orElseThrow().fuel().summary()));
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_discard_action", fuel)
                    .withStyle(style -> style.withColor(ChatFormatting.RED).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))));
            return;
        }
        confirmations.forget(player.getUUID());
        executePlayer(player, rocket, null);
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
        if (!(player.serverLevel().getEntity(expected.entityId()) instanceof RocketEntity rocket)) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        RocketValidationCode code = playerCode(player, rocket);
        if (code != RocketValidationCode.SUCCESS) {
            reject(player, code);
            // N6: a spent token whose rocket already has a pending landed disassembly only reports that status.
            if (code != RocketValidationCode.DISASSEMBLY_PENDING) {
                player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            }
            return false;
        }
        if (rocket.flightData().orElseThrow().fuel().totalAmount() <= 0L || !expected.equals(quote(rocket))) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        return executePlayer(player, rocket, expected);
    }

    private boolean allowRequest(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        return player.serverLevel().getServer().isSameThread()
                && limiter.check(player.getUUID(), now(player)) == RocketIntentRateLimiter.Decision.ALLOWED;
    }

    private boolean validatePlayer(ServerPlayer player, RocketEntity rocket) {
        RocketValidationCode code = playerCode(player, rocket);
        return code == RocketValidationCode.SUCCESS || reject(player, code);
    }

    /** Today's order; with a pending landed disassembly, W85 N6's status branch replaces the busy rejection. */
    private RocketValidationCode playerCode(ServerPlayer player, RocketEntity rocket) {
        Objects.requireNonNull(rocket, "rocket");
        boolean actor = player.isAlive() && !player.isSpectator() && player.level() == rocket.level();
        boolean inRange = actor && RocketInteraction.withinRange(player, rocket.getX(), rocket.getY(), rocket.getZ());
        if (flights.landedDisassemblyPending(rocket)) {
            return RocketLandedDisassemblyRules.status(actor, inRange, identity(rocket), authorized(player, rocket));
        }
        if (!actor) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        if (!inRange) {
            return RocketValidationCode.OUT_OF_RANGE;
        }
        RocketValidationCode code = validateRocket(rocket);
        if (code != RocketValidationCode.SUCCESS) {
            return code;
        }
        return authorized(player, rocket) ? RocketValidationCode.SUCCESS : RocketValidationCode.UNAUTHORIZED;
    }

    private static boolean authorized(ServerPlayer player, RocketEntity rocket) {
        return rocket.ownerId().filter(player.getUUID()::equals).isPresent()
                || player.isCreative() || player.hasPermissions(2);
    }

    /** The identity checks at the top of {@link #validateRocket}, also used alone by the pending status branch. */
    private static RocketValidationCode identity(RocketEntity rocket) {
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread()
                || !rocket.isAlive() || !level.hasChunkAt(rocket.blockPosition())) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        return rocket.operational() ? RocketValidationCode.SUCCESS : RocketValidationCode.UNSUPPORTED_SCHEMA;
    }

    private RocketValidationCode validateRocket(RocketEntity rocket) {
        RocketValidationCode identity = identity(rocket);
        if (identity != RocketValidationCode.SUCCESS) {
            return identity;
        }
        ServerLevel level = (ServerLevel) rocket.level();
        if (!flights.resourceMutationAllowed(rocket)) {
            return RocketValidationCode.REGION_BUSY;
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

    /** A confirmed fueled request passes its taken quote; a zero-fuel request has no consent to compare. */
    private boolean executePlayer(ServerPlayer player, RocketEntity rocket, RocketDisassemblyConfirmations.Quote consent) {
        ServerLevel level = player.serverLevel();
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        long fuel = rocket.flightData().orElseThrow().fuel().totalAmount();
        LandedRequest request = LandedRequest.of(this, level, rocket, player, false);
        if (request == null || consent != null && !request.snapshot.consented(consent)) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_confirmation_invalid"));
            return false;
        }
        RocketValidationCode landed = flights.beginLandedDisassembly(rocket, () -> preflight(level, rocket, snapshot),
                request);
        if (landed == RocketValidationCode.DISASSEMBLY_PENDING) {
            // A confirmed request has spent its token; P carries the consented amount until M2 or M3 reports.
            player.displayClientMessage(Component.translatable(landed.translationKey()), true);
            return true;
        }
        if (landed != RocketValidationCode.SUCCESS) {
            return reject(player, landed);
        }
        RocketTransactionResult result = execute(level, rocket, snapshot, fuel);
        reportTransaction(level, snapshot, player, result);
        if (result.success() && fuel > 0L) {
            player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_fuel_discarded", fuel));
        }
        return result.success();
    }

    RocketValidationCode disassembleForReleaseTest(RocketEntity rocket, long expectedDiscardFuel) {
        Objects.requireNonNull(rocket, "rocket");
        if (flights.landedDisassemblyPending(rocket)) {
            return RocketLandedDisassemblyRules.status(true, true, identity(rocket), true);
        }
        RocketValidationCode code = validateRocket(rocket);
        if (code != RocketValidationCode.SUCCESS) {
            return code;
        }
        var fuelState = rocket.flightData().orElseThrow().fuel();
        long fuel = fuelState.totalAmount();
        if (fuelState.propulsion().filter(value -> value.primary().branch()
                == io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketFuelBank.Branch.FLUID
                || value.oxidizer().amount() > 0).isPresent()) {
            return RocketValidationCode.FUEL_DISPOSAL_REQUIRED;
        }
        if (fuel > 0L && expectedDiscardFuel < 0L) {
            return RocketValidationCode.FUEL_DISPOSAL_REQUIRED;
        }
        if (expectedDiscardFuel >= 0L && expectedDiscardFuel != fuel) {
            return RocketValidationCode.WORLD_CHANGED;
        }
        ServerLevel level = (ServerLevel) rocket.level();
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        LandedRequest request = LandedRequest.of(this, level, rocket, null, true);
        if (request == null) {
            return RocketValidationCode.ENTITY_STATE_INVALID;
        }
        // A landed arrival answers DISASSEMBLY_PENDING; its receipt line follows when M2 or M3 finishes.
        RocketValidationCode landed = flights.beginLandedDisassembly(rocket, () -> preflight(level, rocket, snapshot),
                request);
        if (landed != RocketValidationCode.SUCCESS) {
            return landed;
        }
        RocketTransactionResult result = execute(level, rocket, snapshot, fuel);
        logReleaseTest(rocket.getUUID(), rocket.assemblyTransactionId().orElse(null), result.code(),
                result.changedBlocks(), result.rolledBackBlocks());
        return result.code();
    }

    private static void logReleaseTest(UUID entity, UUID logical, RocketValidationCode code, int blocks, int rolledBack) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_DISASSEMBLY entity={} logical={} code={} blocks={} rolled_back={}",
                entity, logical, code, blocks, rolledBack);
    }

    /** W85 A1: the production request and preflight, for the GameTests that drive an isolated journal. */
    RocketLandedDisassemblies.Request landedRequest(RocketEntity rocket, ServerPlayer requester, boolean releaseTest) {
        return LandedRequest.of(this, (ServerLevel) rocket.level(), rocket, requester, releaseTest);
    }

    Optional<RocketValidationCode> preflight(ServerLevel level, RocketEntity rocket,
                                             RocketStructureSnapshot snapshot) {
        return transactions.preflightDisassembly(level, rocket.ownerId().orElseThrow(), rocket, snapshot)
                .map(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue::code);
    }

    private RocketTransactionResult execute(ServerLevel level, RocketEntity rocket,
                                            RocketStructureSnapshot snapshot, long discardedFuel) {
        // Never zero fuel first: unsuccessful restoration must leave entity fuel intact.
        RocketTransactionResult result = transactions.disassemble(level, rocket.ownerId().orElseThrow(), rocket, snapshot);
        if (result.success()) {
            if (discardedFuel > 0L) {
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_ROCKET_FUEL_DISCARDED entity={} logical={} amount={} vector={} reason=confirmed_disassembly",
                        rocket.getUUID(), rocket.assemblyTransactionId().orElseThrow(), discardedFuel,
                        rocket.flightData().orElseThrow().fuel().summary());
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

    /** The player is null when an asynchronous landed restoration finishes after its requester left. */
    private static void reportTransaction(ServerLevel level, RocketStructureSnapshot snapshot,
                                          ServerPlayer player, RocketTransactionResult result) {
        var assembler = RocketInteraction.assembler(level, new net.minecraft.core.BlockPos(
                snapshot.sourceOrigin().x(), snapshot.sourceOrigin().y() - 1, snapshot.sourceOrigin().z()));
        String detail = result.success() ? "disassembly committed blocks=" + result.changedBlocks()
                : RocketInteraction.issueDetail(result.issue().orElseThrow());
        if (assembler != null) {
            RocketInteraction.update(assembler, result.code(), snapshot.stats(), detail, level);
        }
        if (player != null && result.success()) {
            RocketInteraction.notifyStats(player, snapshot, "disassembly committed");
        } else if (player != null) {
            RocketInteraction.notify(player, result.code(), detail);
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_ROCKET_TRANSACTION operation=disassembly code={} blocks={} snapshot={} entity={}",
                result.code(), result.changedBlocks(), snapshot.contentHash(),
                result.rocketEntityId().map(UUID::toString).orElse("none"));
    }

    /**
     * W85: one landed request's world side. It keeps S and identifiers, never the entity, and finds E only through a
     * lookup that never loads a chunk. The requester is notified while still connected, or through its new session.
     */
    private static final class LandedRequest implements RocketLandedDisassemblies.Request {
        private final RocketDisassemblyService service;
        private final ServerLevel level;
        private final RocketStructureSnapshot structure;
        private final RocketLandedDisassemblyRules.Snapshot snapshot;
        private final UUID logical;
        private final ServerPlayer requester;
        private final boolean releaseTest;

        private LandedRequest(RocketDisassemblyService service, ServerLevel level, RocketEntity rocket,
                              ServerPlayer requester, boolean releaseTest) {
            this.service = service;
            this.level = level;
            this.structure = rocket.snapshot().orElseThrow();
            this.snapshot = pendingSnapshot(rocket);
            this.logical = rocket.assemblyTransactionId().orElseThrow();
            this.requester = requester;
            this.releaseTest = releaseTest;
        }

        /** Null when S cannot be formed, which refuses the request before any mutation. */
        static LandedRequest of(RocketDisassemblyService service, ServerLevel level, RocketEntity rocket,
                                ServerPlayer requester, boolean releaseTest) {
            try {
                return new LandedRequest(service, level, rocket, requester, releaseTest);
            } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) {
                return null;
            }
        }

        private RocketEntity rocket() {
            return level.getEntity(snapshot.entityId()) instanceof RocketEntity rocket ? rocket : null;
        }

        @Override
        public boolean matches() {
            RocketEntity rocket = rocket();
            return rocket != null && rocket.isAlive() && rocket.operational()
                    && snapshot.equals(pendingSnapshot(rocket));
        }

        @Override
        public RocketLandedDisassemblyRules.Presence presence(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord record) {
            RocketEntity rocket = rocket();
            if (rocket != null && rocket.isAlive()) {
                return RocketTransferEntities.isReplaceableLandedAuthority(rocket, record)
                        ? RocketLandedDisassemblyRules.Presence.MATCHES : RocketLandedDisassemblyRules.Presence.CHANGED;
            }
            long section = net.minecraft.world.level.ChunkPos.asLong(
                    net.minecraft.util.Mth.floor(snapshot.x()) >> 4, net.minecraft.util.Mth.floor(snapshot.z()) >> 4);
            // An unloaded section stored E at its unload; ordinary recovery classifies T when it loads again.
            return level.areEntitiesLoaded(section)
                    ? RocketLandedDisassemblyRules.Presence.CHANGED : RocketLandedDisassemblyRules.Presence.UNLOADED;
        }

        @Override
        public RocketTransactionResult restore() {
            RocketEntity rocket = Objects.requireNonNull(rocket(), "rocket");
            return service.execute(level, rocket, rocket.snapshot().orElseThrow(), snapshot.flight().fuel().totalAmount());
        }

        @Override
        public void finished(RocketValidationCode code, RocketTransactionResult result) {
            if (releaseTest) {
                logReleaseTest(snapshot.entityId(), logical, code, result == null ? 0 : result.changedBlocks(),
                        result == null ? 0 : result.rolledBackBlocks());
                return;
            }
            ServerPlayer player = requester == null ? null
                    : level.getServer().getPlayerList().getPlayer(requester.getUUID());
            if (player == null && requester != null && !requester.hasDisconnected()) {
                player = requester;
            }
            if (result == null) {
                if (player != null) {
                    reject(player, code);
                }
                return;
            }
            reportTransaction(level, structure, player, result);
            long fuel = snapshot.flight().fuel().totalAmount();
            if (player != null && result.success() && fuel > 0L) {
                player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_fuel_discarded", fuel));
            }
        }
    }

    private static RocketLandedDisassemblyRules.Snapshot pendingSnapshot(RocketEntity rocket) {
        var snapshot = rocket.snapshot().orElseThrow();
        return new RocketLandedDisassemblyRules.Snapshot(rocket.getUUID(), rocket.ownerId().orElseThrow(),
                snapshot.snapshotId(), snapshot.contentHash(), rocket.flightData().orElseThrow(),
                rocket.getX(), rocket.getY(), rocket.getZ(),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.capture().signature());
    }
}
