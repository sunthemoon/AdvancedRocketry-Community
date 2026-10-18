package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketOperationLedger;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry.Release;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry.State;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W90b (R21 item 4, R22 amendments 1 to 4): the warm-up state machine with a recording ticket sink and a fake clock. */
class RocketWarmupRegistryTest {
    private static final ResourceLocation EARTH = ResourceLocation.tryParse("minecraft:overworld");
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");
    private static final ResourceLocation MARS = ResourceLocation.tryParse("advancedrocketrycommunity:mars");
    private static final Object SURFACE = "moon surface";
    private static final RocketWarmupSettings DEFAULTS = RocketWarmupSettings.DEFAULTS;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static RocketStructureSnapshot snapshot(ResourceLocation level, int x, int z) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        var blocks = List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron), new RocketBlock(new RocketPosition(0, 1, 0), iron));
        return RocketStructureSnapshot.create(UUID.randomUUID(), level, new RocketPosition(x, 80, z), blocks, List.of(),
                new RocketStats(blocks.size(), 100, 1_000, 2_000, 1, 0, 1, 0), 0);
    }

    /** A DistanceManager stand-in: equal by Level, column and key; removing an absent ticket does nothing. */
    static final class Tickets implements RocketWarmupRegistry.Tickets {
        final Set<ResourceLocation> levels = new HashSet<>(Set.of(EARTH, MOON, MARS));
        final Map<ResourceLocation, Map<ChunkPos, Set<UUID>>> held = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        @Override public boolean present(ResourceLocation level) { return levels.contains(level); }
        @Override public void add(ResourceLocation level, ChunkPos column, UUID logical) {
            assertTrue(present(level), "Ticket added to an absent Level"); calls.add("add " + level + " " + column);
            held.computeIfAbsent(level, ignored -> new HashMap<>()).computeIfAbsent(column, ignored -> new HashSet<>()).add(logical);
        }
        @Override public void remove(ResourceLocation level, ChunkPos column, UUID logical) {
            assertTrue(present(level), "Ticket removed from an absent Level"); calls.add("remove " + level + " " + column);
            var columns = held.get(level); if (columns == null || !columns.containsKey(column)) { return; }
            columns.get(column).remove(logical);
            if (columns.get(column).isEmpty()) { columns.remove(column); }
        }
        Set<ChunkPos> columns(ResourceLocation level, UUID logical) {
            var found = new HashSet<ChunkPos>();
            held.getOrDefault(level, Map.of()).forEach((column, keys) -> { if (keys.contains(logical)) { found.add(column); } });
            return found;
        }
        int count(UUID logical) {
            return (int) held.values().stream().flatMap(columns -> columns.values().stream()).filter(keys -> keys.contains(logical)).count();
        }
    }

    /** A probe whose facts the test sets. */
    static final class Probe implements RocketWarmupRegistry.Probe {
        final Set<UUID> online = new HashSet<>(), removed = new HashSet<>(), ready = new HashSet<>();
        final List<UUID> messages = new ArrayList<>();
        @Override public boolean online(UUID player) { return online.contains(player); }
        @Override public boolean sourcePresent(RocketWarmupRegistry.Warmup warmup) { return !removed.contains(warmup.sourceEntity()); }
        @Override public boolean ready(RocketWarmupRegistry.Warmup warmup) { return ready.contains(warmup.logical()); }
        @Override public void destinationReady(UUID player) { messages.add(player); }
    }

    final Tickets tickets = new Tickets();
    final List<String> log = new ArrayList<>();
    final RocketWarmupRegistry registry = new RocketWarmupRegistry(tickets, log::add);

    /** One rocket: its logical ID, source entity and source snapshot stay fixed across its requests. */
    final class Rocket {
        final UUID logical = UUID.randomUUID(), entity = UUID.randomUUID(), player;
        final RocketStructureSnapshot source = snapshot(EARTH, 0, 0);
        Rocket(UUID player) { this.player = player; }
        RocketWarmupRegistry.Request request(UUID id, RocketStructureSnapshot destination) {
            return new RocketWarmupRegistry.Request(logical, player, id, EARTH, entity, source, SURFACE, destination);
        }
        RocketWarmupRegistry.Decision decide(RocketStructureSnapshot destination, boolean ready, long now) {
            return registry.decide(request(UUID.randomUUID(), destination), true, () -> ready, now, DEFAULTS);
        }
    }

    /** A border-crossing pad: origin at x and z = 15 + 64n gives 2 x 2 columns. */
    static RocketStructureSnapshot pad(ResourceLocation level, int index) { return snapshot(level, 15 + 64 * index, 15); }

    @Test void aColdStartAddsExactlyTheCheckedSetOnceInItsOwnLevel() {
        var rocket = new Rocket(null); var destination = pad(MOON, 0);
        var decision = rocket.decide(destination, false, 100);
        assertEquals(Outcome.LOADING, decision.outcome());
        assertEquals(Set.copyOf(RocketDiscoveryColumns.of(destination).columns()), tickets.columns(MOON, rocket.logical));
        assertEquals(4, tickets.calls.size()); assertEquals(4, tickets.count(rocket.logical));
        var warmup = registry.warmup(rocket.logical).orElseThrow();
        assertEquals(State.WARMING, warmup.state()); assertEquals(100, warmup.started()); assertEquals(600, warmup.lifetime());
        assertEquals(List.of(RocketRegion.fromSnapshot(destination)), registry.reservations(UUID.randomUUID()));
        assertTrue(log.get(0).startsWith("ARCE_GOVERNED_WARMUP event=STARTED logical=" + rocket.logical));
        assertTrue(log.get(0).endsWith(" transfer=none level=" + MOON + " columns=4 age=0 reason=none"), log.get(0));
    }

    @Test void repeatedRequestsMakeNoTicketCallAndKeepTheStartTickAndTheStartingRequest() {
        var rocket = new Rocket(UUID.randomUUID()); var first = UUID.randomUUID();
        assertEquals(Outcome.LOADING, registry.decide(rocket.request(first, pad(MOON, 0)), true, () -> false, 10, DEFAULTS).outcome());
        for (int i = 1; i <= 5; i++) {
            // Each relaunch selects the same pad afresh: a new snapshot of the same region.
            assertEquals(Outcome.LOADING, rocket.decide(pad(MOON, 0), false, 10 + 25L * i).outcome());
        }
        assertEquals(4, tickets.calls.size());
        var warmup = registry.warmup(rocket.logical).orElseThrow();
        assertEquals(10, warmup.started()); assertEquals(first, warmup.request()); assertEquals(1, log.size());
    }

    @Test void aReadyDestinationStartsAndBindsOnAcceptanceOrIsReleasedOnRefusal() {
        var accepted = new Rocket(null);
        var decision = accepted.decide(pad(MOON, 0), true, 5);
        assertEquals(Outcome.PROCEED, decision.outcome()); assertTrue(decision.bindable());
        registry.afterBegin(decision, true, 5);
        var bound = registry.warmup(accepted.logical).orElseThrow();
        assertEquals(State.BOUND, bound.state()); assertNotNull(bound.transfer());
        assertEquals(bound, registry.boundTo(bound.transfer()).orElseThrow());

        var refused = new Rocket(null);
        var second = refused.decide(pad(MOON, 1), true, 6);
        registry.afterBegin(second, false, 6);
        assertTrue(registry.warmup(refused.logical).isEmpty()); assertEquals(0, tickets.count(refused.logical));
        assertTrue(log.get(log.size() - 1).contains("event=RELEASED") && log.get(log.size() - 1).endsWith("reason=LAUNCH_ENDED"));
    }

    @Test void theGlobalCapCountsEveryStateAndAReadyDestinationProceedsWithoutAWarmup() {
        var probe = new Probe(); var rockets = new ArrayList<Rocket>();
        for (int i = 0; i < 8; i++) {
            var rocket = new Rocket(null); rockets.add(rocket);
            var decision = rocket.decide(pad(i % 2 == 0 ? MOON : MARS, i), i >= 6, i);
            if (i >= 6) { registry.afterBegin(decision, true, i); }
        }
        probe.ready.add(rockets.get(0).logical); registry.tick(8, probe);
        assertEquals(Set.of(State.WARMING, State.READY, State.BOUND), states());
        int calls = tickets.calls.size();
        var ninth = new Rocket(null);
        assertEquals(Outcome.LIMIT, ninth.decide(pad(MOON, 9), false, 9).outcome());
        var ready = ninth.decide(pad(MOON, 9), true, 9);
        assertEquals(Outcome.PROCEED, ready.outcome()); assertFalse(ready.bindable());
        assertEquals(calls, tickets.calls.size()); assertTrue(registry.warmup(ninth.logical).isEmpty());
        registry.handover(registry.warmup(rockets.get(7).logical).orElseThrow().transfer(), 10);
        assertEquals(Outcome.LOADING, ninth.decide(pad(MOON, 9), false, 11).outcome());
        assertEquals(8, registry.live().size());
    }

    @Test void thePerPlayerLimitCountsWarmingReadyAndBoundWhileAdminWarmupsCountOnlyGlobally() {
        var player = UUID.randomUUID(); var probe = new Probe(); probe.online.add(player);
        var first = new Rocket(player); var second = new Rocket(player); var third = new Rocket(player);
        registry.afterBegin(first.decide(pad(MOON, 0), true, 1), true, 1);
        second.decide(pad(MOON, 1), false, 2); probe.ready.add(second.logical); registry.tick(3, probe);
        assertEquals(State.BOUND, registry.warmup(first.logical).orElseThrow().state());
        assertEquals(State.READY, registry.warmup(second.logical).orElseThrow().state());
        assertEquals(2, registry.count(player));
        assertEquals(Outcome.LIMIT, third.decide(pad(MOON, 2), false, 4).outcome());
        var ready = third.decide(pad(MOON, 2), true, 4);
        assertEquals(Outcome.PROCEED, ready.outcome()); assertFalse(ready.bindable());
        // Another player and an admin launch still start; the admin warm-up has no player.
        assertEquals(Outcome.LOADING, new Rocket(UUID.randomUUID()).decide(pad(MOON, 3), false, 5).outcome());
        var admin = new Rocket(null);
        assertEquals(Outcome.LOADING, admin.decide(pad(MOON, 4), false, 5).outcome());
        assertEquals(4, registry.live().size()); assertEquals(2, registry.count(player));
        // A one-per-player setting lowers the limit for the next allocation.
        var lowered = new RocketWarmupSettings(8, 1, 600); var other = UUID.randomUUID();
        var fourth = new Rocket(other); var fifth = new Rocket(other);
        assertEquals(Outcome.LOADING, registry.decide(fourth.request(UUID.randomUUID(), pad(MOON, 5)), true, () -> false, 6, lowered).outcome());
        assertEquals(Outcome.LIMIT, registry.decide(fifth.request(UUID.randomUUID(), pad(MOON, 6)), true, () -> false, 6, lowered).outcome());
    }

    @Test void aReplacementReleasesTheOldSetBeforeTheNewStart() {
        var rocket = new Rocket(null); var old = pad(MOON, 0);
        rocket.decide(old, false, 1); tickets.calls.clear();
        assertEquals(Outcome.LOADING, rocket.decide(pad(MARS, 1), false, 2).outcome());
        assertEquals(8, tickets.calls.size());
        assertTrue(tickets.calls.subList(0, 4).stream().allMatch(call -> call.startsWith("remove " + MOON)), tickets.calls.toString());
        assertTrue(tickets.calls.subList(4, 8).stream().allMatch(call -> call.startsWith("add " + MARS)), tickets.calls.toString());
        assertTrue(tickets.columns(MOON, rocket.logical).isEmpty());
        assertEquals(MARS, registry.warmup(rocket.logical).orElseThrow().level());
        assertEquals(2, registry.warmup(rocket.logical).orElseThrow().started());
        assertTrue(log.get(1).endsWith("reason=REPLACED"), log.get(1));
    }

    @Test void everyReleaseEventRemovesExactlyItsSetInItsOwnLevelAndDropsTheReservation() {
        for (var reason : Release.values()) {
            var player = UUID.randomUUID(); var probe = new Probe(); probe.online.add(player);
            var rocket = new Rocket(player); var observer = UUID.randomUUID();
            var destination = pad(reason.ordinal() % 2 == 0 ? MOON : MARS, reason.ordinal());
            var decision = rocket.decide(destination, reason == Release.HANDOVER || reason == Release.LAUNCH_ENDED, 0);
            UUID transfer = null;
            if (decision.bindable()) {
                registry.afterBegin(decision, true, 0); transfer = registry.warmup(rocket.logical).orElseThrow().transfer();
            }
            assertEquals(4, tickets.count(rocket.logical));
            assertEquals(1, registry.reservations(observer).size());
            switch (reason) {
                case HANDOVER -> registry.handover(transfer, 5);
                case LAUNCH_ENDED -> registry.launchEnded(rocket.logical, transfer, 5);
                case REPLACED -> rocket.decide(pad(EARTH, 20), false, 5);
                case DISCONNECTED -> { probe.online.clear(); registry.tick(5, probe); }
                case ROCKET_REMOVED -> { probe.removed.add(rocket.entity); registry.tick(5, probe); }
                case EXPIRED -> registry.tick(600, probe);
                case STOPPED -> registry.clear(true, 5);
            }
            assertTrue(tickets.columns(destination.sourceDimension(), rocket.logical).isEmpty(), reason + " left a ticket");
            assertTrue(registry.reservations(observer).stream().noneMatch(RocketRegion.fromSnapshot(destination)::equals),
                    reason + " kept the reservation");
            assertTrue(log.stream().anyMatch(line -> line.contains("logical=" + rocket.logical) && line.contains("event=RELEASED")
                    && line.endsWith("reason=" + reason)), reason + " was not logged");
            if (reason == Release.REPLACED) { registry.clear(true, 6); }
            assertTrue(registry.live().isEmpty());
        }
    }

    @Test void expiryAtTheCapturedLifetimeAndAnAbsentLevelHoldsNoTickets() {
        var probe = new Probe(); var rocket = new Rocket(null);
        rocket.decide(pad(MOON, 0), false, 1_000);
        registry.tick(1_599, probe); assertTrue(registry.warmup(rocket.logical).isPresent());
        registry.tick(1_600, probe); assertTrue(registry.warmup(rocket.logical).isEmpty());
        assertTrue(log.get(log.size() - 1).contains("age=600 reason=EXPIRED"), log.get(log.size() - 1));

        var other = new Rocket(null); other.decide(pad(MARS, 0), false, 2_000);
        tickets.levels.remove(MARS); int calls = tickets.calls.size();
        registry.tick(2_600, probe);
        assertTrue(registry.live().isEmpty()); assertEquals(calls, tickets.calls.size());
    }

    @Test void reservationsExcludeOnlyTheRequestersOwnWarmup() {
        var mine = new Rocket(null); var theirs = new Rocket(null);
        mine.decide(pad(MOON, 0), false, 1); theirs.decide(pad(MOON, 1), false, 1);
        assertEquals(List.of(RocketRegion.fromSnapshot(pad(MOON, 1))), registry.reservations(mine.logical));
        assertEquals(List.of(RocketRegion.fromSnapshot(pad(MOON, 0))), registry.reservations(theirs.logical));
        assertEquals(2, registry.reservations(UUID.randomUUID()).size());
    }

    @Test void disconnectAndRemovalEndOnlyWarmingAndReadyWhileABoundWarmupHandsOver() {
        var player = UUID.randomUUID(); var probe = new Probe(); probe.online.add(player);
        var bound = new Rocket(player); var ready = new Rocket(player);
        registry.afterBegin(bound.decide(pad(MOON, 0), true, 1), true, 1);
        ready.decide(pad(MOON, 1), false, 1); probe.ready.add(ready.logical); registry.tick(2, probe);
        probe.online.clear(); probe.removed.add(bound.entity);
        registry.tick(3, probe);
        assertTrue(registry.warmup(ready.logical).isEmpty());
        var survivor = registry.warmup(bound.logical).orElseThrow();
        assertEquals(State.BOUND, survivor.state()); assertEquals(4, tickets.count(bound.logical));
        registry.handover(survivor.transfer(), 4);
        assertTrue(registry.live().isEmpty()); assertEquals(0, tickets.count(bound.logical));
        assertTrue(log.get(log.size() - 1).endsWith("reason=HANDOVER"));
    }

    @Test void theKeptPadIsOfferedInWarmingAndReadyForTheSameDestinationAndSourceOnly() {
        var rocket = new Rocket(null); var destination = pad(MOON, 3); var probe = new Probe();
        rocket.decide(destination, false, 1);
        assertEquals(destination.sourceOrigin(), registry.kept(rocket.logical, MOON, rocket.source, SURFACE).orElseThrow());
        probe.ready.add(rocket.logical); registry.tick(2, probe);
        assertEquals(State.READY, registry.warmup(rocket.logical).orElseThrow().state());
        assertEquals(destination.sourceOrigin(), registry.kept(rocket.logical, MOON, rocket.source, SURFACE).orElseThrow());
        assertTrue(registry.kept(rocket.logical, MARS, rocket.source, SURFACE).isEmpty());
        assertTrue(registry.kept(rocket.logical, MOON, rocket.source, "another target").isEmpty());
        assertTrue(registry.kept(rocket.logical, MOON, snapshot(EARTH, 0, 0), SURFACE).isEmpty());
        assertTrue(registry.kept(UUID.randomUUID(), MOON, rocket.source, SURFACE).isEmpty());
        registry.afterBegin(rocket.decide(pad(MOON, 3), true, 3), true, 3);
        assertTrue(registry.kept(rocket.logical, MOON, rocket.source, SURFACE).isEmpty());
    }

    @Test void theStartingRequestIsRefusedWhileTheWarmupLivesWhateverTheLedgerKept() {
        var ledger = new RocketOperationLedger(); var rocket = new Rocket(UUID.randomUUID()); var first = UUID.randomUUID();
        assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(first));
        registry.decide(rocket.request(first, pad(MOON, 0)), true, () -> false, 1, DEFAULTS);
        ledger.finish(first, false);
        // The immediate replay: both refuse.
        assertEquals(RocketOperationLedger.BeginResult.REPLAYED, ledger.begin(first)); assertTrue(registry.startedBy(first));
        // Completed-ledger rollover: 4,096 later completions evict the FAILED entry, but the live warm-up still refuses it.
        for (int i = 0; i < RocketOperationLedger.DEFAULT_CAPACITY; i++) {
            var other = UUID.randomUUID(); assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(other));
            ledger.finish(other, i % 2 == 0);
        }
        assertNull(ledger.outcome(first)); assertTrue(registry.startedBy(first));
        // A runtime reset clears both; today's revalidation then applies and nothing is persisted.
        ledger.clear(); registry.clear(true, 2);
        assertEquals(RocketOperationLedger.BeginResult.STARTED, ledger.begin(first)); assertFalse(registry.startedBy(first));
        // After the warm-up ends, the registry no longer refuses the ID either.
        var later = UUID.randomUUID();
        registry.decide(rocket.request(later, pad(MOON, 0)), true, () -> false, 3, DEFAULTS);
        assertTrue(registry.startedBy(later)); registry.tick(603, new Probe());
        assertFalse(registry.startedBy(later));
    }

    @Test void aDiscardReleasesOnlyTheWarmupBoundToExactlyThatTransfer() {
        var rocket = new Rocket(null);
        registry.afterBegin(rocket.decide(pad(MOON, 0), true, 1), true, 1);
        var transfer = registry.warmup(rocket.logical).orElseThrow().transfer(); tickets.calls.clear();
        registry.launchEnded(rocket.logical, UUID.randomUUID(), 2);
        registry.launchEnded(UUID.randomUUID(), transfer, 2);
        assertTrue(tickets.calls.isEmpty()); assertEquals(State.BOUND, registry.warmup(rocket.logical).orElseThrow().state());
        // The post-publication discard: only the four warm-up removals; the registry owns no other authority.
        registry.launchEnded(rocket.logical, transfer, 3);
        assertEquals(4, tickets.calls.size()); assertTrue(tickets.calls.stream().allMatch(call -> call.startsWith("remove " + MOON)));
        assertTrue(registry.live().isEmpty());

        // A discard that runs synchronously inside begin is seen by afterBegin, which then releases instead of binding.
        var quick = new Rocket(null); var request = UUID.randomUUID();
        var decision = registry.decide(quick.request(request, pad(MOON, 1)), true, () -> true, 4, DEFAULTS);
        registry.launchEnded(quick.logical, request, 4);
        registry.afterBegin(decision, true, 4);
        assertTrue(registry.live().isEmpty()); assertEquals(0, tickets.count(quick.logical));
    }

    @Test void aStaleEventNeverRemovesALaterWarmupOfTheSameRocket() {
        var rocket = new Rocket(null);
        registry.afterBegin(rocket.decide(pad(MOON, 0), true, 0), true, 0);
        var old = registry.warmup(rocket.logical).orElseThrow().transfer();
        registry.tick(600, new Probe());
        assertTrue(registry.live().isEmpty());
        rocket.decide(pad(MOON, 0), false, 700);
        registry.launchEnded(rocket.logical, old, 701); registry.handover(old, 701);
        var later = registry.warmup(rocket.logical).orElseThrow();
        assertEquals(State.WARMING, later.state()); assertEquals(700, later.started()); assertEquals(4, tickets.count(rocket.logical));
    }

    @Test void aSettingsChangeAppliesToTheNextAllocationOnly() {
        var probe = new Probe(); var first = new Rocket(null); var second = new Rocket(null);
        registry.decide(first.request(UUID.randomUUID(), pad(MOON, 0)), true, () -> false, 0, DEFAULTS);
        var lowered = new RocketWarmupSettings(1, 1, 200);
        assertEquals(Outcome.LIMIT, registry.decide(second.request(UUID.randomUUID(), pad(MOON, 1)), true, () -> false, 10, lowered).outcome());
        registry.tick(599, probe); assertEquals(600, registry.warmup(first.logical).orElseThrow().lifetime());
        registry.tick(600, probe); assertTrue(registry.live().isEmpty());
        assertEquals(Outcome.LOADING, registry.decide(second.request(UUID.randomUUID(), pad(MOON, 1)), true, () -> false, 600, lowered).outcome());
        assertEquals(200, registry.warmup(second.logical).orElseThrow().lifetime());
        registry.tick(799, probe); assertTrue(registry.warmup(second.logical).isPresent());
        registry.tick(800, probe); assertTrue(registry.live().isEmpty());
    }

    @Test void theReadyMessageIsSentOnceAndAWarmupWritesAtMostFourLines() {
        var player = UUID.randomUUID(); var probe = new Probe(); probe.online.add(player);
        var rocket = new Rocket(player);
        rocket.decide(pad(MOON, 0), false, 0);
        registry.tick(1, probe); assertTrue(probe.messages.isEmpty());
        probe.ready.add(rocket.logical);
        for (int tick = 2; tick < 20; tick++) { registry.tick(tick, probe); }
        assertEquals(List.of(player), probe.messages);
        var decision = rocket.decide(pad(MOON, 0), true, 20);
        registry.afterBegin(decision, true, 20);
        registry.handover(registry.warmup(rocket.logical).orElseThrow().transfer(), 30);
        assertEquals(List.of("STARTED", "READY", "BOUND", "RELEASED"), log.stream().map(line -> line.split(" ")[1].substring(6)).toList());
    }

    @Test void failedPreconditionsAllocateAndBindNothingAndLeaveAWarmupToItsDeadline() {
        var rocket = new Rocket(null);
        var refused = registry.decide(rocket.request(UUID.randomUUID(), pad(MOON, 0)), false, () -> false, 0, DEFAULTS);
        assertEquals(Outcome.PROCEED, refused.outcome()); assertFalse(refused.bindable()); assertTrue(tickets.calls.isEmpty());
        rocket.decide(pad(MOON, 0), false, 1); int calls = tickets.calls.size();
        var elsewhere = registry.decide(rocket.request(UUID.randomUUID(), pad(MARS, 1)), false, () -> true, 2, DEFAULTS);
        assertFalse(elsewhere.bindable()); registry.afterBegin(elsewhere, true, 2);
        var kept = registry.warmup(rocket.logical).orElseThrow();
        assertEquals(State.WARMING, kept.state()); assertEquals(MOON, kept.level()); assertEquals(calls, tickets.calls.size());
    }

    @Test void settingsOnlyLowerTheFixedCeilings() {
        assertEquals(new RocketWarmupSettings(8, 2, 600), RocketWarmupSettings.DEFAULTS);
        for (int[] invalid : new int[][] {{0, 2, 600}, {9, 2, 600}, {8, 0, 600}, {8, 3, 600}, {8, 2, 199}, {8, 2, 601}}) {
            assertThrows(IllegalArgumentException.class, () -> new RocketWarmupSettings(invalid[0], invalid[1], invalid[2]));
        }
        assertDoesNotThrow(() -> new RocketWarmupSettings(1, 1, 200));
    }

    private Set<State> states() {
        var states = new HashSet<State>(); registry.live().forEach(warmup -> states.add(warmup.state())); return states;
    }

}
