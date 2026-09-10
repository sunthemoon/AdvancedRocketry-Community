package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Strict independent schema for an exactly-once Rolling Machine batch journal. */
final class RollingMachineJournalPersistence {
    static final String ROOT = "arce_process_journal";
    static final int MAX_ROOT_BYTES = 65_536;

    private static final Set<String> FIELDS = Set.of(
            "schema_version",
            "transaction_id",
            "machine_id",
            "definition_id",
            "port_revision",
            "before_fingerprint",
            "phase",
            "before",
            "after"
    );
    private static final Set<String> SNAPSHOT_FIELDS = Set.of("revision", "entries");
    private static final Set<String> ENTRY_FIELDS = Set.of(
            "kind",
            "channel",
            "resource_id",
            "amount",
            "capacity"
    );
    private static final Pattern FINGERPRINT = Pattern.compile("[0-9a-f]{64}");

    private RollingMachineJournalPersistence() {
    }

    static CompoundTag encode(ProcessTransactionJournal journal) {
        Objects.requireNonNull(journal, "journal");
        CompoundTag root = new CompoundTag();
        root.putInt("schema_version", ProcessTransactionJournal.SCHEMA_VERSION);
        root.putString("transaction_id", journal.transactionId().toString());
        root.putString("machine_id", journal.machineId().toString());
        root.putString("definition_id", journal.definitionId());
        root.putLong("port_revision", journal.portRevision());
        root.putString("before_fingerprint", journal.beforeFingerprint());
        root.putString("phase", journal.phase().name().toLowerCase(Locale.ROOT));
        root.put("before", encodeSnapshot(journal.before()));
        root.put("after", encodeSnapshot(journal.after()));
        if (root.sizeInBytes() > MAX_ROOT_BYTES) {
            throw new IllegalStateException("Rolling Machine journal exceeded its 64 KiB bound");
        }
        return root;
    }

    static RollingMachineNbtResult<ProcessTransactionJournal> decode(
            CompoundTag parent,
            UUID expectedMachineId
    ) {
        Objects.requireNonNull(parent, "parent");
        Objects.requireNonNull(expectedMachineId, "expectedMachineId");
        if (!parent.contains(ROOT)) {
            return RollingMachineNbtResult.empty();
        }
        Tag raw = parent.get(ROOT);
        if (!(raw instanceof CompoundTag root)) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, raw);
        }
        if (root.sizeInBytes() > MAX_ROOT_BYTES
                || !root.contains("schema_version", Tag.TAG_INT)) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
        if (root.getInt("schema_version") != ProcessTransactionJournal.SCHEMA_VERSION) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.UNSUPPORTED_SCHEMA, root);
        }
        try {
            requireRootFields(root);
            UUID transactionId = parseUuid(root.getString("transaction_id"));
            UUID machineId = parseUuid(root.getString("machine_id"));
            if (!machineId.equals(expectedMachineId)) {
                throw new IllegalArgumentException("journal belongs to a different machine");
            }
            String definitionId = root.getString("definition_id");
            new ProcessResourceKey(ProcessResourceKind.ITEM, "recipe", definitionId);
            long portRevision = root.getLong("port_revision");
            String beforeFingerprint = root.getString("before_fingerprint");
            if (portRevision < 0 || !FINGERPRINT.matcher(beforeFingerprint).matches()) {
                throw new IllegalArgumentException("journal identity fields are invalid");
            }
            ProcessResourceSnapshot before = decodeSnapshot(root.getCompound("before"));
            ProcessResourceSnapshot after = decodeSnapshot(root.getCompound("after"));
            requireCompatibleSnapshots(before, after);
            ProcessTransactionJournal journal = new ProcessTransactionJournal(
                    ProcessTransactionJournal.SCHEMA_VERSION,
                    transactionId,
                    machineId,
                    definitionId,
                    portRevision,
                    beforeFingerprint,
                    before,
                    after,
                    parsePhase(root.getString("phase"))
            );
            return RollingMachineNbtResult.supported(journal);
        } catch (RuntimeException exception) {
            return RollingMachineNbtResult.rejected(MultiblockNbtStatus.INVALID_DATA, root);
        }
    }

    private static CompoundTag encodeSnapshot(ProcessResourceSnapshot snapshot) {
        CompoundTag encoded = new CompoundTag();
        encoded.putLong("revision", snapshot.revision());
        ListTag entries = new ListTag();
        snapshot.balances().forEach((key, balance) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("kind", key.kind().name().toLowerCase(Locale.ROOT));
            entry.putString("channel", key.channel());
            entry.putString("resource_id", key.resourceId());
            entry.putLong("amount", balance.amount());
            entry.putLong("capacity", balance.capacity());
            entries.add(entry);
        });
        encoded.put("entries", entries);
        return encoded;
    }

    private static ProcessResourceSnapshot decodeSnapshot(CompoundTag encoded) {
        if (!SNAPSHOT_FIELDS.equals(encoded.getAllKeys())
                || !encoded.contains("revision", Tag.TAG_LONG)) {
            throw new IllegalArgumentException("journal snapshot fields are invalid");
        }
        Tag rawEntries = encoded.get("entries");
        if (!(rawEntries instanceof ListTag entries)
                || (!entries.isEmpty() && entries.getElementType() != Tag.TAG_COMPOUND)
                || entries.size() > ProcessResourceSnapshot.MAX_ENTRIES) {
            throw new IllegalArgumentException("journal snapshot entries are invalid");
        }
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new LinkedHashMap<>();
        ProcessResourceKey previous = null;
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!ENTRY_FIELDS.equals(entry.getAllKeys())
                    || !entry.contains("kind", Tag.TAG_STRING)
                    || !entry.contains("channel", Tag.TAG_STRING)
                    || !entry.contains("resource_id", Tag.TAG_STRING)
                    || !entry.contains("amount", Tag.TAG_LONG)
                    || !entry.contains("capacity", Tag.TAG_LONG)) {
                throw new IllegalArgumentException("journal resource entry is invalid");
            }
            ProcessResourceKey key = new ProcessResourceKey(
                    parseKind(entry.getString("kind")),
                    entry.getString("channel"),
                    entry.getString("resource_id")
            );
            if (previous != null && previous.compareTo(key) >= 0) {
                throw new IllegalArgumentException("journal entries must be unique and sorted");
            }
            balances.put(key, new ProcessResourceBalance(
                    entry.getLong("amount"),
                    entry.getLong("capacity")
            ));
            previous = key;
        }
        return new ProcessResourceSnapshot(encoded.getLong("revision"), balances);
    }

    private static void requireRootFields(CompoundTag root) {
        if (!FIELDS.equals(root.getAllKeys())
                || !root.contains("transaction_id", Tag.TAG_STRING)
                || !root.contains("machine_id", Tag.TAG_STRING)
                || !root.contains("definition_id", Tag.TAG_STRING)
                || !root.contains("port_revision", Tag.TAG_LONG)
                || !root.contains("before_fingerprint", Tag.TAG_STRING)
                || !root.contains("phase", Tag.TAG_STRING)
                || !root.contains("before", Tag.TAG_COMPOUND)
                || !root.contains("after", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("journal root has missing, unexpected or mistyped fields");
        }
    }

    private static void requireCompatibleSnapshots(
            ProcessResourceSnapshot before,
            ProcessResourceSnapshot after
    ) {
        if (!before.balances().keySet().equals(after.balances().keySet())) {
            throw new IllegalArgumentException("journal snapshots use different resource keys");
        }
        for (ProcessResourceKey key : before.balances().keySet()) {
            if (before.balance(key).capacity() != after.balance(key).capacity()) {
                throw new IllegalArgumentException("journal changed a resource capacity");
            }
        }
    }

    private static UUID parseUuid(String value) {
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("journal UUID is not canonical");
        }
        return parsed;
    }

    private static ProcessJournalPhase parsePhase(String value) {
        for (ProcessJournalPhase phase : ProcessJournalPhase.values()) {
            if (phase.name().toLowerCase(Locale.ROOT).equals(value)) {
                return phase;
            }
        }
        throw new IllegalArgumentException("unknown journal phase");
    }

    private static ProcessResourceKind parseKind(String value) {
        for (ProcessResourceKind kind : ProcessResourceKind.values()) {
            if (kind.name().toLowerCase(Locale.ROOT).equals(value)) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown journal resource kind");
    }
}
