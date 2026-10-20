package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class EscrowSettlementIntentTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final UUID ACTOR = EscrowTestFixtures.id(700);

    @Test void confirmedAcceptFreezesActorAndExactConnectedRecordsAcrossReload() {
        var first = EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE);
        var second = EscrowTestFixtures.operation(2, 99, 1);
        var unrelated = EscrowTestFixtures.operation(3, 98, 0);
        var root = EscrowTestFixtures.ledger(first, second, unrelated);
        var observations = observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, first.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observations);
        assertEquals(Set.of(first.id(), second.id()), preview.ids());
        assertThrows(IllegalArgumentException.class, () -> preview.freeze(root));
        var confirmed = preview.confirm(ACTOR, preview.digest(), 11, observations);
        var frozen = confirmed.freeze(root);
        assertEquals(frozen, EscrowCodec.decode(EscrowCodec.encode(frozen)));
        assertEquals(unrelated, frozen.records().get(2));
        for (var op : frozen.records().subList(0, 2)) {
            assertEquals(EscrowOperation.Phase.SETTLING_ACCEPT, op.phase());
            assertEquals(confirmed.encode(), op.settlement());
        }
        assertEquals(ACTOR, EscrowSettlementIntent.decode(frozen.records().get(0).settlement()).actor());
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(root.id(), 3, 0,
                List.of(frozen.records().get(0), second, unrelated), List.of(), List.of()));
    }

    @Test void abandonRequiresTwoMatchingMissingObservations200TicksApartAndNeverRetires() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.MISSING_ENDPOINT);
        var root = EscrowTestFixtures.ledger(op); var observations = observations(root, true);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ABANDON, ACTOR, "operator", 10, observations);
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(ACTOR, preview.digest(), 209, observations));
        var frozen = preview.confirm(ACTOR, preview.digest(), 210, observations).freeze(root);
        assertEquals(1, frozen.records().size()); assertTrue(frozen.tombstones().isEmpty());
        assertEquals(EscrowOperation.Phase.SETTLING_ABANDON, frozen.records().get(0).phase());
        assertEquals(frozen, EscrowCodec.decode(EscrowCodec.encode(frozen)));
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(ACTOR, preview.digest(), 210, observations(root, false)));
    }

    @Test void changedActorDigestStateMemberOrLedgerRefusesIntact() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(op); var observations = observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observations);
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(UUID.randomUUID(), preview.digest(), 11, observations));
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(ACTOR, "x".repeat(513), 11, observations));
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(ACTOR, EscrowTestFixtures.hash(0), 11, observations));
        var altered = new ArrayList<>(observations); var old = altered.get(0);
        altered.set(0, new EscrowSettlementIntent.Observation(old.endpoint(), old.state(), old.bound(), old.quantity() + 1));
        assertThrows(IllegalArgumentException.class, () -> preview.confirm(ACTOR, preview.digest(), 11, altered));
        var confirmed = preview.confirm(ACTOR, preview.digest(), 11, observations);
        var changed = new EscrowOperation(op.id(), op.sequence(), op.owner(), 2, op.phase(), op.reason(),
                op.payload(), op.payloadHash(), op.source(), op.destination(), op.guard());
        assertThrows(IllegalArgumentException.class, () -> confirmed.freeze(EscrowTestFixtures.ledger(changed)));
        assertThrows(IllegalArgumentException.class, () -> confirmed.freeze(new EscrowLedger(UUID.randomUUID(), 1, 0, List.of(op), List.of(), List.of())));
        assertTrue(op.settlement().isEmpty());
    }

    @Test void changedConnectedComponentAndIncompleteObservationsRefuse() {
        var first = EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(first); var observations = observations(root, false);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(root, first.id(),
                EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, List.of(observations.get(0))));
        var confirmed = EscrowSettlementIntent.preview(root, first.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observations)
                .confirm(ACTOR, EscrowSettlementIntent.preview(root, first.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observations).digest(), 11, observations);
        assertThrows(IllegalArgumentException.class, () -> confirmed.freeze(EscrowTestFixtures.ledger(first, EscrowTestFixtures.operation(2, 99, 1))));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.component(List.of(first), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.component(java.util.Collections.nCopies(65, first), first.id()));
    }

    @Test void floorRetirementHasAnExplicitKindAndCannotLoseItsFloorOrJoinNormalPrefixes() {
        var floor = EscrowTestFixtures.floor(99); floor.putUUID("transfer_id", EscrowTestFixtures.id(800));
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var observations = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var preview = EscrowSettlementIntent.floorPreview(root, EscrowTestFixtures.id(701), floor, ACTOR, "operator", 10, observations);
        assertThrows(IllegalArgumentException.class, () -> preview.floorOperation(2, 210));
        var intent = preview.confirm(ACTOR, preview.digest(), 210, observations); var op = intent.floorOperation(2, 210);
        assertEquals(EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE, op.kind());
        assertTrue(EscrowPrefix.chains(List.of(op)).isEmpty());
        assertEquals(op, EscrowCodec.operation(EscrowCodec.operation(op)));
        var frozen = root.append(List.of(op)); assertEquals(frozen, EscrowCodec.decode(EscrowCodec.encode(frozen)));
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(root.id(), 2, 0, List.of(op), List.of(), List.of()));
        assertEquals(1, frozen.floors().size()); assertTrue(frozen.tombstones().isEmpty());
    }

    @Test void boundsCanonicalDigestAndOfflineOnlyConflictsAreEnforced() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var observations = observations(root, false);
        String actorAtLimit = "a".repeat(16);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, actorAtLimit, 10, observations);
        assertEquals(preview, EscrowSettlementIntent.decode(preview.encode()));
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                ACTOR, "a".repeat(17), 10, observations));
        var malformed = preview.encode(); malformed.putString("choice", "REFUND");
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(malformed));
        var tampered = preview.encode(); tampered.putString("actor_name", "someone else");
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(tampered));
        var unexpected = preview.encode(); unexpected.putBoolean("proved", true);
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.decode(unexpected));
        for (var reason : List.of(EscrowReason.RELOCATED_ENDPOINT, EscrowReason.FLOOR_CONFLICT)) {
            var manual = EscrowTestFixtures.operation(1).withReason(reason);
            assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(EscrowTestFixtures.ledger(manual), manual.id(),
                    EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observations));
        }
    }

    @Test void newActorNamesUseTheLoginBoundButStored128ByteNamesStillDecode() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var root = EscrowTestFixtures.ledger(op); var observed = observations(root, false);
        for (String name : List.of("a".repeat(17), "\0", " ", "non ascii", "\u00e9", "", "\u007f")) {
            assertEquals(EscrowSettlementIntent.ACTOR_NAME_REFUSAL, assertThrows(IllegalArgumentException.class,
                    () -> EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                            ACTOR, name, 10, observed)).getMessage());
            // The explicit floor entry point must reject before inspecting any floor or allocating a decision.
            assertEquals(EscrowSettlementIntent.ACTOR_NAME_REFUSAL, assertThrows(IllegalArgumentException.class,
                    () -> EscrowSettlementIntent.floorPreview(root, op.id(), new CompoundTag(), ACTOR, name, 10, observed)).getMessage());
        }
        for (String name : List.of("!" + "~".repeat(15), "Server")) {
            var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, name, 10, observed);
            assertEquals(name, preview.actorName());
        }
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "Server", 10, observed);
        for (String legacyName : List.of("a".repeat(128), "\0".repeat(128), "\u00e9".repeat(64))) {
            var encoded = preview.encode(); encoded.putString("actor_name", legacyName);
            var base = encoded.copy(); base.remove("confirmed_tick"); base.remove("digest");
            encoded.putString("digest", io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(base));
            var legacy = EscrowSettlementIntent.decode(encoded);
            assertEquals(legacyName, legacy.actorName()); assertEquals(encoded, legacy.encode());
            var confirmed = legacy.confirm(ACTOR, legacy.digest(), 11, observed);
            var o1 = confirmed.freeze(root); var o3 = EscrowSettlementTombstones.install(o1, confirmed);
            var receipt = o3.tombstones().get(0);
            assertEquals(legacyName, EscrowReceiptCodec.decode(EscrowReceiptCodec.encode(receipt)).getString("actor_name"));
            assertEquals(o3, EscrowCodec.decode(EscrowCodec.encode(o3)));
            var oversized = encoded.copy(); oversized.putString("actor_name", legacyName + "a");
            var oversizedBase = oversized.copy(); oversizedBase.remove("confirmed_tick"); oversizedBase.remove("digest");
            oversized.putString("digest", io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources.signature(oversizedBase));
            assertEquals("Invalid audit actor name", assertThrows(IllegalArgumentException.class,
                    () -> EscrowSettlementIntent.decode(oversized)).getMessage());
        }
        assertTrue(op.settlement().isEmpty());
    }

    @Test void allEightConnectedLinksAreTraversedAndTheNinthRefusesBeforeMutation() {
        var ops = new ArrayList<EscrowOperation>();
        for (int i = 0; i < 8; i++) { ops.add(EscrowTestFixtures.operation(i + 1, 99, i).withReason(EscrowReason.OTHER_STATE)); }
        var root = new EscrowLedger(EscrowTestFixtures.id(500), 8, 0, ops, List.of(), List.of());
        assertEquals(8, EscrowSettlementIntent.component(root.records(), ops.get(0).id()).size());
        var next = new ArrayList<>(ops); next.add(EscrowTestFixtures.operation(9, 99, 8));
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedger(root.id(), 9, 0, next, List.of(), List.of()));
        assertTrue(root.records().stream().allMatch(op -> op.settlement().isEmpty()));
    }

    @Test void retirementReservationAcceptsThe64thSlotAndRefusesThe65thIntact() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE);
        var tombstones = new ArrayList<CompoundTag>();
        for (int i = 0; i < 64; i++) {
            tombstones.add(EscrowTestFixtures.receipt(i));
        }
        var atLimit = new EscrowLedger(EscrowTestFixtures.id(500), 1, 0, List.of(op), List.of(), tombstones.subList(0, 63));
        var observed = observations(atLimit, false);
        var preview = EscrowSettlementIntent.preview(atLimit, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "operator", 10, observed);
        var intent = preview.confirm(ACTOR, preview.digest(), 11, observed);
        assertDoesNotThrow(() -> intent.freeze(atLimit));
        var full = new EscrowLedger(atLimit.id(), 1, 0, List.of(op), List.of(), tombstones);
        assertThrows(IllegalArgumentException.class, () -> intent.freeze(full));
        assertTrue(full.records().get(0).settlement().isEmpty()); assertEquals(64, full.tombstones().size());
    }

    @Test void previewCacheAccepts64ActorsReplacementAnd1200TicksButNotOverflowOrExpiration() {
        var op = EscrowTestFixtures.operation(1).withReason(EscrowReason.OTHER_STATE); var root = EscrowTestFixtures.ledger(op);
        var observed = observations(root, false); var cache = new EscrowSettlementOperator.PreviewCache();
        for (int i = 0; i < 64; i++) {
            cache.put(EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                    EscrowTestFixtures.id(20_000 + i), "operator", 10, observed), 10);
        }
        assertEquals(64, cache.size());
        var replacement = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(20_000), "operator", 10, observed);
        assertDoesNotThrow(() -> cache.put(replacement, 10)); assertEquals(64, cache.size());
        var overflow = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT,
                EscrowTestFixtures.id(30_000), "operator", 10, observed);
        assertThrows(IllegalArgumentException.class, () -> cache.put(overflow, 10));
        assertEquals(replacement, cache.get(replacement.actor(), 1_210)); assertNull(cache.get(replacement.actor(), 1_211));
        cache.clear(); assertEquals(0, cache.size());
    }

    static List<EscrowSettlementIntent.Observation> observations(EscrowLedger root, boolean missing) {
        var keys = new java.util.LinkedHashMap<EscrowEndpoint.Key, EscrowEndpoint>();
        // Tests observe only the selected rocket component, excluding the unrelated operation in the first test.
        var component = EscrowSettlementIntent.component(root.records(), root.records().get(0).id());
        component.forEach(op -> { keys.putIfAbsent(op.source().key(), op.source()); keys.putIfAbsent(op.destination().key(), op.destination()); });
        return keys.values().stream().map(endpoint -> new EscrowSettlementIntent.Observation(endpoint,
                missing && endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY ? null
                        : new EscrowPrefix.State(root.id(), endpoint.beforeEpoch(), endpoint.beforeSignature(), List.of()),
                !(missing && endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY),
                endpoint.kind() == EscrowEndpoint.Kind.BLOCK_ENTITY && !missing ? 5_000 : 0)).toList();
    }
}
