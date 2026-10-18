package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * W90 (Root decisions R21 item 3 and R22 answer 4): the governed launch's source check, before pad selection, terrain
 * access, a named-pad claim or F1. Legacy launches never reach it.
 */
final class RocketGovernedAdmission {
    private RocketGovernedAdmission() { }

    /** {@code governed} selects the governed pad bounds; {@code refusal} is null when pad selection may proceed. */
    record Check(boolean governed, RocketFlightRequestResult refusal) { }

    /** The governed test is the one that already chooses the governed F1 branch: a ledger-bound escrow section. */
    static Check check(MinecraftServer server, RocketEntity rocket, RocketFlightPlan plan) {
        if (rocket.escrowSection().ledger() == null) { return new Check(false, null); }
        var source = rocket.snapshot().orElseThrow();
        var code = sourceRefusal(source, () -> RocketTransferEntities.endAlreadyLoaded(server, source));
        return new Check(true, code == null ? null : RocketFlightRequestResult.failure(code, plan.requiredFuel()));
    }

    /**
     * The source must be an admissible checked column set ({@code LAUNCH_CHUNK_BUDGET}) whose columns are all loaded
     * and entity-ticking ({@code LAUNCH_AREA_INACTIVE}); readiness is read only for an admissible set.
     */
    static RocketFlightRequestCode sourceRefusal(RocketStructureSnapshot source, BooleanSupplier ready) {
        if (!RocketDiscoveryColumns.of(source).admissible()) { return RocketFlightRequestCode.LAUNCH_CHUNK_BUDGET; }
        return ready.getAsBoolean() ? null : RocketFlightRequestCode.LAUNCH_AREA_INACTIVE;
    }
}
