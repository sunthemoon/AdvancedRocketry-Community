package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowSettlementFormatTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void neutralObservationFreezesOnlyThePrescribedWrapperAndTypedFrontier() {
        var sample = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var old = sample.destination();
        var nativeRef = new EscrowEndpoint(old.kind(), old.level(), old.identity(), old.logicalRocket(),
                "advancedrocketrycommunity:rocket", old.x(), old.y(), old.z(), old.unit(), old.beforeEpoch(),
                old.beforeSignature(), old.afterSignature());
        var incomplete = new EscrowOperation(sample.id(), sample.sequence(), sample.owner(), sample.createdTick(), sample.phase(),
                sample.reason(), sample.payload(), sample.payloadHash(), sample.source(), nativeRef, sample.guard());
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(incomplete));
        var op = EscrowTestFixtures.nativeModel(incomplete);
        var malformed = EscrowCodec.operation(op); malformed.getCompound("native_binding").putBoolean("complete", false);
        assertThrows(IllegalArgumentException.class, () -> EscrowCodec.operation(malformed));
        var root = EscrowTestFixtures.ledger(op);
        var source = EscrowSettlementIntentTest.observations(root, false).stream()
                .filter(observation -> observation.endpoint().kind() == EscrowEndpoint.Kind.BLOCK_ENTITY).findFirst().orElseThrow();
        var neutral = new EscrowSettlementIntent.Observation(nativeRef,
                new EscrowPrefix.State(root.id(), 0, nativeRef.beforeSignature(), List.of()), false, 0);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(700), "operator", 10, List.of(source, neutral));
        assertEquals(preview, EscrowSettlementIntent.decode(preview.encode()));
        assertFalse(neutral.bound()); assertTrue(neutral.checkpointed().bound());
        assertEquals(neutral.state(), neutral.checkpointed().state());
        var confirmed = preview.confirm(preview.actor(), preview.digest(), 11, preview.observations());
        var frozen = confirmed.freeze(root);
        try (var recovery = new EscrowSettlementRecovery()) {
            var decoded = recovery.intents(frozen);
            assertEquals(List.of(confirmed), decoded); assertSame(decoded, recovery.intents(frozen));
            assertNotSame(decoded, recovery.intents(frozen.generation(1)));
        }
        assertEquals(new RocketEscrowSection(root.id(), 0, List.of()), neutral.normalizedWrapper());
        assertEquals(EscrowSignatureKind.FUEL_VECTOR_V1, neutral.signatureKind());
        var encoded = EscrowSettlementIntent.encodeObservation(neutral);
        encoded.getCompound("normalized_wrapper").putLong("resource_epoch", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decodeObservation(encoded));
        var stale = preview.encode(); stale.putInt("schema_version", 1);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(stale));
    }

    @Test void unknownAccountingCannotBecomeAConservationVerdictOrLoseAuditObservations() {
        for (boolean missing : List.of(false, true)) {
            var op = EscrowTestFixtures.operation(1).withReason(missing ? EscrowReason.MISSING_ENDPOINT : EscrowReason.OTHER_STATE);
            var root = EscrowTestFixtures.ledger(op); var observed = EscrowSettlementIntentTest.observations(root, missing);
            var preview = EscrowSettlementIntent.preview(root, op.id(), missing ? EscrowSettlementIntent.Choice.ABANDON
                    : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, EscrowTestFixtures.id(700), "operator", 10, observed);
            var intent = preview.confirm(preview.actor(), preview.digest(), missing ? 210 : 11, observed);
            var receipt = EscrowSettlementTombstones.create(op, intent);
            assertEquals(3, receipt.getInt("schema_version")); assertEquals("UNKNOWN", receipt.getString("accounting_status"));
            assertEquals(1_000, receipt.getInt("payload_amount"));
            assertFalse(receipt.contains("loss_known")); assertFalse(receipt.contains("audited_loss"));
            var encoded = EscrowReceiptCodec.encode(receipt);
            assertEquals(receipt, EscrowReceiptCodec.decode(encoded));
            assertEquals(2, receipt.getList("before_choice", 10).size());
            System.out.println("receipt " + intent.choice() + " bytes=" + RocketNbtSize.uncompressedBytes(encoded));
            for (String status : List.of("KNOWN", "", "REFUND")) {
                var changed = receipt.copy(); changed.putString("accounting_status", status);
                assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(changed, true));
            }
            for (String field : List.of("loss_known", "audited_loss")) {
                var changed = receipt.copy(); changed.putLong(field, 0);
                assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(changed, true));
            }
            var projected = receipt.copy(); projected.getList("survivors", 10).getCompound(0).putLong("quantity", 999);
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(projected, true));
            var old = receipt.copy(); old.putInt("schema_version", 2);
            assertThrows(IllegalArgumentException.class, () -> EscrowCodec.validateSection(old, true));
        }
    }
    @Test void floorRetirementKeepsZeroPayloadLiabilityDistinctFromUnknownLoss() {
        var floor = EscrowTestFixtures.floor(99); floor.putUUID("transfer_id", EscrowTestFixtures.id(800));
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observed = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(701), floor, EscrowTestFixtures.id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 210, observed);
        var receipt = EscrowSettlementTombstones.create(intent.floorOperation(2, 210), intent);
        assertEquals(0, receipt.getInt("payload_amount")); assertEquals("UNKNOWN", receipt.getString("accounting_status"));
        assertEquals(1, receipt.getList("missing", 10).size()); assertTrue(receipt.getList("survivors", 10).isEmpty());
        assertFalse(receipt.contains("audited_loss")); assertFalse(receipt.contains("loss_known"));
        assertEquals(receipt, EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt)));
    }
}
