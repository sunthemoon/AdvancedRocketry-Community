package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;

/** W333's isolated, real checked files. Never installs a production registry or a runtime hook. */
final class SatelliteRowGameTestFiles implements AutoCloseable {
    static final ManagedSavedDataType TYPE = ManagedSavedDataType.SATELLITE_MISSIONS;
    static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(TYPE);
    static final UUID OWNER = id(1);
    static final List<RewardEntry> YIELD = List.of(new RewardEntry(ModIdentity.id("test_ore"), 64));
    static final String VERSION = "0123456789abcdef";
    final GameTestHelper helper;
    final Path directory, file;
    SatelliteMissionSavedData data = SatelliteMissionSavedData.create(0);

    SatelliteRowGameTestFiles(GameTestHelper helper) throws IOException {
        this.helper = helper;
        SatelliteRegistryTestAwait.require(helper.getLevel().getServer());
        directory = Files.createTempDirectory("arce-w333-registry-");
        file = directory.resolve(TYPE.fileName());
    }

    static UUID id(long n) { return new UUID(333, n); }
    static GroundSurveyJob.Device device(long n) { return new GroundSurveyJob.Device(id(n), ModIdentity.id("row_level"), n); }

    static AsteroidInstance instance(long n, long time, InstanceOrigin origin) {
        return new AsteroidInstance(2, id(1000 + n), OWNER, ModIdentity.id("earth"), ModIdentity.id("small_asteroid"),
                VERSION, VERSION, n, YIELD, time, OptionalLong.empty(), InstanceState.PENDING, origin, Optional.empty());
    }

    static GroundSurveys.Start ground(long n, long time) {
        return new GroundSurveys.Start(id(100 + n), OWNER, device(200 + n),
                instance(n, time, InstanceOrigin.ground(id(100 + n))), 20, 20, 2, 20, 120);
    }

    static ResourceMissions.ResourceStart start(UUID satellite, UUID mission) {
        return new ResourceMissions.ResourceStart(satellite, mission, OWNER, MissionKind.GAS, ModIdentity.id("earth"),
                Optional.empty(), YIELD, "gas-v1/w333", 1000, 1, id(90), Optional.empty());
    }

    void seed() {
        for (long n : List.of(1L, 2L, 3L)) {
            SatelliteKind kind = n == 3 ? SatelliteKind.SURVEY : SatelliteKind.GAS_HARVESTER;
            helper.assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(id(n), ModIdentity.id(kind.id() + "_satellite"),
                    OWNER, time, ModIdentity.id("earth"), new SatelliteBlueprint(List.of(ModIdentity.id("row_primary")), false,
                    new SatelliteStats(4, 10720, 1000, 1, 10)), kind == SatelliteKind.SURVEY
                    ? new SatelliteKindState.Survey(0, 0, 1000, 32, 8) : new SatelliteKindState.Plain(kind)), 0).success(),
                    "Could not seed row satellite " + n);
        }
        helper.assertTrue(data.resources(m -> m.startResource(start(id(1), id(11)), 0)).success(), "Could not seed resource mission");
        helper.assertTrue(data.resources(m -> m.startSurvey(new ResourceMissions.SurveyStart(id(3), id(13), OWNER,
                ModIdentity.id("earth"), VERSION, 1, 1000, 2,
                (count, now) -> List.of(instance(3, now, InstanceOrigin.survey(id(13))))), 0)).success(), "Could not seed mission origin");
        helper.assertTrue(data.groundSurveys(g -> g.start(ground(0, 0), 0)).code() == GroundSurveys.Code.SUCCESS,
                "Could not seed ground survey");
        data.flush(file);
        for (int tick = 1; tick <= 20; tick++) {
            final int now = tick;
            helper.assertTrue(data.groundSurveys(g -> g.advanceSurvey(id(100), device(200), 24000, now)).code()
                    == GroundSurveys.Code.SUCCESS, "Could not ready ground survey");
        }
        data.flush(file);
    }

    CheckedSavedDataPolicy cut(boolean unknown) {
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            if (unknown) { CheckedSavedDataFile.atomicMove(staged, target); }
            throw new SatelliteRegistryTestAwait.InjectedFailure();
        }, (path, type) -> {
            if (unknown) { throw new IllegalStateException(new SatelliteRegistryTestAwait.InjectedFailure()); }
            return CheckedSavedDataFile.readPayload(path, type);
        });
    }

    SatelliteRegistryProof.Completion checkpoint(CheckedSavedDataPolicy policy) {
        return data.checkpoint(file, List.of(data.watch(id(1))), policy, directory -> { });
    }

    SatelliteMissionSavedData reload() {
        return SatelliteMissionSavedData.load(CheckedSavedDataFile.readPayload(file, TYPE).orElseThrow());
    }

    void assertPayloadEqual() {
        try (var loaded = new RegistryClose(reload())) {
            helper.assertTrue(loaded.data.operational() && !loaded.data.isDirty(), "Checked payload did not reload cleanly");
            helper.assertTrue(data.save(new CompoundTag()).equals(loaded.data.save(new CompoundTag())), "Checked payload lost a registry section");
            helper.assertTrue(loaded.data.instance(id(1000)).orElseThrow().origin().equals(InstanceOrigin.ground(id(100))),
                    "Ground origin tag was lost");
            helper.assertTrue(loaded.data.instance(id(1003)).orElseThrow().origin().equals(InstanceOrigin.survey(id(13))),
                    "Mission origin tag was lost");
        }
    }

    @Override public void close() throws IOException {
        data.writePolicy(ATOMIC);
        data.close();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) { Files.deleteIfExists(path); }
        }
    }

    record RegistryClose(SatelliteMissionSavedData data) implements AutoCloseable {
        @Override public void close() { data.close(); }
    }
}
