package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
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
 * every state it can still reach. The fields that never change (IDs, kind, reward, blueprint) are the record's
 * own; the fields a later transition adds (a current mission, a receiver link, times, a quarantine, a paying or
 * rebound terminal, an acknowledgement) take their largest value.
 */
public final class CodecRecordSizer implements RecordSizer {
    public static final CodecRecordSizer INSTANCE = new CodecRecordSizer();

    private static final UUID ANY = new UUID(-1L, -1L);
    private static final String LONGEST_REASON = "Q".repeat(48);
    private static final ResourceLocation LONGEST_LEVEL = ResourceLocation.tryParse("f:" + "l".repeat(126));
    private static final BlockPos FAR = new BlockPos(30_000_000, 319, -30_000_000);

    private CodecRecordSizer() {
    }

    @Override
    public int satelliteBytes(SatelliteState satellite) {
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
        MissionPayload payload = mission.payload() instanceof MissionPayload.Resource resource
                ? new MissionPayload.Resource(resource.kind(), resource.reward(), ANY,
                        Optional.of(new MissionPayload.TerminalLocation(LONGEST_LEVEL, FAR)), true, Optional.of(ANY),
                        true, OptionalLong.of(Long.MAX_VALUE))
                : mission.payload();
        long ready = Math.max(mission.completesAtLogicalTime(), Long.MAX_VALUE - 1);
        MissionState claimed = variant(mission, MissionStatus.CLAIMED, OptionalLong.of(ready),
                OptionalLong.of(Long.MAX_VALUE), Optional.empty(), payload);
        MissionState quarantined = variant(mission, MissionStatus.QUARANTINED, OptionalLong.of(ready),
                OptionalLong.empty(), Optional.of(new MissionQuarantine(LONGEST_REASON, MissionStatus.READY, true)),
                payload);
        return bounded(() -> Math.max(bytes(SatelliteNbtCodec.encodeMission(claimed)),
                bytes(SatelliteNbtCodec.encodeMission(quarantined))), SatelliteLimits.MAX_MISSION_RECORD_NBT_BYTES);
    }

    @Override
    public int instanceBytes(AsteroidInstance instance) {
        AsteroidInstance allocated = new AsteroidInstance(instance.schemaVersion(), instance.instanceId(),
                instance.ownerId(), instance.system(), instance.asteroidType(), instance.tableVersion(),
                instance.candidateFingerprint(), instance.seed(), instance.yield(), instance.createdAt(),
                OptionalLong.of(Long.MAX_VALUE), InstanceState.ALLOCATED, instance.sourceMission(), Optional.of(ANY));
        return bounded(() -> bytes(SatelliteNbtCodec.encodeInstance(allocated)),
                SatelliteLimits.MAX_INSTANCE_RECORD_NBT_BYTES);
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

    /** A variant the encoder refuses is reserved at the record bound it exceeded. */
    private static int bounded(java.util.function.IntSupplier size, int bound) {
        try {
            return size.getAsInt();
        } catch (IllegalArgumentException overBound) {
            return bound;
        }
    }
}
