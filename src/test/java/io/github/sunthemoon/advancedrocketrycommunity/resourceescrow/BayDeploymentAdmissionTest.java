package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentReasons;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W27d1 A0: D1 through the real store and the shared authority. The 64/8 pending and 4,096/128 retained limits refuse
 * before INTENT with their own runtime reasons and change nothing; a REJECTED receipt re-arms with its own operation.
 */
final class BayDeploymentAdmissionTest {
    @TempDir Path temp;
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID OWNER = id(11);
    private static BayReceipt intent(long n, UUID rocket) { return receipt(n, OWNER, rocket, BayReceipt.Phase.INTENT); }
    private static String reason(BayReceiptStore.Admission admission) { return BayDeploymentReasons.admission(admission); }

    @Test void pendingLimitsRefuseWithTheirOwnReasonsAndARejectedReceiptReArmsWithItsOperation() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            UUID rocket = id(80);
            for (long n = 1; n <= 8; n++) { assertTrue(harness.put(intent(n, rocket)).queued()); }
            assertEquals("ROCKET_PENDING_FULL", reason(harness.put(intent(9, rocket))), "8 per rocket");
            for (long n = 9; n <= 64; n++) { assertTrue(harness.put(intent(n, id(100 + n / 8))).queued()); }
            assertEquals(64, harness.authority.pendingCount());
            assertEquals("DEPLOYMENT_CAPACITY", reason(harness.put(intent(65, id(999)))), "64 per server");
            harness.settle();
            // Durably reject one never-registered bay: its pending slot is freed, its retained slot kept.
            var first = harness.store().receipts().get(0);
            var rejected = first.withPhase(BayReceipt.Phase.REJECTED, "DEPLOYMENT_UNAVAILABLE", 20);
            assertTrue(harness.put(rejected).queued()); harness.settle();
            assertEquals(63, harness.authority.pendingCount());
            assertTrue(harness.put(intent(66, id(998))).queued(), "the freed pending slot is reusable");
            harness.settle();
            var reArmed = rejected.withPhase(BayReceipt.Phase.INTENT, "NONE", 30).withKey(id(81), new RocketPosition(0, 2, 0));
            var full = harness.put(reArmed);
            assertEquals("DEPLOYMENT_CAPACITY", reason(full), "full refuses the re-arm");
            assertEquals(rejected, harness.store().current(rejected.op()).orElseThrow(), "without changing op, key or package");
            assertTrue(harness.put(harness.store().receipts().get(1).withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 31)).queued());
            harness.settle();
            assertTrue(harness.put(reArmed).queued(), "a later exact resolution permits the re-arm with the same op");
            harness.settle();
            assertEquals(reArmed, harness.store().current(rejected.op()).orElseThrow());
            assertEquals(64, harness.authority.pendingCount());
        }
    }

    @Test void retainedLimitsAndStoreRefusalsMapToTheirReasons() throws Exception {
        try (var harness = BayReceiptHarness.empty(temp)) {
            long n = 0;
            for (int i = 0; i < EscrowAdmissionAuthority.RETAINED_PER_OWNER; i++) {
                assertTrue(harness.put(receipt(++n, OWNER, id(60_000 + n), BayReceipt.Phase.REJECTED)).queued());
            }
            assertEquals("OWNER_RECEIPTS_FULL", reason(harness.put(intent(++n, id(1)))), "128 per owner");
            for (int owner = 1; owner < EscrowAdmissionAuthority.RETAINED / EscrowAdmissionAuthority.RETAINED_PER_OWNER; owner++) {
                for (int i = 0; i < EscrowAdmissionAuthority.RETAINED_PER_OWNER; i++) {
                    assertTrue(harness.put(receipt(++n, id(3_000 + owner), id(60_000 + n), BayReceipt.Phase.REJECTED)).queued());
                }
            }
            assertEquals("RECEIPTS_FULL", reason(harness.put(receipt(++n, id(9_000), id(2), BayReceipt.Phase.INTENT))), "4,096");
            var busy = harness.store().receipts().get(0);
            assertEquals("RECEIPT_PENDING", reason(harness.put(busy.withPhase(BayReceipt.Phase.INTENT, "NONE", 3))),
                    "a change in flight for the operation");
            harness.settle();
            var other = harness.store().receipts().get(1);
            assertEquals("BAY_CONFLICT", reason(harness.put(busy.withKey(other.rocket(), other.bay()))), "another receipt holds the key");
            assertEquals("BAY_RECEIPTS_UNAVAILABLE", reason(BayReceiptStore.Admission.of(BayReceiptStore.Status.STORE_UNAVAILABLE)));
            assertEquals("RECEIPT_IDENTITY_CONFLICT", reason(new BayReceiptStore.Admission(BayReceiptStore.Status.RESERVE_REFUSED,
                    EscrowAdmissionAuthority.Retention.RESERVED, EscrowAdmissionAuthority.Result.IDENTITY_CONFLICT)));
            assertEquals("BAY_CONFLICT", reason(BayReceiptStore.Admission.of(BayReceiptStore.Status.RELEASED_LOCKED)));
            assertThrows(IllegalArgumentException.class, () -> reason(BayReceiptStore.Admission.of(BayReceiptStore.Status.QUEUED)));
        }
    }
}
