package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/** ADR-066 section 3.3: the reverse owner/member index, its updates, its cursor and its bounds. */
final class StationAccessIndexTest {
    @Test
    void committedCreateTeamAndRemoveChangesUpdateTheIndexAndItsGeneration() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        UUID heir = UUID.randomUUID();
        UUID station = UUID.randomUUID();
        long start = registry.accessGeneration();
        registry.reserve(station, owner, "Indexed", ModIdentity.id("earth"), 0L);
        assertEquals(start, registry.accessGeneration(), "A reservation grants no access");
        assertTrue(registry.nextAccessCandidate(owner, false, null).isEmpty());

        registry.commit(station);
        long created = registry.accessGeneration();
        assertTrue(created > start);
        assertEquals(Optional.of(station), registry.nextAccessCandidate(owner, false, null));

        registry.addMember(station, member);
        long added = registry.accessGeneration();
        assertTrue(added > created);
        assertEquals(Optional.of(station), registry.nextAccessCandidate(member, false, null));

        registry.invite(station, invitee);
        assertEquals(added, registry.accessGeneration(), "An invitation is not access");
        assertTrue(registry.nextAccessCandidate(invitee, false, null).isEmpty());
        registry.acceptInvitation(station, invitee);
        long accepted = registry.accessGeneration();
        assertTrue(accepted > added);
        assertEquals(Optional.of(station), registry.nextAccessCandidate(invitee, false, null));

        registry.removeMember(station, member);
        long removed = registry.accessGeneration();
        assertTrue(removed > accepted);
        assertTrue(registry.nextAccessCandidate(member, false, null).isEmpty());

        registry.transferOwnership(station, heir);
        long transferred = registry.accessGeneration();
        assertTrue(transferred > removed);
        assertEquals(Optional.of(station), registry.nextAccessCandidate(heir, false, null));
        assertEquals(Optional.of(station), registry.nextAccessCandidate(owner, false, null),
                "The previous owner stays as a member");
        assertEquals(3, registry.accessLinks());

        StationState current = registry.find(station).orElseThrow();
        registry.replaceChecked(current, current.withGravityMilli(500));
        assertEquals(transferred, registry.accessGeneration(), "A gravity update keeps the team");

        registry.delete(station);
        assertTrue(registry.accessGeneration() > transferred);
        assertEquals(0, registry.accessLinks());
        for (UUID player : List.of(owner, invitee, heir)) {
            assertTrue(registry.nextAccessCandidate(player, false, null).isEmpty());
        }
        assertTrue(registry.nextAccessCandidate(owner, true, null).isEmpty());
    }

    @Test
    void theCursorWalksUuidOrderForAPlayerAndEveryStationForAnOperator() {
        StationRegistryModel registry = new StationRegistryModel();
        UUID player = UUID.randomUUID();
        TreeSet<UUID> joined = new TreeSet<>();
        TreeSet<UUID> all = new TreeSet<>();
        for (int index = 0; index < 12; index++) {
            UUID station = UUID.randomUUID();
            registry.reserve(station, UUID.randomUUID(), "Station " + index, ModIdentity.id("earth"), index);
            registry.commit(station);
            all.add(station);
            if (index % 3 != 0) {
                registry.addMember(station, player);
                joined.add(station);
            }
        }
        assertEquals(new ArrayList<>(joined), walk(registry, player, false));
        assertEquals(new ArrayList<>(all), walk(registry, player, true));
        assertEquals(8, registry.accessCandidates(player, false));
        assertEquals(12, registry.accessCandidates(player, true));
        UUID middle = new ArrayList<>(joined).get(3);
        assertEquals(Optional.ofNullable(joined.higher(middle)), registry.nextAccessCandidate(player, false, middle),
                "The cursor resumes after the last inspected station");
        UUID absent = UUID.randomUUID();
        assertEquals(Optional.ofNullable(joined.higher(absent)), registry.nextAccessCandidate(player, false, absent),
                "A cursor whose station was deleted still resumes in UUID order");
    }

    @Test
    void theIndexIsRebuiltFromTheRecordsAtLoadAndUnavailableWhileBlocked() {
        StationRegistrySavedData data = new StationRegistrySavedData();
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID station = UUID.randomUUID();
        data.reserve(station, owner, "Persistent", ModIdentity.id("earth"), 0L);
        data.commit(station);
        data.addMember(station, member);
        UUID reserved = UUID.randomUUID();
        data.reserve(reserved, member, "Only reserved", ModIdentity.id("earth"), 1L);

        StationRegistrySavedData restored = StationRegistrySavedData.load(data.save(new CompoundTag()));
        assertTrue(restored.operational());
        assertTrue(restored.accessGeneration().isPresent());
        assertEquals(Optional.of(station), restored.nextAccessCandidate(owner, false, null));
        assertEquals(Optional.of(station), restored.nextAccessCandidate(member, false, null));
        assertTrue(restored.nextAccessCandidate(member, false, station).isEmpty(), "A reservation is not indexed");

        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 99);
        StationRegistrySavedData blocked = StationRegistrySavedData.load(future);
        assertFalse(blocked.operational());
        assertTrue(blocked.accessGeneration().isEmpty(), "A blocked registry has no generation");
        assertTrue(blocked.nextAccessCandidate(owner, true, null).isEmpty());
    }

    @Test
    void theIndexHoldsTheFullBoundAndRefusesMore() {
        StationAccessIndex index = new StationAccessIndex();
        for (int station = 0; station < StationLimits.MAX_STATIONS; station++) {
            List<UUID> members = new ArrayList<>();
            for (int member = 0; member < StationLimits.MAX_MEMBERS; member++) {
                members.add(new UUID(station + 1L, member + 1L));
            }
            index.add(state(station, new UUID(0L, station + 1L), members));
        }
        assertEquals(StationLimits.MAX_STATIONS, index.stations());
        assertEquals(StationAccessIndex.MAX_LINKS, index.links());
        assertEquals(135_168, StationAccessIndex.MAX_LINKS);
        assertThrows(IllegalStateException.class,
                () -> index.add(state(StationLimits.MAX_STATIONS, UUID.randomUUID(), List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> index.add(state(0, new UUID(0L, 1L), List.of())), "A duplicate station is refused");
    }

    @Test
    void replacingAnUnchangedTeamKeepsTheGeneration() {
        StationAccessIndex index = new StationAccessIndex();
        StationState station = state(0, UUID.randomUUID(), List.of(UUID.randomUUID()));
        index.add(station);
        long generation = index.generation();
        index.update(station.withGravityMilli(250));
        assertEquals(generation, index.generation());
        UUID joining = UUID.randomUUID();
        index.update(station.withMember(joining));
        assertEquals(generation + 1, index.generation());
        assertEquals(Optional.of(station.stationId()), index.next(joining, false, null));
        index.remove(station.stationId());
        index.remove(station.stationId());
        assertEquals(generation + 2, index.generation(), "Removing an unknown station changes nothing");
        assertEquals(0, index.players(), "The indexed team is removed, not the caller's stale copy");
        assertEquals(0, index.links());
        index.update(station.withMember(UUID.randomUUID()));
        assertEquals(generation + 2, index.generation(), "An unindexed station is not added by an update");
    }

    private static List<UUID> walk(StationRegistryModel registry, UUID player, boolean operator) {
        List<UUID> visited = new ArrayList<>();
        Optional<UUID> next = registry.nextAccessCandidate(player, operator, null);
        while (next.isPresent()) {
            visited.add(next.get());
            next = registry.nextAccessCandidate(player, operator, next.get());
        }
        return visited;
    }

    private static StationState state(int index, UUID owner, List<UUID> members) {
        StationGridCell cell = StationRegistryModel.spiralCell(index);
        return new StationState(StationLimits.STATE_SCHEMA_VERSION, new UUID(1L << 40, index + 1L), owner,
                "Bound " + index, cell, cell.region(), cell.landingPad(), ModIdentity.id("earth"), 0L,
                StationEnvironmentProfile.BASIC_SPACE, members, List.of());
    }
}
