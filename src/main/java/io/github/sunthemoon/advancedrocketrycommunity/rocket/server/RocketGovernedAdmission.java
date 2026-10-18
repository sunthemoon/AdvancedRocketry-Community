package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * W90 (Root decisions R21 item 3 and R22 answer 4): the governed launch's source check, before pad selection, terrain
 * access, a named-pad claim or F1. Legacy launches never reach it. W90b adds R22 amendments 1 and 2 at the same place.
 */
final class RocketGovernedAdmission {
    private RocketGovernedAdmission() { }

    /**
     * {@code governed} selects the governed pad bounds; {@code refusal} is null when pad selection may proceed;
     * {@code kept} is the origin of the rocket's live warm-up pad that surface selection revalidates first, or null.
     */
    record Check(boolean governed, RocketFlightRequestResult refusal, RocketPosition kept) { }

    /**
     * The governed test is the one that already chooses the governed F1 branch: a ledger-bound escrow section. Every
     * launch first meets R22 amendment 2: while a warm-up lives, the request ID that started it is refused, whatever
     * the bounded, process-local operation ledger still holds (the trusted admin entry never consults that ledger).
     */
    static Check check(MinecraftServer server, RocketEntity rocket, RocketFlightData countdown, RocketDestinationWarmups warmups) {
        var plan = countdown.plan().orElseThrow();
        if (warmups.startedBy(plan.requestId())) {
            return new Check(false, RocketFlightRequestResult.failure(RocketFlightRequestCode.REQUEST_REPLAYED), null);
        }
        if (rocket.escrowSection().ledger() == null) { return new Check(false, null, null); }
        var source = rocket.snapshot().orElseThrow();
        var code = sourceRefusal(source, () -> RocketTransferEntities.endAlreadyLoaded(server, source));
        return code != null ? new Check(true, RocketFlightRequestResult.failure(code, plan.requiredFuel()), null)
                : new Check(true, null, warmups.kept(countdown, rocket));
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
