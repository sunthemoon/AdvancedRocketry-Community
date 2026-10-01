package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-054 section 11 at one endpoint: the source rows, the destination rows and the incoming gate, and the codec. */
final class TransitEndpointStateTest {
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID SOURCE = new UUID(0L, 10L);
    private static final UUID HERE = new UUID(0L, 20L);
    private static final UUID ELSEWHERE = new UUID(0L, 30L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    /** An in-memory ledger: a flush makes everything so far durable. */
    private static final class FakeLedger implements TransitLedgerView {
        final Map<TransitKey, TransitRecord> records = new TreeMap<>(TransitKey.ORDER);
        final Map<UUID, Long> dispatched = new HashMap<>();
        final List<String> audit = new ArrayList<>();
        long epoch = 1L;
        boolean registrationDurable = true;
        int budget = 64;
        int registrations = 32;

        void flush() {
            epoch++;
        }

        void put(TransitRecord record) {
            records.put(record.key(), record);
            dispatched.merge(record.key().source(), record.key().seq(), Math::max);
        }

        @Override
        public long saveEpoch() {
            return epoch;
        }

        @Override
        public long dispatchedThrough(UUID source) {
            return dispatched.getOrDefault(source, 0L);
        }

        @Override
        public Optional<TransitRecord> record(TransitKey key) {
            return Optional.ofNullable(records.get(key));
        }

        @Override
        public List<TransitRecord> forEndpoint(UUID endpoint) {
            List<TransitRecord> found = new ArrayList<>();
            records.values().forEach(record -> {
                if (record.destination().equals(endpoint) || endpoint.equals(record.paidEndpoint())) {
                    found.add(record);
                }
            });
            return found;
        }

        @Override
        public boolean registrationDurable(UUID endpoint) {
            return registrationDurable;
        }

        @Override
        public EndgameCode register(UUID source, UUID owner, OutboxEntry entry, long now) {
            if (registrations-- <= 0) {
                return EndgameCode.RATE_LIMITED;
            }
            put(TransitRecord.registered(source, entry, owner, epoch, now));
            return EndgameCode.OK;
        }

        @Override
        public boolean takeReconciliation() {
            return budget-- > 0;
        }

        @Override
        public void claim(TransitKey key, UUID endpoint) {
            records.put(key, records.get(key).claimed(endpoint));
        }

        @Override
        public void recover(TransitKey key, UUID endpoint) {
            records.put(key, records.get(key).claimed(endpoint));
        }

        @Override
        public void acknowledge(TransitKey key) {
            records.put(key, records.get(key).acknowledged(epoch));
        }

        @Override
        public void quarantine(TransitKey key) {
            records.put(key, records.get(key).quarantined());
        }

        @Override
        public void audit(String action, String result, UUID endpoint, UUID owner, String fields) {
            audit.add(action);
        }
    }

    /** A receive buffer of a fixed number of stacks. */
    private static final class Buffer implements TransitDestinationState.ReceiveBuffer {
        final List<ItemStack> stacks = new ArrayList<>();
        final int capacity;

        Buffer(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public boolean fits(List<List<ItemStack>> payloads) {
            return stacks.size() + payloads.stream().mapToInt(List::size).sum() <= capacity;
        }

        @Override
        public void insert(List<ItemStack> added) {
            added.forEach(stack -> stacks.add(stack.copy()));
        }
    }

    private static TransitPayload payload() {
        return TransitPayload.of(List.of(new ItemStack(Items.DIAMOND, 7))).orElseThrow();
    }

    private static OutboxEntry entry(long seq, UUID destination) {
        return new OutboxEntry(seq, destination, payload(), 25_000, 20, EndgameSystem.RAILGUN);
    }

    @Test
    void anEntryRegistersOnlyOnceAnAgedSaveShowedItAndIsReleasedOnceItsRecordIsDurable() {
        FakeLedger ledger = new FakeLedger();
        TransitSourceState source = new TransitSourceState();
        OutboxEntry entry = source.escrow(HERE, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        assertEquals(1L, entry.seq());
        assertEquals(2L, source.nextSeq());
        assertTrue(source.anythingUnpersisted());
        source.reconcile(ledger, SOURCE, OWNER, 100L);
        assertTrue(ledger.records.isEmpty(), "registered before a save showed the entry");
        source.observed(Set.of(1L), 100L);
        assertFalse(source.anythingUnpersisted());
        source.reconcile(ledger, SOURCE, OWNER, 139L);
        assertTrue(ledger.records.isEmpty(), "registered before the observation was 40 ticks old");
        source.reconcile(ledger, SOURCE, OWNER, 140L);
        TransitRecord record = ledger.records.get(new TransitKey(SOURCE, 1L));
        assertEquals(TransitRecord.State.IN_TRANSIT, record.state());
        assertEquals(160L, record.arriveAt(), "the travel time starts at registration");
        source.reconcile(ledger, SOURCE, OWNER, 141L);
        assertEquals(1, source.outbox().size(), "released before the record was durable");
        ledger.flush();
        source.reconcile(ledger, SOURCE, OWNER, 142L);
        assertTrue(source.outbox().isEmpty(), "not released once durable");
    }

    @Test
    void sourceRowsDropStaleEntriesFixARollbackAndRegisterInOrder() {
        FakeLedger ledger = new FakeLedger();
        TransitSourceState source = new TransitSourceState();
        source.escrow(HERE, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        source.escrow(HERE, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        source.observed(Set.of(1L, 2L), 0L);
        source.reconcile(ledger, SOURCE, OWNER, 40L);
        assertEquals(Set.of(new TransitKey(SOURCE, 1L)), ledger.records.keySet(),
                "the second entry registered while the first waits for its durable record");
        ledger.flush();
        source.reconcile(ledger, SOURCE, OWNER, 41L);
        source.reconcile(ledger, SOURCE, OWNER, 42L);
        assertEquals(2, ledger.records.size());
        // The record of seq 2 is delivered and pruned while the source's release was lost: a stale drop.
        TransitSourceState restored = new TransitSourceState();
        CompoundTag saved = new CompoundTag();
        source.write(saved);
        restored.read(saved);
        ledger.records.remove(new TransitKey(SOURCE, 2L));
        restored.reconcile(ledger, SOURCE, OWNER, 43L);
        assertTrue(restored.outbox().stream().noneMatch(entry -> entry.seq() == 2L));
        assertTrue(ledger.audit.contains("OUTBOX_STALE_DROPPED"));
        // A chunk older than the ledger: next_seq moves past dispatched_through and escrow waits a tick.
        TransitSourceState old = new TransitSourceState();
        TransitSourceState.Pass pass = old.reconcile(ledger, SOURCE, OWNER, 44L);
        assertTrue(pass.escrowBlocked() && old.nextSeq() == 3L && ledger.audit.contains("SOURCE_ROLLBACK"));
    }

    @Test
    void aQuarantinedOutboxPayloadIsNeverRegistered() {
        FakeLedger ledger = new FakeLedger();
        TransitSourceState source = new TransitSourceState();
        ListTag raw = new ListTag();
        CompoundTag widget = new CompoundTag();
        widget.putString("id", "removedmod:widget");
        widget.putByte("Count", (byte) 1);
        raw.add(widget);
        source.escrow(HERE, TransitPayload.raw(raw), 25_000, 20, EndgameSystem.RAILGUN);
        source.observed(Set.of(1L), 0L);
        source.reconcile(ledger, SOURCE, OWNER, 100L);
        assertTrue(ledger.records.isEmpty());
        assertEquals(1, source.quarantined().size());
    }

    @Test
    void aClaimWaitsInIncomingUntilAnAgedSaveShowsItThenMovesAndAcknowledges() {
        FakeLedger ledger = new FakeLedger();
        TransitRecord arrived = TransitRecord.registered(SOURCE, entry(1L, HERE), OWNER, 1L, 0L).arrived();
        ledger.put(arrived);
        TransitDestinationState destination = new TransitDestinationState();
        Buffer buffer = new Buffer(9);
        destination.reconcile(ledger, HERE, OWNER, buffer, 10L);
        assertTrue(destination.incoming().isEmpty(), "claimed a record that is not durable");
        ledger.flush();
        ledger.registrationDurable = false;
        destination.reconcile(ledger, HERE, OWNER, buffer, 11L);
        assertTrue(destination.incoming().isEmpty(), "claimed before its own registration was durable");
        ledger.registrationDurable = true;
        destination.reconcile(ledger, HERE, OWNER, buffer, 12L);
        TransitKey key = arrived.key();
        assertEquals(TransitRecord.State.CLAIMED, ledger.records.get(key).state());
        assertTrue(destination.incoming().containsKey(key) && destination.receipts().contains(key));
        assertTrue(buffer.stacks.isEmpty(), "the claim is not extractable yet");
        destination.observed(Set.of(key), Set.of(key), 20L);
        destination.reconcile(ledger, HERE, OWNER, buffer, 59L);
        assertTrue(buffer.stacks.isEmpty(), "moved before the observation was 40 ticks old");
        destination.reconcile(ledger, HERE, OWNER, buffer, 60L);
        assertEquals(1, buffer.stacks.size());
        assertTrue(ledger.records.get(key).acknowledged(), "the move and the acknowledgement are one step");
        destination.reconcile(ledger, HERE, OWNER, buffer, 61L);
        assertTrue(destination.receipts().contains(key), "the receipt went before the acknowledgement was durable");
        ledger.flush();
        destination.reconcile(ledger, HERE, OWNER, buffer, 62L);
        assertFalse(destination.holdsContents());
    }

    @Test
    void destinationRecoveryRowsMatchTheReferenceModel() {
        FakeLedger ledger = new FakeLedger();
        TransitRecord arrived = TransitRecord.registered(SOURCE, entry(1L, HERE), OWNER, 1L, 0L).arrived();
        TransitKey key = arrived.key();
        ledger.put(arrived);
        ledger.flush();
        TransitDestinationState destination = new TransitDestinationState();
        Buffer buffer = new Buffer(9);
        destination.reconcile(ledger, HERE, OWNER, buffer, 0L);
        CompoundTag saved = new CompoundTag();
        destination.write(saved);
        // Claim: D saved, ledger not (the record is ARRIVED again, D holds the receipt): CLAIM_RECOVERED.
        ledger.put(arrived);
        TransitDestinationState restored = new TransitDestinationState();
        restored.read(saved);
        restored.reconcile(ledger, HERE, OWNER, buffer, 1L);
        assertEquals(TransitRecord.State.CLAIMED, ledger.records.get(key).state());
        assertTrue(ledger.audit.contains("CLAIM_RECOVERED"));
        // Claim: ledger flushed, D not (D has no receipt): rematerialized once into incoming.
        TransitDestinationState lost = new TransitDestinationState();
        lost.reconcile(ledger, HERE, OWNER, buffer, 2L);
        assertTrue(lost.incoming().containsKey(key) && ledger.audit.contains("REMATERIALIZED"));
        // A persisted, aged receipt with nothing incoming acknowledges (D moved; the acknowledgement was lost).
        TransitDestinationState moved = new TransitDestinationState();
        CompoundTag receiptOnly = new CompoundTag();
        receiptOnly.put("incoming", new ListTag());
        ListTag receipts = new ListTag();
        receipts.add(TransitCodec.encodeKey(key));
        receiptOnly.put("receipts", receipts);
        receiptOnly.put("conflicts", new ListTag());
        moved.read(receiptOnly);
        moved.observed(Set.of(), Set.of(key), 10L);
        moved.reconcile(ledger, HERE, OWNER, buffer, 50L);
        assertTrue(ledger.records.get(key).acknowledged());
        // Pruned while D's chunk still showed the payload as incoming: D's own content moves again.
        ledger.records.remove(key);
        restored.observed(Set.of(key), Set.of(key), 60L);
        restored.reconcile(ledger, HERE, OWNER, buffer, 100L);
        assertEquals(1, buffer.stacks.size());
        assertFalse(restored.holdsContents(), "a pruned record's payload or receipt stayed");
    }

    @Test
    void conflictsAfterAnOperatorRestoreAreFrozenAndRoomIsReservedForWholePayloads() {
        FakeLedger ledger = new FakeLedger();
        TransitRecord elsewhere = TransitRecord.registered(SOURCE, entry(1L, ELSEWHERE), OWNER, 1L, 0L).arrived()
                .claimed(ELSEWHERE);
        ledger.put(elsewhere);
        ledger.flush();
        TransitDestinationState destination = new TransitDestinationState();
        CompoundTag state = new CompoundTag();
        state.put("incoming", new ListTag());
        ListTag receipts = new ListTag();
        receipts.add(TransitCodec.encodeKey(elsewhere.key()));
        state.put("receipts", receipts);
        state.put("conflicts", new ListTag());
        destination.read(state);
        destination.reconcile(ledger, HERE, OWNER, new Buffer(9), 0L);
        assertEquals(Set.of(elsewhere.key()), destination.conflicts());
        assertTrue(ledger.audit.contains("REDIRECT_DOUBLE_PAY") && destination.holdsContents());

        FakeLedger room = new FakeLedger();
        room.put(TransitRecord.registered(SOURCE, entry(1L, HERE), OWNER, 1L, 0L).arrived());
        room.put(TransitRecord.registered(SOURCE, entry(2L, HERE), OWNER, 1L, 0L).arrived());
        room.flush();
        TransitDestinationState full = new TransitDestinationState();
        full.reconcile(room, HERE, OWNER, new Buffer(1), 0L);
        assertEquals(1, full.incoming().size(), "the second payload does not fit beside the reserved first");
        room.budget = 0;
        TransitDestinationState starved = new TransitDestinationState();
        assertFalse(starved.reconcile(room, HERE, OWNER, new Buffer(9), 1L), "worked without budget");
    }

    @Test
    void theSectionRoundTripsAndTheTagScanShowsWhatIsHeld() {
        TransitSourceState source = new TransitSourceState();
        source.escrow(HERE, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        TransitDestinationState destination = new TransitDestinationState();
        FakeLedger ledger = new FakeLedger();
        ledger.put(TransitRecord.registered(SOURCE, entry(1L, HERE), OWNER, 1L, 0L).arrived());
        ledger.flush();
        destination.reconcile(ledger, HERE, OWNER, new Buffer(9), 0L);
        CompoundTag section = new CompoundTag();
        source.write(section);
        destination.write(section);
        CompoundTag root = new CompoundTag();
        root.put(TransitTags.SECTION, section);
        TransitTags.Shown shown = TransitTags.scan(root);
        TransitKey key = new TransitKey(SOURCE, 1L);
        assertEquals(new TransitTags.Shown(Set.of(1L), Set.of(key), Set.of(key)), shown);
        assertTrue(shown.holdsContents());
        assertFalse(TransitTags.scan(new CompoundTag()).holdsContents());
        TransitSourceState sourceCopy = new TransitSourceState();
        sourceCopy.read(section);
        assertEquals(source.outbox(), sourceCopy.outbox());
        TransitDestinationState destinationCopy = new TransitDestinationState();
        destinationCopy.read(section);
        assertEquals(destination.incoming(), destinationCopy.incoming());
        CompoundTag ahead = section.copy();
        ahead.putLong("next_seq", 1L);
        assertThrows(IllegalArgumentException.class, () -> new TransitSourceState().read(ahead),
                "an entry at or above next_seq");
    }
}
