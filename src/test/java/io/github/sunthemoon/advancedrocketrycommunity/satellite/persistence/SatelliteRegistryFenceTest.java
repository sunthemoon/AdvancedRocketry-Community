package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SatelliteRegistryFenceTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void everyEntryConsultsTheLiveFenceBeforeNewAdmissionOrReplay() {
        var data = SatelliteMissionSavedData.create(0);
        var blocked = new AtomicBoolean(true);
        data.deploymentFence((satellite, removal) -> blocked.get());
        UUID satellite = UUID.randomUUID(), mission = UUID.randomUUID(), owner = UUID.randomUUID();
        CompoundTag before = data.save(new CompoundTag());
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launch(satellite, mission, owner,
                SatelliteRegistryProofTest.definition(), ModIdentity.id("earth"), 0, false).code());
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launchIdle(t -> idle(satellite, owner, t), 0).code());
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.decommission(satellite, owner, false, false).code());
        assertEquals(before, data.save(new CompoundTag()));
        assertEquals(0, data.mutationSequence());
        blocked.set(false);
        assertEquals(SatelliteOperationCode.SUCCESS, data.launchIdle(t -> idle(satellite, owner, t), 0).code());
        blocked.set(true);
        before = data.save(new CompoundTag());
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launchIdle(t -> idle(satellite, owner, t), 0).code());
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.decommission(satellite, owner, true, true).code());
        assertEquals(before, data.save(new CompoundTag()));
        data.deploymentFence((id, removal) -> !removal); // Exactly durable REGISTERED: launch held, normal removal allowed.
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launchIdle(t -> idle(satellite, owner, t), 0).code());
        assertEquals(SatelliteOperationCode.SUCCESS, data.decommission(satellite, owner, false, false).code());
    }

    private static SatelliteState idle(UUID satellite, UUID owner, long time) {
        return SatelliteState.launchIdle(satellite, ModIdentity.id("gas_harvester"), owner, time, ModIdentity.id("earth"),
                new SatelliteBlueprint(List.of(ModIdentity.id("primary")), false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER));
    }
}
