package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderStatus;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentReasons;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W231's global saturation/release checks, using four owners under R244's selected cap. */
final class W231BayFairnessTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID A = id(231_001), B = id(231_002), C = id(231_003);

    @Test void fourOwnersCanDenyOtherOwnersAcrossBothConsumersWithoutATimeout() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 64; n++) {
                assertTrue(h.put(receipt(n, fillingOwner(n), id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            assertEquals(64, h.authority.pendingCount());
            assertRefused(h, B); assertRefused(h, C); // Queued/captured, not just durable rows, count.
            h.settle(); assertFalse(h.store().holdsWork());
            var before = BayReceiptCodec.encode(h.store().durable());
            for (int tick = 0; tick < 1000; tick++) {
                h.store().tick(h.tick++);
                assertRefused(h, B); assertRefused(h, C);
            }
            assertEquals(before, BayReceiptCodec.encode(h.store().durable()));
            assertEquals(64, h.authority.pendingCount()); assertEquals(16, h.authority.retainedFor(A));
            assertEquals(0, h.authority.retainedFor(B)); assertEquals(0, h.authority.retainedFor(C));
        }
    }

    @Test void onlyExactResolutionFreesASlotAndThereIsNoOwnerReservationOnIt() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 64; n++) {
                assertTrue(h.put(receipt(n, fillingOwner(n), id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            h.settle();
            var first = h.store().receipts().get(0);
            assertTrue(h.put(first.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 2)).queued());
            assertEquals(64, h.authority.pendingCount(), "queued rejection has not released durable authority");
            h.settle(); assertEquals(63, h.authority.pendingCount()); assertEquals(16, h.authority.retainedFor(A));
            assertEquals(EscrowAdmissionAuthority.Result.RESERVED, loader(h, C)); // Preview reserves nothing.
            var admitted = receipt(65, B, id(249_000), BayReceipt.Phase.INTENT);
            assertTrue(h.put(admitted).queued());
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, loader(h, C), "first caller wins, not an owner-fair queue");
            h.settle();
            assertTrue(h.put(admitted.withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 3)).queued()); h.settle();
            assertEquals(EscrowAdmissionAuthority.Result.RESERVED, h.authority.tryReserve(id(900_001), C, id(249_001),
                    EscrowAdmissionAuthority.Consumer.PROPULSION));
            h.fluid.add(new EscrowAdmissionAuthority.Pending(id(900_001), C, id(249_001), EscrowAdmissionAuthority.Consumer.PROPULSION));
            assertEquals(64, h.authority.pendingCount()); assertEquals(65, h.authority.retainedCount());
            assertEquals(FluidFuelLoaderStatus.ESCROW_FULL, EscrowLoaderAdmission.status(EscrowCoordinator.Fill.SERVER_FULL));
        }
    }

    @Test void mixedOwnersAndARestartKeepTheSameGlobalSaturation() throws Exception {
        try (var h = BayReceiptHarness.empty(temp)) {
            for (int n = 1; n <= 64; n++) {
                assertTrue(h.put(receipt(n, id(231_000 + (n - 1) / 8), id(240_000 + (n - 1) / 8), BayReceipt.Phase.INTENT)).queued());
            }
            h.settle(); assertRefused(h, B);
        }
        var authority = new EscrowAdmissionAuthority();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
        try (var restarted = new BayReceiptHarness(authority, temp)) {
            var payload = CheckedSavedDataFile.readPayload(restarted.file, ManagedSavedDataType.BAY_RECEIPTS).orElseThrow();
            restarted.receipts.start(BayReceiptSavedData.load(payload), CheckedSavedDataFile.Presence.PRESENT, restarted.file, 0);
            assertEquals(64, authority.pendingCount()); assertRefused(restarted, C);
            assertEquals(8, authority.retainedFor(id(231_000)));
        }
    }

    private static EscrowAdmissionAuthority.Result loader(BayReceiptHarness h, UUID owner) {
        return h.authority.checkReserve(id(900_001), owner, id(249_001), EscrowAdmissionAuthority.Consumer.PROPULSION);
    }
    private static UUID fillingOwner(int n) { return n <= 16 ? A : id(232_000 + (n - 1) / 16); }
    private static void assertRefused(BayReceiptHarness h, UUID owner) {
        assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, loader(h, owner));
        var admission = h.put(receipt(1000, owner, id(249_000), BayReceipt.Phase.INTENT));
        assertEquals("DEPLOYMENT_CAPACITY", BayDeploymentReasons.admission(admission));
        assertFalse(admission.queued());
    }
}
