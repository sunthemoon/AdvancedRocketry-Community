package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * The escrow ledger as a protective read sees it (W329, Root decision R393). A root in storage is LOADED. Without one,
 * the PROPULSION delegate's startup classification tells NO_FILE, a proven absence, from NOT_LOADED, a file that did not
 * load or whose presence is unknown (W328). Under NOT_LOADED a protective read answers its protective value (not idle,
 * not unprotected, not neutral, locked, frozen), because the ledger may hold history it cannot see.
 *
 * <p>Before that classification runs at ServerStartingEvent, a missing root is UNCLASSIFIED and the reads keep their
 * no-ledger answer, as the bay receipts do before they start (R390): only rocket joins while the spawn chunks load read
 * then, a loadable ledger is already visible through vanilla's lazy load, and every action rechecks after startup. A
 * missing root read off the server thread or without the runtime answers NOT_LOADED. The lookup never creates a ledger
 * or a file.
 */
public record EscrowLedgerLookup(State state, @Nullable ResourceEscrowSavedData data) {
    public enum State { LOADED, NO_FILE, NOT_LOADED, UNCLASSIFIED }

    public EscrowLedgerLookup {
        Objects.requireNonNull(state);
        EscrowTags.require((state == State.LOADED) == (data != null), "Only a LOADED ledger lookup carries the ledger");
    }

    /** The load-only lookup first; the runtime's classification only when it finds no root. */
    public static EscrowLedgerLookup of(MinecraftServer server) {
        var data = ResourceEscrowSavedData.existing(server);
        if (data != null) { return new EscrowLedgerLookup(State.LOADED, data); }
        if (!server.isSameThread()) { return new EscrowLedgerLookup(State.NOT_LOADED, null); }
        var runtime = server.overworld().getCapability(EscrowRuntime.CAPABILITY).resolve().orElse(null);
        return new EscrowLedgerLookup(runtime == null ? State.NOT_LOADED : runtime.missingLedger(server), null);
    }

    /**
     * The state of a missing root. Before the startup classification it is UNCLASSIFIED and the delegate is not read;
     * afterwards only a NO_FILE delegate gives NO_FILE, and a NOT_LOADED or LOADED one gives NOT_LOADED.
     */
    static State missing(boolean classified, Supplier<EscrowAdmissionAuthority.Delegate.State> delegate) {
        if (!classified) { return State.UNCLASSIFIED; }
        return delegate.get() == EscrowAdmissionAuthority.Delegate.State.NO_FILE ? State.NO_FILE : State.NOT_LOADED;
    }

    /** A protective read must answer its protective value. */
    public boolean notLoaded() { return state == State.NOT_LOADED; }
}
