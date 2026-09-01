package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class StationRegistryModelTest {
    @Test
    void tenCommittedStationsAreUniqueAndDoNotOverlap() {
        StationRegistryModel registry = new StationRegistryModel();
        List<StationState> states = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            UUID id = UUID.nameUUIDFromBytes(("station-" + index).getBytes());
            registry.reserve(id, UUID.randomUUID(), "Station " + index,
                    ModIdentity.id("earth"), index);
            states.add(registry.commit(id));
        }

        assertEquals(10, registry.stations().size());
        assertEquals(10, states.stream().map(StationState::cell).distinct().count());
        for (int first = 0; first < states.size(); first++) {
            for (int second = first + 1; second < states.size(); second++) {
                assertFalse(states.get(first).region().overlaps(states.get(second).region()));
            }
        }
    }

    @Test
    void releasedReservationCanBeReusedButCommittedCellCannot() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID firstId = UUID.randomUUID();
        StationGridCell firstCell = registry.reserve(firstId, UUID.randomUUID(), "First",
                ModIdentity.id("earth"), 1).cell();
        assertTrue(registry.release(firstId));

        UUID secondId = UUID.randomUUID();
        StationGridCell reused = registry.reserve(secondId, UUID.randomUUID(), "Second",
                ModIdentity.id("earth"), 2).cell();
        assertEquals(firstCell, reused);
        registry.commit(secondId);

        UUID thirdId = UUID.randomUUID();
        StationGridCell next = registry.reserve(thirdId, UUID.randomUUID(), "Third",
                ModIdentity.id("earth"), 3).cell();
        assertNotEquals(reused, next);
    }

    @Test
    void concurrentReservationProducesUniqueCells() throws Exception {
        StationRegistryModel registry = new StationRegistryModel();
        int count = 32;
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(count);
        List<UUID> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            ids.add(UUID.randomUUID());
        }
        for (int index = 0; index < count; index++) {
            int value = index;
            executor.submit(() -> {
                ready.countDown();
                start.await();
                registry.reserve(ids.get(value), UUID.randomUUID(), "Concurrent " + value,
                        ModIdentity.id("earth"), value);
                return null;
            });
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));

        Set<StationGridCell> cells = new HashSet<>();
        registry.reservations().forEach(reservation -> assertTrue(cells.add(reservation.cell())));
        assertEquals(count, cells.size());
    }

    @Test
    void duplicateIdentityAndCellFailClosedDuringRestore() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID id = UUID.randomUUID();
        StationReservation reservation = new StationReservation(id, UUID.randomUUID(), "One",
                new StationGridCell(0, 0), ModIdentity.id("earth"), 0);
        registry.restoreReservation(reservation);
        assertThrows(IllegalArgumentException.class, () -> registry.restoreReservation(reservation));
        assertThrows(IllegalArgumentException.class, () -> registry.restoreReservation(
                new StationReservation(UUID.randomUUID(), UUID.randomUUID(), "Two",
                        new StationGridCell(0, 0), ModIdentity.id("earth"), 0)
        ));
    }

    @Test
    void indexedLookupReturnsOnlyCommittedRegion() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID id = UUID.randomUUID();
        StationReservation reservation = registry.reserve(id, UUID.randomUUID(), "Lookup",
                ModIdentity.id("earth"), 0);
        assertTrue(registry.findAt(reservation.landingPad().x(), reservation.landingPad().z()).isEmpty());
        StationState state = registry.commit(id);
        assertEquals(id, registry.findAt(state.landingPad().x(), state.landingPad().z())
                .orElseThrow().stationId());
        assertTrue(registry.findAt(state.region().maximumX() + 1, state.landingPad().z()).isEmpty());
    }
}
