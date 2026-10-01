package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-055 sections 2 and 4: the operation order, cost, output-full retry and settings bounds. */
final class LaserDrillOperationTest {
    private static CelestialCatalog catalog;
    private static LaserDrillTables tables;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
        catalog = LaserDrillTablesTest.catalog();
        tables = LaserDrillTables.create(List.of(LaserDrawTest.REFERENCE), catalog).result().orElseThrow();
    }

    @Test
    void theFirstFailingConditionWinsInContractOrder() {
        Builder all = new Builder();
        assertEquals(EndgameCode.OK, decide(all));
        List<EndgameCode> expected = List.of(EndgameCode.UNOWNED, EndgameCode.SYSTEM_DISABLED,
                EndgameCode.STATION_UNAVAILABLE, EndgameCode.UNFORMED, EndgameCode.NO_LENS, EndgameCode.STOPPED,
                EndgameCode.REDSTONE_BLOCKED, EndgameCode.ACTIVE_LIMIT, EndgameCode.ORBIT_BODY_UNAVAILABLE,
                EndgameCode.NO_TABLE, EndgameCode.INSUFFICIENT_ENERGY, EndgameCode.OUTPUT_FULL);
        // Failing every condition from the last one up: each added failure takes over the reported code.
        Builder failing = new Builder();
        List<EndgameCode> observed = new ArrayList<>();
        for (int step = expected.size() - 1; step >= 0; step--) {
            failing.fail(step);
            observed.add(0, decide(failing));
        }
        assertEquals(expected, observed);
        assertEquals(EndgameCode.STRUCTURE_UNLOADED, decide(new Builder().structure(EndgameCode.STRUCTURE_UNLOADED)));
    }

    @Test
    void anOperationTakesTheDrawAtTheCurrentIndex() {
        LaserDrillOperation.Decision decision = LaserDrillOperation.decide(new Builder().index(8).build(), stack -> true);
        assertTrue(decision.operates());
        assertEquals(LaserDraw.draw(LaserDrawTest.REFERENCE, 0L, 8), decision.stack().orElseThrow());
        assertEquals("diamond", decision.stack().orElseThrow().item().getPath(), "reference vector seed 0, n = 8");
    }

    @Test
    void aFullOutputKeepsTheSameDrawForTheRetry() {
        LaserDrillOperation.Inputs inputs = new Builder().index(6).build();
        LaserDrillOperation.Decision full = LaserDrillOperation.decide(inputs, stack -> false);
        assertEquals(EndgameCode.OUTPUT_FULL, full.code());
        LaserDrillOperation.Decision retry = LaserDrillOperation.decide(inputs, stack -> true);
        assertEquals(full.stack(), retry.stack(), "the index did not advance, so the same stack is retried");
        assertEquals("raw_gold", retry.stack().orElseThrow().item().getPath());
    }

    @Test
    void theEnergyMustCoverTheWholeCost() {
        assertEquals(EndgameCode.INSUFFICIENT_ENERGY, decide(new Builder().energy(9_999)));
        assertEquals(EndgameCode.OK, decide(new Builder().energy(10_000)));
    }

    @Test
    void theCostFollowsTheEnergyPercent() {
        int[][] vectors = {{10, 1_000}, {100, 10_000}, {250, 25_000}, {1_000, 100_000}};
        for (int[] vector : vectors) {
            LaserDrillSettings settings = new LaserDrillSettings(vector[0], 20, 64, 32, 4, 32, 7);
            assertEquals(vector[1], settings.costFe());
        }
        assertEquals(10_000, LaserDrillSettings.DEFAULTS.costFe());
    }

    @Test
    void settingsOutsideTheirRangesAreRejected() {
        int[][] invalid = {
                {9, 20, 64, 32, 4, 32, 7}, {1_001, 20, 64, 32, 4, 32, 7}, {100, 19, 64, 32, 4, 32, 7},
                {100, 1_201, 64, 32, 4, 32, 7}, {100, 20, 0, 32, 4, 32, 7}, {100, 20, 257, 32, 4, 32, 7},
                {100, 20, 64, 33, 4, 32, 7}, {100, 20, 64, 32, 5, 32, 7}, {100, 20, 64, 32, 4, 33, 7},
                {100, 20, 64, 32, 4, 32, 8}, {100, 20, 64, 0, 4, 32, 7}};
        for (int[] v : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LaserDrillSettings(v[0], v[1], v[2], v[3], v[4], v[5], v[6]));
        }
    }

    private static EndgameCode decide(Builder builder) {
        return LaserDrillOperation.decide(builder.build(), stack -> builder.outputAccepts).code();
    }

    /** All conditions hold by default: a formed, owned, running drill on an Earth station with energy and room. */
    private static final class Builder {
        boolean owned = true;
        boolean enabled = true;
        EndgameCode station = EndgameCode.OK;
        EndgameCode structure = EndgameCode.OK;
        boolean lens = true;
        boolean running = true;
        boolean redstone = true;
        boolean admitted = true;
        Optional<CelestialBodyDefinition> body = catalog.get(CelestialIds.EARTH_ID);
        LaserDrillTables drillTables = tables;
        long energy = 200_000;
        long index;
        boolean outputAccepts = true;

        void fail(int condition) {
            switch (condition) {
                case 0 -> owned = false;
                case 1 -> enabled = false;
                case 2 -> station = EndgameCode.STATION_UNAVAILABLE;
                case 3 -> structure = EndgameCode.UNFORMED;
                case 4 -> lens = false;
                case 5 -> running = false;
                case 6 -> redstone = false;
                case 7 -> admitted = false;
                case 8 -> body = Optional.empty();
                // The reference set has only a default table; a gas giant cannot use it.
                case 9 -> body = catalog.get(PlanetaryContent.GAS_GIANT);
                case 10 -> energy = 0;
                case 11 -> outputAccepts = false;
                default -> throw new IllegalArgumentException();
            }
        }

        Builder structure(EndgameCode code) {
            structure = code;
            return this;
        }

        Builder energy(long value) {
            energy = value;
            return this;
        }

        Builder index(long value) {
            index = value;
            return this;
        }

        LaserDrillOperation.Inputs build() {
            return new LaserDrillOperation.Inputs(owned, enabled, station, structure, lens, running, redstone, admitted,
                    body, drillTables, energy, 10_000, 0L, index);
        }
    }
}
