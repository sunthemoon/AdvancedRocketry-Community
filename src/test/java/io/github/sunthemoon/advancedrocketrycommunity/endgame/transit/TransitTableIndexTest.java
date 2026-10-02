package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * C13 performance: the transit table's indexes always answer as a scan of every record would, through random
 * registrations, transitions (arrive, claim, acknowledge, stub, return, redirect) and removals.
 */
final class TransitTableIndexTest {
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final int ENDPOINTS = 6;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static UUID endpoint(int index) {
        return new UUID(0L, 100L + index);
    }

    @Test
    void theIndexesAgreeWithAFullScanThroughRandomMutations() {
        TransitPayload payload = TransitPayload.of(List.of(new ItemStack(Items.DIAMOND, 3))).orElseThrow();
        for (long seed = 1; seed <= 20; seed++) {
            Random random = new Random(seed);
            TransitTable table = new TransitTable(() -> { });
            long[] nextSeq = new long[ENDPOINTS];
            for (int step = 0; step < 400; step++) {
                List<TransitRecord> all = new ArrayList<>(table.records());
                int action = all.isEmpty() ? 0 : random.nextInt(8);
                if (action == 0 && table.size() < 64) {
                    int source = random.nextInt(ENDPOINTS);
                    UUID destination = endpoint((source + 1 + random.nextInt(ENDPOINTS - 1)) % ENDPOINTS);
                    table.add(TransitRecord.registered(endpoint(source), new OutboxEntry(++nextSeq[source],
                            destination, payload, 1, 1 + random.nextInt(600), EndgameSystem.RAILGUN), OWNER, 1L,
                            random.nextInt(1000)));
                } else if (!all.isEmpty()) {
                    TransitRecord record = all.get(random.nextInt(all.size()));
                    switch (action) {
                        case 1 -> {
                            if (record.state() == TransitRecord.State.IN_TRANSIT) {
                                table.replace(record.arrived());
                            }
                        }
                        case 2 -> {
                            if (record.state() == TransitRecord.State.ARRIVED) {
                                table.replace(record.claimed(record.destination()));
                            }
                        }
                        case 3 -> {
                            if (record.state() == TransitRecord.State.CLAIMED && !record.acknowledged()) {
                                table.replace(record.acknowledged(2L));
                            }
                        }
                        case 4 -> {
                            if (record.acknowledged() && !record.stub()) {
                                table.replace(record.asStub());
                            }
                        }
                        case 5 -> {
                            if (record.state() == TransitRecord.State.CLAIMED && !record.acknowledged()) {
                                table.replace(record.returned());
                            }
                        }
                        case 6 -> {
                            if (record.state() != TransitRecord.State.CLAIMED) {
                                table.replace(record.redirectedTo(endpoint(random.nextInt(ENDPOINTS))));
                            }
                        }
                        default -> table.remove(record.key());
                    }
                }
                assertAgrees(table, random.nextInt(1200), "seed " + seed + " step " + step);
            }
        }
    }

    private static void assertAgrees(TransitTable table, long now, String where) {
        List<TransitRecord> all = new ArrayList<>(table.records());
        for (int i = 0; i < ENDPOINTS + 1; i++) {
            UUID id = endpoint(i);
            assertEquals(all.stream().filter(record -> record.destination().equals(id)
                    || id.equals(record.paidEndpoint())).toList(), table.forEndpoint(id), where + " forEndpoint");
            assertEquals(all.stream().filter(record -> record.key().source().equals(id)).toList(),
                    table.fromSource(id), where + " fromSource");
            assertEquals(all.stream().anyMatch(record -> record.names(id)), table.names(id), where + " names");
        }
        assertEquals(all.stream().filter(record -> record.state() == TransitRecord.State.IN_TRANSIT
                        && record.arriveAt() <= now)
                .sorted(Comparator.comparingLong(TransitRecord::arriveAt).thenComparing(TransitRecord::key))
                .limit(64).toList(), table.due(now, 64), where + " due");
        assertEquals(all.stream().filter(record -> record.acknowledged() && !record.stub()).toList(),
                table.acknowledgedWithPayload(), where + " acknowledged with payload");
    }
}
