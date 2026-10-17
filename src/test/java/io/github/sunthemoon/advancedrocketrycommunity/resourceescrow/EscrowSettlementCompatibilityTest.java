package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowSettlementCompatibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oldDuplicateCandidateO1AndO3RootsDecodeButCannotReleaseOrMakeANewDecision() {
        var root = duplicateCandidates(); var intent = historicalIntent(root);
        var frozen = root.replace(root.records().stream().map(op -> op.settle(intent)).toList(), List.of(), List.of());
        assertEquals(frozen, EscrowCodec.decode(EscrowCodec.encode(frozen)));
        var installed = EscrowSettlementTombstones.install(frozen, intent);
        assertEquals(installed, EscrowCodec.decode(EscrowCodec.encode(installed)));
        var refusal = assertThrows(IllegalArgumentException.class, () -> EscrowSettlementTombstones.release(installed, intent));
        assertEquals("Settlement has multiple physical candidates for one logical rocket", refusal.getMessage());
        assertThrows(IllegalArgumentException.class, () -> EscrowSettlementIntent.preview(root, root.records().get(0).id(),
                intent.choice(), intent.actor(), intent.actorName(), 10, intent.observations()));
        assertThrows(IllegalArgumentException.class, () -> intent.confirm(intent.actor(), intent.digest(), 11, intent.observations()));
        assertTrue(installed.rocketAuthorities().isEmpty());
        assertTrue(installed.tombstones().stream().noneMatch(tag -> tag.getBoolean("released")));
    }

    static EscrowLedger duplicateCandidates() {
        var first = EscrowSettlementCapacityTest.operation(1, 99, 0, true).withReason(EscrowReason.OTHER_STATE);
        var model = EscrowTestFixtures.operation(2); var old = first.destination();
        var second = new EscrowEndpoint(old.kind(), old.level(), EscrowTestFixtures.id(999), old.logicalRocket(), old.type(),
                5, 0, 4, old.unit(), old.beforeEpoch(), old.beforeSignature(), old.afterSignature());
        var other = EscrowTestFixtures.nativeModel(new EscrowOperation(model.id(), 2, first.owner(), 1, first.phase(), first.reason(),
                model.payload(), model.payloadHash(), model.source(), second, first.guard()));
        return EscrowTestFixtures.ledger(first, other);
    }

    /** Builds the previously accepted wire form, without asking today's preview to authorize it. */
    static EscrowSettlementIntent historicalIntent(EscrowLedger root) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 2); tag.putString("choice", "ACCEPT_CURRENT");
        tag.putUUID("ledger_id", root.id()); tag.putUUID("actor", EscrowTestFixtures.id(700));
        tag.putString("actor_name", "operator"); tag.putLong("preview_tick", 10);
        var members = new ListTag();
        for (var op : root.records()) {
            var member = EscrowSettlementIntent.Member.of(op); var entry = new CompoundTag();
            entry.putUUID("op_id", member.id()); entry.putString("phase", member.phase().name());
            entry.putString("reason", member.reason().name()); entry.putString("digest", member.digest()); members.add(entry);
        }
        tag.put("members", members); var observations = new ListTag();
        EscrowSettlementIntentTest.observations(root, false).stream()
                .sorted(java.util.Comparator.comparing(value -> RocketNativeResources.signature(EscrowCodec.endpoint(value.endpoint()))))
                .map(EscrowSettlementIntent::encodeObservation).forEach(observations::add);
        tag.put("observations", observations);
        String digest = RocketNativeResources.signature(tag);
        tag.putLong("confirmed_tick", 11); tag.putString("digest", digest);
        return EscrowSettlementIntent.decode(tag);
    }
}
