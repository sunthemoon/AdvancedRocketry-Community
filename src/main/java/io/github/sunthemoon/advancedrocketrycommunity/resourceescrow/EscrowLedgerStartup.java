package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * The escrow ledger's startup lookup at ServerStartingEvent, before the first tick (W328, W327 audit L2). A loaded root
 * binds. Only a proven absence gives NO_FILE, from which the first fill may create the ledger. A present file that did
 * not load, or a path whose status check failed, keeps the PROPULSION delegate NOT_LOADED for the session: no ledger is
 * created, the shared authority answers UNAVAILABLE for fuel, cargo and the bay, and nothing reconciles. A restart that
 * loads the file or proves it absent clears it. An unknown presence is never read as absent.
 */
final class EscrowLedgerStartup {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    private EscrowLedgerStartup() { }

    /**
     * Classifies the ledger once, on the server thread. {@code existing} is the load-only lookup's root, and
     * {@code presence} is consulted only when it is null. Returns why the delegate stays NOT_LOADED, or empty when the
     * root bound, the file was proven absent or the delegate was already classified.
     */
    static Optional<EscrowNotLoadedReason> start(EscrowAdmissionAuthority.Delegate propulsion,
            @Nullable ResourceEscrowSavedData existing, Consumer<ResourceEscrowSavedData> bind,
            Function<Path, CheckedSavedDataFile.Presence> presence, Path file) {
        EscrowTags.require(propulsion.consumer() == EscrowAdmissionAuthority.Consumer.PROPULSION,
                "The escrow ledger starts on the PROPULSION delegate");
        if (propulsion.state() != EscrowAdmissionAuthority.Delegate.State.NOT_LOADED) { return Optional.empty(); }
        if (existing != null) { bind.accept(existing); return Optional.empty(); }
        var state = presence.apply(file);
        if (state == CheckedSavedDataFile.Presence.ABSENT) { propulsion.noFile(); return Optional.empty(); }
        var reason = EscrowNotLoadedReason.of(state);
        LOGGER.warn("ARCE_ESCROW_LEDGER_NOT_LOADED reason={} file={}; {}, so fuel, cargo and bay admission and escrow "
                + "reconciliation stay refused for this session", reason, file.getFileName(), reason.description());
        return Optional.of(reason);
    }
}
