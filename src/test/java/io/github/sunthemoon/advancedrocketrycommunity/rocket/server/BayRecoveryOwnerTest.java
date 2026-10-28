package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R25 A0: a REGISTERED group and its checked record must agree on owner before any recovery effect, for a BEFORE
 * record, an AFTER record and a group whose receipts name two owners. The first case is review pass 2's probe.
 */
final class BayRecoveryOwnerTest {
    private static final UUID OTHER = new UUID(999, 1);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    /** S' and D' for bays 0 and 1 of {@code record}, each consumed by its own INTENT's operation. */
    private static BayConsumedSnapshots.Result consumed(RocketTransferRecord record) {
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(),
                Map.of(bay(0), intent(record, 0), bay(1), intent(record, 1))).orElseThrow();
        var budget = new RocketCargoWorkBudget();
        for (long tick = 1; !job.step(budget, tick); tick++) { }
        return job.result().orElseThrow();
    }

    private static BayReceipt withOwner(BayReceipt receipt, UUID owner) {
        return new BayReceipt(receipt.op(), receipt.seq(), receipt.rocket(), receipt.bay(), receipt.satellite(), owner,
                receipt.definition(), receipt.kind(), receipt.orbit(), receipt.packageSig(), receipt.transfer(), receipt.srcBefore(),
                receipt.srcAfter(), receipt.dstBefore(), receipt.dstAfter(), receipt.phase(), receipt.reason(), receipt.preexisting(),
                receipt.released(), receipt.registryEpoch(), receipt.createdTick(), receipt.updatedTick(), receipt.attempts());
    }

    private static List<BayReceipt> receipts(RocketTransferRecord record, UUID secondOwner) {
        var after = consumed(record);
        return List.of(registered(intent(record, 0), after.source().contentHash(), after.destination().contentHash()),
                withOwner(registered(intent(record, 1), after.source().contentHash(), after.destination().contentHash()), secondOwner));
    }

    private static BayGroup group(RocketTransferRecord record) {
        return BayGroup.resumed(LOGICAL, TRANSFER, receipts(record, OWNER).stream().map(BayMember::restartRegistered).toList())
                .orElseThrow();
    }

    /** The same T, with its owner replaced; checksums are recomputed, so only the owner differs. */
    private static RocketTransferRecord owned(RocketTransferRecord record, UUID owner) {
        return RocketTransferRecord.create(record.transferId(), record.logicalRocketId(), owner, record.sourceEntityId(),
                record.sourceSnapshot(), record.destinationSnapshot(), record.sourceFlightData(), record.destinationFlightData(),
                record.requiredFuel(), record.createdAtGameTime());
    }

    @Test void aRegisteredGroupRefusesABeforeRecordWithAnotherOwner() {
        var before = record(2);
        var group = group(before);
        assertEquals(BayGroup.Half.BEFORE, BayConsumptionSteps.half(before, group), "control: the matching owner is BEFORE");
        assertNull(BayConsumptionSteps.half(owned(before, OTHER), group), "a checked T with another owner is not the group's T");
    }

    @Test void aRegisteredGroupRefusesAnAfterRecordWithAnotherOwner() {
        var before = record(2);
        var group = group(before);
        var after = consumed(before);
        var consumed = before.withConsumedSnapshots(after.source(), after.destination());
        assertEquals(BayGroup.Half.AFTER, BayConsumptionSteps.half(consumed, group), "control: the matching owner is AFTER");
        assertNull(BayConsumptionSteps.half(owned(consumed, OTHER), group), "a consumed T with another owner is not the group's T'");
    }

    @Test void aGroupWhoseReceiptsNameTwoOwnersNeverFreezes() {
        var before = record(2);
        var mixed = receipts(before, OTHER);
        assertTrue(BayGroup.resumed(LOGICAL, TRANSFER, mixed.stream().map(BayMember::restartRegistered).toList()).isEmpty(),
                "a restart group has one owner");
        var claims = BayRestartRows.claims(mixed);
        assertEquals(1, claims.size());
        var claim = claims.get(0);
        assertEquals(BayClaim.Hold.REPAIR, claim.hold(), "a mixed-owner restart group holds for repair");
        assertEquals("TRANSFER_AUTHORITY_CONFLICT", claim.reason());
        assertTrue(claim.group().isEmpty(), "no group is frozen, so no recovery effect can follow");
        assertEquals(BayClaim.Stage.OUTCOMES, claim.stage());
        var members = mixed.stream().map(BayMember::restartRegistered).toList();
        assertThrows(IllegalArgumentException.class, () -> new BayGroup(LOGICAL, TRANSFER, members, mixed.get(0).srcBefore(),
                mixed.get(0).dstBefore(), mixed.get(0).srcAfter(), mixed.get(0).dstAfter()), "an in-session group has one owner too");
    }
}
