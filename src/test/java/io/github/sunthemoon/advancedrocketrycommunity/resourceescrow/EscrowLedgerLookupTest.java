package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W329 (R393): the ledger lookup's classification of a missing root, over real admission delegates. Only a proven
 * absence keeps the no-ledger answers after startup; before the startup classification nothing reads the delegate.
 * W328F: off the server thread or without an Overworld, nothing touches storage.
 */
final class EscrowLedgerLookupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static final EscrowAdmissionAuthority.Store IDLE_STORE = new EscrowAdmissionAuthority.Store() {
        @Override public EscrowAdmissionAuthority.Index index() { return new EscrowAdmissionAuthority.Index(true, List.of()); }
        @Override public boolean holdsWork() { return false; }
    };

    private static EscrowAdmissionAuthority.Delegate delegate() {
        return new EscrowAdmissionAuthority().register(EscrowAdmissionAuthority.Consumer.PROPULSION);
    }

    @Test void beforeTheStartupClassificationAMissingRootIsUnclassifiedAndTheDelegateIsNotRead() {
        assertEquals(EscrowLedgerLookup.State.UNCLASSIFIED, EscrowLedgerLookup.missing(false, () -> {
            throw new AssertionError("the delegate was read before the startup classification");
        }));
        assertFalse(new EscrowLedgerLookup(EscrowLedgerLookup.State.UNCLASSIFIED, null).notLoaded());
    }

    @Test void afterTheClassificationOnlyANoFileDelegateKeepsTheNoLedgerAnswer() {
        var unloaded = delegate();
        assertEquals(EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.missing(true, unloaded::state));

        var absent = delegate(); absent.noFile();
        assertEquals(EscrowLedgerLookup.State.NO_FILE, EscrowLedgerLookup.missing(true, absent::state));
        assertFalse(new EscrowLedgerLookup(EscrowLedgerLookup.State.NO_FILE, null).notLoaded());

        // A store attached while storage holds no root is not a proven absence.
        var attached = delegate(); assertTrue(attached.retarget(IDLE_STORE));
        assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, attached.state());
        assertEquals(EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.missing(true, attached::state));
        assertTrue(new EscrowLedgerLookup(EscrowLedgerLookup.State.NOT_LOADED, null).notLoaded());
    }

    /** W328F (review check 5): off the server thread, or without an Overworld, the lookup never touches storage. */
    @Test void offTheServerThreadOrWithoutAnOverworldTheLookupNeverTouchesStorage() {
        java.util.function.Supplier<ResourceEscrowSavedData> storage = () -> {
            throw new AssertionError("vanilla storage was touched");
        };
        java.util.function.Supplier<EscrowLedgerLookup.State> runtime = () -> {
            throw new AssertionError("the runtime's classification was read");
        };
        for (boolean overworld : new boolean[] {true, false}) {
            var offThread = EscrowLedgerLookup.of(false, overworld, storage, runtime);
            assertEquals(EscrowLedgerLookup.State.NOT_LOADED, offThread.state());
            assertNull(offThread.data());
        }
        assertEquals(EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.of(true, false, storage, runtime).state());

        // On the server thread with an Overworld: storage first, and the runtime only when it finds no root.
        var data = ResourceEscrowSavedData.create();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var loaded = EscrowLedgerLookup.of(true, true, () -> { reads.incrementAndGet(); return data; }, runtime);
        assertEquals(EscrowLedgerLookup.State.LOADED, loaded.state());
        assertSame(data, loaded.data());
        assertEquals(1, reads.get());
        for (var state : List.of(EscrowLedgerLookup.State.NO_FILE, EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.State.UNCLASSIFIED)) {
            assertEquals(state, EscrowLedgerLookup.of(true, true, () -> { reads.incrementAndGet(); return null; }, () -> state).state());
        }
        assertEquals(4, reads.get());
    }

    @Test void onlyALoadedLookupCarriesTheLedger() {
        var data = ResourceEscrowSavedData.create();
        assertSame(data, new EscrowLedgerLookup(EscrowLedgerLookup.State.LOADED, data).data());
        assertFalse(new EscrowLedgerLookup(EscrowLedgerLookup.State.LOADED, data).notLoaded());
        assertThrows(IllegalArgumentException.class, () -> new EscrowLedgerLookup(EscrowLedgerLookup.State.LOADED, null));
        for (var state : List.of(EscrowLedgerLookup.State.NO_FILE, EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.State.UNCLASSIFIED)) {
            assertThrows(IllegalArgumentException.class, () -> new EscrowLedgerLookup(state, data), state::name);
        }
        assertThrows(NullPointerException.class, () -> new EscrowLedgerLookup(null, null));
    }

    @Test void theGameTestSeamReturnsToNotLoadedRefusesAdmissionAndRestoresEitherWay() {
        var authority = new EscrowAdmissionAuthority();
        var propulsion = authority.register(EscrowAdmissionAuthority.Consumer.PROPULSION);
        authority.register(EscrowAdmissionAuthority.Consumer.BAY).noFile();
        assertTrue(propulsion.retarget(IDLE_STORE));
        var op = EscrowTestFixtures.id(1); var owner = EscrowTestFixtures.id(2); var rocket = EscrowTestFixtures.id(3);
        assertEquals(EscrowAdmissionAuthority.Result.RESERVED,
                authority.checkReserve(op, owner, rocket, EscrowAdmissionAuthority.Consumer.PROPULSION));

        assertTrue(propulsion.unloadForTests());
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NOT_LOADED, propulsion.state());
        assertEquals(EscrowLedgerLookup.State.NOT_LOADED, EscrowLedgerLookup.missing(true, propulsion::state));
        for (var consumer : EscrowAdmissionAuthority.Consumer.values()) {
            assertEquals(EscrowAdmissionAuthority.Result.UNAVAILABLE, authority.checkReserve(op, owner, rocket, consumer), consumer::name);
        }

        assertTrue(propulsion.retarget(null));
        assertEquals(EscrowAdmissionAuthority.Delegate.State.NO_FILE, propulsion.state());
        assertTrue(propulsion.unloadForTests());
        assertTrue(propulsion.retarget(IDLE_STORE));
        assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, propulsion.state());
        assertEquals(EscrowAdmissionAuthority.Result.RESERVED,
                authority.checkReserve(op, owner, rocket, EscrowAdmissionAuthority.Consumer.PROPULSION));
    }

    @Test void theGameTestSeamRefusesWhileTheAttachedStoreHoldsWork() {
        var propulsion = delegate();
        var busy = new EscrowAdmissionAuthority.Store() {
            @Override public EscrowAdmissionAuthority.Index index() { return new EscrowAdmissionAuthority.Index(true, List.of()); }
            @Override public boolean holdsWork() { return true; }
        };
        assertTrue(propulsion.retarget(busy));
        assertFalse(propulsion.unloadForTests());
        assertEquals(EscrowAdmissionAuthority.Delegate.State.LOADED, propulsion.state());
    }
}
