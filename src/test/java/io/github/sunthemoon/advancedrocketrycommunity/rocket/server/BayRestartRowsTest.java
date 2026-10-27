package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W27d1 A0: the startup classification builds one claim per held rocket; REJECTED receipts claim nothing. */
final class BayRestartRowsTest {
    private static final String HASH = RocketNativeResources.hash("w27d1-restart");
    private static final UUID OWNER = new UUID(3, 3);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static BayReceipt receipt(int n, UUID rocket, UUID transfer, BayReceipt.Phase phase, boolean released) {
        var receipt = new BayReceipt(new UUID(1, n), n, rocket, new RocketPosition(0, 9 - n, 0), new UUID(2, n), OWNER,
                ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA, ModIdentity.id("earth").toString(), HASH,
                transfer, HASH, null, HASH, null, phase, phase == BayReceipt.Phase.REPAIR ? "BAY_CONFLICT" : "NONE",
                false, false, 0, 1, 1, 0);
        return released ? receipt.withReleased() : receipt;
    }

    @Test void heldRocketsClaimTheirReceiptsInBayOrderAndRejectedReceiptsClaimNothing() {
        UUID a = new UUID(9, 1), b = new UUID(9, 2), c = new UUID(9, 3), t = new UUID(8, 1);
        var claims = BayRestartRows.claims(List.of(
                receipt(1, a, t, BayReceipt.Phase.INTENT, false),
                receipt(2, a, t, BayReceipt.Phase.INTENT, false),
                receipt(3, b, t, BayReceipt.Phase.REJECTED, false),
                receipt(4, c, t, BayReceipt.Phase.REPAIR, false),
                receipt(5, c, t, BayReceipt.Phase.REJECTED, true)));
        assertEquals(List.of(a, c), claims.stream().map(BayClaim::logical).toList(), "the REJECTED-only rocket is unclaimed");
        var first = claims.get(0);
        assertEquals(BayClaim.Origin.RESTART, first.origin());
        assertTrue(first.source().isEmpty(), "a restart claim never has source authority");
        assertEquals(List.of(new RocketPosition(0, 7, 0), new RocketPosition(0, 8, 0)), first.members().stream().map(BayMember::bay).toList());
        assertTrue(first.members().stream().allMatch(member -> member.state() == BayMember.State.INTENT));
        assertEquals(BayClaim.Hold.OWNED_WAIT, first.hold());
        var second = claims.get(1);
        assertEquals(BayClaim.Hold.REPAIR, second.hold(), "a REPAIR receipt holds until offline repair");
        assertEquals("BAY_CONFLICT", second.reason());
        assertTrue(second.members().stream().allMatch(member -> member.state() == BayMember.State.HELD),
                "REPAIR and released receipts wait unchanged");
        assertFalse(second.finished());
    }

    @Test void receiptsOfOneRocketNamingDifferentTransfersHoldForRepairWithoutAWrite() {
        UUID rocket = new UUID(9, 9);
        var claims = BayRestartRows.claims(List.of(receipt(1, rocket, new UUID(8, 1), BayReceipt.Phase.INTENT, false),
                receipt(2, rocket, new UUID(8, 2), BayReceipt.Phase.INTENT, false)));
        assertEquals(1, claims.size());
        assertEquals(BayClaim.Hold.REPAIR, claims.get(0).hold());
        assertEquals("TRANSFER_AUTHORITY_CONFLICT", claims.get(0).reason());
        assertTrue(claims.get(0).members().stream().allMatch(member -> member.state() == BayMember.State.HELD));
    }
}
