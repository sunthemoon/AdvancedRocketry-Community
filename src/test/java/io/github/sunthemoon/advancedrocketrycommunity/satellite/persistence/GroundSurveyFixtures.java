package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.progression.ResearchAccount;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Deterministic registry fixtures; no runtime controller or world scan is involved. */
public final class GroundSurveyFixtures {
    public static final ResourceLocation SYSTEM = ResourceLocation.tryParse("advancedrocketrycommunity:earth");
    public static final ResourceLocation TYPE = ResourceLocation.tryParse("advancedrocketrycommunity:small_asteroid");
    public static final ResourceLocation LEVEL = ResourceLocation.tryParse("minecraft:overworld");
    public static final String VERSION = "0123456789abcdef";
    public static final UUID OWNER = id(1);
    public static final List<RewardEntry> YIELD = List.of(new RewardEntry(ResourceLocation.tryParse("minecraft:iron_ore"), 64));

    private GroundSurveyFixtures() { }
    public static UUID id(long value) { return new UUID(21, value); }
    public static GroundSurveyJob.Device device(long value) { return new GroundSurveyJob.Device(id(value), LEVEL, value); }

    public static AsteroidInstance pending(long index, UUID owner, InstanceOrigin origin) {
        return new AsteroidInstance(2, id(1_000 + index), owner, SYSTEM, TYPE, VERSION, VERSION,
                index, YIELD, 0, OptionalLong.empty(), InstanceState.PENDING, origin, Optional.empty());
    }

    public static GroundSurveyJob active(long index, UUID owner) {
        AsteroidInstance instance = pending(index, owner, InstanceOrigin.ground(id(100 + index)));
        return new GroundSurveyJob(1, id(100 + index), owner, instance.instanceId(), device(200 + index),
                GroundSurveyJob.Snapshot.from(instance), 20, 20, 0, 0, 1, GroundSurveyJob.Phase.ACTIVE,
                OptionalLong.empty(), OptionalLong.empty(), new GroundSurveyJob.Analysis(
                GroundSurveyJob.AnalysisState.WAITING, 2, 20, 120, 0, Optional.empty()), Optional.empty());
    }

    public static GroundSurveyJob ready(long index, UUID owner) {
        GroundSurveyJob job = active(index, owner);
        for (int tick = 0; tick < 20; tick++) { job = job.advanced(tick); }
        return job;
    }

    public static GroundSurveyJob running(long index, UUID owner, GroundSurveyJob.Device processor) {
        GroundSurveyJob job = ready(index, owner);
        return job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.RUNNING, 0,
                Optional.of(new GroundSurveyJob.Claim(1, processor))), 20);
    }

    public static GroundSurveyJob processed(long index, UUID owner) {
        GroundSurveyJob job = running(index, owner, device(300 + index));
        return job.analyzed(job.analysis().with(GroundSurveyJob.AnalysisState.CREDITED, job.analysis().ticks(),
                job.analysis().claim()), 30);
    }

    public static AsteroidInstance instance(GroundSurveyJob job, InstanceState state) {
        AsteroidInstance instance = pending(job.snapshot().seed(), job.ownerId(), InstanceOrigin.ground(job.jobId()));
        return state == InstanceState.PENDING ? instance : instance.transition(state, OptionalLong.of(24_000),
                state == InstanceState.ALLOCATED ? Optional.of(id(900)) : Optional.empty());
    }

    public static CompoundTag root(List<GroundSurveyJob> jobs, List<AsteroidInstance> instances) {
        CompoundTag root = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        root.putLong("save_epoch", 2);
        ListTag jobTags = new ListTag();
        jobs.forEach(job -> jobTags.add(GroundSurveyCodec.encode(job)));
        root.put("ground_surveys", jobTags);
        ListTag instanceTags = new ListTag();
        instances.forEach(instance -> instanceTags.add(SatelliteNbtCodec.encodeInstance(instance)));
        root.put("instances", instanceTags);
        ListTag accounts = new ListTag();
        java.util.stream.Stream.concat(jobs.stream().map(GroundSurveyJob::ownerId), instances.stream()
                .map(AsteroidInstance::ownerId)).distinct().forEach(owner -> accounts.add(
                SatelliteNbtCodec.encodeAccount(new ResearchAccount(1, owner, 77, 100, 23))));
        root.put("research_accounts", accounts);
        return root;
    }

    /** Seven legacy instance states, including AVAILABLE/ALLOCATED whose finished survey was pruned. */
    public static CompoundTag schema3() {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.restore(0, 0, 19, CodecRecordSizer.INSTANCE);
        registry.restoreAccount(new ResearchAccount(1, OWNER, 77, 100, 23));
        for (int index = 0; index < 7; index++) {
            InstanceState state = new InstanceState[]{InstanceState.PENDING, InstanceState.AVAILABLE,
                    InstanceState.AVAILABLE, InstanceState.ALLOCATED, InstanceState.DEPLETED,
                    InstanceState.EXPIRED, InstanceState.QUARANTINED}[index];
            UUID surveyId = id(500 + index);
            AsteroidInstance instance = pending(index, OWNER, InstanceOrigin.survey(surveyId));
            if (state != InstanceState.PENDING) {
                instance = instance.transition(state, OptionalLong.of(24_000),
                        state == InstanceState.ALLOCATED ? Optional.of(id(900)) : Optional.empty());
            }
            registry.restoreInstance(instance);
            if (index == 0 || index == 1) {
                UUID satelliteId = id(600 + index);
                var craft = craft(satelliteId, SatelliteKind.SURVEY);
                registry.restoreSatellite(index == 0 ? craft.startMission(surveyId) : craft);
                registry.restoreMission(new MissionState(2, surveyId, satelliteId, OWNER, TYPE,
                        MissionKind.SURVEY, SYSTEM, Optional.empty(), index, 0, 72_000, 1,
                        index == 0 ? MissionStatus.ACTIVE : MissionStatus.CLAIMED,
                        index == 0 ? OptionalLong.empty() : OptionalLong.of(72_000),
                        index == 0 ? OptionalLong.empty() : OptionalLong.of(72_000), "survey-v1", Optional.empty(),
                        new MissionPayload.Survey(List.of(instance.instanceId()), VERSION)));
            }
            if (index == 3) {
                registry.restoreSatellite(craft(id(700), SatelliteKind.ASTEROID_MINER).startMission(id(900)));
                registry.restoreMission(new MissionState(2, id(900), id(700), OWNER, TYPE, MissionKind.ASTEROID,
                        SYSTEM, Optional.of(instance.instanceId()), 5, 0, 72_000, 1, MissionStatus.ACTIVE,
                        OptionalLong.empty(), OptionalLong.empty(), "asteroid-v1", Optional.empty(),
                        new MissionPayload.Resource(MissionKind.ASTEROID, YIELD, id(800), Optional.empty(), false,
                                Optional.empty(), false, OptionalLong.empty())));
            }
        }
        CompoundTag root = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        root.putInt("schema_version", 3);
        root.putString("format_epoch", "v1.6.0-satellite-missions");
        root.remove("ground_surveys");
        for (Tag raw : root.getList("instances", Tag.TAG_COMPOUND)) {
            CompoundTag instance = (CompoundTag) raw;
            instance.putInt("schema_version", 1);
            instance.putUUID("source_mission", instance.getUUID("origin_id"));
            instance.remove("origin_id");
            instance.remove("origin_kind");
        }
        return root;
    }

    private static SatelliteState craft(UUID id, SatelliteKind kind) {
        SatelliteKindState state = kind == SatelliteKind.SURVEY
                ? new SatelliteKindState.Survey(0, 0, 1_000, 32, 8) : new SatelliteKindState.Plain(kind);
        return SatelliteState.launchIdle(id, TYPE, OWNER, 0, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, new SatelliteStats(4, 10_720, 1_000, 1, 10)), state);
    }
}
