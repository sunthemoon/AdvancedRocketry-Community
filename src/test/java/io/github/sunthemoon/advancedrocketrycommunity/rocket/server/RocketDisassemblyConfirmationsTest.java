package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RocketDisassemblyConfirmationsTest {
    private final UUID player = UUID.randomUUID();
    private final RocketDisassemblyConfirmations book = new RocketDisassemblyConfirmations();

    @Test
    void repeatedOfferDoesNotExtendExpiryAndCanOnlyBeConsumedOnce() {
        var quote = quote();
        var offer = book.offer(player, quote, 100).orElseThrow();
        assertEquals(offer, book.offer(player, quote, 299).orElseThrow());
        assertEquals(quote, book.take(player, offer.token(), 299).orElseThrow());
        assertTrue(book.take(player, offer.token(), 299).isEmpty());
    }

    @Test
    void exactExpiryAndBackwardTimeInvalidateConsent() {
        var first = book.offer(player, quote(), 100).orElseThrow();
        assertTrue(book.take(player, first.token(), 300).isEmpty());
        var second = book.offer(player, quote(), 400).orElseThrow();
        assertTrue(book.take(player, second.token(), 399).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> book.offer(player, quote(), -1));
    }

    @Test
    void wrongPlayerOrTokenCannotUseOrCancelAnotherOffer() {
        var offer = book.offer(player, quote(), 0).orElseThrow();
        assertTrue(book.take(UUID.randomUUID(), offer.token(), 1).isEmpty());
        assertTrue(book.take(player, UUID.randomUUID(), 1).isEmpty());
        assertEquals(offer.quote(), book.take(player, offer.token(), 2).orElseThrow());
    }

    @Test
    void changedQuoteReplacesRatherThanAccumulatesConsent() {
        var first = book.offer(player, quote(), 0).orElseThrow();
        var second = book.offer(player, quote(), 1).orElseThrow();
        assertNotEquals(first.token(), second.token());
        assertTrue(book.take(player, first.token(), 2).isEmpty());
        assertEquals(second.quote(), book.take(player, second.token(), 2).orElseThrow());
    }

    @Test
    void boundRejectsNewPlayersButAllowsReplacementAndReclaimsExpiredOffers() {
        var first = book.offer(player, quote(), 0).orElseThrow();
        for (int index = 1; index < RocketDisassemblyConfirmations.MAX_PENDING; index++) {
            assertTrue(book.offer(UUID.randomUUID(), quote(), 0).isPresent());
        }
        assertTrue(book.offer(UUID.randomUUID(), quote(), 1).isEmpty());
        assertTrue(book.offer(player, quote(), 1).isPresent());
        assertTrue(book.take(player, first.token(), 1).isEmpty());
        assertTrue(book.offer(UUID.randomUUID(), quote(), 200).isPresent());
    }

    @Test
    void logoutAndManagerClearInvalidateOnlyTheirOwnedConsent() {
        UUID other = UUID.randomUUID();
        var first = book.offer(player, quote(), 0).orElseThrow();
        var second = book.offer(other, quote(), 0).orElseThrow();
        book.forget(player);
        assertTrue(book.take(player, first.token(), 1).isEmpty());
        assertTrue(book.take(other, second.token(), 1).isPresent());
        var third = book.offer(player, quote(), 2).orElseThrow();
        book.clear();
        assertTrue(book.take(player, third.token(), 2).isEmpty());
    }

    @Test
    void bindingIncludesOwnerSnapshotFlightAndPositionWithoutMutableWorldReferences() {
        var q = quote();
        var changedFlight = q.flight().withFuel(q.flight().fuel().fill(1).state(), 1);
        assertNotEquals(q, new RocketDisassemblyConfirmations.Quote(q.entityId(), q.ownerId(), q.snapshotId(),
                q.snapshotHash(), changedFlight, q.x(), q.y(), q.z()));
        assertNotEquals(q, new RocketDisassemblyConfirmations.Quote(q.entityId(), UUID.randomUUID(), q.snapshotId(),
                q.snapshotHash(), q.flight(), q.x(), q.y(), q.z()));
        assertNotEquals(q, new RocketDisassemblyConfirmations.Quote(q.entityId(), q.ownerId(), UUID.randomUUID(),
                q.snapshotHash(), q.flight(), q.x(), q.y(), q.z()));
        assertNotEquals(q, new RocketDisassemblyConfirmations.Quote(q.entityId(), q.ownerId(), q.snapshotId(),
                "b".repeat(64), q.flight(), q.x(), q.y(), q.z()));
        assertNotEquals(q, new RocketDisassemblyConfirmations.Quote(q.entityId(), q.ownerId(), q.snapshotId(),
                q.snapshotHash(), q.flight(), q.x() + 1, q.y(), q.z()));
    }

    @Test
    void quoteRejectsMalformedHashEmptyFuelAndNonfinitePosition() {
        var q = quote();
        assertThrows(IllegalArgumentException.class, () -> new RocketDisassemblyConfirmations.Quote(
                q.entityId(), q.ownerId(), q.snapshotId(), "invalid", q.flight(), 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RocketDisassemblyConfirmations.Quote(
                q.entityId(), q.ownerId(), q.snapshotId(), q.snapshotHash(), q.flight(), Double.NaN, 0, 0));
        var empty = RocketFlightData.initial(UUID.randomUUID(), 1000, 1,
                RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(),
                new RocketPosition(0, 80, 0), 0);
        assertThrows(IllegalArgumentException.class, () -> new RocketDisassemblyConfirmations.Quote(
                q.entityId(), q.ownerId(), q.snapshotId(), q.snapshotHash(), empty, 0, 0, 0));
    }

    private RocketDisassemblyConfirmations.Quote quote() {
        var empty = RocketFlightData.initial(UUID.randomUUID(), 1000, 1,
                RocketFlightPlanner.EARTH.bodyId(), RocketFlightPlanner.EARTH.dimensionId(),
                new RocketPosition(0, 80, 0), 0);
        return new RocketDisassemblyConfirmations.Quote(UUID.randomUUID(), player, UUID.randomUUID(),
                "a".repeat(64), empty.withFuel(empty.fuel().fill(256).state(), 0), .5, 80, .5);
    }
}
