package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Analysis;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.AnalysisState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Claim;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Device;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Phase;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Quarantine;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * R129 (W168-R1): a lifecycle reservation is computed once, from the record as admitted or restored, so it must cover
 * every state a record constructor accepts with the same fixed fields. Each case enumerates those states with their
 * longest strings and compares the largest real encoding with the smallest reservation computed from any of them.
 */
final class ReservationCoverageTest {
    private static final UUID ANY = new UUID(-1L, -1L);
    private static final long LATE = Long.MAX_VALUE;
    private static final String REASON = "Q".repeat(48);
    private static final BlockPos FAR = new BlockPos(30_000_000, 319, -30_000_000);
    /** StorageBudget's mission section (ADR-050 section 7). */
    private static final long MISSION_BUDGET = 6L * 1024 * 1024;
    private static final CodecRecordSizer SIZER = CodecRecordSizer.INSTANCE;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyMissionStateFitsItsReservation() {
        for (MissionState mission : List.of(data(true), data(false), survey(), asteroid(), RegistryWriterFixtures.claimed(0),
                GroundPreflightTest.held(0))) {
            covered("mission " + mission.kind().id() + (mission.discoveryRequired() ? " discovery" : ""),
                    missionStates(mission), state -> bytes(SatelliteNbtCodec.encodeMission(state)), SIZER::missionBytes);
        }
    }

    @Test void theReviewsHeldDiscoveryMissionIsCoveredByItsOwnReservation() {
        MissionState held = GroundPreflightTest.held(0);
        assertEquals(1_389, bytes(SatelliteNbtCodec.encodeMission(held)));
        assertTrue(SIZER.missionBytes(held) >= 1_389, "reserved " + SIZER.missionBytes(held));
    }

    @Test void everySatelliteStateFitsItsReservation() {
        for (SatelliteKind kind : SatelliteKind.values()) {
            covered("satellite " + kind.id(), satelliteStates(kind),
                    state -> bytes(SatelliteNbtCodec.encodeSatellite(state)), SIZER::satelliteBytes);
        }
    }

    @Test void everyInstanceStateFitsItsReservation() {
        for (InstanceOrigin origin : List.of(InstanceOrigin.survey(ANY), InstanceOrigin.ground(ANY))) {
            covered("instance " + origin.kind(), instanceStates(origin),
                    state -> bytes(SatelliteNbtCodec.encodeInstance(state)), SIZER::instanceBytes);
        }
    }

    @Test void everyGroundJobStateFitsItsReservation() {
        covered("ground job", groundStates(), job -> bytes(GroundSurveyCodec.encode(job)), SIZER::groundSurveyBytes);
    }

    @Test void everyAccountFitsTheFixedAccountReservation() {
        for (ResearchAccount account : List.of(ResearchAccount.empty(ANY), new ResearchAccount(1, ANY,
                SatelliteLimits.MAX_RESEARCH_BALANCE, SatelliteLimits.MAX_LIFETIME_RESEARCH,
                SatelliteLimits.MAX_LIFETIME_RESEARCH))) {
            int encoded = bytes(SatelliteNbtCodec.encodeAccount(account));
            System.out.println("ARCE_R129_COVERAGE account largest=" + encoded + " reservation=" + RecordSizer.ACCOUNT_BYTES);
            assertTrue(encoded <= RecordSizer.ACCOUNT_BYTES, "account " + encoded);
        }
    }

    /** The proof's other two terms: the empty root fits the framing, and a list element is its record less 3 bytes. */
    @Test void theRootAddsOnlyItsFramingToItsListedRecords() {
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, SIZER);
        int empty = rootBytes(registry);
        System.out.println("ARCE_R129_FRAMING empty_root=" + empty + " framing=" + RecordSizer.ROOT_FRAMING_BYTES);
        assertTrue(empty <= RecordSizer.ROOT_FRAMING_BYTES, "empty root " + empty);
        MissionState mission = data(true);
        SatelliteState satellite = satelliteStates(SatelliteKind.SOLAR).get(0);
        ResearchAccount account = ResearchAccount.empty(ANY);
        AsteroidInstance instance = instanceStates(InstanceOrigin.ground(ANY)).get(0);
        GroundSurveyJob job = groundStates().get(0);
        registry.restoreMission(mission);
        registry.restoreSatellite(satellite);
        registry.restoreAccount(account);
        registry.restoreInstance(instance);
        registry.groundSurveys().restore(job);
        int records = bytes(SatelliteNbtCodec.encodeMission(mission)) + bytes(SatelliteNbtCodec.encodeSatellite(satellite))
                + bytes(SatelliteNbtCodec.encodeAccount(account)) + bytes(SatelliteNbtCodec.encodeInstance(instance))
                + bytes(GroundSurveyCodec.encode(job));
        assertEquals(empty + records - 5 * 3, rootBytes(registry));
    }

    /**
     * Capacity effect: a root that the pre-R129 reservations filled to the mission budget still loads unchanged. It is
     * now above that budget, so it admits no new mission until retention frees space; its own records keep working,
     * and the ground proof still holds because every section's reservations stay far below the root bound.
     */
    @Test void aRootFilledToTodaysMissionBudgetStillLoads() {
        MissionState sample = RegistryWriterFixtures.claimed(0);
        int previous = previousMissionReservation(sample);
        int current = SIZER.missionBytes(sample);
        int count = (int) (MISSION_BUDGET / previous);
        var registry = SatelliteMissionRegistry.restore(0, 0, 2, SIZER);
        registry.restoreSatellite(RegistryWriterFixtures.idle(id(1), 0));
        registry.restoreAccount(ResearchAccount.empty(OWNER));
        for (int index = 0; index < count; index++) { registry.restoreMission(RegistryWriterFixtures.claimed(index)); }
        CompoundTag root = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());

        var loaded = SatelliteRegistryPayload.decodeCurrent(root);
        assertFalse(loaded.restoreReport().changed());
        assertEquals(root, SatelliteRegistryPayload.encodeCurrent(loaded, new CompoundTag()));
        assertEquals((long) count * current, loaded.reservedBytes("MISSIONS"));
        assertTrue((long) count * previous <= MISSION_BUDGET && (long) count * current > MISSION_BUDGET,
                "The change moves this root from within to above its mission budget");
        assertEquals(SatelliteOperationCode.STORAGE_BUDGET, loaded.resources().startResource(
                RegistryWriterFixtures.gas(id(1), id(77), YIELD, Optional.empty()), 0).code());
        assertEquals(SatelliteOperationCode.SUCCESS, loaded.resources().purge(sample.missionId()).code());
        assertEquals((long) (count - 1) * current, loaded.reservedBytes("MISSIONS"));
        var request = GroundPreflightBenchmark.start();
        long added = SIZER.groundSurveyBytes(active(0, OWNER)) + SIZER.instanceBytes(request.instance());
        assertTrue(GroundPreflightBenchmark.reservationBound(loaded) + added <= SatelliteLimits.MAX_REGISTRY_NBT_BYTES,
                "A START here is still proved by reservations");
        assertEquals(GroundSurveys.Code.SUCCESS, loaded.groundSurveys().start(request, 0).code());
        System.out.println("ARCE_R129_BUDGET_ROOT missions=" + count + " reservation_before=" + previous
                + " reservation_after=" + current + " reserved_after=" + (long) count * current
                + " budget=" + MISSION_BUDGET);
    }

    /** Reports how many records of each shape fit their section budget, before and after R129. */
    @Test void reportCapacityBeforeAndAfter() {
        for (MissionState mission : List.of(dataStart(), data(true), survey(), asteroid(), RegistryWriterFixtures.claimed(0),
                GroundPreflightTest.held(0))) {
            int before = previousMissionReservation(mission);
            int after = SIZER.missionBytes(mission);
            System.out.println("ARCE_R129_CAPACITY mission " + mission.kind().id() + " encoded=" + bytes(
                    SatelliteNbtCodec.encodeMission(mission)) + " reservation_before=" + before + " reservation_after="
                    + after + " fit_before=" + Math.min(3072, MISSION_BUDGET / before)
                    + " fit_after=" + Math.min(3072, MISSION_BUDGET / after));
            assertEquals(before + 40, after, "Every mission gains resolved_at and the longer previous status");
        }
        for (AsteroidInstance instance : List.of(pending(0, OWNER, InstanceOrigin.ground(id(100))),
                instanceStates(InstanceOrigin.survey(ANY)).get(0))) {
            System.out.println("ARCE_R129_CAPACITY instance reservation=" + SIZER.instanceBytes(instance)
                    + " fit=" + 2L * 1024 * 1024 / SIZER.instanceBytes(instance));
        }
        for (GroundSurveyJob job : List.of(active(0, OWNER), groundStates().get(0))) {
            System.out.println("ARCE_R129_CAPACITY ground_job reservation=" + SIZER.groundSurveyBytes(job)
                    + " fit=" + 1024L * 1024 / SIZER.groundSurveyBytes(job));
        }
    }

    /** R132/W168-R4: +40 applies only before saturation; capped reservations may grow by 6 or 0 bytes. */
    @Test void missionReservationCapacitySaturatesAtTheUnchangedRecordCap() {
        for (int oldBytes : List.of(4056, 4090, 4096)) {
            CompoundTag raw = DecoderPreflightTest.rawMission(4096, false);
            raw.getCompound("quarantine").putString("previous_status", "ready");
            raw.remove("resolved_at");
            DecoderPreflightTest.resizeVersion(raw, oldBytes);
            MissionState mission = SatelliteNbtCodec.decodeMission(raw);
            assertEquals(raw, SatelliteNbtCodec.encodeMission(mission));
            assertEquals(oldBytes, previousMissionReservation(mission));
            assertEquals(4096, SIZER.missionBytes(mission));
            assertEquals(Math.min(40, 4096 - oldBytes), SIZER.missionBytes(mission) - oldBytes);
            assertEquals(oldBytes == 4056, SIZER.missionReservation(mission).proven(),
                    "Only the variant that actually encodes at 4096 proves the lifecycle bound");
            assertEquals(1536, Math.min(3072, MISSION_BUDGET / SIZER.missionBytes(mission)));
        }
    }

    private static <T> void covered(String name, List<T> states, ToIntFunction<T> encoded, ToIntFunction<T> reserved) {
        assertFalse(states.isEmpty(), name);
        T widest = states.stream().max(Comparator.comparingInt(encoded)).orElseThrow();
        int largest = encoded.applyAsInt(widest);
        int reservation = states.stream().mapToInt(reserved).min().orElseThrow();
        System.out.println("ARCE_R129_COVERAGE " + name + " states=" + states.size() + " largest=" + largest
                + " reservation=" + reservation);
        assertTrue(largest <= reservation, name + ": " + widest + " encodes " + largest
                + " bytes, but a legal state of the same record reserves " + reservation);
    }

    // --- Missions ---

    private static List<MissionState> missionStates(MissionState fixed) {
        List<Optional<MissionQuarantine>> holds = new ArrayList<>(List.of(Optional.empty()));
        for (MissionStatus previous : MissionStatus.values()) {
            for (boolean seen : List.of(false, true)) {
                legal(() -> new MissionQuarantine(REASON, previous, seen)).ifPresent(hold -> holds.add(Optional.of(hold)));
            }
        }
        List<MissionState> states = new ArrayList<>();
        for (MissionStatus status : MissionStatus.values()) {
            for (Optional<MissionQuarantine> hold : holds) {
                for (OptionalLong ready : times()) {
                    for (OptionalLong resolved : times()) {
                        for (MissionPayload payload : payloads(fixed.payload())) {
                            legal(() -> new MissionState(fixed.schemaVersion(), fixed.missionId(), fixed.satelliteId(),
                                    fixed.ownerId(), fixed.definitionId(), fixed.kind(), fixed.targetBodyId(),
                                    fixed.instanceId(), fixed.seed(), fixed.startedAtLogicalTime(),
                                    fixed.completesAtLogicalTime(), fixed.startEpoch(), status, ready, resolved,
                                    fixed.rewardVersion(), hold, payload)).ifPresent(states::add);
                        }
                    }
                }
            }
        }
        return states;
    }

    /** Data and survey payloads never change; a resource payload varies its terminal and acknowledgement fields. */
    private static List<MissionPayload> payloads(MissionPayload fixed) {
        if (!(fixed instanceof MissionPayload.Resource resource)) { return List.of(fixed); }
        List<MissionPayload> payloads = new ArrayList<>();
        for (Optional<MissionPayload.TerminalLocation> display : List.of(Optional.<MissionPayload.TerminalLocation>empty(),
                Optional.of(new MissionPayload.TerminalLocation(longest('l'), FAR)))) {
            for (boolean rebound : List.of(false, true)) {
                for (Optional<UUID> paid : List.of(Optional.<UUID>empty(), Optional.of(ANY))) {
                    for (boolean acknowledged : List.of(false, true)) {
                        legal(() -> new MissionPayload.Resource(resource.kind(), resource.reward(), ANY, display, rebound,
                                paid, acknowledged, acknowledged ? OptionalLong.of(LATE) : OptionalLong.empty()))
                                .ifPresent(payloads::add);
                    }
                }
            }
        }
        return payloads;
    }

    /** The pre-R129 reservation (CLAIMED, or QUARANTINED from READY with ready_at only), for the capacity report. */
    private static int previousMissionReservation(MissionState mission) {
        MissionPayload payload = payloads(mission.payload()).stream()
                .max(Comparator.comparingInt(value -> bytes(SatelliteNbtCodec.encodeMission(mission.withPayload(value)))))
                .orElseThrow();
        MissionState claimed = mission(mission, MissionStatus.CLAIMED, OptionalLong.of(LATE), Optional.empty(), payload);
        MissionState held = mission(mission, MissionStatus.QUARANTINED, OptionalLong.empty(),
                Optional.of(new MissionQuarantine(REASON, MissionStatus.READY, true)), payload);
        return Math.max(bytes(SatelliteNbtCodec.encodeMission(claimed)), bytes(SatelliteNbtCodec.encodeMission(held)));
    }

    private static MissionState mission(MissionState fixed, MissionStatus status, OptionalLong resolved,
            Optional<MissionQuarantine> hold, MissionPayload payload) {
        return new MissionState(fixed.schemaVersion(), fixed.missionId(), fixed.satelliteId(), fixed.ownerId(),
                fixed.definitionId(), fixed.kind(), fixed.targetBodyId(), fixed.instanceId(), fixed.seed(),
                fixed.startedAtLogicalTime(), fixed.completesAtLogicalTime(), fixed.startEpoch(), status,
                OptionalLong.of(LATE), resolved, fixed.rewardVersion(), hold, payload);
    }

    /** A data mission as {@code MissionState.start} admits it from the fixture definition. */
    private static MissionState dataStart() {
        return activeMission(MissionKind.DATA, TYPE, SYSTEM, Optional.empty(), MissionState.DATA_REWARD_VERSION,
                new MissionPayload.Data(100, 10, true));
    }

    private static MissionState data(boolean discovery) {
        return activeMission(MissionKind.DATA, longest('d'), longest('t'), Optional.empty(),
                "ࠀ".repeat(SatelliteLimits.MAX_REWARD_VERSION_CHARS), new MissionPayload.Data(10_000, 10_000, discovery));
    }

    private static MissionState survey() {
        List<UUID> instances = List.of(id(1), id(2), id(3), id(4));
        return activeMission(MissionKind.SURVEY, longest('d'), longest('t'), Optional.empty(), "r".repeat(200),
                new MissionPayload.Survey(instances, VERSION));
    }

    private static MissionState asteroid() {
        return activeMission(MissionKind.ASTEROID, longest('d'), longest('t'), Optional.of(ANY), "r".repeat(200),
                new MissionPayload.Resource(MissionKind.ASTEROID, entries('r'), id(5), Optional.empty(), false,
                        Optional.empty(), false, OptionalLong.empty()));
    }

    private static MissionState activeMission(MissionKind kind, ResourceLocation definition, ResourceLocation target,
            Optional<UUID> instance, String rewardVersion, MissionPayload payload) {
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, ANY, ANY, OWNER, definition, kind, target,
                instance, 0, 0, 20, 1, MissionStatus.ACTIVE, OptionalLong.empty(), OptionalLong.empty(), rewardVersion,
                Optional.empty(), payload);
    }

    // --- Satellites, instances and ground jobs ---

    private static List<SatelliteState> satelliteStates(SatelliteKind kind) {
        List<ResourceLocation> components = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_BLUEPRINT_COMPONENTS; index++) {
            components.add(longest((char) ('a' + index)));
        }
        var blueprint = new SatelliteBlueprint(components, false, new SatelliteStats(4, 10_720, 1_000, 1, 10));
        Optional<ResourceLocation> orbit = kind == SatelliteKind.DATA ? Optional.empty() : Optional.of(longest('o'));
        List<SatelliteKindState> kindStates = switch (kind) {
            case SURVEY -> List.of(new SatelliteKindState.Survey(0, 0, 1_000, 32, 8),
                    new SatelliteKindState.Survey(SatelliteLimits.MAX_BATTERY, LATE, 1_000, 32, 8));
            case SOLAR -> List.of(new SatelliteKindState.Solar(100, Optional.empty()),
                    new SatelliteKindState.Solar(100, Optional.of(ANY)));
            default -> List.of(new SatelliteKindState.Plain(kind));
        };
        List<SatelliteState> states = new ArrayList<>();
        for (SatelliteStatus status : SatelliteStatus.values()) {
            for (Optional<UUID> mission : List.of(Optional.<UUID>empty(), Optional.of(ANY))) {
                for (SatelliteKindState kindState : kindStates) {
                    legal(() -> new SatelliteState(SatelliteLimits.SATELLITE_SCHEMA_VERSION, ANY, longest('d'), OWNER, 0,
                            status, mission, kind, orbit, blueprint, kindState)).ifPresent(states::add);
                }
            }
        }
        return states;
    }

    private static List<AsteroidInstance> instanceStates(InstanceOrigin origin) {
        List<AsteroidInstance> states = new ArrayList<>();
        for (InstanceState state : InstanceState.values()) {
            for (OptionalLong expires : times()) {
                for (Optional<UUID> allocation : List.of(Optional.<UUID>empty(), Optional.of(ANY))) {
                    legal(() -> new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, ANY, OWNER, longest('s'),
                            longest('a'), VERSION, VERSION, 0, entries('y'), 0, expires, state, origin, allocation))
                            .ifPresent(states::add);
                }
            }
        }
        return states;
    }

    private static List<GroundSurveyJob> groundStates() {
        int ticks = 72_000;
        var snapshot = new GroundSurveyJob.Snapshot(longest('s'), longest('a'), VERSION, VERSION, 0, VERSION);
        List<Analysis> analyses = new ArrayList<>();
        for (AnalysisState state : AnalysisState.values()) {
            for (int progress : List.of(0, ticks - 1, ticks)) {
                for (Optional<Claim> claim : List.of(Optional.<Claim>empty(),
                        Optional.of(new Claim(LATE, new Device(ANY, longest('p'), LATE))))) {
                    legal(() -> new Analysis(state, ticks, 10_000, 10_000, progress, claim)).ifPresent(analyses::add);
                }
            }
        }
        // Ground reasons accept any string: three bytes per char for U+0800 and for each surrogate of a pair.
        List<Optional<Quarantine>> holds = new ArrayList<>(List.of(Optional.empty()));
        for (String reason : List.of("ࠀ".repeat(48), "😀".repeat(24))) {
            for (Phase previous : Phase.values()) {
                legal(() -> new Quarantine(reason, previous)).ifPresent(hold -> holds.add(Optional.of(hold)));
            }
        }
        List<GroundSurveyJob> states = new ArrayList<>();
        for (Phase phase : Phase.values()) {
            for (Optional<Quarantine> hold : holds) {
                for (Device source : List.of(new Device(ANY, LEVEL, 0), new Device(ANY, longest('l'), LATE))) {
                    for (int progress : List.of(0, ticks - 1, ticks)) {
                        for (OptionalLong completed : times()) {
                            for (OptionalLong resolved : times()) {
                                for (Analysis analysis : analyses) {
                                    legal(() -> new GroundSurveyJob(SatelliteLimits.GROUND_SURVEY_SCHEMA_VERSION, ANY,
                                            OWNER, ANY, source, snapshot, ticks, 10_000, progress, 0, 1, phase,
                                            completed, resolved, analysis, hold)).ifPresent(states::add);
                                }
                            }
                        }
                    }
                }
            }
        }
        return states;
    }

    // --- Shared ---

    private static List<OptionalLong> times() {
        return List.of(OptionalLong.empty(), OptionalLong.of(LATE));
    }

    /** Seventeen distinct 128-character items, one of each. */
    private static List<RewardEntry> entries(char tag) {
        List<RewardEntry> entries = new ArrayList<>();
        for (int index = 0; index < SatelliteLimits.MAX_REWARD_ENTRIES; index++) {
            entries.add(new RewardEntry(ResourceLocation.tryParse(
                    String.format(Locale.ROOT, "f:%c%02d", tag, index) + "x".repeat(123)), 1));
        }
        return entries;
    }

    private static ResourceLocation longest(char tag) {
        return ResourceLocation.tryParse("f:" + tag + "x".repeat(125));
    }

    private static <T> Optional<T> legal(Supplier<T> constructor) {
        try {
            return Optional.of(constructor.get());
        } catch (IllegalArgumentException illegal) {
            return Optional.empty();
        }
    }

    private static int rootBytes(SatelliteMissionRegistry registry) {
        return bytes(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
    }

    private static int bytes(CompoundTag tag) {
        return SatelliteNbtSize.uncompressedBytes(tag);
    }
}
