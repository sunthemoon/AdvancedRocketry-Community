package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-054 section 11 destination state in an endpoint's root: claimed payloads wait in a non-extractable
 * <em>incoming</em> area with a receipt each (at most 64), and move into the extractable receive buffer only after a
 * chunk-save or chunk-load tag showed them at least 40 ticks earlier (the incoming gate, finding F02). Items whose
 * record names another endpoint after an operator file restore are frozen ({@code REDIRECT_CONFLICT},
 * {@code REDIRECT_DOUBLE_PAY}) until an operator resolve. Reconciliation runs the destination rows of
 * {@link TransitRules}; observation ticks are runtime state.
 */
public final class TransitDestinationState {
    public static final int MAX_RECEIPTS = 64;

    private final TreeMap<TransitKey, TransitPayload> incoming = new TreeMap<>(TransitKey.ORDER);
    private final TreeSet<TransitKey> receipts = new TreeSet<>(TransitKey.ORDER);
    private final TreeSet<TransitKey> conflicts = new TreeSet<>(TransitKey.ORDER);
    private final Map<TransitKey, Long> incomingObservedAt = new HashMap<>();
    private final Map<TransitKey, Long> receiptObservedAt = new HashMap<>();
    /** Runtime: the record a pass the budget cut short stopped at; the next pass starts there (review C12R-M1). */
    @Nullable
    private TransitKey resumeAt;

    /** The destination rows a record can take, in order; a record with none costs no reconciliation. */
    private enum Row {
        RECOVER, CLAIM, ACKNOWLEDGE, REMATERIALIZE
    }

    /** The device's receive buffer: room for whole payloads, and the insertion of a moved one. */
    public interface ReceiveBuffer {
        /** Whether these payloads all fit at once, beyond the buffer's current contents. */
        boolean fits(List<List<ItemStack>> payloads);

        /** Inserts a payload that {@link #fits} accepted; nothing may be lost. */
        void insert(List<ItemStack> stacks);
    }

    /** A copy in key order, so a resolve or a removal settles payloads in the same order on every run. */
    public SortedMap<TransitKey, TransitPayload> incoming() {
        return Collections.unmodifiableSortedMap(new TreeMap<>(incoming));
    }

    public SortedSet<TransitKey> receipts() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(receipts));
    }

    public SortedSet<TransitKey> conflicts() {
        return Collections.unmodifiableSortedSet(new TreeSet<>(conflicts));
    }

    /** A fresh endpoint root: nothing incoming, no receipts, no conflicts. */
    public void clear() {
        incoming.clear();
        receipts.clear();
        conflicts.clear();
        incomingObservedAt.clear();
        receiptObservedAt.clear();
        resumeAt = null;
    }

    /** Incoming payloads, receipts or frozen conflicts: the endpoint is busy and refuses a non-operator break. */
    public boolean holdsContents() {
        return !incoming.isEmpty() || !receipts.isEmpty() || !conflicts.isEmpty();
    }

    public boolean anythingUnpersisted() {
        for (TransitKey key : incoming.keySet()) {
            if (!incomingObservedAt.containsKey(key)) {
                return true;
            }
        }
        for (TransitKey key : receipts) {
            if (!receiptObservedAt.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    /** A chunk-save or chunk-load tag showed these incoming payloads and receipts of this endpoint at {@code now}. */
    public void observed(Set<TransitKey> shownIncoming, Set<TransitKey> shownReceipts, long now) {
        for (TransitKey key : shownIncoming) {
            if (incoming.containsKey(key)) {
                incomingObservedAt.putIfAbsent(key, now);
            }
        }
        for (TransitKey key : shownReceipts) {
            if (receipts.contains(key)) {
                receiptObservedAt.putIfAbsent(key, now);
            }
        }
    }

    private static boolean aged(Map<TransitKey, Long> observedAt, TransitKey key, long now) {
        Long observed = observedAt.get(key);
        return observed != null && now - observed >= EndgameLimits.PERSISTENCE_AGE_TICKS;
    }

    /**
     * The destination rows for a live, registered endpoint, within the shared reconciliation budget: claims and their
     * recovery, acknowledgement and rematerialization of the records for this endpoint; then the incoming gate; then
     * receipts. Only a record a row applies to costs a reconciliation; when the budget runs out the rows stop at that
     * record and the next pass starts there, while the gate and the receipts (at most 64 each) still run. Returns
     * whether anything changed.
     */
    public boolean reconcile(TransitLedgerView ledger, UUID self, @Nullable UUID owner, ReceiveBuffer buffer,
                             long now) {
        boolean changed = false;
        long epoch = ledger.saveEpoch();
        boolean registrationDurable = ledger.registrationDurable(self);
        TransitKey start = resumeAt;
        resumeAt = null;
        for (TransitRecord record : fromResume(ledger.forEndpoint(self), start)) {
            TransitKey key = record.key();
            if (conflicts.contains(key) || record.state() == TransitRecord.State.QUARANTINED) {
                continue;
            }
            TransitRules.RecordFacts facts = record.facts(self, epoch);
            boolean receiptHere = receipts.contains(key);
            Row row = row(facts, key, receiptHere, registrationDurable, now);
            if (row == null) {
                continue;
            }
            if (!ledger.takeReconciliation()) {
                resumeAt = key;
                break;
            }
            switch (row) {
                case RECOVER -> {
                    ledger.recover(key, self);
                    ledger.audit("CLAIM_RECOVERED", "OK", self, owner, "transfer=" + key);
                    changed = true;
                }
                case CLAIM -> changed |= land(ledger, self, owner, record, buffer, false);
                case ACKNOWLEDGE -> {
                    ledger.acknowledge(key);
                    changed = true;
                }
                case REMATERIALIZE -> changed |= land(ledger, self, owner, record, buffer, true);
                default -> throw new IllegalStateException("Unknown row " + row);
            }
        }
        for (Map.Entry<TransitKey, TransitPayload> entry : new ArrayList<>(incoming.entrySet())) {
            TransitKey key = entry.getKey();
            if (conflicts.contains(key)) {
                continue; // Frozen once, until a resolve (review C13R2-N3: it was frozen and audited every pass).
            }
            Optional<TransitRecord> record = ledger.record(key);
            TransitRules.RecordFacts facts = record.map(found -> found.facts(self, epoch)).orElse(null);
            if (facts != null && !facts.paidHere() && !facts.destinedHere()) {
                // Only an operator's restore of an older file can leave a payload here whose record names another
                // endpoint (a redirect needs this endpoint's retirement).
                freeze(ledger, self, owner, key, "REDIRECT_CONFLICT");
                changed = true;
            } else if (TransitRules.moves(aged(incomingObservedAt, key, now), facts)) {
                Optional<List<ItemStack>> stacks = entry.getValue().decode();
                if (stacks.isEmpty()) {
                    freeze(ledger, self, owner, key, "INCOMING_QUARANTINED");
                } else {
                    buffer.insert(stacks.get());
                    incoming.remove(key);
                    incomingObservedAt.remove(key);
                    if (facts != null && facts.claimedHere()) {
                        ledger.acknowledge(key);
                    }
                }
                changed = true;
            }
        }
        for (TransitKey key : new ArrayList<>(receipts)) {
            if (conflicts.contains(key)) {
                continue;
            }
            Optional<TransitRecord> record = ledger.record(key);
            TransitRules.RecordFacts facts = record.map(found -> found.facts(self, epoch)).orElse(null);
            if (record.isPresent() && record.get().state() == TransitRecord.State.QUARANTINED) {
                continue;
            }
            if (facts != null && !facts.destinedHere() && !facts.paidHere() && !facts.acknowledged()) {
                // The ledger names another endpoint for a claim this endpoint made.
                freeze(ledger, self, owner, key, facts.state() == TransitRecord.State.CLAIMED
                        ? "REDIRECT_DOUBLE_PAY" : "REDIRECT_CONFLICT");
                changed = true;
            } else if (TransitRules.dropsReceipt(facts)) {
                receipts.remove(key);
                receiptObservedAt.remove(key);
                changed = true;
            }
        }
        return changed;
    }

    @Nullable
    private Row row(TransitRules.RecordFacts facts, TransitKey key, boolean receiptHere, boolean registrationDurable,
                    long now) {
        if (TransitRules.recovers(facts, receiptHere)) {
            return Row.RECOVER;
        }
        if (TransitRules.claims(facts, receiptHere, registrationDurable)) {
            return Row.CLAIM;
        }
        if (TransitRules.acknowledges(facts, aged(receiptObservedAt, key, now), incoming.containsKey(key))) {
            return Row.ACKNOWLEDGE;
        }
        return TransitRules.rematerializes(facts, receiptHere) ? Row.REMATERIALIZE : null;
    }

    /** The records in key order, starting at {@code start} (when given) and wrapping around to the ones before it. */
    private static List<TransitRecord> fromResume(List<TransitRecord> records, @Nullable TransitKey start) {
        if (start == null) {
            return records;
        }
        List<TransitRecord> ordered = new ArrayList<>(records.size());
        for (TransitRecord record : records) {
            if (TransitKey.ORDER.compare(record.key(), start) >= 0) {
                ordered.add(record);
            }
        }
        for (TransitRecord record : records) {
            if (TransitKey.ORDER.compare(record.key(), start) < 0) {
                ordered.add(record);
            }
        }
        return ordered;
    }

    /** A claim or a rematerialization: the payload lands in incoming with a new receipt, when it all fits. */
    private boolean land(TransitLedgerView ledger, UUID self, @Nullable UUID owner, TransitRecord record,
                         ReceiveBuffer buffer, boolean rematerialize) {
        TransitPayload payload = record.payload();
        Optional<List<ItemStack>> stacks = payload == null ? Optional.empty() : payload.decode();
        if (stacks.isEmpty()) {
            ledger.quarantine(record.key());
            ledger.audit("TRANSIT_QUARANTINED", "OK", self, owner, "transfer=" + record.key());
            return true;
        }
        if (receipts.size() >= MAX_RECEIPTS || !buffer.fits(reserved(stacks.get()))) {
            return false;
        }
        if (!rematerialize) {
            ledger.claim(record.key(), self);
        } else {
            ledger.audit("REMATERIALIZED", "OK", self, owner, "transfer=" + record.key());
        }
        incoming.put(record.key(), payload);
        incomingObservedAt.remove(record.key());
        receipts.add(record.key());
        receiptObservedAt.remove(record.key());
        return true;
    }

    /** Every incoming payload already reserves room, plus the new one (whole payloads only). */
    private List<List<ItemStack>> reserved(List<ItemStack> added) {
        List<List<ItemStack>> payloads = new ArrayList<>();
        for (TransitPayload payload : incoming.values()) {
            payload.decode().ifPresent(payloads::add);
        }
        payloads.add(added);
        return payloads;
    }

    private void freeze(TransitLedgerView ledger, UUID self, @Nullable UUID owner, TransitKey key, String code) {
        conflicts.add(key);
        ledger.audit(code, code, self, owner, "transfer=" + key);
    }

    // ---- Removal and resolve (sections 9 and 9.1) ------------------------------------------------------------

    public Optional<TransitPayload> takeIncoming(TransitKey key) {
        incomingObservedAt.remove(key);
        return Optional.ofNullable(incoming.remove(key));
    }

    public boolean dropReceipt(TransitKey key) {
        receiptObservedAt.remove(key);
        return receipts.remove(key);
    }

    public boolean clearConflict(TransitKey key) {
        return conflicts.remove(key);
    }

    public void write(CompoundTag tag) {
        ListTag in = new ListTag();
        incoming.forEach((key, payload) -> in.add(TransitCodec.encodeIncoming(key, payload)));
        tag.put("incoming", in);
        tag.put("receipts", keys(receipts));
        tag.put("conflicts", keys(conflicts));
    }

    private static ListTag keys(Set<TransitKey> keys) {
        ListTag list = new ListTag();
        keys.forEach(key -> list.add(TransitCodec.encodeKey(key)));
        return list;
    }

    /** Strict: a malformed section quarantines the device root. */
    public void read(CompoundTag tag) {
        incoming.clear();
        receipts.clear();
        conflicts.clear();
        incomingObservedAt.clear();
        receiptObservedAt.clear();
        for (Tag raw : EndgameNbt.requireList(tag, "incoming", Tag.TAG_COMPOUND, MAX_RECEIPTS)) {
            TransitCodec.IncomingPayload payload = TransitCodec.decodeIncoming((CompoundTag) raw);
            if (incoming.putIfAbsent(payload.key(), payload.payload()) != null) {
                throw new IllegalArgumentException("An incoming payload appears twice");
            }
        }
        readKeys(tag, "receipts", receipts);
        readKeys(tag, "conflicts", conflicts);
    }

    private static void readKeys(CompoundTag tag, String name, Set<TransitKey> into) {
        for (Tag raw : EndgameNbt.requireList(tag, name, Tag.TAG_COMPOUND, MAX_RECEIPTS)) {
            if (!into.add(TransitCodec.decodeKey((CompoundTag) raw))) {
                throw new IllegalArgumentException("A transfer key appears twice in " + name);
            }
        }
    }
}
