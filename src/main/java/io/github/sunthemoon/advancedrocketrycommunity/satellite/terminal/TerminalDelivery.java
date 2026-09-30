package io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.DeliveryReconciliation;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-051 section 5: the Satellite Terminal's delivery section (root schema 2): the terminal ID, the reward
 * buffer and the receipts, with their runtime persistence flags and the reconciliation queue. Everything read
 * from NBT starts unpersisted; only a chunk load or chunk save tag marks it persisted.
 */
public final class TerminalDelivery {
    public static final int MAX_BUFFER_ENTRIES = 32;
    public static final int MAX_BUFFER_ITEMS = 3_456;
    public static final int MAX_RECEIPTS = 256;
    public static final int RECONCILE_PER_TICK = 64;
    public static final int MAX_ITEM_ID_CHARS = 128;
    static final String TERMINAL_ID = "terminal_id";
    static final String BUFFER = "reward_buffer";
    static final String RECEIPTS = "receipts";
    static final Set<String> KEYS = Set.of(TERMINAL_ID, BUFFER, RECEIPTS);
    private static final Comparator<UUID> ORDER = Comparator.comparingLong(UUID::getMostSignificantBits)
            .thenComparingLong(UUID::getLeastSignificantBits);

    private final UUID terminalId;
    private boolean idPersisted;
    private final Map<ResourceLocation, Integer> buffer = new LinkedHashMap<>();
    private final Map<UUID, Receipt> receipts = new LinkedHashMap<>();
    private final Deque<UUID> pass = new ArrayDeque<>();

    private static final class Receipt {
        private boolean persisted;
        private boolean audited;
    }

    private TerminalDelivery(UUID terminalId) {
        this.terminalId = Objects.requireNonNull(terminalId, "terminalId");
    }

    /** A new terminal, or the first load of a schema-1 root: a fresh ID, not yet persisted. */
    public static TerminalDelivery create() {
        return new TerminalDelivery(UUID.randomUUID());
    }

    /** Strict schema-2 decode; throws {@link IllegalArgumentException} for anything outside the bounds. */
    public static TerminalDelivery read(CompoundTag data) {
        if (!data.hasUUID(TERMINAL_ID)) {
            throw new IllegalArgumentException("Terminal delivery section has no terminal ID");
        }
        TerminalDelivery delivery = new TerminalDelivery(data.getUUID(TERMINAL_ID));
        ListTag entries = list(data, BUFFER, Tag.TAG_COMPOUND, MAX_BUFFER_ENTRIES);
        long total = 0L;
        for (int index = 0; index < entries.size(); index++) {
            CompoundTag entry = entries.getCompound(index);
            if (!Set.of("item", "count").equals(entry.getAllKeys()) || !entry.contains("item", Tag.TAG_STRING)
                    || !entry.contains("count", Tag.TAG_INT)) {
                throw new IllegalArgumentException("Terminal reward entry is malformed");
            }
            String raw = entry.getString("item");
            ResourceLocation item = raw.length() > MAX_ITEM_ID_CHARS ? null : ResourceLocation.tryParse(raw);
            int count = entry.getInt("count");
            if (item == null || count < 1 || count > MAX_BUFFER_ITEMS
                    || delivery.buffer.putIfAbsent(item, count) != null) {
                throw new IllegalArgumentException("Terminal reward entry is outside its bounds");
            }
            total += count;
        }
        if (total > MAX_BUFFER_ITEMS) {
            throw new IllegalArgumentException("Terminal reward buffer exceeds " + MAX_BUFFER_ITEMS + " items");
        }
        for (Tag tag : list(data, RECEIPTS, Tag.TAG_INT_ARRAY, MAX_RECEIPTS)) {
            if (((IntArrayTag) tag).size() != 4
                    || delivery.receipts.putIfAbsent(NbtUtils.loadUUID(tag), new Receipt()) != null) {
                throw new IllegalArgumentException("Terminal receipt is malformed");
            }
        }
        return delivery;
    }

    private static ListTag list(CompoundTag data, String key, byte elementType, int maximum) {
        if (!(data.get(key) instanceof ListTag list) || !list.isEmpty() && list.getElementType() != elementType
                || list.size() > maximum) {
            throw new IllegalArgumentException("Terminal " + key + " is not a bounded list");
        }
        return list;
    }

    public void write(CompoundTag data) {
        data.putUUID(TERMINAL_ID, terminalId);
        ListTag entries = new ListTag();
        buffer.forEach((item, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("item", item.toString());
            entry.putInt("count", count);
            entries.add(entry);
        });
        data.put(BUFFER, entries);
        ListTag ids = new ListTag();
        receipts.keySet().forEach(id -> ids.add(NbtUtils.createUUID(id)));
        data.put(RECEIPTS, ids);
    }

    public UUID terminalId() {
        return terminalId;
    }

    public boolean idPersisted() {
        return idPersisted;
    }

    /** Whether the ID or any receipt still waits for a chunk save (ADR-051 section 5). */
    public boolean unpersisted() {
        return !idPersisted || receipts.values().stream().anyMatch(receipt -> !receipt.persisted);
    }

    /** A chunk load or chunk save tag held this terminal with these receipts. */
    public void observed(UUID id, Collection<UUID> persistedReceipts) {
        if (!terminalId.equals(id)) {
            return;
        }
        idPersisted = true;
        persistedReceipts.forEach(missionId -> {
            Receipt receipt = receipts.get(missionId);
            if (receipt != null) {
                receipt.persisted = true;
            }
        });
    }

    public List<RewardEntry> buffer() {
        List<RewardEntry> entries = new ArrayList<>(buffer.size());
        buffer.forEach((item, count) -> entries.add(new RewardEntry(item, count)));
        return entries;
    }

    public int bufferItems() {
        return buffer.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int receiptCount() {
        return receipts.size();
    }

    /** Null when the whole reward and one receipt fit; otherwise the refusal (ADR-051 section 6). */
    public SatelliteOperationCode room(List<RewardEntry> reward) {
        long entries = buffer.size() + reward.stream().filter(entry -> !buffer.containsKey(entry.item())).count();
        long items = bufferItems() + reward.stream().mapToLong(RewardEntry::count).sum();
        if (entries > MAX_BUFFER_ENTRIES || items > MAX_BUFFER_ITEMS) {
            return SatelliteOperationCode.DELIVERY_BUFFER_FULL;
        }
        return receipts.size() >= MAX_RECEIPTS ? SatelliteOperationCode.TERMINAL_RECEIPTS_FULL : null;
    }

    /** Adds a reward and an unpersisted receipt; the caller checked {@link #room}. */
    public void pay(UUID missionId, List<RewardEntry> reward) {
        if (room(reward) != null || receipts.containsKey(missionId)) {
            throw new IllegalStateException("The terminal cannot take this reward");
        }
        reward.forEach(entry -> buffer.merge(entry.item(), entry.count(), Integer::sum));
        receipts.put(missionId, new Receipt());
    }

    /** Removes up to {@code count} of one buffered item; returns the amount removed. */
    public int take(ResourceLocation item, int count) {
        int held = buffer.getOrDefault(item, 0);
        int taken = Math.max(0, Math.min(held, count));
        if (taken == held) {
            buffer.remove(item);
        } else if (taken > 0) {
            buffer.put(item, held - taken);
        }
        return taken;
    }

    public DeliveryReconciliation.ReceiptView receipt(UUID missionId) {
        Receipt receipt = receipts.get(missionId);
        return receipt == null ? DeliveryReconciliation.ReceiptView.NONE
                : receipt.persisted ? DeliveryReconciliation.ReceiptView.PERSISTED
                : DeliveryReconciliation.ReceiptView.UNPERSISTED;
    }

    public Set<UUID> receiptIds() {
        return Set.copyOf(receipts.keySet());
    }

    public void dropReceipt(UUID missionId) {
        receipts.remove(missionId);
    }

    /** True the first time a one-off audit (double pay, paid then cancelled) is reported for this receipt. */
    public boolean firstAudit(UUID missionId) {
        Receipt receipt = receipts.get(missionId);
        if (receipt == null || receipt.audited) {
            return false;
        }
        receipt.audited = true;
        return true;
    }

    /**
     * The terminal side of one ADR-051 section 7 row, after the registry side was applied: drop, rematerialize
     * (waiting while the buffer is full) or a one-off audit. Returns the audit event to report, or null.
     */
    public String apply(DeliveryReconciliation.Action action, UUID missionId, java.util.Optional<MissionState> mission) {
        return switch (action) {
            case DROP_RECEIPT -> {
                dropReceipt(missionId);
                yield "RECEIPT_DROPPED";
            }
            case REMATERIALIZE -> {
                List<RewardEntry> reward = ((MissionPayload.Resource) mission.orElseThrow().payload()).reward();
                if (room(reward) != null) {
                    yield null;
                }
                pay(missionId, reward);
                yield "REMATERIALIZED";
            }
            case KEEP_AUDIT_DOUBLE_PAY -> firstAudit(missionId) ? "REBIND_DOUBLE_PAY" : null;
            case KEEP_AUDIT_PAID_THEN_CANCELLED -> firstAudit(missionId) ? "PAID_THEN_CANCELLED" : null;
            default -> null;
        };
    }

    public boolean passActive() {
        return !pass.isEmpty();
    }

    /** Queues the bound missions and every receipt's mission, each once, in ID order (ADR-051 section 7). */
    public void startPass(Collection<UUID> bound) {
        TreeSet<UUID> ids = new TreeSet<>(ORDER);
        ids.addAll(bound);
        ids.addAll(receipts.keySet());
        pass.clear();
        pass.addAll(ids);
    }

    /** At most {@code maximum} queued missions, removed from the queue. */
    public List<UUID> nextBatch(int maximum) {
        List<UUID> batch = new ArrayList<>(Math.min(maximum, pass.size()));
        while (batch.size() < maximum && !pass.isEmpty()) {
            batch.add(pass.poll());
        }
        return batch;
    }
}
