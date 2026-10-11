package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** The persistent root and all runtime queues belong to the Overworld storage lifecycle. */
public final class FieldCleanupSavedData extends SavedData {
    public static final String NAME = "advancedrocketrycommunity_force_field_cleanup";
    private FieldCleanupRoot root = new FieldCleanupRoot();
    private CompoundTag refused;
    private boolean saveRefusalReported;
    private final ArrayDeque<FieldIdentity.Key> queue = new ArrayDeque<>();
    private final Set<FieldIdentity.Key> queued = new HashSet<>();
    private final ArrayDeque<Observation> observations = new ArrayDeque<>();
    private final Set<Observation> observed = new HashSet<>();
    private final FieldWorkBudget budget = new FieldWorkBudget();
    private boolean observationTurn;
    private final java.util.Map<FieldIdentity.Key, Integer> cleanupCursors = new java.util.HashMap<>();

    public static FieldCleanupSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FieldCleanupSavedData::load,
                FieldCleanupSavedData::new, NAME);
    }
    public static FieldCleanupSavedData load(CompoundTag raw) {
        FieldCleanupSavedData data = new FieldCleanupSavedData();
        try { data.root = FieldCleanupRoot.decode(raw); }
        catch (IllegalArgumentException failure) { data.refused = FieldCleanupRoot.bounded(raw) ? raw.copy() : raw; }
        data.root.entries().forEach(entry -> data.enqueue(entry.identity().key()));
        return data;
    }
    public boolean operational() { return refused == null; }
    public FieldCleanupRoot view() { return root; }
    public boolean reserve(FieldIdentity identity, int claims) {
        if (!operational()) { return false; }
        var old = root.get(identity.key());
        if (old != null && (old.orphan() || !old.identity().equals(identity))) { return false; }
        return put(new FieldCleanupRoot.Entry(identity, claims | (old == null ? 0 : old.claims()), false));
    }
    public boolean canRemove(FieldIdentity identity, int claims) {
        return operational() && root.canOrphan(new FieldCleanupRoot.Entry(identity, claims, true));
    }
    public boolean retire(FieldIdentity identity, int claims) {
        if (!canRemove(identity, claims)) { return false; }
        var old = root.get(identity.key());
        return put(new FieldCleanupRoot.Entry(identity, claims | (old == null ? 0 : old.claims()), true));
    }
    public boolean put(FieldCleanupRoot.Entry entry) {
        if (!operational() || !root.put(entry)) { return false; }
        enqueue(entry.identity().key());
        setDirty();
        return true;
    }
    public void forget(FieldIdentity.Key key) {
        root.remove(key);
        if (root.entries().stream().noneMatch(entry -> entry.identity().projector().equals(key.projector()))) { budget.forget(key); }
        cleanupCursors.remove(key);
        setDirty();
    }
    int cleanupOffset(FieldCleanupRoot.Entry entry) {
        int start = cleanupCursors.getOrDefault(entry.identity().key(), 1);
        for (int i = 0; i < 32; i++) {
            int offset = (start + i - 1) % 32 + 1;
            if ((entry.claims() & FieldIdentity.bit(offset)) != 0) {
                cleanupCursors.put(entry.identity().key(), offset % 32 + 1);
                return offset;
            }
        }
        throw new IllegalStateException("Empty cleanup claim");
    }
    private void enqueue(FieldIdentity.Key key) { if (queued.add(key)) { queue.addLast(key); } }
    public void observe(FieldIdentity identity, BlockPos target) {
        Observation observation = new Observation(identity, target.immutable());
        if (observations.size() < FieldCleanupRoot.MAX_ENTRIES * 32 && observed.add(observation)) {
            observations.addLast(observation);
        }
    }
    public void tick(MinecraftServer server) {
        if (!operational()) { return; }
        long now = server.overworld().getGameTime();
        // Visiting records is bounded even if all chunks are unavailable or all identities are on cooldown.
        int visits = Math.min(128, queue.size() + observations.size());
        for (int i = 0; i < visits; i++) {
            observationTurn = !observationTurn;
            if ((observationTurn && !observations.isEmpty()) || queue.isEmpty()) {
                Observation observation = observations.pollFirst();
                if (observation == null) { continue; }
                observed.remove(observation);
                if (!budget.hasCapacity(2, now)) {
                    observations.addFirst(observation); observed.add(observation); break;
                }
                if (!budget.acquire(observation.identity().key(), now, 2)
                        || !ForceFieldService.validate(server, this, observation)) {
                    observe(observation.identity(), observation.target());
                }
            } else {
                FieldIdentity.Key key = queue.removeFirst();
                queued.remove(key);
                FieldCleanupRoot.Entry entry = root.get(key);
                if (entry != null) {
                    int cost = entry.orphan() ? 2 : 9;
                    if (!budget.hasCapacity(cost, now)) { queue.addFirst(key); queued.add(key); break; }
                    if (budget.acquire(key, now, cost)) { ForceFieldService.work(server, this, entry); }
                    if (root.get(key) != null) { enqueue(key); }
                }
            }
        }
    }
    @Override public CompoundTag save(CompoundTag ignored) {
        if (refused != null) { return FieldCleanupRoot.bounded(refused) ? refused.copy() : refused; }
        return root.encode();
    }
    public record Observation(FieldIdentity identity, BlockPos target) { }
    int inspectedCells() { return budget.cells(); }
    java.util.OptionalLong lastVisit(FieldIdentity.Key key) { return budget.lastVisit(key); }
    @Override public void save(java.io.File file) {
        if (refused != null && !FieldCleanupRoot.bounded(refused)) {
            setDirty();
            if (!saveRefusalReported) {
                saveRefusalReported = true;
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.warn(
                        "ARCE_FORCE_FIELD_CLEANUP_SAVE_REFUSED: back up and repair the oversized cleanup file");
            }
            return;
        }
        super.save(file);
    }
}
