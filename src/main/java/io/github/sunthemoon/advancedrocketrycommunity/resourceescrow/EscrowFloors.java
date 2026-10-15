package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Named-transfer reservations are reconstructed from durable operations; NONE never consumes a floor. */
public final class EscrowFloors {
    public record Key(UUID rocket, UUID transfer) { }
    public record Usage(int used, int reserved) { public int total() { return used + reserved; } }
    private EscrowFloors() { }

    static Key key(EscrowGuard guard) { return guard.transfer() == null ? null : new Key(guard.rocket(), guard.transfer()); }
    static Key key(CompoundTag floor) { return floor.hasUUID("transfer_id") ? new Key(floor.getUUID("rocket_id"), floor.getUUID("transfer_id")) : null; }

    public static Usage usage(EscrowLedger ledger, Collection<EscrowOperation> queued) {
        Set<Key> used = new HashSet<>(), reserved = new HashSet<>();
        ledger.floors().forEach(floor -> used.add(key(floor)));
        java.util.stream.Stream.concat(ledger.records().stream(), queued.stream()).forEach(op -> {
            Key key = key(op.guard()); if (key != null && !used.contains(key)) { reserved.add(key); }
        });
        // Legacy neutral floors remain counted, even though this adapter never produces one.
        return new Usage(ledger.floors().size(), reserved.size());
    }

    /** Reserve count and maximum section growth before queued L1 state changes. */
    static void preflight(EscrowLedger candidate) {
        EscrowSettlementIntent.reserve(candidate);
        var usage = usage(candidate, List.of());
        EscrowTags.require(usage.total() <= EscrowLimits.FLOORS, "Escrow floor capacity exhausted");
        Map<Key, CompoundTag> existing = new HashMap<>(); candidate.floors().forEach(floor -> existing.put(key(floor), floor));
        Map<Key, EscrowOperation> named = new HashMap<>();
        for (var op : candidate.records()) {
            Key key = key(op.guard()); if (key == null) { continue; }
            named.put(key, op);
            var floor = existing.get(key);
            EscrowTags.require(floor == null || floor.getUUID("entity_id").equals(op.destination().identity()), "Floor identity cannot be rebound");
        }
        long bytes = RocketNbtSize.uncompressedBytes(EscrowCodec.encode(candidate));
        for (Key key : named.keySet()) {
            var floor = existing.get(key);
            bytes += 1_024L - (floor == null ? 0 : RocketNbtSize.uncompressedBytes(floor) - 3L);
        }
        EscrowTags.require(bytes <= EscrowLimits.ROOT_BYTES, "Escrow floor byte reservation exhausted");
    }

    /** Called inside the exact L2 snapshot, before the ledger writer captures it. */
    static EscrowLedger retire(EscrowLedger ledger, Set<UUID> ids, Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers) {
        var floors = new ArrayList<>(ledger.floors());
        for (var op : ledger.records()) {
            if (!ids.contains(op.id()) || op.guard().transfer() == null) { continue; }
            var frontier = frontiers.get(op.destination().key());
            long highest = ledger.records().stream().filter(record -> record.destination().key().equals(op.destination().key()))
                    .mapToLong(EscrowOperation::sequence).max().orElseThrow();
            CompoundTag floor = EscrowCodec.guard(op.guard()); floor.putInt("schema_version", 2);
            floor.putUUID("entity_id", op.destination().identity()); floor.putLong("resource_epoch", frontier.epoch());
            floor.putString("signature", frontier.signature()); floor.putLong("highest_seq", highest);
            floor.putString("signature_kind", op.nativeBinding().kind().name()); floor.putString("invariant_digest", op.nativeBinding().invariant());
            EscrowCodec.validateSection(floor, false);
            Key key = key(op.guard());
            var previous = floors.stream().filter(value -> key.equals(key(value))).findFirst().orElse(null);
            if (previous != null) {
                EscrowTags.require(previous.getUUID("entity_id").equals(op.destination().identity())
                        && previous.getLong("resource_epoch") <= frontier.epoch()
                        && previous.getLong("highest_seq") <= highest, "Floor authority would regress");
                if (previous.getLong("resource_epoch") == frontier.epoch()) {
                    EscrowTags.require(previous.getString("signature").equals(frontier.signature()), "Floor signature conflict");
                }
                floors.remove(previous);
            }
            floors.add(floor);
        }
        EscrowLedger retired = ledger.retire(ids);
        return retired.replace(retired.records(), floors, retired.tombstones());
    }

    /** An exact checked journal snapshot without T is necessary; pending/queued references retain it. */
    static EscrowLedger collect(EscrowLedger ledger, Collection<EscrowOperation> queued, java.util.function.Predicate<UUID> absent) {
        Set<Key> retained = new HashSet<>();
        java.util.stream.Stream.concat(ledger.records().stream(), queued.stream()).forEach(op -> {
            if (key(op.guard()) != null) { retained.add(key(op.guard())); }
        });
        List<CompoundTag> floors = ledger.floors().stream().filter(floor -> key(floor) == null
                || retained.contains(key(floor)) || !absent.test(key(floor).transfer())).toList();
        return ledger.replace(ledger.records(), floors, ledger.tombstones());
    }
}
