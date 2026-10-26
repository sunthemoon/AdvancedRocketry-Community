package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketDisassemblyService.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/** One fixed disassembly request: original consent, age, world identity and completion reporting. */
final class RocketDisassemblyRequest implements RocketLandedDisassemblies.Request {
    private final RocketDisassemblyService service;
    private final ServerLevel level;
    private final RocketStructureSnapshot structure;
    final RocketLandedDisassemblyRules.Snapshot snapshot;
    private final UUID logical;
    private final ServerPlayer requester;
    private final boolean releaseTest;
    private final long requestedAt;
    private RocketValidationCode admissionRefusal = RocketValidationCode.REGION_BUSY;
    private boolean retainedReported;

    private RocketDisassemblyRequest(RocketDisassemblyService service, ServerLevel level, RocketEntity rocket,
                          ServerPlayer requester, boolean releaseTest) {
        this.service = service;
        this.level = level;
        this.structure = rocket.snapshot().orElseThrow();
        this.snapshot = pendingSnapshot(rocket);
        this.logical = rocket.assemblyTransactionId().orElseThrow();
        this.requester = requester;
        this.releaseTest = releaseTest;
        requestedAt = level.getServer().overworld().getGameTime();
        if (service.sealed == null && !(level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)) {
            throw new IllegalStateException("Legacy disassembly adapter is GameTest-only");
        }
    }

    /** Null when S cannot be formed, which refuses the request before any mutation. */
    static RocketDisassemblyRequest of(RocketDisassemblyService service, ServerLevel level, RocketEntity rocket,
                            ServerPlayer requester, boolean releaseTest) {
        try {
            return new RocketDisassemblyRequest(service, level, rocket, requester, releaseTest);
        } catch (IllegalArgumentException | java.util.NoSuchElementException invalid) {
            return null;
        }
    }

    private RocketEntity rocket() {
        return level.getEntity(snapshot.entityId()) instanceof RocketEntity rocket ? rocket : null;
    }

    @Override public long requestedAt() { return requestedAt; }
    @Override public boolean retainedExecutor() {
        var rocket = rocket();
        return service.sealed != null && rocket != null
                && io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate.retirementUnprotected(rocket);
    }
    boolean preparationCurrent() {
        var rocket = rocket();
        return matches() && (releaseTest || requester != null && !requester.hasDisconnected()
                && requester.isAlive() && !requester.isSpectator() && requester.level() == level
                && RocketDisassemblyService.authorized(requester, rocket)
                && RocketInteraction.withinRange(requester, snapshot.x(), snapshot.y(), snapshot.z()));
    }
    @Override public boolean authorized() {
        long now = level.getServer().overworld().getGameTime();
        // No resourceMutationAllowed/whole-journal check here: B2 owns its exact U after admission.
        return now >= requestedAt && now - requestedAt <= RocketDisassemblyConfirmations.LIFETIME_TICKS
                && preparationCurrent() && !RocketReleaseAccess.fenced(level.getServer(), logical);
    }
    @Override public boolean beginRetained(java.util.function.Consumer<SealedDisassembly.Result> completion) {
        var rocket = rocket();
        if (rocket == null || !authorized()) { return false; }
        var issue = service.preflight(level, rocket, structure);
        if (issue.isPresent()) { admissionRefusal = issue.orElseThrow(); return false; }
        var inline = new SealedDisassembly.Result[1]; var admitting = new boolean[] {true};
        var id = service.sealed.begin(rocket, this::authorized, result -> {
            if (admitting[0]) { inline[0] = result; } else { completion.accept(result); }
        });
        admitting[0] = false;
        if (id == null) {
            // A null return and the optional pre-admission result have started no executor or world mutation.
            admissionRefusal = inline[0] == SealedDisassembly.Result.PROOF_PLAN_LIMIT
                    ? RocketValidationCode.SCAN_BUDGET_EXCEEDED : RocketValidationCode.REGION_BUSY;
            return false;
        }
        return true;
    }
    @Override public RocketValidationCode admissionRefusal() { return admissionRefusal; }
    @Override public void finishedRetained(SealedDisassembly.Result result) {
        if (retainedReported) { return; } retainedReported = true;
        var player = requester == null ? null : level.getServer().getPlayerList().getPlayer(requester.getUUID());
        if (player == null && requester != null && !requester.hasDisconnected()) { player = requester; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_ROCKET_SEALED_DISASSEMBLY entity={} logical={} result={}",
                snapshot.entityId(), logical, result);
        if (result != SealedDisassembly.Result.SEALED) {
            if (player != null) { reject(player, RocketValidationCode.REGION_BUSY); }
            return;
        }
        long fuel = snapshot.flight().fuel().totalAmount();
        if (fuel > 0) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_ROCKET_FUEL_DISCARDED entity={} logical={} amount={} vector={} reason=confirmed_disassembly",
                    snapshot.entityId(), logical, fuel, snapshot.flight().fuel().summary());
        }
        if (player != null) {
            RocketInteraction.notifyStats(player, structure, "disassembly sealed");
            if (fuel > 0) { player.sendSystemMessage(Component.translatable(MESSAGE + "disassembly_fuel_discarded", fuel)); }
        }
    }

    @Override
    public boolean matches() {
        RocketEntity rocket = rocket();
        return rocket != null && rocket.isAlive() && rocket.operational()
                && snapshot.equals(pendingSnapshot(rocket));
    }

    @Override
    public boolean restorable() {
        RocketTransactionSavedData savedData = RocketTransactionSavedData.get(level.getServer());
        return savedData.operational() && !service.transactions.hasPendingRecovery(savedData, structure);
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


    private static RocketLandedDisassemblyRules.Snapshot pendingSnapshot(RocketEntity rocket) {
        var snapshot = rocket.snapshot().orElseThrow();
        return new RocketLandedDisassemblyRules.Snapshot(rocket.getUUID(), rocket.ownerId().orElseThrow(),
                snapshot.snapshotId(), snapshot.contentHash(), rocket.flightData().orElseThrow(),
                rocket.getX(), rocket.getY(), rocket.getZ(),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.RocketFluidFuelRuntime.capture().signature());
    }
}
