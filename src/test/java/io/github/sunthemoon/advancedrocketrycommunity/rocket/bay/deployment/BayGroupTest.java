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
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W170 A0: every D2 to D7 prefix of a member and a claim for one and eight bays, mixed groups and the refusals. */
final class BayGroupTest {
    private static final UUID L = new UUID(170, 1), T = new UUID(170, 2), S = new UUID(170, 3), OWNER = new UUID(170, 4);
    private static final String B1 = RocketNativeResources.hash("src-before"), B2 = RocketNativeResources.hash("dst-before"),
            A1 = RocketNativeResources.hash("src-after"), A2 = RocketNativeResources.hash("dst-after");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static BayReceipt intent(int bay) {
        return new BayReceipt(new UUID(7, bay), bay + 1, L, new RocketPosition(0, bay, 0), new UUID(9, bay), OWNER,
                ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA, ModIdentity.id("earth").toString(), B1, T,
                B1, null, B2, null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 10, 10, 0);
    }
    private static BayReceipt registered(BayReceipt intent) {
        return new BayReceipt(intent.op(), intent.seq(), intent.rocket(), intent.bay(), intent.satellite(), intent.owner(),
                intent.definition(), intent.kind(), intent.orbit(), intent.packageSig(), intent.transfer(), B1, A1, B2, A2,
                BayReceipt.Phase.REGISTERED, "NONE", false, false, 5, 10, 20, 0);
    }
    /** A checkpoint claim whose {@code count} bays reached INTENT, acknowledged. */
    private static BayClaim admitted(int count) {
        var members = new ArrayList<BayMember>();
        for (int n = 0; n < count; n++) { members.add(BayMember.candidate(new RocketPosition(0, n, 0), new UUID(9, n))); }
        var claim = BayClaim.checkpoint(L, T, S, members);
        for (int step = 0; step < 6; step++) { claim.groupPassed(6); }
        for (int n = 0; n < count; n++) {
            var member = claim.nextCandidate().orElseThrow();
            member.admitted(intent(n)); member.intentAcknowledged();
        }
        assertTrue(claim.nextCandidate().isEmpty());
        return claim;
    }
    private static BayGroup group(List<BayMember> members) { return new BayGroup(L, T, members, B1, B2, A1, A2); }

    @Test void oneAndEightBaysWalkEveryPrefixFromD2ToD7() {
        for (int count : new int[] {1, 8}) {
            var claim = admitted(count);
            assertFalse(claim.finished(), "INTENT is not resolved");
            claim.members().forEach(BayMember::registered);
            assertEquals("BAY_REGISTRY_PROOF", claim.reason(), "D3 waits for the registry's acknowledgement");
            assertTrue(claim.members().stream().allMatch(BayMember::awaiting));
            claim.members().forEach(BayMember::proved);
            assertTrue(claim.members().stream().allMatch(BayMember::consumable));
            var group = group(claim.members());
            claim.freeze(group);
            assertEquals(BayClaim.Stage.PUBLISH, claim.stage(), "an in-session group publishes first");
            assertEquals(count, group.operations().size());
            claim.members().forEach(member -> member.groupQueued(registered(member.receipt().orElseThrow())));
            assertThrows(IllegalStateException.class, () -> claim.advance(BayClaim.Stage.PROVE), "D5 comes before D6");
            claim.members().forEach(BayMember::groupAcknowledged);
            assertThrows(IllegalStateException.class, () -> claim.advance(BayClaim.Stage.RESUME), "an in-session claim holds its source");
            claim.advance(BayClaim.Stage.CONSUME);
            assertEquals("BAY_CONSUMPTION", claim.reason());
            claim.advance(BayClaim.Stage.PROVE);
            claim.advance(BayClaim.Stage.RETIRE);
            claim.advance(BayClaim.Stage.PROVE); // B16: a refused guard repeats D6
            claim.advance(BayClaim.Stage.RETIRE);
            assertFalse(claim.finished(), "REGISTERED members are not resolved before retirement");
            assertThrows(IllegalStateException.class, () -> claim.end(BayClaim.Handoff.READY_SOURCE));
            claim.members().forEach(BayMember::retired);
            assertTrue(claim.finished());
            assertThrows(IllegalArgumentException.class, () -> claim.end(BayClaim.Handoff.ORDINARY), "a consumed group ends with its source");
            claim.end(BayClaim.Handoff.READY_SOURCE);
            assertEquals(BayClaim.Stage.ENDED, claim.stage());
        }
    }

    @Test void aMixedGroupFreezesOnlyItsProvedMembersAndADefiniteFailureRestoresIntent() {
        var claim = admitted(3);
        var proved = claim.members().get(0); var rejected = claim.members().get(1); var third = claim.members().get(2);
        proved.registered(); proved.proved();
        rejected.outcomeQueued(rejected.receipt().orElseThrow().withPhase(BayReceipt.Phase.REJECTED, "RESEARCH_LOCKED", 11));
        third.registered();
        assertThrows(IllegalArgumentException.class, () -> claim.freeze(group(List.of(proved))),
                "a member still waiting for its outcome blocks the freeze");
        rejected.outcomeAcknowledged(); third.proved();
        assertThrows(IllegalArgumentException.class, () -> claim.freeze(group(List.of(proved, rejected))), "only proved members");
        claim.freeze(group(List.of(proved, third)));
        assertThrows(IllegalStateException.class, () -> claim.freeze(group(List.of(proved, third))), "a frozen group never changes");
        var queued = registered(proved.receipt().orElseThrow());
        proved.groupQueued(queued);
        assertThrows(IllegalStateException.class, () -> proved.groupFailed(queued), "only INTENT is restored");
        proved.groupFailed(proved.receipt().orElseThrow().withPhase(BayReceipt.Phase.INTENT, "NONE", 12));
        assertEquals(BayMember.State.PROVED, proved.state(), "a definite failure publishes again");
        assertEquals(BayMember.State.REJECTED, rejected.state(), "the REJECTED bay stays out of the group");
    }

    @Test void aRestartGroupResumesFromRegisteredReceiptsAndNeedsItsSourceBeforeConsuming() {
        var members = new ArrayList<BayMember>();
        for (int n = 0; n < 8; n++) { members.add(BayMember.restartRegistered(registered(intent(n)))); }
        var claim = BayClaim.restart(L, T, members);
        var group = BayGroup.resumed(L, T, members).orElseThrow();
        claim.freeze(group);
        assertEquals(BayClaim.Stage.RESUME, claim.stage(), "R1 and R2 come first after a restart");
        assertThrows(IllegalStateException.class, () -> claim.advance(BayClaim.Stage.CONSUME), "no source authority yet");
        claim.establishSource(S);
        assertThrows(IllegalStateException.class, () -> claim.establishSource(S));
        claim.advance(BayClaim.Stage.CONSUME);
        assertEquals(S, claim.source().orElseThrow());
        assertEquals(List.of(A1, A2), List.of(group.srcAfter(), group.dstAfter()));
        assertEquals(BayGroup.Half.BEFORE, group.sourceHalf(B1));
        assertEquals(BayGroup.Half.AFTER, group.destinationHalf(A2));
        assertEquals(BayGroup.Half.OUTSIDE, group.sourceHalf(A2), "never the other end's hash");
    }

    @Test void incoherentRestartGroupsAreRefused() {
        var a = BayMember.restartRegistered(registered(intent(0)));
        var otherAfter = intent(1);
        var b = BayMember.restartRegistered(new BayReceipt(otherAfter.op(), 2, L, otherAfter.bay(), otherAfter.satellite(), OWNER,
                otherAfter.definition(), otherAfter.kind(), otherAfter.orbit(), otherAfter.packageSig(), T, B1,
                RocketNativeResources.hash("another"), B2, A2, BayReceipt.Phase.REGISTERED, "NONE", false, false, 0, 1, 1, 0));
        assertTrue(BayGroup.resumed(L, T, List.of(a, b)).isEmpty(), "different after hashes");
        assertTrue(BayGroup.resumed(L, new UUID(1, 1), List.of(a)).isEmpty(), "another transfer");
        assertTrue(BayGroup.resumed(L, T, List.of()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(L, T, List.of(a), B1, B2, B1, A2), "an unchanged source");
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(L, T, List.of(a, a), B1, B2, A1, A2), "a bay twice");
        var nine = new ArrayList<BayMember>();
        for (int n = 0; n < 9; n++) { nine.add(BayMember.restartRegistered(registered(intent(n)))); }
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(L, T, nine, B1, B2, A1, A2), "eight bays at most");
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(new UUID(5, 5), T, List.of(a), B1, B2, A1, A2), "another rocket");
        var candidate = BayMember.candidate(new RocketPosition(0, 0, 0), new UUID(9, 0));
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(L, T, List.of(candidate), B1, B2, A1, A2), "an unproved bay");
    }

    @Test void theGroupTokenRefusesAfterEveryInvalidation() {
        var group = BayGroup.resumed(L, T, List.of(BayMember.restartRegistered(registered(intent(0))))).orElseThrow();
        BooleanSupplier first = group.token();
        assertTrue(first.getAsBoolean());
        group.invalidate();
        assertFalse(first.getAsBoolean(), "a save, join, leave or movement refuses every earlier token");
        BooleanSupplier second = group.token();
        assertTrue(second.getAsBoolean());
        group.invalidate();
        assertFalse(second.getAsBoolean()); assertFalse(first.getAsBoolean());
    }

    @Test void d3ReportsUnprovedFromTheThirdFailureAndPacesEachRequest200TicksAfterAFailure() {
        var claim = admitted(1);
        claim.members().get(0).registered();
        assertEquals(200, BayClaim.REGISTRY_RETRY_TICKS, "W27A section 4's D3 pacing");
        assertEquals(3, BayClaim.REGISTRY_UNPROVED_FAILURES, "W27A section 4's REGISTRY_UNPROVED threshold");
        assertTrue(claim.registryRequestDue(0), "the first request is not paced");
        long failedAt = 1_000;
        for (int failure = 1; failure <= 4; failure++) {
            claim.registryFailed(failedAt);
            assertEquals(failure, claim.registryFailures());
            assertFalse(claim.registryRequestDue(failedAt), "no request in the failure's own tick");
            assertFalse(claim.registryRequestDue(failedAt + 199), "no request sooner than 200 ticks after failure " + failure);
            assertTrue(claim.registryRequestDue(failedAt + 200), "the next request 200 ticks after failure " + failure);
            assertEquals(failure < 3 ? "BAY_REGISTRY_PROOF" : "REGISTRY_UNPROVED", claim.reason(), "the reason after failure " + failure);
            failedAt += 200 + failure; // a later request may fail a few ticks after it became due
        }
        assertEquals(BayClaim.Hold.OWNED_WAIT, claim.hold(), "REGISTRY_UNPROVED waits; it never rejects or repairs");
        assertTrue(claim.members().stream().allMatch(member -> member.state() == BayMember.State.REGISTERING));
        claim.waiting(null); // the proof clears it
        assertEquals("BAY_REGISTRY_PROOF", claim.reason());
    }

    @Test void restartRowsResumeOrPromote() {
        var claim = admitted(1);
        var member = claim.members().get(0);
        member.registered();
        assertThrows(IllegalStateException.class, member::registered, "D2 runs once per INTENT");
        var intentOnly = BayMember.restartIntent(intent(1));
        intentOnly.registered();
        assertEquals(BayMember.State.REGISTERING, intentOnly.state(), "a restart promotion is watched like an idempotent D2");
        assertThrows(IllegalArgumentException.class, () -> BayMember.restartRegistered(intent(2)), "only REGISTERED resumes");
        assertThrows(IllegalArgumentException.class, () -> BayMember.restartRegistered(registered(intent(2)).withReleased()),
                "released receipts belong to W27d4");
    }
}
