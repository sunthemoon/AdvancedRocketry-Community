package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayDeploymentSession;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptWriter;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowBarrier;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowChunkStore;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowReason;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowSection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTravelProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalTestAccess;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.SatelliteBayRocketAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.TestDataStorage;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R154: restart claims whose census slots overlap take D6 to D7 one at a time, in claim order. The real D6 and D7 class
 * runs each claim through the coordinator's step queue. The stand-in barrier gives every slot a save generation: a capture
 * forces a save of its column, which supersedes every earlier proof of that column, as the save monitor does. A stand-in
 * receipt writer reads D7's guard a few ticks after the removal is queued. Claim i flies from end i to end i + 1, so its
 * destination column is the next claim's source column, as neighbouring GameTest fixtures and neighbouring pads share them.
 * Without the turn the claims supersede each other's proofs in rotation and none finishes; the last four cases cover the
 * turn's hand-offs.
 */
final class BaySourceProofTurnTest {
    @BeforeAll static void bootstrap() throws Exception {
        MinecraftBootstrap.initialize();
        var bootstrap = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowAuditDirectionTest")
                .getDeclaredMethod("bootstrap");
        bootstrap.setAccessible(true); bootstrap.invoke(null);
    }
    @TempDir Path directory;

    /** A slot capture completes this many ticks after it starts; the guarded removal reads its guard this many after D7. */
    private static final int CAPTURE_TICKS = 3, WRITE_TICKS = 2, TICK_BOUND = 2_000;
    private static final UUID OWNER = new UUID(154, 2);
    /** Alternating Earth and Moon ends, each in its own chunk column: (0, 0), (4, 0), (2, 0) and (8, 0). */
    private static final RocketPosition[] ENDS = {new RocketPosition(8, 80, 8), new RocketPosition(72, 80, 8),
            new RocketPosition(40, 80, 8), new RocketPosition(136, 80, 8)};

    @Test void twoClaimsWhoseCensusSlotsOverlapFinishOneAtATimeInClaimOrder() throws Exception { run(2, f -> { }, 0, 1); }
    @Test void threeClaimsWhoseCensusSlotsOverlapFinishOneAtATimeInClaimOrder() throws Exception { run(3, f -> { }, 0, 1, 2); }

    /** D7 ends with GUARD_REFUSED (the source changed after the removal queued): the turn passes on and L asks again last. */
    @Test void aRefusedRemovalPassesTheTurnOnAndTheClaimQueuesBehindTheClaimsWaiting() throws Exception {
        run(3, f -> f.refuseFirst = true, 1, 2, 0);
    }

    /** W185-R5's park: D7 releases its proofs and the turn; once the store is quiet the claim asks again behind the others. */
    @Test void aParkedRetirementPassesTheTurnOnAndTheClaimQueuesBehindTheClaimsWaiting() throws Exception {
        run(3, f -> f.busy = true, 1, 2, 0);
    }

    /**
     * The holder's source column unloads just before its last capture completes, so the coordinator's watch invalidates the
     * proof before D7 queues the removal. D7 sends the claim back to D6 within its turn, but D6 must wait for the source to
     * load, and a claim waiting with no proof passes the turn on. It asks again once its source is back.
     */
    @Test void aHolderThatMustWaitForItsSourceToLoadPassesTheTurnOn() throws Exception {
        run(2, f -> f.each = fixture -> {
            var earth = fixture.levels.get(Level.OVERWORLD);
            long column = new ChunkPos(ENDS[0].x() >> 4, ENDS[0].z() >> 4).toLong();
            var slots = BayAbsenceCensus.slots(fixture.records.get(0));
            var last = slots.get(slots.size() - 1);
            if (!fixture.triggered && fixture.captures.stream().anyMatch(c -> c.slot().equals(last) && c.due() == fixture.now + 1)) {
                earth.unloaded.add(column); fixture.triggered = true;
            }
            if (earth.unloaded.contains(column) && fixture.proof.turnsForTests().holds(fixture.claim(1).logical())) {
                assertTrue(fixture.proof.turnsForTests().line().isEmpty(), "the waiting claim stayed in line");
                earth.unloaded.remove(column);
            }
        }, 1, 0);
    }

    /**
     * D5's copy pass runs at PROVE while the holder is still capturing: its attempt ends as its next step would end it,
     * BARRIER_UNSTABLE with W98's 200-tick retry (W98-R11 unchanged), and the turn passes on at once.
     */
    @Test void aCopyPassDuringACapturePassesTheTurnOnAndKeepsW98sRetry() throws Exception {
        run(2, f -> f.each = fixture -> {
            var first = fixture.claim(0);
            Map<UUID, ?> capturing = fixture.field("capturing");
            if (fixture.triggered || !capturing.containsKey(first.logical())) { return; }
            fixture.triggered = true;
            fixture.proof.yieldTurn(first);
            assertFalse(fixture.<Map<UUID, ?>>field("capturing").containsKey(first.logical()), "the attempt still captures");
            assertEquals(fixture.now + BaySourceProof.RETRY_TICKS, fixture.<Map<UUID, Long>>field("retryAt").get(first.logical()));
            assertTrue(fixture.proof.turnsForTests().holder().isEmpty(), "the turn did not pass on");
        }, 1, 0);
    }

    @FunctionalInterface private interface Scenario { void apply(Fixture f) throws Exception; }
    @FunctionalInterface private interface Hook { void run(Fixture f) throws Exception; }

    private void run(int count, Scenario scenario, int... order) throws Exception {
        var f = new Fixture(count);
        try {
            for (int i = 0; i + 1 < count; i++) {
                var shared = new HashSet<>(BayAbsenceCensus.slots(f.records.get(i)));
                shared.retainAll(BayAbsenceCensus.slots(f.records.get(i + 1)));
                assertFalse(shared.isEmpty(), "control: claims " + i + " and " + (i + 1) + " share a census slot");
            }
            scenario.apply(f);
            f.claims.values().forEach(claim -> f.queue.offer(claim.logical()));
            for (long tick = 1; tick <= TICK_BOUND && f.ended.size() < count; tick++) { f.tick(tick); }
            assertEquals(count, f.ended.size(), "Only " + f.ended.size() + " of " + count + " claims finished within " + TICK_BOUND
                    + " ticks; " + f.superseded + " captures were superseded");
            var expected = new ArrayList<UUID>();
            for (int i : order) { expected.add(f.claim(i).logical()); }
            assertEquals(expected, f.ended, "the claims finished out of claim order");
            f.claims.values().forEach(claim -> assertEquals(BayClaim.Handoff.READY_SOURCE, claim.handoff().orElseThrow()));
            assertEquals(1, f.mostAtOnce, "more than one claim captured or held its proofs at once");
            assertEquals(0, f.superseded, "a bay capture superseded another bay claim's proof");
            for (int i = 1; i < count; i++) {
                assertTrue(f.turnWaits.contains(f.claim(i).logical()), "claim " + i + " never waited for its turn with BAY_PROOF_TURN");
            }
        } finally { f.close(); }
    }

    private final class Fixture implements AutoCloseable {
        final MinecraftServer server = UnstartedServer.owned();
        final Map<ResourceKey<Level>, ProbeLevel> levels = new HashMap<>();
        final LinkedHashMap<UUID, BayClaim> claims = new LinkedHashMap<>();
        final List<RocketTransferRecord> records = new ArrayList<>();
        final RocketTransferSavedData journal = new RocketTransferSavedData();
        final BayStepQueue queue = new BayStepQueue();
        final BaySourceProof proof;
        final Map<RocketRetirementProof.Slot, AtomicLong> generations = new HashMap<>();
        final List<Capture> captures = new ArrayList<>();
        final List<Write> writes = new ArrayList<>();
        final List<UUID> ended = new ArrayList<>();
        final Set<UUID> turnWaits = new HashSet<>();
        long now;
        int mostAtOnce, superseded;
        /** Scenario switches: the store is busy until D7 first parks; the first queued removal's source changes; a hook. */
        boolean busy, refuseFirst, triggered;
        Hook each = f -> { };

        record Capture(RocketRetirementProof.Slot slot, AtomicLong generation, long mine, long due, Consumer<EscrowBarrier.Result> callback) { }
        record Write(BayClaim claim, BooleanSupplier guard, long due) { }
        /** The stand-in writer queued the removal; the coordinator's D7 returns IDLE until its outcome. */
        static final class Queued extends RuntimeException { Queued() { super(null, null, false, false); } }

        Fixture(int count) throws Exception {
            var access = UnstartedServer.allocate(LevelStorageSource.LevelStorageAccess.class);
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "resources", new HashMap<>());
            UnstartedServer.set(LevelStorageSource.LevelStorageAccess.class, access, "levelDirectory", new LevelStorageSource.LevelDirectory(directory));
            UnstartedServer.set(MinecraftServer.class, server, "storageSource", access);
            var earth = level(Level.OVERWORLD);
            var moon = level(ResourceKey.create(Registries.DIMENSION, RocketFlightPlanner.MOON.dimensionId()));
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(earth.key, earth, moon.key, moon));
            var groups = new ArrayList<BayGroup>();
            for (int i = 0; i < count; i++) {
                var before = record(i);
                var intent = intent(before, i);
                var job = BayConsumedSnapshots.prepare(before.sourceSnapshot(), before.destinationSnapshot(),
                        Map.of(BayConsumptionFixtures.bay(0), intent)).orElseThrow();
                var budget = new RocketCargoWorkBudget();
                for (long tick = 1; !job.step(budget, tick); tick++) { }
                var result = job.result().orElseThrow();
                var record = before.withConsumedSnapshots(result.source(), result.destination());
                records.add(record); journal.put(record);
                var member = BayMember.restartRegistered(BayConsumptionFixtures.registered(intent, result.source().contentHash(),
                        result.destination().contentHash()));
                var group = BayGroup.resumed(record.logicalRocketId(), record.transferId(), List.of(member)).orElseThrow();
                var claim = BayClaim.restart(record.logicalRocketId(), record.transferId(), group.members());
                claim.freeze(group); claim.establishSource(record.sourceEntityId());
                claim.advance(BayClaim.Stage.CONSUME); claim.advance(BayClaim.Stage.PROVE);
                claims.put(claim.logical(), claim); groups.add(group);
            }
            journal.save(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat").toFile());
            RocketJournalTestAccess.awaitWriter(journal);
            earth.storage.set(RocketTransferSavedData.DATA_NAME, journal);
            for (var record : records) {
                assertTrue(journal.actionable(record.logicalRocketId()));
                var level = levels.get(ResourceKey.create(Registries.DIMENSION, record.sourceSnapshot().sourceDimension()));
                var source = rocket(level, record);
                assertNotNull(BaySourceProof.projection(source, record), "control: the source serializes to its frozen projection");
            }
            // The session's lookups read an index with no store: a committed removal leaves no receipt for the group.
            var session = UnstartedServer.allocate(BayDeploymentSession.class);
            UnstartedServer.set(BayDeploymentSession.class, session, "server", server);
            UnstartedServer.set(BayDeploymentSession.class, session, "receipts", UnstartedServer.allocate(BayReceipts.class));
            proof = new BaySourceProof(server, session, queue::offer, (held, op) -> { }, held -> false, (held, guard) -> {
                writes.add(new Write(held, guard, now + WRITE_TICKS));
                try { this.<Set<UUID>>field("retiring").add(held.logical()); } // as D7 marks a queued removal
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                throw new Queued();
            });
            UnstartedServer.set(BaySourceProof.class, proof, "storeWaits", (BooleanSupplier) () -> busy);
            UnstartedServer.set(BaySourceProof.class, proof, "port", new RocketRetirementWorld(server, null) {
                @Override public RocketOrdinaryRetirement.SlotState slot(RocketRetirementProof.Slot slot) { return RocketOrdinaryRetirement.SlotState.LOADED; }
                @Override public boolean captureSupported(RocketRetirementProof.Slot slot) { return true; }
                @Override public boolean captureAvailable(RocketRetirementProof.Slot slot, long tick) { return true; }
                @Override public void retryNextTick(Runnable retry) { fail("the stand-in barrier always has capture quota"); }
                @Override public boolean capture(RocketRetirementProof.Slot slot, Predicate<CompoundTag> validator, long tick,
                                                 Consumer<String> sink, Consumer<EscrowBarrier.Result> callback) {
                    // The capture's forced save of this column supersedes every earlier proof of it.
                    var generation = generations.computeIfAbsent(slot, ignored -> new AtomicLong());
                    captures.add(new Capture(slot, generation, generation.incrementAndGet(), tick + CAPTURE_TICKS, callback));
                    return true;
                }
            });
            assertEquals(count, groups.size());
        }

        /** One coordinator tick: due captures and removals complete, the watch is checked, then the step queue runs. */
        void tick(long tick) throws Exception {
            now = tick;
            UnstartedServer.set(MinecraftServer.class, server, "tickCount", (int) tick);
            for (var capture : List.copyOf(captures)) {
                if (capture.due() > tick) { continue; }
                captures.remove(capture);
                var stored = new EscrowChunkStore.Capture(CompletableFuture.completedFuture(null), null, Path.of("."),
                        capture.slot().chunk(), capture.generation(), capture.mine(), 1, () -> { }, () -> { });
                var proved = new EscrowBarrier.Proof(null, stored, new AtomicBoolean(true), new AtomicBoolean(true));
                if (!proved.valid()) { superseded++; }
                capture.callback().accept(proved.valid() ? new EscrowBarrier.Result(proved, EscrowReason.NONE)
                        : new EscrowBarrier.Result(null, EscrowReason.SUPERSEDING_SAVE));
            }
            for (var write : List.copyOf(writes)) {
                if (write.due() > tick) { continue; }
                writes.remove(write);
                if (refuseFirst) { refuseFirst = false; write.claim().group().orElseThrow().invalidate(); }
                var outcome = write.guard().getAsBoolean() ? BayReceiptWriter.Outcome.COMMITTED : BayReceiptWriter.Outcome.GUARD_REFUSED;
                retired().invoke(proof, write.claim(), write.claim().group().orElseThrow(), outcome);
                if (write.claim().stage() == BayClaim.Stage.ENDED) { ended.add(write.claim().logical()); }
            }
            proof.observe();
            queue.run(this::step);
            each.run(this);
            Set<UUID> active = new HashSet<>(this.<Map<UUID, ?>>field("capturing").keySet());
            active.addAll(this.<Map<UUID, ?>>field("proofs").keySet());
            mostAtOnce = Math.max(mostAtOnce, active.size());
        }

        BayStepQueue.Result step(UUID logical) {
            var claim = claims.get(logical);
            var result = switch (claim.stage()) {
                case PROVE -> proof.prove(claim);
                case RETIRE -> {
                    try { yield proof.retire(claim); }
                    catch (Queued queued) { yield BayStepQueue.Result.IDLE; }
                }
                default -> BayStepQueue.Result.IDLE;
            };
            if ("BAY_PROOF_TURN".equals(claim.reason())) { turnWaits.add(logical); }
            if (busy && claim.stage() == BayClaim.Stage.PROVE && "BAY_RECEIPT_WRITE".equals(claim.reason())) { busy = false; }
            return result;
        }

        BayClaim claim(int i) { return List.copyOf(claims.values()).get(i); }

        Method retired() throws NoSuchMethodException {
            var method = BaySourceProof.class.getDeclaredMethod("retired", BayClaim.class, BayGroup.class, BayReceiptWriter.Outcome.class);
            method.setAccessible(true);
            return method;
        }

        @SuppressWarnings("unchecked")
        <T> T field(String name) throws ReflectiveOperationException {
            var field = BaySourceProof.class.getDeclaredField(name); field.setAccessible(true);
            return (T) field.get(proof);
        }

        ProbeLevel level(ResourceKey<Level> key) {
            var level = UnstartedServer.allocate(ProbeLevel.class);
            level.key = key; level.storage = TestDataStorage.over(directory.resolve(key.location().getPath()));
            try {
                var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime");
                Object runtime = type.getConstructor().newInstance();
                level.capability = (Capability<?>) type.getField("CAPABILITY").get(null);
                level.runtime = LazyOptional.of(() -> runtime);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            level.lookup = new EntityLookup<>(); level.sections = new EntitySectionStorage<>(Entity.class, chunk -> Visibility.TICKING);
            level.getter = new LevelEntityGetterAdapter<>(level.lookup, level.sections);
            level.unloaded = new HashSet<>();
            UnstartedServer.set(ServerLevel.class, level, "server", server);
            levels.put(key, level);
            return level;
        }

        @Override public void close() { journal.abort(); }
    }

    private static RocketTravelProfile body(int end) { return end % 2 == 0 ? RocketFlightPlanner.EARTH : RocketFlightPlanner.MOON; }

    /** PREPARED T of claim {@code i}: four structural blocks and one bay holding its package, from end i to end i + 1. */
    private static RocketTransferRecord record(int i) {
        UUID logical = new UUID(154, 10 + i), transfer = new UUID(154, 20 + i), sourceId = new UUID(154, 30 + i);
        var from = body(i); var to = body(i + 1);
        ResourceLocation iron = ResourceLocation.tryParse("minecraft:iron_block");
        var blocks = new ArrayList<RocketBlock>(List.of(
                new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(0, 2, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(new RocketPosition(1, 1, 0), new RocketBlockState(iron, Map.of())),
                new RocketBlock(BayConsumptionFixtures.bay(0), new RocketBlockState(ModIdentity.id("rocket_satellite_bay"), Map.of()),
                        new RocketBlockEntityPayload(SatelliteBayRocketAdapter.ID, SatelliteBayRoot.encode(
                                new SatelliteBayRoot.Contents(OWNER, BayConsumptionFixtures.pkg(i), null))))));
        var source = RocketStructureSnapshot.create(new UUID(154, 40 + i), from.dimensionId(), ENDS[i], blocks,
                List.of(new RocketPosition(1, 1, 0)), new RocketStats(5, 200L, 1_000L, 1_000L, 1, 1, 1, 1), 0L);
        var destination = source.relocated(new UUID(154, 50 + i), to.dimensionId(), ENDS[i + 1], 160L);
        RocketFuelState fuel = RocketFuelState.empty(1_000L).fill(1_000L).state();
        var plan = RocketFlightPlanner.plan(source.stats(), fuel, from, to, transfer, 0L).plan();
        var transit = RocketFlightData.initial(logical, source.stats().fuelCapacity(), source.stats().seatCount(), from.bodyId(),
                        from.dimensionId(), ENDS[i], 0L)
                .withFuel(fuel, 0L).withPassengers(RocketPassengerManifest.empty(1)).withPlan(plan).startCountdown(0L)
                .completeCountdown(RocketFlightLimits.COUNTDOWN_TICKS)
                .beginTransit(transfer, RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS);
        var arrival = transit.arriveAtDestination(transit.fuel().debit(plan).state(), to.bodyId(), to.dimensionId(), ENDS[i + 1],
                RocketFlightLimits.COUNTDOWN_TICKS + RocketFlightLimits.ASCENT_TICKS + RocketFlightLimits.TRANSIT_TICKS);
        return RocketTransferRecord.create(transfer, logical, OWNER, sourceId, source, destination, transit, arrival, plan.requiredFuel(), 0L);
    }

    private static BayReceipt intent(RocketTransferRecord record, int i) {
        return new BayReceipt(new UUID(154, 60 + i), i + 1, record.logicalRocketId(), BayConsumptionFixtures.bay(0), new UUID(154, 70 + i),
                OWNER, ModIdentity.id("data_satellite").toString(), SatelliteKind.DATA, ModIdentity.id("earth").toString(),
                RocketNativeResources.signature(BayConsumptionFixtures.pkg(i)), record.transferId(), record.sourceSnapshot().contentHash(),
                null, record.destinationSnapshot().contentHash(), null, BayReceipt.Phase.INTENT, "NONE", false, false, 0, 1, 1, 0);
    }

    /** T's exact source at the bound altitude, with the vanilla entity state that {@code Entity.save} reads. */
    private static RocketEntity rocket(ProbeLevel level, RocketTransferRecord record) throws ReflectiveOperationException {
        var origin = record.sourceSnapshot().sourceOrigin();
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(Entity.class, rocket, "uuid", record.sourceEntityId());
        UnstartedServer.set(Entity.class, rocket, "type", EntityType.ARMOR_STAND); // any serializable type: only the id string differs
        UnstartedServer.set(Entity.class, rocket, "deltaMovement", Vec3.ZERO);
        UnstartedServer.set(Entity.class, rocket, "tags", new HashSet<String>());
        var data = new SynchedEntityData(rocket);
        define(data, "DATA_SHARED_FLAGS_ID", (byte) 0); define(data, "DATA_AIR_SUPPLY_ID", 300);
        define(data, "DATA_CUSTOM_NAME_VISIBLE", false); define(data, "DATA_CUSTOM_NAME", Optional.empty());
        define(data, "DATA_SILENT", false); define(data, "DATA_NO_GRAVITY", true);
        define(data, "DATA_POSE", Pose.STANDING); define(data, "DATA_TICKS_FROZEN", 0);
        UnstartedServer.set(Entity.class, rocket, "entityData", data);
        var defineSynched = RocketEntity.class.getDeclaredMethod("defineSynchedData");
        defineSynched.setAccessible(true); defineSynched.invoke(rocket);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", record.logicalRocketId());
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", OWNER);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", record.sourceSnapshot());
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", record.sourceFlightData());
        UnstartedServer.set(RocketEntity.class, rocket, "escrowSection", RocketEscrowSection.neutral());
        UnstartedServer.set(Entity.class, rocket, "level", level);
        UnstartedServer.set(Entity.class, rocket, "passengers", ImmutableList.of());
        int y = origin.y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS;
        UnstartedServer.set(Entity.class, rocket, "position", new Vec3(origin.x() + .5, y, origin.z() + .5));
        UnstartedServer.set(Entity.class, rocket, "blockPosition", new BlockPos(origin.x(), y, origin.z()));
        UnstartedServer.set(Entity.class, rocket, "chunkPosition", new ChunkPos(origin.x() >> 4, origin.z() >> 4));
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(origin.x(), y, origin.z(), origin.x() + 1, y + 1, origin.z() + 1));
        UnstartedServer.set(Entity.class, rocket, "isAddedToWorld", true);
        level.lookup.add(rocket); level.sections.getOrCreateSection(SectionPos.asLong(rocket.blockPosition())).add(rocket);
        return rocket;
    }

    @SuppressWarnings("unchecked")
    private static <T> void define(SynchedEntityData data, String accessor, T value) throws ReflectiveOperationException {
        var field = Entity.class.getDeclaredField(accessor); field.setAccessible(true);
        data.define((EntityDataAccessor<T>) field.get(null), value);
    }

    static final class ProbeLevel extends ServerLevel {
        ResourceKey<Level> key;
        DimensionDataStorage storage;
        EntityLookup<Entity> lookup;
        EntitySectionStorage<Entity> sections;
        LevelEntityGetter<Entity> getter;
        Capability<?> capability;
        LazyOptional<Object> runtime;
        /** Columns whose entities are not loaded; every other column is entity-ticking. */
        Set<Long> unloaded;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public DimensionDataStorage getDataStorage() { return storage; }
        @Override public boolean areEntitiesLoaded(long chunk) { return !unloaded.contains(chunk); }
        @Override public boolean isPositionEntityTicking(BlockPos pos) { return !unloaded.contains(ChunkPos.asLong(pos)); }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public LevelEntityGetter<Entity> getEntities() { return getter; }
        @Override public Entity getEntity(UUID id) { return lookup.getEntity(id); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> requested, Direction side) {
            return requested == capability ? runtime.cast() : LazyOptional.empty();
        }
    }
}
