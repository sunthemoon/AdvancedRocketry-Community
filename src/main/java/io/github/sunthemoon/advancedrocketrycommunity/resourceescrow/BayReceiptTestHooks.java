package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * Gated receipt fixtures, following {@link EscrowTestEndpoints}: only a GameTestServer or a gated release-test server
 * reaches them. Nothing in production creates or removes a receipt (W27c1; deployment arrives with W27c2 to W27d).
 */
public final class BayReceiptTestHooks {
    private BayReceiptTestHooks() { }

    /** Appends or replaces a receipt through the real store, its admission checks and its checked writer. */
    public static BayReceiptStore.Admission put(MinecraftServer server, BayReceipt receipt, Consumer<BayReceiptWriter.Outcome> completion) {
        EscrowTestEndpoints.requireEnabled(server);
        return EscrowRuntime.of(server).bayReceipts(server).storeForAdmission(server).put(receipt, completion);
    }
    /** Removes any receipt, released ones excepted, for fixture cleanup; the online command removes only REJECTED ones. */
    public static BayReceiptStore.Admission remove(MinecraftServer server, UUID op, Consumer<BayReceiptWriter.Outcome> completion) {
        EscrowTestEndpoints.requireEnabled(server);
        return store(server).map(store -> store.remove(op, completion)).orElse(BayReceiptStore.Admission.of(BayReceiptStore.Status.INVALID));
    }
    public static Optional<BayReceiptStore> store(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        return Optional.ofNullable(EscrowRuntime.of(server).bayReceipts(server).store());
    }
    /** Only GameTests can clean their own released fixture; gated dedicated servers cannot retire one. */
    public static BayReceiptStore.Admission removeReleasedFixture(MinecraftServer server, UUID op) {
        return store(server).map(store -> store.removeReleasedFixture(server, op, outcome -> { }))
                .orElse(BayReceiptStore.Admission.of(BayReceiptStore.Status.INVALID));
    }
    public static long nextSequence(MinecraftServer server) {
        return store(server).map(store -> store.issuedThrough() + 1).orElse(1L);
    }
    public static EscrowAdmissionAuthority authority(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        return EscrowRuntime.of(server).propulsion(server).authority();
    }
}
