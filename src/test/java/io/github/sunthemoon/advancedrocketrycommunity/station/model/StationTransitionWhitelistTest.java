package io.github.sunthemoon.advancedrocketrycommunity.station.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState.Transition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 5.2: the checked-update whitelist gains exactly ALTITUDE_ONLY and ORIENTATION_ONLY. It does
 * not relax growth, gravity, relocation, pad, owner, region, shrink or vacuum checks.
 */
final class StationTransitionWhitelistTest {
    private static final StationGridCell CELL = new StationGridCell(2, -1);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void eachNamedTransitionChangesOnlyItsOwnField() {
        StationState base = station(StationLimits.REGION_SIZE, StationEnvironmentProfile.BASIC_SPACE,
                StationOrbitalControls.initial(270_000));
        assertEquals(Optional.of(Transition.GROWTH), base.withExpandedRegion().checkedTransitionFrom(base));
        assertEquals(Optional.of(Transition.GRAVITY_ONLY), base.withGravityMilli(500).checkedTransitionFrom(base));
        StationState higher = base.withOrbitalControls(base.orbitalControls().withDistance(190));
        assertEquals(Optional.of(Transition.ALTITUDE_ONLY), higher.checkedTransitionFrom(base));
        StationState turning = base.withOrbitalControls(base.orbitalControls().withRates(1, -2, 3, 40L));
        assertEquals(Optional.of(Transition.ORIENTATION_ONLY), turning.checkedTransitionFrom(base));
        StationState fixed = base.withOrbitalControls(base.orbitalControls().withPhases(1, 2, 3, 0L));
        assertEquals(Optional.of(Transition.ORIENTATION_ONLY), fixed.checkedTransitionFrom(base));
        assertTrue(turning.isCheckedUpdateOf(base) && higher.isCheckedUpdateOf(base));
        assertTrue(base.withOrbitBody(ModIdentity.id("moon")).isOrbitRelocationOf(base));
        assertFalse(base.withOrbitBody(ModIdentity.id("moon")).isCheckedUpdateOf(base),
                "relocation stays separate from the checked management transitions");
    }

    @Test
    void mixedChangesAndEveryOtherFieldAreRefused() {
        StationState base = station(StationLimits.REGION_SIZE, StationEnvironmentProfile.BASIC_SPACE,
                new StationOrbitalControls(10, 1, 2, 3, 4, 5, 6, 100L));
        StationOrbitalControls controls = base.orbitalControls();
        List<StationState> refused = List.of(
                base,
                // Two transitions at once.
                base.withOrbitalControls(controls.withDistance(11).withRates(0, 0, 0, 200L)),
                base.withGravityMilli(200).withOrbitalControls(controls.withDistance(11)),
                base.withExpandedRegion().withOrbitalControls(controls.withDistance(11)),
                base.withExpandedRegion().withGravityMilli(200),
                base.withOrbitBody(ModIdentity.id("moon")).withOrbitalControls(controls.withDistance(11)),
                // An epoch that goes back.
                base.withOrbitalControls(new StationOrbitalControls(10, 1, 2, 3, 4, 5, 6, 99L)),
                // Vacuum and solar angle are not checked fields.
                station(StationLimits.REGION_SIZE, new StationEnvironmentProfile(0, false, 270_000), controls),
                station(StationLimits.REGION_SIZE, new StationEnvironmentProfile(0, true, 1), controls),
                // Ownership, team and identity.
                base.transferOwnership(new UUID(9, 9)),
                base.invite(new UUID(9, 10)));
        for (StationState candidate : refused) {
            assertEquals(Optional.empty(), candidate.checkedTransitionFrom(base), candidate.toString());
            assertFalse(candidate.isCheckedUpdateOf(base));
        }
        // Shrinking back is not growth.
        StationState expanded = base.withExpandedRegion();
        assertEquals(Optional.empty(), base.checkedTransitionFrom(expanded));
        assertFalse(expanded.withOrbitalControls(controls.withDistance(11)).withOrbitBody(ModIdentity.id("moon"))
                .isOrbitRelocationOf(expanded), "a relocation cannot also change controls");
    }

    private static StationState station(int width, StationEnvironmentProfile environment,
                                        StationOrbitalControls controls) {
        return new StationState(StationLimits.STATE_SCHEMA_VERSION, new UUID(70, 1), new UUID(70, 2), "Whitelist",
                CELL, CELL.region(width), CELL.landingPad(), ModIdentity.id("earth"), 5L, environment,
                List.of(new UUID(70, 3)), List.of(), controls);
    }
}
