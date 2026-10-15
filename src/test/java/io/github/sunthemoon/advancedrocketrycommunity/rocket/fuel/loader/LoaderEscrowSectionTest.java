package io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTestTags.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowPrefix;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LoaderEscrowSectionTest {
    private static final String DIGEST = "0".repeat(64);

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static EscrowPrefix.Marker source(long sequence, long epochAfter) {
        return new EscrowPrefix.Marker(UUID.randomUUID(), sequence, "SOURCE", epochAfter);
    }

    @Test void neutralAndOneMarkerSectionsRoundTripInTheEndpointLayout() {
        var neutral = LoaderEscrowSection.neutral();
        assertEquals(Set.of("schema_version", "resource_epoch", "markers"), neutral.encode().getAllKeys());
        assertEquals(neutral, LoaderEscrowSection.decode(neutral.encode()));
        assertFalse(neutral.locked());
        invalid(() -> neutral.state(DIGEST));
        UUID ledger = UUID.randomUUID();
        var retired = new LoaderEscrowSection(ledger, 3, List.of());
        assertEquals(retired, LoaderEscrowSection.decode(retired.encode()));
        var locked = new LoaderEscrowSection(ledger, 4, List.of(source(9, 4)));
        CompoundTag encoded = locked.encode();
        assertEquals(Set.of("schema_version", "ledger_id", "resource_epoch", "markers"), encoded.getAllKeys());
        assertEquals(Set.of("op_id", "seq", "role", "epoch_after"), encoded.getList("markers", 10).getCompound(0).getAllKeys());
        assertEquals(locked, LoaderEscrowSection.decode(encoded));
        assertTrue(locked.locked());
        assertEquals(new EscrowPrefix.State(ledger, 4, DIGEST, locked.markers()), locked.state(DIGEST));
    }

    @Test void theLargestOneMarkerSectionFitsTwoHundredFiftySixBytes() {
        var largest = new LoaderEscrowSection(UUID.randomUUID(), Long.MAX_VALUE, List.of(source(Long.MAX_VALUE, Long.MAX_VALUE)));
        assertTrue(size(largest.encode()).bytes() <= LoaderEscrowSection.MAX_BYTES);
        assertEquals(largest, LoaderEscrowSection.decode(largest.encode()));
        assertEquals(256, LoaderEscrowSection.MAX_BYTES);
    }

    @Test void markerCountRoleAndEpochAreExact() {
        UUID ledger = UUID.randomUUID();
        invalid(() -> new LoaderEscrowSection(ledger, 2, List.of(source(1, 1), source(2, 2))));
        invalid(() -> new LoaderEscrowSection(ledger, 1, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, "DESTINATION", 1))));
        invalid(() -> new LoaderEscrowSection(ledger, 2, List.of(source(1, 1))));
        invalid(() -> new LoaderEscrowSection(ledger, -1, List.of()));
        invalid(() -> new LoaderEscrowSection(null, 1, List.of()));
        invalid(() -> new LoaderEscrowSection(null, 1, List.of(source(1, 1))));
        CompoundTag one = new LoaderEscrowSection(ledger, 1, List.of(source(1, 1))).encode();
        // Two real markers exceed 256 bytes by themselves; a small second element isolates the one-marker gate.
        CompoundTag two = one.copy();
        ListTag markers = two.getList("markers", 10).copy();
        CompoundTag second = markers.getCompound(0).copy();
        second.putUUID("op_id", UUID.randomUUID());
        markers.add(second);
        two.put("markers", markers);
        assertFalse(LoaderEscrowSection.bounded(two));
        invalid(() -> LoaderEscrowSection.decode(two));
        CompoundTag counted = one.copy();
        counted.getList("markers", 10).add(new CompoundTag());
        assertTrue(LoaderEscrowSection.bounded(counted));
        var refused = assertThrows(LoaderDataException.class, () -> LoaderEscrowSection.decode(counted));
        assertTrue(refused.getMessage().contains("marker list"), refused.getMessage());
        for (String role : new String[] {"DESTINATION", "OTHER", ""}) {
            CompoundTag tag = one.copy();
            tag.getList("markers", 10).getCompound(0).putString("role", role);
            invalid(() -> LoaderEscrowSection.decode(tag));
        }
        for (long sequence : new long[] {0, -1}) {
            CompoundTag tag = one.copy();
            tag.getList("markers", 10).getCompound(0).putLong("seq", sequence);
            invalid(() -> LoaderEscrowSection.decode(tag));
        }
    }

    @Test void futureUnknownAndMistypedSectionsAreRefused() {
        CompoundTag valid = new LoaderEscrowSection(UUID.randomUUID(), 1, List.of(source(5, 1))).encode();
        schemaVersions(valid, LoaderEscrowSection::decode);
        CompoundTag epoch = valid.copy(); epoch.putInt("resource_epoch", 1);
        invalid(() -> LoaderEscrowSection.decode(epoch));
        CompoundTag ints = valid.copy(); ListTag numbers = new ListTag(); numbers.add(IntTag.valueOf(1)); ints.put("markers", numbers);
        invalid(() -> LoaderEscrowSection.decode(ints));
        CompoundTag ledger = valid.copy(); ledger.putIntArray("ledger_id", new int[3]);
        invalid(() -> LoaderEscrowSection.decode(ledger));
        CompoundTag sequence = valid.copy(); sequence.getList("markers", 10).getCompound(0).putInt("seq", 5);
        invalid(() -> LoaderEscrowSection.decode(sequence));
        CompoundTag extra = valid.copy(); extra.getList("markers", 10).getCompound(0).putInt("extra", 1);
        invalid(() -> LoaderEscrowSection.decode(extra));
        for (String key : new String[] {"resource_epoch", "markers"}) {
            CompoundTag missing = valid.copy(); missing.remove(key);
            invalid(() -> LoaderEscrowSection.decode(missing));
        }
    }

    @Test void envelopeIsTwoHundredFiftySixBytesDepthEightAndOneHundredTwentyEightNodes() {
        CompoundTag valid = LoaderEscrowSection.neutral().encode();
        bounds(valid, LoaderEscrowSection::bounded, 256, 8, 128);
        // The envelope gate runs before field checks: 257 bytes is refused as oversized, 256 only for its unknown field.
        var over = assertThrows(LoaderDataException.class, () -> LoaderEscrowSection.decode(padTo(valid, "pad", 257)));
        assertTrue(over.getMessage().contains("exceeds its envelope"), over.getMessage());
        var at = assertThrows(LoaderDataException.class, () -> LoaderEscrowSection.decode(padTo(valid, "pad", 256)));
        assertTrue(at.getMessage().contains("Unknown loader field"), at.getMessage());
    }
}
