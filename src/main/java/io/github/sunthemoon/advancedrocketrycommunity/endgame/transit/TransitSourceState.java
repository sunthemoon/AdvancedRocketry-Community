package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
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
import net.minecraft.nbt.Tag;

/**
 * ADR-054 section 11 source state in an endpoint's root: {@code next_seq} (from 1) and an outbox of at most four
 * entries. An entry counts as persisted only once a chunk-save or chunk-load tag of the endpoint showed it (section 2),
 * and registers once that observation is at least 40 ticks old. Reconciliation runs the source rows of
 * {@link TransitRules}; the observation ticks are runtime state, never saved.
 */
public final class TransitSourceState {
    public static final int MAX_OUTBOX = 4;

    private final TreeMap<Long, OutboxEntry> outbox = new TreeMap<>();
    private final Map<Long, Long> observedAt = new HashMap<>();
    private long nextSeq = 1L;

    /** What a reconciliation pass did. */
    public record Pass(boolean changed, boolean escrowBlocked) {
    }

    public long nextSeq() {
        return nextSeq;
    }

    public List<OutboxEntry> outbox() {
        return List.copyOf(outbox.values());
    }

    public boolean outboxFree() {
        return outbox.size() < MAX_OUTBOX;
    }

    public boolean holdsContents() {
        return !outbox.isEmpty();
    }

    /** Whether an entry waits for a chunk save to show it: the block entity keeps calling {@code setChanged()}. */
    public boolean anythingUnpersisted() {
        for (long seq : outbox.keySet()) {
            if (!observedAt.containsKey(seq)) {
                return true;
            }
        }
        return false;
    }

    /** Escrow (step 1): the entry takes {@code seq = next_seq++}; the caller has taken payload and energy. */
    public OutboxEntry escrow(UUID destination, TransitPayload payload, int paidFe, int travel, EndgameSystem system) {
        if (!outboxFree()) {
            throw new IllegalStateException("The outbox is full");
        }
        OutboxEntry entry = new OutboxEntry(nextSeq, destination, payload, paidFe, travel, system);
        nextSeq = Math.addExact(nextSeq, 1L);
        outbox.put(entry.seq(), entry);
        observedAt.remove(entry.seq());
        return entry;
    }

    /** A chunk-save or chunk-load tag showed these entries of this endpoint at {@code now}. */
    public void observed(Set<Long> seqs, long now) {
        for (long seq : seqs) {
            if (outbox.containsKey(seq)) {
                observedAt.putIfAbsent(seq, now);
            }
        }
    }

    public boolean persistedAndAged(long seq, long now) {
        Long observed = observedAt.get(seq);
        return observed != null && now - observed >= EndgameLimits.PERSISTENCE_AGE_TICKS;
    }

    /**
     * The source rows for a live, registered source: a rollback first ({@code SOURCE_ROLLBACK}, escrow refused this
     * tick), then each entry in seq order: drop a stale one ({@code OUTBOX_STALE_DROPPED}), register the lowest
     * persisted and aged one, release one whose record is durable. An entry whose payload no longer decodes is never
     * registered ({@code OUTBOX_QUARANTINED}).
     */
    public Pass reconcile(TransitLedgerView ledger, UUID self, UUID owner, long now) {
        long dispatched = ledger.dispatchedThrough(self);
        if (TransitRules.rollback(nextSeq, dispatched)) {
            ledger.audit("SOURCE_ROLLBACK", "OK", self, owner, "next_seq=" + nextSeq + " dispatched_through="
                    + dispatched);
            nextSeq = dispatched + 1L;
            return new Pass(true, true);
        }
        boolean changed = false;
        long lowest = outbox.isEmpty() ? -1L : outbox.firstKey();
        for (OutboxEntry entry : new ArrayList<>(outbox.values())) {
            Optional<TransitRecord> record = ledger.record(new TransitKey(self, entry.seq()));
            TransitRules.SourceStep step = TransitRules.sourceStep(entry.seq(), lowest,
                    persistedAndAged(entry.seq(), now), dispatched, record.isPresent(),
                    record.map(found -> found.durable(ledger.saveEpoch())).orElse(false));
            switch (step) {
                case STALE_DROP -> {
                    remove(entry.seq());
                    ledger.audit("OUTBOX_STALE_DROPPED", "OK", self, owner, "seq=" + entry.seq() + " payload="
                            + entry.payload().hash());
                    changed = true;
                }
                case REGISTER -> {
                    if (entry.payload().decode().isPresent()) {
                        EndgameCode code = ledger.register(self, owner, entry, now);
                        changed |= code == EndgameCode.OK;
                    }
                }
                case RELEASE -> {
                    remove(entry.seq());
                    changed = true;
                }
                default -> {
                }
            }
        }
        return new Pass(changed, false);
    }

    /** Entries whose payload no longer decodes: never registered, removed only by an operator purge. */
    public List<OutboxEntry> quarantined() {
        List<OutboxEntry> found = new ArrayList<>();
        for (OutboxEntry entry : outbox.values()) {
            if (entry.payload().decode().isEmpty()) {
                found.add(entry);
            }
        }
        return found;
    }

    public Optional<OutboxEntry> remove(long seq) {
        observedAt.remove(seq);
        return Optional.ofNullable(outbox.remove(seq));
    }

    /** Clears every entry (a resolve or a removal settles them first). */
    public List<OutboxEntry> clear() {
        List<OutboxEntry> removed = List.copyOf(outbox.values());
        outbox.clear();
        observedAt.clear();
        return removed;
    }

    public void write(CompoundTag tag) {
        tag.putLong("next_seq", nextSeq);
        ListTag list = new ListTag();
        outbox.values().forEach(entry -> list.add(TransitCodec.encodeEntry(entry)));
        tag.put("outbox", list);
    }

    /** Strict: a malformed section quarantines the device root. */
    public void read(CompoundTag tag) {
        outbox.clear();
        observedAt.clear();
        nextSeq = EndgameNbt.requireLong(tag, "next_seq");
        if (nextSeq < 1L) {
            throw new IllegalArgumentException("next_seq starts at 1");
        }
        for (Tag raw : EndgameNbt.requireList(tag, "outbox", Tag.TAG_COMPOUND, MAX_OUTBOX)) {
            OutboxEntry entry = TransitCodec.decodeEntry((CompoundTag) raw);
            if (entry.seq() >= nextSeq || outbox.putIfAbsent(entry.seq(), entry) != null) {
                throw new IllegalArgumentException("An outbox entry at or above next_seq, or twice");
            }
        }
    }
}
