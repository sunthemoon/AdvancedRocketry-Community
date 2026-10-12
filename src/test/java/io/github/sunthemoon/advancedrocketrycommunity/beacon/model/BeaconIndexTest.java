package io.github.sunthemoon.advancedrocketrycommunity.beacon.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconIndex.Admission;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-065 section 7 bounds: 32 per owner, 256 per body, 4,096 in all and a 1 MiB root; refusals change nothing. */
class BeaconIndexTest {
    private static final String EARTH = "advancedrocketrycommunity:earth";
    private static final String MOON = "advancedrocketrycommunity:moon";
    private static final String OVERWORLD = "minecraft:overworld";

    static BeaconRecord record(UUID owner, String body, int x) {
        return new BeaconRecord(UUID.randomUUID(), owner, body, OVERWORLD, x, 64, 0, 1, true, "b" + x);
    }

    private static BeaconIndex index() {
        return new BeaconIndex(record -> 100, 10);
    }

    @Test
    void anOwnerHoldsAtMostThirtyTwoAndLookupReadsOnlyThatOwner() {
        BeaconIndex index = index();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        for (int i = 0; i < BeaconLimits.MAX_PER_OWNER; i++) {
            assertEquals(Admission.ADMITTED, index.put(record(owner, EARTH, i)));
        }
        assertEquals(Admission.ADMITTED, index.put(record(other, EARTH, 99)));
        long bytes = index.encodedBytes();
        assertEquals(Admission.OWNER_FULL, index.put(record(owner, MOON, 100)));
        assertEquals(bytes, index.encodedBytes());
        assertEquals(BeaconLimits.MAX_PER_OWNER + 1, index.size());
        List<BeaconRecord> owned = index.ownedBy(owner);
        assertEquals(BeaconLimits.MAX_PER_OWNER, owned.size());
        assertTrue(owned.stream().allMatch(record -> record.owner().equals(owner)));
        assertEquals(1, index.ownedBy(other).size());
        assertTrue(index.ownedBy(UUID.randomUUID()).isEmpty());
    }

    @Test
    void aBodyHoldsAtMostTwoHundredFiftySixIncludingAMoveOntoIt() {
        BeaconIndex index = index();
        for (int i = 0; i < BeaconLimits.MAX_PER_BODY; i++) {
            assertEquals(Admission.ADMITTED, index.put(record(UUID.randomUUID(), EARTH, i)));
        }
        assertEquals(Admission.BODY_FULL, index.put(record(UUID.randomUUID(), EARTH, 300)));
        BeaconRecord moon = record(UUID.randomUUID(), MOON, 400);
        assertEquals(Admission.ADMITTED, index.put(moon));
        BeaconRecord moved = new BeaconRecord(moon.beaconId(), moon.owner(), EARTH, OVERWORLD, 400, 64, 0, 1, true, "m");
        assertEquals(Admission.BODY_FULL, index.put(moved));
        assertEquals(moon, index.get(moon.beaconId()).orElseThrow());
        assertEquals(1, index.countForBody(MOON));
        assertEquals(BeaconLimits.MAX_PER_BODY, index.countForBody(EARTH));
    }

    @Test
    void theIndexHoldsAtMostFourThousandNinetySix() {
        BeaconIndex index = new BeaconIndex(record -> 1, 0);
        for (int i = 0; i < BeaconLimits.MAX_TOTAL; i++) {
            String body = "advancedrocketrycommunity:body_" + (i / BeaconLimits.MAX_PER_BODY);
            assertEquals(Admission.ADMITTED, index.put(record(UUID.randomUUID(), body, i)));
        }
        assertEquals(Admission.INDEX_FULL, index.put(record(UUID.randomUUID(), "advancedrocketrycommunity:spare", 0)));
        assertEquals(BeaconLimits.MAX_TOTAL, index.size());
    }

    @Test
    void theRootByteBoundRefusesInsertsAndGrowingReplacements() {
        BeaconIndex index = new BeaconIndex(record -> record.name().equals("big") ? 700_000 : 500_000, 1_000);
        UUID owner = UUID.randomUUID();
        BeaconRecord first = new BeaconRecord(UUID.randomUUID(), owner, EARTH, OVERWORLD, 0, 64, 0, 0, true, "a");
        BeaconRecord second = new BeaconRecord(UUID.randomUUID(), owner, EARTH, OVERWORLD, 1, 64, 0, 0, true, "a");
        assertEquals(Admission.ADMITTED, index.put(first));
        assertEquals(Admission.ADMITTED, index.put(second));
        assertEquals(1_001_000, index.encodedBytes());
        assertEquals(Admission.BYTES_FULL, index.put(record(owner, EARTH, 5)));
        BeaconRecord grown = new BeaconRecord(first.beaconId(), owner, EARTH, OVERWORLD, 0, 64, 0, 0, true, "big");
        assertEquals(Admission.BYTES_FULL, index.put(grown));
        assertEquals(first, index.get(first.beaconId()).orElseThrow());
        assertEquals(1_001_000, index.encodedBytes());
        index.remove(second.beaconId());
        assertEquals(Admission.ADMITTED, index.put(grown));
        assertEquals(701_000, index.encodedBytes());
    }

    @Test
    void replacementsKeepTheOwnerAndUnchangedRecordsAreNotRewritten() {
        BeaconIndex index = index();
        BeaconRecord record = record(UUID.randomUUID(), EARTH, 1);
        assertEquals(Admission.ADMITTED, index.put(record));
        assertEquals(Admission.UNCHANGED, index.put(record));
        BeaconRecord stolen = new BeaconRecord(record.beaconId(), UUID.randomUUID(), EARTH, OVERWORLD, 1, 64, 0, 1, true,
                "b1");
        assertEquals(Admission.OWNER_MISMATCH, index.put(stolen));
        BeaconRecord inactive = new BeaconRecord(record.beaconId(), record.owner(), EARTH, OVERWORLD, 1, 64, 0, 2, false,
                "renamed");
        assertEquals(Admission.ADMITTED, index.put(inactive));
        assertEquals(inactive, index.get(record.beaconId()).orElseThrow());
        assertEquals(1, index.size());
        assertTrue(Admission.UNCHANGED.stored() && Admission.ADMITTED.stored());
        assertFalse(Admission.OWNER_MISMATCH.stored() || Admission.BYTES_FULL.stored());
    }

    @Test
    void removalUnlinksEveryIndex() {
        BeaconIndex index = index();
        UUID owner = UUID.randomUUID();
        BeaconRecord a = record(owner, EARTH, 1);
        BeaconRecord b = record(owner, MOON, 2);
        index.put(a);
        index.put(b);
        assertEquals(a, index.remove(a.beaconId()).orElseThrow());
        assertTrue(index.remove(a.beaconId()).isEmpty());
        assertEquals(List.of(b), index.ownedBy(owner));
        assertEquals(0, index.countForBody(EARTH));
        assertEquals(10 + 100, index.encodedBytes());
        index.remove(b.beaconId());
        assertEquals(0, index.ownedCount(owner));
        assertEquals(10, index.encodedBytes());
    }

    @Test
    void canonicalOrderIsTheLowercaseUuidString() {
        BeaconIndex index = index();
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            BeaconRecord record = record(UUID.randomUUID(), EARTH, i);
            index.put(record);
            ids.add(record.beaconId().toString());
        }
        List<String> order = index.records().stream().map(record -> record.beaconId().toString()).toList();
        assertEquals(ids.stream().sorted().toList(), order);
    }
}
