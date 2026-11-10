package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteRegistryGameTestSupport;
import net.minecraft.gametest.framework.GameTestHelper;

/** A bounded setup wait, separate from the unchanged measured budget of a registry-request test. */
final class SatelliteRegistryTestWindow {
    private final int measuredTicks;
    private long openedAt = -1;

    SatelliteRegistryTestWindow(int measuredTicks) { this.measuredTicks = measuredTicks; }

    // At most measuredTicks + 1 ticks to pass an autosave, plus measuredTicks for an inherited writer request.
    static int setupTicks(int measuredTicks) { return 2 * measuredTicks + 1; }

    static boolean ready(int tickCount, boolean writerBusy, int measuredTicks) {
        return !writerBusy && GameTestAutosave.ticksUntil(tickCount) > measuredTicks;
    }

    boolean open(GameTestHelper helper, SatelliteMissionSavedData data) {
        var server = helper.getLevel().getServer();
        if (!ready(server.getTickCount(), SatelliteRegistryGameTestSupport.busy(server, data), measuredTicks)) {
            return false;
        }
        openedAt = helper.getTick();
        if (openedAt > 0) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_REGISTRY_TEST_WINDOW waited={} server_tick={} measured_budget={}",
                    openedAt, server.getTickCount(), measuredTicks);
        }
        return true;
    }

    void checkBudget(GameTestHelper helper) {
        if (openedAt < 0) {
            helper.assertTrue(helper.getTick() <= setupTicks(measuredTicks), "Registry fixture setup exceeded its own wait bound");
        } else {
            helper.assertTrue(helper.getTick() - openedAt < measuredTicks, "Registry request exceeded its unchanged measured timeout");
        }
    }
}
