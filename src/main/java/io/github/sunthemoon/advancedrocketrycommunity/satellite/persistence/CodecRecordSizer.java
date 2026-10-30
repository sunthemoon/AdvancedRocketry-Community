package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.BoundedSavedDataIo;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.InstanceState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionPayload;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionQuarantine;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RecordSizer;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-050 section 7 lifecycle reservation for the persisted registry: the largest encoding of a record over
 * every state it can still reach or be loaded in, since the reservation is computed once from whichever state
 * admitted or restored it. The fields that never change (IDs, kind, reward, blueprint) are the record's
 * own; the fields a later transition adds (a current mission, a receiver link, times, a quarantine, a paying or
 * rebound terminal, an acknowledgement) take their largest value.
 */
public final class CodecRecordSizer implements RecordSizer {
    public static final CodecRecordSizer INSTANCE = new CodecRecordSizer();

    private static final UUID ANY = new UUID(-1L, -1L);
    private static final String LONGEST_REASON = "Q".repeat(48);
    // Unlike mission reason codes, ground reasons permit every 48-character string.
    // NBT modified UTF-8 uses at most three bytes per Java char, including surrogate chars.
    private static final String LONGEST_GROUND_REASON = "\u0800".repeat(48);
    private static final ResourceLocation LONGEST_LEVEL = ResourceLocation.tryParse("f:" + "l".repeat(126));
    private static final BlockPos FAR = new BlockPos(30_000_000, 319, -30_000_000);

    private CodecRecordSizer() {
    }

    @Override
    public int satelliteBytes(SatelliteState satellite) {
        return satelliteReservation(satellite).bytes();
    }

    @Override
    public Reservation satelliteReservation(SatelliteState satellite) {
        SatelliteKindState state = satellite.kindState() instanceof SatelliteKindState.Solar solar
                ? new SatelliteKindState.Solar(solar.outputMultiplierPercent(), Optional.of(ANY))
                : satellite.kindState() instanceof SatelliteKindState.Survey survey
                ? new SatelliteKindState.Survey(Math.max(survey.charge(), 1_000_000L), Long.MAX_VALUE, survey.scanEnergy(),
                        survey.scanRadius(), survey.scanCell())
                : satellite.kindState();
        SatelliteState busy = new SatelliteState(satellite.schemaVersion(), satellite.satelliteId(),
                satellite.definitionId(), satellite.ownerId(), satellite.launchedAtLogicalTime(),
                SatelliteStatus.OPERATIONAL, Optional.of(ANY), satellite.kind(), satellite.orbitBody(),
                satellite.blueprint(), state);
        SatelliteState recovery = new SatelliteState(satellite.schemaVersion(), satellite.satelliteId(),
                satellite.definitionId(), satellite.ownerId(), satellite.launchedAtLogicalTime(),
                SatelliteStatus.RECOVERY_REQUIRED, Optional.empty(), satellite.kind(), satellite.orbitBody(),
                satellite.blueprint(), state);
        return bounded(() -> Math.max(bytes(SatelliteNbtCodec.encodeSatellite(busy)),
                bytes(SatelliteNbtCodec.encodeSatellite(recovery))), SatelliteLimits.MAX_SATELLITE_RECORD_NBT_BYTES);
    }

    @Override
    public int missionBytes(MissionState mission) {
        return missionReservation(mission).bytes();
    }

    @Override
    public Reservation missionReservation(MissionState mission) {
        MissionPayload payload = mission.payload() instanceof MissionPayload.Resource resource
                ? new MissionPayload.Resource(resource.kind(), resource.reward(), ANY,
                        Optional.of(new MissionPayload.TerminalLocation(LONGEST_LEVEL, FAR)), true, Optional.of(ANY),
                        true, OptionalLong.of(Long.MAX_VALUE))
                : mission.payload();
        long ready = Math.max(mission.completesAtLogicalTime(), Long.MAX_VALUE - 1);
        MissionState claimed = variant(mission, MissionStatus.CLAIMED, OptionalLong.of(ready),
                OptionalLong.of(Long.MAX_VALUE), Optional.empty(), payload);
        // Held from CLAIM_PENDING_DISCOVERY: the longest previous status and the only one that keeps both times.
        // The constructor accepts it for every kind, so a loaded file can hold it even where no transition leads.
        MissionState quarantined = variant(mission, MissionStatus.QUARANTINED, OptionalLong.of(ready),
                OptionalLong.of(Long.MAX_VALUE), Optional.of(new MissionQuarantine(LONGEST_REASON,
                        MissionStatus.CLAIM_PENDING_DISCOVERY, true)), payload);
        return bounded(() -> Math.max(bytes(SatelliteNbtCodec.encodeMission(claimed)),
                bytes(SatelliteNbtCodec.encodeMission(quarantined))), SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES);
    }

    @Override
    public int instanceBytes(AsteroidInstance instance) {
        return instanceReservation(instance).bytes();
    }

    @Override
    public Reservation instanceReservation(AsteroidInstance instance) {
        AsteroidInstance allocated = new AsteroidInstance(instance.schemaVersion(), instance.instanceId(),
                instance.ownerId(), instance.system(), instance.asteroidType(), instance.tableVersion(),
                instance.candidateFingerprint(), instance.seed(), instance.yield(), instance.createdAt(),
                OptionalLong.of(Long.MAX_VALUE), InstanceState.ALLOCATED, instance.origin(), Optional.of(ANY));
        return bounded(() -> Math.max(bytes(SatelliteNbtCodec.encodeInstance(allocated)),
                        bytes(SatelliteNbtCodec.encodeInstance(allocated.quarantine()))),
                SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES);
    }

    @Override
    public int groundSurveyBytes(GroundSurveyJob job) {
        return groundSurveyReservation(job).bytes();
    }

    @Override
    public Reservation groundSurveyReservation(GroundSurveyJob job) {
        var source = new GroundSurveyJob.Device(ANY, LONGEST_LEVEL, Long.MAX_VALUE);
        var analysis = new GroundSurveyJob.Analysis(GroundSurveyJob.AnalysisState.CREDITED,
                job.analysis().ticks(), job.analysis().energy(), job.analysis().credit(), job.analysis().ticks(),
                Optional.of(new GroundSurveyJob.Claim(Long.MAX_VALUE, source)));
        GroundSurveyJob largest = new GroundSurveyJob(job.schemaVersion(), job.jobId(), job.ownerId(), job.instanceId(),
                source, job.snapshot(), job.surveyTicks(), job.surveyEnergy(), job.surveyTicks(), job.createdAt(),
                job.startEpoch(), GroundSurveyJob.Phase.PROCESSED, OptionalLong.of(Long.MAX_VALUE),
                OptionalLong.of(Long.MAX_VALUE), analysis, Optional.empty()).quarantine(LONGEST_GROUND_REASON);
        return bounded(() -> bytes(GroundSurveyCodec.encode(largest)), SatelliteLimits.MAX_GROUND_SURVEY_RECORD_NBT_BYTES);
    }

    @Override
    public EncodingSize encodingSize(Object record) { return ExactGroundPreflight.measure(record); }

    @Override
    public boolean groundMutationFits(SatelliteMissionRegistry registry, GroundSurveyJob job,
            AsteroidInstance instance, boolean removeInstance, ResearchAccount account, long now, long observedTime) {
        try {
            if (registry.residentEncoding().known()) {
                // Match the original encoder/edited-root epoch checks even though the fields are fixed width.
                registry.epochToWrite();
                Math.addExact(registry.epochs().ceiling(), 1L);
                return ExactGroundPreflight.fits(registry, job, instance, removeInstance, account);
            }
            // The root bound is checked once, on the edited root, by the validation write below.
            var root = SatelliteRegistryPayload.encodeUnchecked(registry, new net.minecraft.nbt.CompoundTag());
            root.getCompound("clock").putLong("logical_game_time", now);
            root.getCompound("clock").putLong("last_observed_game_time", observedTime);
            root.putLong("save_epoch", Math.addExact(registry.epochs().ceiling(), 1L));
            replace(root, "ground_surveys", "job_id", job.jobId(), GroundSurveyCodec.encode(job));
            if (instance != null) {
                replace(root, "instances", "instance_id", instance.instanceId(),
                        removeInstance ? null : SatelliteNbtCodec.encodeInstance(instance));
            }
            if (account != null) {
                replace(root, "research_accounts", "owner_id", account.ownerId(), SatelliteNbtCodec.encodeAccount(account));
            }
            BoundedSavedDataIo.validatePayload(root, ManagedSavedDataType.SATELLITE_MISSIONS,
                    SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
            return true;
        } catch (ArithmeticException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void replace(net.minecraft.nbt.CompoundTag root, String section, String key, UUID id,
            net.minecraft.nbt.CompoundTag next) {
        var records = root.getList(section, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int index = 0; index < records.size(); index++) {
            if (records.getCompound(index).getUUID(key).equals(id)) {
                if (next == null) { records.remove(index); }
                else { records.set(index, next); }
                return;
            }
        }
        if (next != null) { records.add(next); }
    }

    private static MissionState variant(MissionState mission, MissionStatus status, OptionalLong readyAt,
                                        OptionalLong resolvedAt, Optional<MissionQuarantine> quarantine,
                                        MissionPayload payload) {
        return new MissionState(mission.schemaVersion(), mission.missionId(), mission.satelliteId(), mission.ownerId(),
                mission.definitionId(), mission.kind(), mission.targetBodyId(), mission.instanceId(), mission.seed(),
                mission.startedAtLogicalTime(), mission.completesAtLogicalTime(), mission.startEpoch(), status, readyAt,
                resolvedAt, mission.rewardVersion(), quarantine, payload);
    }

    private static int bytes(net.minecraft.nbt.CompoundTag tag) {
        return SatelliteNbtSize.uncompressedBytes(tag);
    }

    /** A refused variant keeps its accounting cap, but cannot authorize a whole-root preflight bypass. */
    private static Reservation bounded(java.util.function.IntSupplier size, int bound) {
        try {
            return new Reservation(size.getAsInt(), true);
        } catch (IllegalArgumentException overBound) {
            return new Reservation(bound, false);
        }
    }
}
