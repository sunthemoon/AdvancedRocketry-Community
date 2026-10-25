package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketI6TransactionGuardTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oldOriginTransactionBlocksRetirementWithoutNamingCurrentDestination() {
        var transfer = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var source = transfer.sourceSnapshot();
        var transaction = transaction(UUID.randomUUID(), RocketRegion.fromSnapshot(source), UUID.randomUUID());
        assertTrue(RocketI6Retirement.transactionConflicts(transaction, transfer,
                transfer.destinationSnapshot(), UUID.randomUUID()));
    }

    @Test void predecessorIdentityBlocksEvenWhenItsTransactionRegionIsElsewhere() {
        var transfer = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var other = transfer.sourceSnapshot().relocated(UUID.randomUUID(), transfer.sourceSnapshot().sourceDimension(),
                new RocketPosition(900, 80, 900), 0);
        assertTrue(RocketI6Retirement.transactionConflicts(transaction(transfer.sourceSnapshot().snapshotId(),
                RocketRegion.fromSnapshot(other), UUID.randomUUID()), transfer, transfer.destinationSnapshot(), UUID.randomUUID()));
        assertTrue(RocketI6Retirement.transactionConflicts(transaction(UUID.randomUUID(),
                RocketRegion.fromSnapshot(other), transfer.sourceEntityId()), transfer, transfer.destinationSnapshot(), UUID.randomUUID()));
    }

    @Test void currentDestinationStillBlocksAndUnrelatedTransactionDoesNot() {
        var transfer = RocketStationMotionRuleTest.record(new RocketPosition(72, 80, 8));
        var current = transfer.destinationSnapshot(); var entity = UUID.randomUUID();
        var elsewhere = current.relocated(UUID.randomUUID(), current.sourceDimension(), new RocketPosition(900, 80, 900), 0);
        assertTrue(RocketI6Retirement.transactionConflicts(transaction(UUID.randomUUID(),
                RocketRegion.fromSnapshot(current), UUID.randomUUID()), transfer, current, entity));
        assertTrue(RocketI6Retirement.transactionConflicts(transaction(current.snapshotId(),
                RocketRegion.fromSnapshot(elsewhere), UUID.randomUUID()), transfer, current, entity));
        assertTrue(RocketI6Retirement.transactionConflicts(transaction(UUID.randomUUID(),
                RocketRegion.fromSnapshot(elsewhere), entity), transfer, current, entity));
        assertFalse(RocketI6Retirement.transactionConflicts(transaction(UUID.randomUUID(),
                RocketRegion.fromSnapshot(elsewhere), UUID.randomUUID()), transfer, current, entity));
    }

    private static RocketTransactionRecord transaction(UUID snapshot, RocketRegion region, UUID entity) {
        return new RocketTransactionRecord(UUID.randomUUID(), RocketTransactionType.DISASSEMBLY,
                RocketTransactionPhase.LOCKED, snapshot, "fixture", region, 0, entity);
    }
}
