package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegistryModel;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * ADR-066 section 3.3 against the real registry and access policy: the candidate set is the reverse index, not the
 * name-sorted 32-entry access list, so the nearest station is found even when its name sorts last and its cell is on
 * the negative side of the grid; a membership removal during the query starts a fresh pass without that station.
 */
final class SafetyReturnRegistryQueryTest {
    private static final StationAccessService ACCESS = new StationAccessService();

    @Test
    void theNearestStationOutsideTheThirtyTwoNamePrefixIsFound() {
        UUID player = UUID.randomUUID();
        StationRegistryModel registry = new StationRegistryModel();
        List<StationState> stations = stations(registry, player, 40);
        StationState last = stations.get(39);
        assertTrue(last.cell().x() < 0, "The fixture's nearest station is on the negative side of the grid");
        List<StationState> prefix = ACCESS.accessibleDestinations(registry.stations(), player, false);
        assertEquals(StationLimits.MAX_ACCESSIBLE_DESTINATIONS, prefix.size());
        assertFalse(prefix.stream().anyMatch(state -> state.stationId().equals(last.stationId())),
                "The nearest station is outside the name-sorted prefix");

        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(player, last.landingPad().x() + 100.5D, last.landingPad().z() + 100.5D);
        SafetyReturnQuery.Result result = finish(query, world(registry));
        assertEquals(last.stationId(),
                assertInstanceOf(SafetyReturnQuery.Station.class, result).target().stationId());
    }

    @Test
    void aMembershipRemovedDuringTheQueryIsNotUsed() {
        UUID player = UUID.randomUUID();
        StationRegistryModel registry = new StationRegistryModel();
        List<StationState> stations = stations(registry, player, 40);
        StationState last = stations.get(39);
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(player, last.landingPad().x() + 100.5D, last.landingPad().z() + 100.5D);
        SafetyReturnQuery.World world = world(registry);
        assertTrue(query.tick(world).isEmpty(), "Forty candidates take more than one turn");
        registry.removeMember(last.stationId(), player);
        SafetyReturnQuery.Result result = finish(query, world);
        UUID chosen = assertInstanceOf(SafetyReturnQuery.Station.class, result).target().stationId();
        assertFalse(chosen.equals(last.stationId()), "A removed membership authorized the return");
        for (StationState other : stations) {
            if (!other.stationId().equals(last.stationId())) {
                assertTrue(distance(other, last) >= distance(registry.find(chosen).orElseThrow(), last),
                        "A nearer authorized station was skipped");
            }
        }
    }

    @Test
    void anOperatorUsesEveryCommittedStation() {
        UUID operator = UUID.randomUUID();
        StationRegistryModel registry = new StationRegistryModel();
        List<StationState> stations = stations(registry, UUID.randomUUID(), 3);
        StationState target = stations.get(2);
        SafetyReturnQuery query = new SafetyReturnQuery();
        query.admit(operator, target.landingPad().x() + 0.5D, target.landingPad().z() + 0.5D);
        SafetyReturnQuery.World world = new RegistryWorld(registry, true);
        assertEquals(target.stationId(), assertInstanceOf(SafetyReturnQuery.Station.class, finish(query, world))
                .target().stationId());
        SafetyReturnQuery stranger = new SafetyReturnQuery();
        stranger.admit(operator, 0.5D, 0.5D);
        assertInstanceOf(SafetyReturnQuery.Overworld.class, finish(stranger, world(registry)),
                "Without operator rights the same player has no candidate");
    }

    /** Stations named so that index order is name order; the player is a member of each. */
    private static List<StationState> stations(StationRegistryModel registry, UUID member, int count) {
        List<StationState> stations = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            UUID id = UUID.randomUUID();
            registry.reserve(id, UUID.randomUUID(), String.format("Station %02d", index), ModIdentity.id("earth"), 0L);
            registry.commit(id);
            stations.add(registry.addMember(id, member));
        }
        return stations;
    }

    private static SafetyReturnQuery.Result finish(SafetyReturnQuery query, SafetyReturnQuery.World world) {
        for (int tick = 0; tick < 20; tick++) {
            List<SafetyReturnQuery.Result> results = query.tick(world);
            if (!results.isEmpty()) {
                assertEquals(1, results.size());
                return results.get(0);
            }
        }
        throw new AssertionError("The query did not finish");
    }

    private static SafetyReturnQuery.World world(StationRegistryModel registry) {
        return new RegistryWorld(registry, false);
    }

    private static double distance(StationState station, StationState from) {
        double dx = station.landingPad().x() - from.landingPad().x() - 100.0D;
        double dz = station.landingPad().z() - from.landingPad().z() - 100.0D;
        return dx * dx + dz * dz;
    }

    /** The service's view with every arrival loaded and safe. */
    private record RegistryWorld(StationRegistryModel registry, boolean operators) implements SafetyReturnQuery.World {
        @Override
        public boolean authorityAvailable() {
            return true;
        }

        @Override
        public long generation() {
            return registry.accessGeneration();
        }

        @Override
        public boolean operator(UUID player) {
            return operators;
        }

        @Override
        public Optional<UUID> next(UUID player, boolean operator, UUID after) {
            return registry.nextAccessCandidate(player, operator, after);
        }

        @Override
        public Optional<SafetyReturnQuery.Target> inspect(UUID player, boolean operator, UUID station) {
            return registry.find(station)
                    .filter(state -> ACCESS.allowed(state, player, operator, StationAccessAction.VISIT))
                    .map(state -> new SafetyReturnQuery.Target(state.stationId(), state.landingPad().x(),
                            state.landingPad().y(), state.landingPad().z()));
        }

        @Override
        public boolean probe(SafetyReturnQuery.Target target, int step) {
            return true;
        }
    }
}
