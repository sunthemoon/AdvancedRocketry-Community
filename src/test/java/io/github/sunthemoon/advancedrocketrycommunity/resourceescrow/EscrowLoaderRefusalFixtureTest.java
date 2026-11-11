package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowLoaderRefusalFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path temporary;

    @Test void anUnbackedSyntheticIndexHoldsAnUnrelatedRocketBeforeCapacityAdmission() {
        var authority = authority();
        var receipts = receipts(authority, temporary.resolve("unbacked.dat"));
        try {
            var pending = pending(true);
            assertTrue(receipts.substituteForTests(index(true, pending)));
            assertNull(receipts.store());
            assertTrue(receipts.holds(UUID.randomUUID(), () -> { throw new AssertionError("Unexpected journal lookup"); }));
            assertEquals(EscrowLimits.OPERATIONS, authority.pendingCount());
            assertEquals(EscrowAdmissionAuthority.Result.SERVER_FULL, check(authority));
            pending.clear();
            assertTrue(receipts.substituteForTests(null));
            assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, receipts.state());
        } finally { receipts.stop(); }
        assertFalse(Files.exists(temporary.resolve("unbacked.dat")));
    }

    @Test void aBackedSyntheticIndexReachesEachSharedRefusalWithoutAHoldReservationOrWrite() throws Exception {
        for (boolean loaded : List.of(true, false)) {
            Path folder = Files.createDirectory(temporary.resolve(Boolean.toString(loaded)));
            Path file = folder.resolve("receipts.dat");
            var authority = authority();
            var receipts = receipts(authority, file);
            try {
                EscrowLoaderCutProbe.emptyBayForRefusal(receipts, file, 1);
                var backing = receipts.store();
                assertNotNull(backing);
                var baseline = backing.durable();
                assertEquals(0, baseline.writerGeneration());
                assertEquals(0, baseline.issuedThrough());
                assertTrue(baseline.receipts().isEmpty());
                assertFalse(backing.holdsWork());
                var pending = pending(loaded);
                assertTrue(receipts.substituteForTests(index(loaded, pending)));
                assertFalse(receipts.holds(UUID.randomUUID(), () -> { throw new AssertionError("Unexpected journal lookup"); }));
                assertEquals(loaded ? EscrowLimits.OPERATIONS : 0, authority.pendingCount());
                assertEquals(loaded ? EscrowAdmissionAuthority.Result.SERVER_FULL : EscrowAdmissionAuthority.Result.UNAVAILABLE,
                        check(authority));
                assertEquals(loaded ? EscrowLimits.OPERATIONS : 0, authority.pendingCount(), "the refusal reserved nothing");
                assertEquals(baseline, backing.durable());
                pending.clear();
                assertTrue(receipts.substituteForTests(null));
                assertSame(backing, receipts.store());
                assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, receipts.state());
                assertEquals(0, authority.pendingCount());
                assertFalse(backing.holdsWork());
                receipts.tick(100);
                assertEquals(baseline, backing.durable());
            } finally { receipts.stop(); }
            try (var files = Files.list(folder)) { assertEquals(0, files.count(), "no receipt or staging file was written"); }
        }
    }

    @Test void setupRefusesUnloadedExistingAndAlreadyBoundStoresWithoutChangingThem() throws Exception {
        for (var presence : List.of(CheckedSavedDataFile.Presence.PRESENT, CheckedSavedDataFile.Presence.UNKNOWN)) {
            var receipts = new BayReceipts(authority().register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
            Path file = temporary.resolve(presence.name() + ".dat");
            receipts.start(null, presence, file, 0);
            try {
                assertThrows(IllegalArgumentException.class, () -> EscrowLoaderCutProbe.emptyBayForRefusal(receipts, file, 1));
                assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, receipts.state());
                assertNull(receipts.store());
                assertFalse(Files.exists(file));
            } finally { receipts.stop(); }
        }
        Path file = temporary.resolve("existing.dat");
        var receipts = receipts(authority(), file);
        try {
            byte[] original = {1, 2, 3};
            Files.write(file, original);
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderCutProbe.emptyBayForRefusal(receipts, file, 1));
            assertArrayEquals(original, Files.readAllBytes(file));
            assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, receipts.state());
            assertNull(receipts.store());
        } finally { receipts.stop(); }
        Path absent = temporary.resolve("bound.dat");
        var bound = receipts(authority(), absent);
        try {
            EscrowLoaderCutProbe.emptyBayForRefusal(bound, absent, 1);
            var original = bound.store();
            assertThrows(IllegalArgumentException.class, () -> EscrowLoaderCutProbe.emptyBayForRefusal(bound, absent, 2));
            assertSame(original, bound.store());
            assertFalse(Files.exists(absent));
        } finally { bound.stop(); }
    }

    private static EscrowAdmissionAuthority authority() {
        var authority = new EscrowAdmissionAuthority();
        authority.registerIndex(EscrowAdmissionAuthority.Consumer.PROPULSION, () -> new EscrowAdmissionAuthority.Index(true, List.of()));
        return authority;
    }

    private static BayReceipts receipts(EscrowAdmissionAuthority authority, Path file) {
        var receipts = new BayReceipts(authority.register(EscrowAdmissionAuthority.Consumer.BAY), CheckedSavedDataFile::atomicMove);
        receipts.start(null, CheckedSavedDataFile.Presence.ABSENT, file, 0);
        return receipts;
    }

    private static ArrayList<EscrowAdmissionAuthority.Pending> pending(boolean full) {
        var pending = new ArrayList<EscrowAdmissionAuthority.Pending>();
        if (full) {
            for (int i = 0; i < EscrowLimits.OPERATIONS; i++) {
                pending.add(new EscrowAdmissionAuthority.Pending(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        EscrowAdmissionAuthority.Consumer.BAY));
            }
        }
        return pending;
    }

    private static EscrowAdmissionAuthority.Store index(boolean loaded, List<EscrowAdmissionAuthority.Pending> pending) {
        return new EscrowAdmissionAuthority.Store() {
            @Override public EscrowAdmissionAuthority.Index index() { return new EscrowAdmissionAuthority.Index(loaded, pending); }
            @Override public boolean holdsWork() { return false; }
        };
    }

    private static EscrowAdmissionAuthority.Result check(EscrowAdmissionAuthority authority) {
        return authority.checkReserve(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EscrowAdmissionAuthority.Consumer.PROPULSION);
    }
}
