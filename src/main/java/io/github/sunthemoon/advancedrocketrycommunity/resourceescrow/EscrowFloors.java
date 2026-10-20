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
        if (usage.total() > EscrowLimits.FLOORS) {
            throw new EscrowSettlementCapacity.ReservationFull("Escrow floor capacity exhausted");
        }
        Map<Key, CompoundTag> existing = new HashMap<>(); candidate.floors().forEach(floor -> existing.put(key(floor), floor));
        for (var op : candidate.records()) {
            Key key = key(op.guard()); if (key == null) { continue; }
            var floor = existing.get(key);
            EscrowTags.require(floor == null || floor.getUUID("entity_id").equals(op.rocket().identity()), "Floor identity cannot be rebound");
        }
        if (RocketNbtSize.uncompressedBytes(EscrowCodec.encode(candidate)) + reservedGrowth(candidate)
                > EscrowLimits.ROOT_BYTES) {
            throw new EscrowSettlementCapacity.ReservationFull("Escrow floor byte reservation exhausted");
        }
    }

    static long reservedGrowth(EscrowLedger candidate) {
        Map<Key, CompoundTag> existing = new HashMap<>(); candidate.floors().forEach(floor -> existing.put(key(floor), floor));
        Set<Key> named = new HashSet<>();
        // A retained named floor keeps its ceiling even after its operation retires.
        // Otherwise a later floor intent would reclaim an occupied-size discount.
        existing.keySet().stream().filter(java.util.Objects::nonNull).forEach(named::add);
        candidate.records().stream().map(op -> key(op.guard())).filter(java.util.Objects::nonNull).forEach(named::add);
        long bytes = 0;
        for (Key key : named) {
            var floor = existing.get(key);
            bytes += 1_024L - (floor == null ? 0 : RocketNbtSize.uncompressedBytes(floor) - 3L);
        }
        return bytes;
    }

    /** Called inside the exact L2 snapshot, before the ledger writer captures it. */
    static EscrowLedger retire(EscrowLedger ledger, Set<UUID> ids, Map<EscrowEndpoint.Key, EscrowPrefix.State> frontiers) {
        var floors = new ArrayList<>(ledger.floors());
        for (var op : ledger.records()) {
            if (!ids.contains(op.id()) || op.guard().transfer() == null) { continue; }
            var frontier = frontiers.get(op.rocket().key());
            long highest = ledger.records().stream().filter(record -> record.rocket().key().equals(op.rocket().key()))
                    .mapToLong(EscrowOperation::sequence).max().orElseThrow();
            CompoundTag floor = EscrowCodec.guard(op.guard()); floor.putInt("schema_version", 2);
            floor.putUUID("entity_id", op.rocket().identity()); floor.putLong("resource_epoch", frontier.epoch());
            floor.putString("signature", frontier.signature()); floor.putLong("highest_seq", highest);
            floor.putString("signature_kind", op.nativeBinding().kind().name()); floor.putString("invariant_digest", op.nativeBinding().invariant());
            EscrowCodec.validateSection(floor, false);
            Key key = key(op.guard());
            var previous = floors.stream().filter(value -> key.equals(key(value))).findFirst().orElse(null);
            if (previous != null) {
                EscrowTags.require(previous.getUUID("entity_id").equals(op.rocket().identity())
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
