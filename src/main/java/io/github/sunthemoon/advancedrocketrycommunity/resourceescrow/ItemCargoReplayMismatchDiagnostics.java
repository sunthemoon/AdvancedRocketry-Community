package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;

/**
 * W381: failure-only text for the replay-mismatch fixture's hold check. The check itself is unchanged: the held
 * operation must be present with its admitted identity and both native endpoints must equal their captures. This
 * class only names which of those parts failed and what was observed, from values the fixture already holds.
 */
final class ItemCargoReplayMismatchDiagnostics {
    /** The most keys or ledger rows one message lists; the rest are counted. */
    static final int LISTED = 8;

    private ItemCargoReplayMismatchDiagnostics() { }

    /** The parts of the hold check that failed, in the order the fixture evaluates them; empty when it holds. */
    static List<String> failedChecks(boolean present, boolean sameId, boolean deviceUnchanged, boolean rocketUnchanged) {
        var failed = new ArrayList<String>();
        if (!present) { failed.add("OPERATION_MISSING"); }
        else if (!sameId) { failed.add("OPERATION_ID_CHANGED"); }
        if (!deviceUnchanged) { failed.add("DEVICE_CHANGED"); }
        if (!rocketUnchanged) { failed.add("ROCKET_CHANGED"); }
        return List.copyOf(failed);
    }

    /** Top-level keys whose values differ or exist on one side only, sorted; at most {@link #LISTED}, then a count. */
    static String changedKeys(CompoundTag before, CompoundTag now) {
        var keys = new TreeSet<String>();
        for (String key : before.getAllKeys()) {
            if (!now.contains(key) || !before.get(key).equals(now.get(key))) { keys.add(key); }
        }
        for (String key : now.getAllKeys()) { if (!before.contains(key)) { keys.add(key); } }
        if (keys.size() <= LISTED) { return keys.toString(); }
        return keys.stream().limit(LISTED).toList() + "+" + (keys.size() - LISTED);
    }

    /** One bounded row per ledger record: identity prefix, phase, reason, device match and whether the frozen digest is the injected one. */
    static String ledgerRows(EscrowLedger ledger, java.util.UUID device, String injectedDigest) {
        var rows = new ArrayList<String>();
        for (var op : ledger.records()) {
            if (rows.size() == LISTED) { break; }
            var binding = op.nativeBinding();
            boolean injected = injectedDigest != null
                    && (injectedDigest.equals(binding.snapshotHash()) || injectedDigest.equals(binding.sourceAfter()));
            rows.add(op.id().toString().substring(0, 8) + "/" + op.phase() + "/" + op.reason()
                    + "/device=" + op.device().identity().equals(device) + "/injected=" + injected);
        }
        int more = ledger.records().size() - rows.size();
        return "generation=" + ledger.writerGeneration() + " records=" + rows + (more > 0 ? "+" + more : "");
    }
}
