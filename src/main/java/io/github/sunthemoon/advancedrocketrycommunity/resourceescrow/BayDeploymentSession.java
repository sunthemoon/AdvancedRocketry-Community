package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayRejections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.server.MinecraftServer;

/**
 * The bay deployment coordinator's one handle on the receipts for one server lifetime (W128's one-page design), issued
 * by {@link BayReceipts#session}; a second request throws. It reaches the store's put, lookups and acknowledgements,
 * holds the coordinator's claim predicate and the runtime rejection map, and issues registry permits. resourceescrow
 * gains no dependency on the coordinator itself. Server thread only.
 */
public final class BayDeploymentSession {
    /** A bay's menu status: the receipt's phase and reason, or NONE with a runtime rejection's reason. */
    public record Status(String phase, String reason) {
        public Status { Objects.requireNonNull(phase); Objects.requireNonNull(reason); }
    }
    private final BayReceipts receipts;
    private final MinecraftServer server;
    private final BayRejections rejections = new BayRejections();
    private Predicate<UUID> claims = logical -> false;
    private boolean claimsInstalled, closed;

    BayDeploymentSession(BayReceipts receipts, MinecraftServer server) {
        this.receipts = Objects.requireNonNull(receipts); this.server = Objects.requireNonNull(server);
    }

    /** The coordinator's claim predicate, registered once. It reads coordinator RAM only. */
    public void claims(Predicate<UUID> predicate) {
        owner();
        if (claimsInstalled) { throw new IllegalStateException("The bay claim predicate is already installed"); }
        claims = Objects.requireNonNull(predicate); claimsInstalled = true;
    }
    boolean claimed(UUID logical) { return claims.test(logical); }

    /** D0 is gated (TASK W128): only a GameTestServer or a gated release-test dedicated server starts deployments. */
    public boolean admissionEnabled() { owner(); return !closed && EscrowTestEndpoints.enabled(server); }

    /** BAY_RECEIPTS_UNAVAILABLE: the index is not loaded, the writer is unavailable, or the session has closed. */
    public boolean unavailable() {
        owner();
        if (closed) { return true; }
        return switch (receipts.state()) {
            case NOT_LOADED -> true;
            case NO_FILE -> false;
            case LOADED -> receipts.store() == null || receipts.store().unavailable();
        };
    }

    /** The store's put, with its real retained and pending admission; while NO_FILE the first put creates the root. */
    public BayReceiptStore.Admission put(BayReceipt next, Consumer<BayReceiptWriter.Outcome> completion) {
        if (unavailable()) { return BayReceiptStore.Admission.of(BayReceiptStore.Status.STORE_UNAVAILABLE); }
        return receipts.storeForAdmission(server).put(next, completion);
    }

    /** W170 D4: one group's REGISTERED (or other) replacements in one generation; see {@link BayReceiptStore#putGroup}. */
    public BayReceiptStore.Admission putGroup(List<BayReceipt> group, Consumer<BayReceiptWriter.Outcome> completion) {
        if (unavailable()) { return BayReceiptStore.Admission.of(BayReceiptStore.Status.STORE_UNAVAILABLE); }
        return store().map(store -> store.putGroup(group, completion)).orElse(BayReceiptStore.Admission.of(BayReceiptStore.Status.INVALID));
    }

    /** W170 D7: one group's guarded removal in one generation of a quiet store; see {@link BayReceiptStore#removeGroup}. */
    public BayReceiptStore.Admission removeGroup(List<UUID> group, java.util.function.BooleanSupplier guard,
            Consumer<BayReceiptWriter.Outcome> completion) {
        if (unavailable()) { return BayReceiptStore.Admission.of(BayReceiptStore.Status.STORE_UNAVAILABLE); }
        return store().map(store -> store.removeGroup(group, guard, completion))
                .orElse(BayReceiptStore.Admission.of(BayReceiptStore.Status.INVALID));
    }

    public Optional<BayReceipt> find(UUID satellite) { return store().flatMap(store -> store.find(satellite)); }
    /** The operation's receipt in RAM, including a queued or captured change. */
    public Optional<BayReceipt> current(UUID op) { return store().flatMap(store -> store.current(op)); }
    public List<BayReceipt> receipts() { return store().map(BayReceiptStore::receipts).orElse(List.of()); }
    public boolean acknowledged(UUID op) { return store().map(store -> store.acknowledged(op)).orElse(false); }
    /** The next receipt sequence: one above every sequence issued in any state. */
    public long nextSequence() { return store().map(store -> store.issuedThrough() + 1).orElse(1L); }
    public BayRejections rejections() { owner(); return rejections; }

    /** A permit for D2's revalidation; refused unless the operation's RAM receipt is INTENT for this satellite. */
    public BayRegistryPermit permit(UUID op, UUID satellite) {
        if (!intent(op, satellite)) { throw new IllegalStateException("A registry permit needs the operation's INTENT"); }
        return new BayRegistryPermit(this::intent, op, satellite);
    }
    boolean intent(UUID op, UUID satellite) {
        owner();
        return !closed && current(op).filter(receipt -> receipt.phase() == BayReceipt.Phase.INTENT && !receipt.released()
                && receipt.satellite().equals(satellite)).isPresent();
    }

    /** The menu status of a package's satellite; empty when neither a receipt nor a runtime rejection names it. */
    Optional<Status> status(UUID satellite) {
        var receipt = find(satellite);
        if (receipt.isPresent()) { return Optional.of(new Status(receipt.get().phase().name(), receipt.get().reason())); }
        return rejections.get(satellite).map(rejection -> new Status("NONE", rejection.reason()));
    }

    /** Stopping or crash: refuse new puts and permits and drop the rejection map. */
    public void close() { closed = true; rejections.clear(); }
    public boolean closed() { return closed; }

    private Optional<BayReceiptStore> store() { owner(); return Optional.ofNullable(receipts.store()); }
    private void owner() { EscrowTags.require(server.isSameThread(), "The bay deployment session requires the server thread"); }
}
