package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One immutable controller-owned, ordered resource snapshot; no world or lifecycle state. */
public final class ClassicResources {
    public static final int MAX_BANKS = 64;
    private final UUID machineId;
    private final long revision;
    private final List<ClassicResourceBank> banks;

    private ClassicResources(UUID machineId, long revision, List<ClassicResourceBank> banks) {
        this.machineId = Objects.requireNonNull(machineId, "machineId");
        if (revision < 0 || banks.size() > MAX_BANKS) {
            throw new IllegalArgumentException("Invalid revision or retained bank count");
        }
        var seen = new HashSet<ClassicBankKey>();
        for (ClassicResourceBank bank : banks) {
            if (!seen.add(Objects.requireNonNull(bank, "bank").key())) {
                throw new IllegalArgumentException("Duplicate retained bank key");
            }
        }
        this.revision = revision;
        this.banks = List.copyOf(banks);
        ClassicResourcesCodec.requireAggregate(this);
    }

    /** Explicit new construction, never used as a fallback for missing/unsupported persisted roots. */
    public static ClassicResources empty(UUID machineId) { return new ClassicResources(machineId, 0, List.of()); }

    static ClassicResources restore(UUID machineId, long revision, List<ClassicResourceBank> banks) {
        return new ClassicResources(machineId, revision, banks);
    }

    public UUID machineId() { return machineId; }
    public long revision() { return revision; }
    public List<ClassicResourceBank> banks() { return banks; }

    public Optional<ClassicResourceBank> bank(ClassicBankKey key) {
        Objects.requireNonNull(key, "key");
        return banks.stream().filter(bank -> bank.key().equals(key)).findFirst();
    }

    /** Add/replace a bounded set without discarding any retained bank; increment once, not per slot. */
    public ClassicResources replace(List<ClassicResourceBank> changes, boolean simulate) {
        Objects.requireNonNull(changes, "changes");
        if (changes.size() > MAX_BANKS) { throw new IllegalArgumentException("Too many bank changes"); }
        var seen = new HashSet<ClassicBankKey>();
        List<ClassicResourceBank> replacement = new ArrayList<>(banks);
        boolean changed = false;
        for (ClassicResourceBank change : changes) {
            if (!seen.add(Objects.requireNonNull(change, "change").key())) {
                throw new IllegalArgumentException("Duplicate changed bank key");
            }
            int index = -1;
            for (int i = 0; i < replacement.size(); i++) {
                if (replacement.get(i).key().equals(change.key())) { index = i; break; }
            }
            if (index < 0) { replacement.add(change); changed = true; }
            else if (!replacement.get(index).samePayload(change)) { replacement.set(index, change); changed = true; }
        }
        if (!changed) { return this; }
        if (revision == Long.MAX_VALUE) { throw new IllegalStateException("Resource revision exhausted"); }
        // Aggregate framing/preflight completes even for simulation; the original never mutates.
        ClassicResources candidate = new ClassicResources(machineId, revision + 1, replacement);
        return simulate ? this : candidate;
    }
}
