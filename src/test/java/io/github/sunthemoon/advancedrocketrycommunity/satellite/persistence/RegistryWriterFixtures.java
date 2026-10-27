package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.GroundSurveyFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

/** W129b: registry fixtures that fill all five sections, a bound-padded root and a blocked root. */
final class RegistryWriterFixtures {
    static final UUID GAS_SATELLITE = id(1), SECOND_GAS = id(2), RESOURCE_MISSION = id(11), TERMINAL = id(90);

    private RegistryWriterFixtures() { }

    /** Every section populated: idle crafts, a data satellite, an acknowledged claim, an active resource, a ground job. */
    static SatelliteMissionSavedData mixed(java.nio.file.Path file, BlockPos.MutableBlockPos display, List<RewardEntry> reward) {
        var data = SatelliteMissionSavedData.create(0);
        for (UUID id : List.of(GAS_SATELLITE, SECOND_GAS)) {
            require(data.launchIdle(time -> idle(id, time), 0).success(), "idle launch");
        }
        SatelliteRegistryProofTest.launch(data);
        require(data.resources(m -> m.startResource(gas(GAS_SATELLITE, RESOURCE_MISSION, reward,
                MissionPayload.TerminalLocation.of(ModIdentity.id("terminal_level"), display)), 0)).success(), "gas start");
        require(data.groundSurveys(g -> g.start(ground(0, 0), 0)).code() == GroundSurveys.Code.SUCCESS, "ground start");
        data.flush(file);
        require(data.resources(m -> m.claimResource(RESOURCE_MISSION, OWNER, TERMINAL, rewards -> null, 1000)).success(),
                "gas claim");
        require(data.reconcile(TERMINAL, RESOURCE_MISSION, DeliveryReconciliation.ReceiptView.PERSISTED, Optional.empty(),
                1000).changed(), "acknowledgement");
        require(data.resources(m -> m.startResource(gas(SECOND_GAS, id(12), YIELD, Optional.empty()), 1001)).success(),
                "second gas start");
        return data;
    }

    /** About {@code missions} claimed gas missions of roughly 3 KiB each (W128's record shape). */
    static SatelliteMissionSavedData padded(int missions) {
        var registry = SatelliteMissionRegistry.restore(0, 0, 1, CodecRecordSizer.INSTANCE);
        for (int index = 0; index < missions; index++) { registry.restoreMission(claimed(index)); }
        registry.finishRestore();
        return SatelliteMissionSavedData.load(SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag()));
    }

    static SatelliteMissionSavedData blocked() {
        CompoundTag raw = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        raw.putInt("schema_version", 999); raw.putString("unrecognized", "w129b");
        return SatelliteMissionSavedData.load(raw);
    }

    static MissionState claimed(int index) {
        List<RewardEntry> reward = new ArrayList<>();
        for (int entry = 0; entry < SatelliteLimits.MAX_REWARD_ENTRIES; entry++) {
            reward.add(new RewardEntry(ResourceLocation.tryParse(String.format("x:%05d_%02d_", index, entry) + "i".repeat(115)), 1));
        }
        return new MissionState(SatelliteLimits.MISSION_SCHEMA_VERSION, new UUID(31, index), new UUID(32, index),
                new UUID(33, index % 256), TYPE, MissionKind.GAS, SYSTEM, Optional.empty(), index, 0, 20, 1,
                MissionStatus.CLAIMED, OptionalLong.of(20), OptionalLong.of(20), "r".repeat(200), Optional.empty(),
                new MissionPayload.Resource(MissionKind.GAS, reward, new UUID(34, index), Optional.empty(), false,
                        Optional.empty(), false, OptionalLong.empty()));
    }

    static SatelliteState idle(UUID id, long time) {
        return SatelliteState.launchIdle(id, ModIdentity.id("gas_harvester"), OWNER, time, SYSTEM,
                new SatelliteBlueprint(List.of(TYPE), false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER));
    }

    static ResourceMissions.ResourceStart gas(UUID satellite, UUID mission, List<RewardEntry> reward,
            Optional<MissionPayload.TerminalLocation> display) {
        return new ResourceMissions.ResourceStart(satellite, mission, OWNER, MissionKind.GAS, SYSTEM, Optional.empty(),
                reward, "gas-v1/test", 1000, 1, TERMINAL, display);
    }

    static GroundSurveys.Start ground(long index, long time) {
        var source = pending(index, OWNER, InstanceOrigin.ground(id(100 + index)));
        var instance = new AsteroidInstance(source.schemaVersion(), source.instanceId(), source.ownerId(), source.system(),
                source.asteroidType(), source.tableVersion(), source.candidateFingerprint(), source.seed(), source.yield(),
                time, source.expiresAt(), source.state(), source.origin(), source.allocatedMission());
        return new GroundSurveys.Start(id(100 + index), OWNER, device(200 + index), instance, 20, 20, 2, 20, 120);
    }

    /** The uncompressed NBT bytes, as written into the file's payload. */
    static byte[] bytes(CompoundTag tag) {
        try {
            var output = new ByteArrayOutputStream();
            try (var stream = new DataOutputStream(output)) { NbtIo.write(tag, stream); }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void require(boolean condition, String step) {
        if (!condition) { throw new IllegalStateException("Fixture step failed: " + step); }
    }
}
