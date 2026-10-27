package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayPermitsForTests;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** W27d1 A0: the coordinator's registry permit is honoured only for its satellite, only by validation, only in scope. */
final class SatelliteRegistryPermitScopeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void thePermitOpensOnlyTheValidationPreviewOfItsOwnSatelliteInsideOneScope() {
        var data = SatelliteMissionSavedData.create(0);
        data.deploymentFence((satellite, removal) -> true); // every satellite has an INTENT receipt
        UUID x = UUID.randomUUID(), y = UUID.randomUUID(), owner = UUID.randomUUID(), op = UUID.randomUUID();
        var intent = new AtomicBoolean(true);
        var permit = BayPermitsForTests.permit((candidate, satellite) -> candidate.equals(op) && intent.get(), op, x);
        CompoundTag before = data.save(new CompoundTag());
        assertTrue(data.deploymentPending(x), "no scope, no permit");
        assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.previewIdle(t -> idle(x, owner, t)).code());
        data.validateWithPermit(permit, () -> {
            assertFalse(data.deploymentPending(x), "validation honours the permit for its satellite");
            assertTrue(data.deploymentPending(y), "never for another satellite");
            assertEquals(SatelliteOperationCode.SUCCESS, data.previewIdle(t -> idle(x, owner, t)).code(), "the preview honours it");
            assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launchIdle(t -> idle(x, owner, t), 0).code(),
                    "launchIdle ignores it");
            assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.launch(x, UUID.randomUUID(), owner,
                    SatelliteRegistryProofTest.definition(), ModIdentity.id("earth"), 0, false).code(), "launch ignores it");
            assertEquals(SatelliteOperationCode.DEPLOYMENT_PENDING, data.decommission(x, owner, true, true).code(),
                    "decommission ignores it");
            intent.set(false);
            assertTrue(data.deploymentPending(x), "once the operation's RAM receipt is no longer INTENT, the permit is dead");
            intent.set(true);
            assertThrows(IllegalStateException.class, () -> data.validateWithPermit(permit, () -> 0), "scopes do not nest");
            return 0;
        });
        assertTrue(data.deploymentPending(x), "the scope ends with the call");
        assertThrows(IllegalStateException.class, () -> data.validateWithPermit(permit, () -> { throw new IllegalStateException(); }));
        assertTrue(data.deploymentPending(x), "and when it throws");
        assertEquals(before, data.save(new CompoundTag()), "validation inside the scope changed nothing");
    }

    private static SatelliteState idle(UUID satellite, UUID owner, long time) {
        return SatelliteState.launchIdle(satellite, ModIdentity.id("gas_harvester"), owner, time, ModIdentity.id("earth"),
                new SatelliteBlueprint(List.of(ModIdentity.id("primary")), false, new SatelliteStats(4, 10720, 1000, 1, 10)),
                new SatelliteKindState.Plain(SatelliteKind.GAS_HARVESTER));
    }
}
