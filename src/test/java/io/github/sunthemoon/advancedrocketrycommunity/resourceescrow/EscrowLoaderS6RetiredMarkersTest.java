package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6RetiredMarkersTest {
    private static final UUID LEDGER = UUID.fromString("39ffe916-f807-4e60-93c4-c2655a153070");
    private static final UUID OP = UUID.fromString("ca6f9687-6b3c-4782-8ae0-e39c5808be63");
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactRetiredMarkersAreMetadataNotRecordBackedHeldFuel() {
        for (String role : List.of("SOURCE", "DESTINATION")) {
            var markers = List.of(new EscrowPrefix.Marker(OP, 1, role, 1));
            assertEquals(0, count(OP, LEDGER, 3, 1, 0, LEDGER, 1, role, markers));
            assertEquals(0, count(OP, LEDGER, 3, 1, 0, LEDGER, 1, role, List.of()));
        }
        var result = EscrowLoaderS6Accounting.classify(true, 4_000, 1_000, null, true, true,
                count(OP, LEDGER, 3, 1, 0, LEDGER, 1, "SOURCE", List.of(new EscrowPrefix.Marker(OP, 1, "SOURCE", 1))));
        assertEquals(new EscrowLoaderS6Accounting.Partition(4_000, 0, 1_000, 0, 0), result);
    }

    @Test void noWitnessOrLiveRecordsNeverProjectMarkersAway() {
        var markers = List.of(new EscrowPrefix.Marker(OP, 1, "SOURCE", 1));
        assertEquals(1, count(null, LEDGER, 3, 1, 0, LEDGER, 1, "SOURCE", markers));
        assertEquals(1, count(OP, LEDGER, 3, 1, 1, LEDGER, 1, "SOURCE", markers));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6Accounting.classify(
                true, 4_000, 1_000, null, true, true, count(null, LEDGER, 3, 1, 0, LEDGER, 1, "SOURCE", markers)));
    }

    @Test void changedRetiredOperationBindingRoleSequenceEpochAndDuplicateAreRejected() {
        var exact = List.of(new EscrowPrefix.Marker(OP, 1, "SOURCE", 1));
        for (var markers : List.of(List.of(new EscrowPrefix.Marker(LEDGER, 1, "SOURCE", 1)),
                List.of(new EscrowPrefix.Marker(OP, 2, "SOURCE", 1)),
                List.of(new EscrowPrefix.Marker(OP, 1, "DESTINATION", 1)),
                List.of(new EscrowPrefix.Marker(OP, 1, "SOURCE", 2)), List.of(exact.get(0), exact.get(0)))) {
            assertThrows(IllegalArgumentException.class, () -> count(OP, LEDGER, 3, 1, 0, LEDGER, 1, "SOURCE", markers));
        }
        assertThrows(IllegalArgumentException.class, () -> count(OP, LEDGER, 2, 1, 0, LEDGER, 1, "SOURCE", exact));
        assertThrows(IllegalArgumentException.class, () -> count(OP, LEDGER, 3, 2, 0, LEDGER, 1, "SOURCE", exact));
        assertThrows(IllegalArgumentException.class, () -> count(OP, LEDGER, 3, 1, 0, OP, 1, "SOURCE", exact));
        assertThrows(IllegalArgumentException.class, () -> count(OP, LEDGER, 3, 1, 0, LEDGER, 2, "SOURCE", exact));
    }

    private static int count(UUID op, UUID ledger, long generation, long issued, int records,
            UUID bound, long epoch, String role, List<EscrowPrefix.Marker> markers) {
        return EscrowLoaderS6Recovery.activeMarkers(op, ledger, generation, issued, records, bound, epoch, role, markers);
    }

    /** Read-only replay of retained native metadata through the same helper as the live observer. */
    public static void main(String[] args) throws Exception {
        bootstrap();
        for (String file : args) {
            var root = net.minecraft.nbt.TagParser.parseTag(java.nio.file.Files.readString(java.nio.file.Path.of(file)));
            int active = 0;
            for (String role : List.of("SOURCE", "DESTINATION")) {
                var section = root.getCompound(role);
                var markers = new java.util.ArrayList<EscrowPrefix.Marker>();
                for (var tag : section.getList("markers", 10)) {
                    var marker = (net.minecraft.nbt.CompoundTag) tag;
                    markers.add(new EscrowPrefix.Marker(marker.getUUID("op_id"), marker.getLong("seq"),
                            marker.getString("role"), marker.getLong("epoch_after")));
                }
                active += count(root.getUUID("operation"), root.getUUID("ledger"), root.getLong("generation"),
                        root.getLong("issued"), root.getInt("records"), section.getUUID("ledger_id"),
                        section.getLong("resource_epoch"), role, markers);
            }
            var partition = EscrowLoaderS6Accounting.classify(true, root.getLong("source_amount"),
                    root.getLong("destination_amount"), null, true, true, active);
            assertEquals(new EscrowLoaderS6Accounting.Partition(4_000, 0, 1_000, 0, 0), partition);
            System.out.println("RETIRED_METADATA_REPLAY_PASS " + file);
        }
    }
}
