package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** ADR-054 sections 6, 9 and 10 and review R3-M2 on the in-memory endgame root. */
class EndgameRootTest {
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "laser_target");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final Predicate<UUID> NONE_PINNED = id -> false;

    /** Review C11R-L3: a Level key a record cannot hold is refused with a code, not thrown on the server tick. */
    @Test
    void aLevelKeyOverTheRecordBoundIsRefused() {
        EndgameRoot root = EndgameRoot.create();
        ResourceLocation longLevel = ResourceLocation.tryBuild("datapack", "d".repeat(130));
        UUID id = new UUID(0L, 77L);
        assertEquals(EndgameCode.TARGET_OUT_OF_BOUNDS, root.register(id, KIND, OWNER, longLevel, 0L, false, 64, 64));
        assertTrue(root.endpoint(id).isEmpty() && !root.changedSinceEpoch());
        ResourceLocation longKind = ResourceLocation.tryBuild("datapack", "k".repeat(70));
        assertEquals(EndgameCode.TARGET_OUT_OF_BOUNDS, root.register(id, longKind, OWNER, LEVEL, 0L, false, 64, 64));
        assertEquals(EndgameCode.OK, root.register(id, KIND, OWNER, LEVEL, 0L, false, 64, 64));
    }

    @Test
    void registrationIsIdempotentAtItsPositionAndConflictsElsewhere() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 7L);
        assertEquals(EndgameCode.OK, register(root, id, OWNER, 10L, false));
        assertEquals(EndgameCode.OK, register(root, id, OWNER, 10L, false));
        assertEquals(EndgameCode.ENDPOINT_POSITION_CONFLICT, register(root, id, OWNER, 11L, false));
        assertEquals(1, root.places());
    }

    @Test
    void retiredAndFrozenIdsNeverRegister() {
        EndgameRoot root = EndgameRoot.create();
        UUID frozen = new UUID(0L, 1L);
        assertEquals(EndgameCode.ENDPOINT_RETIRED, register(root, frozen, OWNER, 1L, true));
        UUID removed = new UUID(0L, 2L);
        register(root, removed, OWNER, 2L, false);
        assertTrue(root.remove(removed));
        assertEquals(EndgameCode.ENDPOINT_RETIRED, register(root, removed, OWNER, 2L, false), "young tombstone");
        assertTrue(root.settle(removed, NONE_PINNED).ok());
        assertEquals(EndgameCode.ENDPOINT_RETIRED, register(root, removed, OWNER, 2L, false), "settled tombstone");
        UUID missing = new UUID(0L, 3L);
        register(root, missing, OWNER, 3L, false);
        assertTrue(root.markMissing(missing));
        assertTrue(root.retired(missing));
        assertEquals(EndgameCode.ENDPOINT_RETIRED, register(root, missing, OWNER, 3L, false));
    }

    @Test
    void youngTombstonesTakeEndpointPlacesUntilTheySettle() {
        EndgameRoot root = EndgameRoot.create();
        for (int i = 0; i < 4; i++) {
            assertEquals(EndgameCode.OK, root.register(new UUID(0L, i), KIND, OWNER, LEVEL, i, false, 100, 4));
        }
        assertEquals(EndgameCode.ENDPOINT_LIMIT, root.register(new UUID(0L, 9L), KIND, OWNER, LEVEL, 9L, false, 100, 4));
        assertTrue(root.remove(new UUID(0L, 0L)));
        assertEquals(EndgameCode.ENDPOINT_LIMIT, root.register(new UUID(0L, 9L), KIND, OWNER, LEVEL, 9L, false, 100, 4),
                "a young tombstone still holds its place (review R3-M2)");
        assertTrue(root.settle(new UUID(0L, 0L), NONE_PINNED).ok());
        assertEquals(EndgameCode.OK, root.register(new UUID(0L, 9L), KIND, OWNER, LEVEL, 9L, false, 100, 4));
        assertEquals(EndgameCode.ENDPOINT_LIMIT,
                root.register(new UUID(0L, 10L), KIND, new UUID(5L, 5L), LEVEL, 10L, false, 4, 64), "global limit");
    }

    @Test
    void aRegistrationIsDurableOnlyAfterTheNextSuccessfulWrite() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 1L);
        register(root, id, OWNER, 1L, false);
        assertFalse(root.registrationDurable(id));
        assertEquals(2L, root.epochToWrite());
        root.markPersisted();
        assertEquals(2L, root.saveEpoch());
        assertTrue(root.registrationDurable(id));
        assertEquals(2L, root.epochToWrite(), "an unchanged root rewrites the same epoch");
        root.markPersisted();
        assertEquals(2L, root.saveEpoch());
    }

    @Test
    void forgettingMissingRecordsIsTheOwnersOrAnOperatorsAndSettlesTheTombstone() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 1L);
        register(root, id, OWNER, 1L, false);
        assertEquals(EndgameCode.ENDPOINT_NOT_FOUND, root.forget(id, OWNER, false, NONE_PINNED).code(), "not MISSING");
        root.markMissing(id);
        assertEquals(1, root.places(OWNER), "a MISSING record counts until forgotten");
        assertEquals(EndgameCode.UNAUTHORIZED, root.forget(id, new UUID(9L, 9L), false, NONE_PINNED).code());
        assertTrue(root.forget(id, OWNER, false, NONE_PINNED).ok());
        assertEquals(0, root.places(OWNER));
        assertTrue(root.tombstone(id).orElseThrow() instanceof Tombstone.Settled);
    }

    @Test
    void retiringALostEndpointSettlesAtOnce() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 1L);
        register(root, id, OWNER, 1L, false);
        assertTrue(root.retireLost(id, NONE_PINNED).ok());
        assertTrue(root.tombstone(id).orElseThrow() instanceof Tombstone.Settled, "review R4-L1");
        assertEquals(EndgameCode.ENDPOINT_NOT_FOUND, root.retireLost(id, NONE_PINNED).code());
    }

    @Test
    void eachOwnerKeepsAtMostTwoHundredFiftySixSettledUnpinnedTombstones() {
        EndgameRoot root = EndgameRoot.create();
        UUID first = new UUID(OWNER.getMostSignificantBits(), 0L);
        Set<UUID> pinned = new HashSet<>(Set.of(first));
        for (int i = 0; i <= 256; i++) {
            assertTrue(churnChange(root, OWNER, i, pinned::contains).evicted().isEmpty(), "within the cap: " + i);
        }
        assertEquals(257, root.settledTombstones().size(), "the pinned one does not count toward the cap");
        UUID oldestUnpinned = new UUID(OWNER.getMostSignificantBits(), 1L);
        EndgameRoot.Change change = churnChange(root, OWNER, 257, pinned::contains);
        assertEquals(List.of(oldestUnpinned), change.evicted(), "a pinned tombstone is skipped, the next oldest goes");
        assertTrue(root.tombstone(first).isPresent());
        assertEquals(257, root.settledTombstones().size());
    }

    @Test
    void theServerKeepsAtMostEightThousandOneHundredNinetyTwoSettledUnpinnedTombstones() {
        EndgameRoot root = EndgameRoot.create();
        int owners = 33;
        int each = 249;
        UUID firstEver = null;
        for (int o = 0; o < owners; o++) {
            UUID owner = new UUID(100L + o, 0L);
            for (int i = 0; i < each; i++) {
                UUID id = churn(root, owner, i);
                firstEver = firstEver == null ? id : firstEver;
            }
        }
        assertEquals(8_192, root.settledTombstones().size(), "33 x 249 = 8,217 minus 25 evicted");
        assertTrue(root.tombstone(firstEver).isEmpty(), "the server's oldest went first");
    }

    @Test
    void housekeepingRemovesOnlyReadBackTombstonesAndOnlyAboveFourThousandNinetySix() {
        EndgameRoot root = EndgameRoot.create();
        for (int o = 0; o < 17; o++) {
            for (int i = 0; i < 245; i++) {
                churn(root, new UUID(200L + o, 0L), i);
            }
        }
        int total = root.settledTombstones().size();
        assertEquals(17 * 245, total);
        assertTrue(root.housekeep(id -> false, NONE_PINNED).isEmpty(), "nothing read back yet");
        List<UUID> evicted = root.housekeep(id -> true, NONE_PINNED);
        assertEquals(total - EndgameLimits.TOMBSTONE_HOUSEKEEPING_THRESHOLD, evicted.size());
        assertEquals(EndgameLimits.TOMBSTONE_HOUSEKEEPING_THRESHOLD, root.settledTombstones().size());
    }

    @Test
    void operatorsEvictOneOwnersSettledTombstones() {
        EndgameRoot root = EndgameRoot.create();
        UUID other = new UUID(2L, 2L);
        churn(root, OWNER, 0);
        churn(root, OWNER, 1);
        churn(root, other, 0);
        UUID young = new UUID(OWNER.getMostSignificantBits(), 50L);
        register(root, young, OWNER, 50L, false);
        root.remove(young);
        assertEquals(2, root.evictOwner(OWNER, NONE_PINNED).size());
        assertEquals(1, root.settledTombstones().size());
        assertTrue(root.tombstone(young).isPresent(), "young tombstones are not evicted by the command");
    }

    @Test
    void zonesAreUniqueBoundedAndAccounted() {
        EndgameRoot root = EndgameRoot.create();
        ProtectedZone spawn = ProtectedZone.of("spawn", LEVEL, 0, 0, 10, 10, List.of());
        assertEquals(EndgameCode.OK, root.addZone(spawn, 2));
        assertEquals(EndgameCode.ZONE_EXISTS, root.addZone(spawn, 2));
        assertEquals(EndgameCode.OK, root.addZone(ProtectedZone.of("b", LEVEL, 0, 0, 1, 1, List.of()), 2));
        assertEquals(EndgameCode.ZONE_LIMIT, root.addZone(ProtectedZone.of("c", LEVEL, 0, 0, 1, 1, List.of()), 2));
        assertEquals(2L * EndgameLimits.ZONE_RECORD_BYTES, root.accountedBytes());
        assertEquals(EndgameCode.OK, root.removeZone("spawn"));
        assertEquals(EndgameCode.ZONE_NOT_FOUND, root.removeZone("spawn"));
    }

    @Test
    void accountingCountsEndpointPlacesSettledTombstonesAndZones() {
        EndgameRoot root = EndgameRoot.create();
        register(root, new UUID(0L, 1L), OWNER, 1L, false);
        UUID young = new UUID(0L, 2L);
        register(root, young, OWNER, 2L, false);
        root.remove(young);
        churn(root, OWNER, 3);
        assertEquals(2L * EndgameLimits.ENDPOINT_RECORD_BYTES + EndgameLimits.TOMBSTONE_RECORD_BYTES,
                root.accountedBytes());
    }

    @Test
    void anOperatorReassignmentFollowsTheNewOwnersLimit() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 1L);
        register(root, id, OWNER, 1L, false);
        UUID next = new UUID(3L, 3L);
        assertEquals(EndgameCode.OK, root.reassign(id, next, 64));
        assertEquals(next, root.endpoint(id).orElseThrow().owner());
        UUID full = new UUID(0L, 2L);
        root.register(full, KIND, OWNER, LEVEL, 2L, false, 100, 64);
        assertEquals(EndgameCode.ENDPOINT_LIMIT, root.reassign(full, next, 1));
        assertEquals(EndgameCode.ENDPOINT_NOT_FOUND, root.reassign(new UUID(0L, 99L), next, 64));
    }

    private static EndgameCode register(EndgameRoot root, UUID id, UUID owner, long pos, boolean frozen) {
        return root.register(id, KIND, owner, LEVEL, pos, frozen, EndgameLimits.MAX_ENDPOINTS,
                EndgameLimits.MAX_ENDPOINTS_PER_OWNER);
    }

    /** Register, remove and settle one endpoint of {@code owner}; returns its ID. */
    private static UUID churn(EndgameRoot root, UUID owner, int index) {
        return churnChange(root, owner, index, NONE_PINNED).code() == EndgameCode.OK
                ? new UUID(owner.getMostSignificantBits(), index) : null;
    }

    private static EndgameRoot.Change churnChange(EndgameRoot root, UUID owner, int index, Predicate<UUID> pinned) {
        UUID id = new UUID(owner.getMostSignificantBits(), index);
        assertEquals(EndgameCode.OK, register(root, id, owner, index, false));
        assertTrue(root.remove(id));
        return root.settle(id, pinned);
    }
}
