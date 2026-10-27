package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W27d1 A0: the claim's start and end (R49 Q1), the 8-bay limit and every member prefix of D0 to D2 and restart. */
final class BayClaimTest {
    private static final UUID L = new UUID(27, 1), T = new UUID(27, 2), S = new UUID(27, 3), OWNER = new UUID(27, 4);
    private static final String HASH = RocketNativeResources.hash("w27d1");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static List<BayMember> bays(int count) {
        var members = new ArrayList<BayMember>();
        for (int n = 0; n < count; n++) { members.add(BayMember.candidate(new RocketPosition(0, n, 0), new UUID(9, n))); }
        return members;
    }
    private static BayReceipt intent(int bay) {
        return new BayReceipt(new UUID(7, bay), bay + 1, L, new RocketPosition(0, bay, 0), new UUID(9, bay), OWNER,
                ModIdentity.id("w27b_solar").toString(), SatelliteKind.SOLAR, ModIdentity.id("earth").toString(), HASH, T,
                HASH, null, HASH, null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 10, 10, 0);
    }
    private static void passGroup(BayClaim claim) { for (int step = 0; step < 6; step++) { claim.groupPassed(6); } }

    @Test void bayAfterTheFirstEightAreRefusedAtOnceWithTheRocketLimit() {
        var claim = BayClaim.checkpoint(L, T, S, bays(10));
        assertEquals(BayClaim.Stage.GROUP, claim.stage());
        assertEquals(8, claim.members().stream().filter(member -> member.state() == BayMember.State.CANDIDATE).count());
        assertEquals(List.of("ROCKET_DEPLOYMENT_LIMIT", "ROCKET_DEPLOYMENT_LIMIT"), claim.members().subList(8, 10).stream()
                .map(BayMember::reason).toList());
        assertEquals("BAY_VALIDATION", claim.reason());
        assertEquals(BayClaim.Hold.OWNED_WAIT, claim.hold());
    }

    @Test void aGroupRefusalRejectsEveryBayAndTheClaimEndsAtOnce() {
        var claim = BayClaim.checkpoint(L, T, S, bays(2));
        claim.groupPassed(6); claim.groupPassed(6);
        claim.rejectAll("BAY_SOURCE_PROTECTED");
        assertTrue(claim.members().stream().allMatch(member -> member.state() == BayMember.State.REFUSED
                && member.reason().equals("BAY_SOURCE_PROTECTED")));
        assertTrue(claim.finished(), "D0 rejected every bay: the claim ends at once (Q1)");
        claim.end(BayClaim.Handoff.READY_SOURCE);
        assertEquals(BayClaim.Stage.ENDED, claim.stage());
        assertEquals("READY_SOURCE", claim.reason());
    }

    @Test void theAssemblyJournalRequestIsRecordedOnlyInTheGroupStage() {
        var claim = BayClaim.checkpoint(L, T, S, bays(1));
        for (int step = 0; step < 5; step++) { claim.groupPassed(6); }
        assertFalse(claim.checkpointRequested());
        claim.markCheckpointRequested();
        assertTrue(claim.checkpointRequested());
        assertEquals("ASSEMBLY_JOURNAL_CHECKPOINT", claim.reason(), "the claim is HELD while the request waits");
        claim.groupPassed(6);
        assertEquals(BayClaim.Stage.BAYS, claim.stage());
        assertThrows(IllegalStateException.class, claim::markCheckpointRequested);
    }

    @Test void everyD1AndD2PrefixKeepsTheClaimUntilTheLastRejectedAcknowledgement() {
        var claim = BayClaim.checkpoint(L, T, S, bays(3));
        passGroup(claim);
        BayMember first = claim.nextCandidate().orElseThrow(); first.admitted(intent(0));
        BayMember second = claim.nextCandidate().orElseThrow(); second.refuse("BAY_OWNER_MISMATCH");
        BayMember third = claim.nextCandidate().orElseThrow(); third.admitted(intent(2));
        assertTrue(claim.nextCandidate().isEmpty());
        assertEquals(BayClaim.Stage.OUTCOMES, claim.stage(), "a partial admission still has outcomes to wait for");
        assertFalse(claim.finished(), "INTENT queued: held");
        third.refuse("RECEIPT_WRITE_FAILED"); // a definite D1 failure: the store restored the prior state
        first.intentAcknowledged();
        assertFalse(claim.finished(), "INTENT acknowledged: D2 is queued");
        var rejected = intent(0).withPhase(BayReceipt.Phase.REJECTED, "DEPLOYMENT_UNAVAILABLE", 20);
        first.outcomeQueued(rejected);
        assertEquals(BayMember.State.REJECT_WRITE, first.state());
        first.outcomeFailed(intent(0));
        assertEquals(BayMember.State.INTENT, first.state(), "a definitely failed REJECTED write is requeued");
        first.outcomeQueued(rejected);
        assertFalse(claim.finished(), "awaiting the REJECTED acknowledgement");
        first.outcomeAcknowledged();
        assertTrue(claim.finished());
        var restarted = BayMember.restartIntent(intent(0));
        var restart = BayClaim.restart(L, T, List.of(restarted));
        assertThrows(IllegalStateException.class, () -> restart.end(BayClaim.Handoff.ORDINARY), "INTENT still unresolved");
        restarted.outcomeQueued(intent(0).withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 21));
        restarted.outcomeAcknowledged();
        assertThrows(IllegalArgumentException.class, () -> restart.end(BayClaim.Handoff.READY_SOURCE),
                "only a session with source authority hands READY_SOURCE");
        restart.end(BayClaim.Handoff.ORDINARY);
        assertEquals("RELEASED_TO_ORDINARY", restart.reason());
    }

    @Test void restartRowsHoldOrWriteAndARepairOrHeldMemberNeverEnds() {
        var interrupted = BayMember.restartIntent(intent(0));
        var held = BayMember.restartHeld(intent(1).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 3),
                "REGISTRY_SIGNATURE_CONFLICT");
        var claim = BayClaim.restart(L, T, List.of(interrupted, held));
        claim.repair("REGISTRY_SIGNATURE_CONFLICT");
        assertEquals(BayClaim.Stage.OUTCOMES, claim.stage());
        assertEquals(BayClaim.Hold.REPAIR, claim.hold());
        interrupted.outcomeQueued(intent(0).withPhase(BayReceipt.Phase.REJECTED, "INTERRUPTED", 5));
        interrupted.outcomeAcknowledged();
        assertFalse(claim.finished(), "a REPAIR receipt holds until offline repair");
        var repair = BayMember.restartIntent(intent(2));
        repair.outcomeQueued(intent(2).withPhase(BayReceipt.Phase.REPAIR, "REGISTRY_SIGNATURE_CONFLICT", 6));
        assertEquals(BayMember.State.REPAIR_WRITE, repair.state());
        repair.outcomeAcknowledged();
        assertEquals(BayMember.State.HELD, repair.state());
        var blocked = BayMember.restartIntent(intent(3));
        blocked.hold("REGISTRY_BLOCKED");
        assertFalse(blocked.resolved());
        assertThrows(IllegalArgumentException.class, () -> BayMember.restartIntent(intent(4).withReleased()));
    }

    @Test void theBookReportsEachHandoffOnceForItsOwnTransferAndThenForgetsTheRocket() {
        var book = new BayClaimBook();
        var claim = BayClaim.checkpoint(L, T, S, bays(1));
        assertTrue(book.begin(claim));
        assertFalse(book.begin(BayClaim.checkpoint(L, T, S, bays(1))), "one claim per logical rocket");
        assertTrue(book.holds(L));
        assertTrue(book.report(L, T).isEmpty(), "a running claim reports nothing");
        claim.rejectAll("REGISTRY_BLOCKED");
        claim.end(BayClaim.Handoff.ORDINARY);
        assertTrue(book.holds(L), "an ended claim holds until its handoff is reported");
        assertTrue(book.report(L, new UUID(0, 1)).isEmpty(), "another record of L is not this claim's handoff");
        assertEquals(BayClaim.Handoff.ORDINARY, book.report(L, T).orElseThrow());
        assertFalse(book.holds(L));
        assertTrue(book.report(L, T).isEmpty(), "reported once");
        for (int n = 0; n < BayClaimBook.LIMIT; n++) { assertTrue(book.begin(BayClaim.checkpoint(new UUID(5, n), T, S, bays(1)))); }
        assertFalse(book.begin(BayClaim.checkpoint(new UUID(6, 0), T, S, bays(1))), "at most 256 claims");
    }
}
