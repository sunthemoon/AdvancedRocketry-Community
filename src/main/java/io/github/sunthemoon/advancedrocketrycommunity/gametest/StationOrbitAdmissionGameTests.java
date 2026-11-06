package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.StarSystemContent;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationRuntime;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** New station admission excludes the shared world host without rewriting an existing Space orbit. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationOrbitAdmissionGameTests {
    private StationOrbitAdmissionGameTests() {
    }

    @GameTest(template = "empty", batch = "station_orbit_admission", timeoutTicks = 100)
    public static void operatorCreationRefusesTheSpaceBodyButKeepsExistingStations(GameTestHelper helper) {
        var existing = new StationWarpGameTests.Fixture(helper, "Legacy Space orbit", CelestialIds.SPACE_ID, 0);
        var manager = StationRuntime.manager().orElseThrow();
        var created = new ArrayList<StationState>();
        try {
            CompoundTag before = existing.data.save(new CompoundTag());
            var refused = manager.createForOperator(existing.server, UUID.randomUUID(), "World host",
                    CelestialIds.SPACE_ID);
            refused.station().ifPresent(created::add);
            helper.assertTrue(refused.code() == StationCreationCode.UNKNOWN_ORBIT_BODY && refused.station().isEmpty(),
                    "Operator creation accepted the shared Space body: " + refused.code());
            helper.assertTrue(before.equals(existing.data.save(new CompoundTag())),
                    "A refused creation changed the registry or left a reservation");
            helper.assertTrue(StationRegistrySavedData.load(before).find(existing.id())
                            .filter(existing.created::equals).isPresent(),
                    "An existing Space-orbit station changed across loading");

            // The same refusal covers a non-orbitable star. A real orbit-only body is still accepted.
            helper.assertTrue(manager.createForOperator(existing.server, UUID.randomUUID(), "Star",
                            StarSystemContent.TAU_CETI).code() == StationCreationCode.UNKNOWN_ORBIT_BODY,
                    "Operator creation accepted a body that cannot be orbited");
            var allowed = manager.createForOperator(existing.server, UUID.randomUUID(), "Orbit-only planet",
                    StarSystemContent.TAU_CETI_E);
            allowed.station().ifPresent(created::add);
            helper.assertTrue(allowed.success(), "Operator creation refused a real orbit-only body: " + allowed.code());
            helper.assertTrue(existing.station().equals(existing.created), "An existing Space-orbit station changed");
        } finally {
            for (var station : created) {
                existing.data.delete(station.stationId());
                StationNoticeSavedData.get(existing.server).remove(station.stationId());
                existing.platforms.removeTemplate(existing.space, station.cell());
            }
            existing.close();
        }
        helper.succeed();
    }
}
