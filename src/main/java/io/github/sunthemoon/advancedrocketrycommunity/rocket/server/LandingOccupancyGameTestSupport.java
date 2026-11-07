package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * W311F: the landing selector ({@code RocketLandingPadSelector}) refuses an arrival in two steps. The quick check refuses
 * for rockets whose entity box touches the arrival; the structure discovery ({@code NamedLandingPadOccupancy}, W13 R1)
 * then refuses for rockets whose structure overlaps it. Since W311 (R359) the entity box follows the structure, so the
 * quick check catches most wide occupants first. Named pads run only the discovery.
 */
final class LandingOccupancyGameTestSupport {
    private LandingOccupancyGameTestSupport() { }

    /** The arrival's whole blocks, the box that the selector's quick check searches. */
    static AABB arrivalBox(RocketRegion arrival) {
        return new AABB(arrival.minimum().x(), arrival.minimum().y(), arrival.minimum().z(),
                arrival.maximum().x() + 1.0D, arrival.maximum().y() + 1.0D, arrival.maximum().z() + 1.0D);
    }

    /** The selector's quick check: every rocket that vanilla's entity sections return for the arrival's box. */
    static List<RocketEntity> quickCheck(ServerLevel level, RocketRegion arrival) {
        return level.getEntitiesOfClass(RocketEntity.class, arrivalBox(arrival));
    }

    /** The rocket type's fixed 1.5 by 4 box at the rocket's position, which was its whole entity box before W311. */
    static AABB baseBox(RocketEntity rocket) {
        return ModEntities.ROCKET.get().getDimensions().makeBoundingBox(rocket.position());
    }

    /**
     * Test-only: narrows a parked occupant's entity box to the type's base box without moving it, so that the quick check
     * misses it and only the structure discovery can refuse the arrival, as W13 R1 intended. The narrowed box lasts until
     * the rocket's snapshot or flight data changes or the rocket is reloaded. A rocket that already has its base box is
     * left as it is, and the quick check is asserted again. {@code test} starts each failure message.
     */
    static void leaveToDiscovery(GameTestHelper helper, ServerLevel level, RocketEntity parked, RocketRegion arrival,
            String test) {
        AABB arrivalBox = arrivalBox(arrival);
        AABB base = baseBox(parked);
        helper.assertTrue(!base.intersects(arrivalBox), test + ": the occupant's base box overlaps the arrival, so the"
                + " quick check, not the structure discovery, would refuse it");
        if (!parked.getBoundingBox().equals(base)) {
            helper.assertTrue(parked.getBoundingBox().intersects(arrivalBox), test + ": the occupant's structure-sized"
                    + " entity box (W311) does not reach the arrival, so narrowing it no longer changes which step refuses");
            parked.setBoundingBox(base);
        }
        var found = quickCheck(level, arrival);
        helper.assertTrue(found.isEmpty(), test + ": with the occupant's base box, the selector's quick check still finds "
                + found.size() + " rocket(s) at the arrival, so the structure discovery would not be what refuses it");
    }
}
