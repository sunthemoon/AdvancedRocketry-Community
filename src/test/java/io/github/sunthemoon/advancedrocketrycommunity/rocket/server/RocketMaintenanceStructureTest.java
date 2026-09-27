package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketFlightNetwork;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RocketMaintenanceStructureTest {
    private static final Path SERVER_SOURCE = Path.of(
            "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server"
    );
    private static final Path GAMETEST_SOURCE = Path.of(
            "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest"
    );

    @Test
    void extractedProductionResponsibilitiesStayBelowReviewBudget() throws IOException {
        assertLineBudget(SERVER_SOURCE.resolve("RocketManager.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketAssemblerScanService.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketDisassemblyService.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketFlightLifecycleController.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketInteraction.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketTargetContextService.java"), 500);
        assertLineBudget(SERVER_SOURCE.resolve("RocketTransactionExecutor.java"), 500);
    }

    @Test
    void splitFlightGameTestsStayBelowMandatoryAdrThreshold() throws IOException {
        assertLineBudget(GAMETEST_SOURCE.resolve("RocketFlightGameTests.java"), 800);
        assertLineBudget(GAMETEST_SOURCE.resolve("RocketFlightSecurityGameTests.java"), 800);
        assertLineBudget(GAMETEST_SOURCE.resolve("RocketTransferRecoveryGameTests.java"), 800);
        assertLineBudget(GAMETEST_SOURCE.resolve("RocketFlightGameTestFixtures.java"), 800);
    }

    @Test
    void saveSchemasRemainStableAndFlightChannelUsesNavigationRevision() {
        assertEquals(2, RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION);
        assertEquals(3, RocketFlightPlan.SCHEMA_VERSION);
        assertEquals(2, RocketFlightLimits.TRANSFER_JOURNAL_SCHEMA_VERSION);
        assertEquals("7", RocketFlightNetwork.protocolVersion());
    }

    @Test
    void allFiveFlightScenariosRemainRegisteredExactlyOnce() throws IOException {
        List<Path> classes = List.of(
                GAMETEST_SOURCE.resolve("RocketFlightGameTests.java"),
                GAMETEST_SOURCE.resolve("RocketFlightSecurityGameTests.java"),
                GAMETEST_SOURCE.resolve("RocketTransferRecoveryGameTests.java")
        );
        String sources = String.join("\n", classes.stream().map(RocketMaintenanceStructureTest::read).toList());
        List<String> methods = List.of(
                "earthStationEarthUsesApprovedPadAndPreservesNeighborSpace",
                "earthMoonRoundTripConservesFuelAndBlockedPadReturnsSource",
                "simultaneousRocketsUseDisjointAuthorityAndPads",
                "hostileFlightIntentsCannotChangeAuthority",
                "transferRecoveryReconcilesAllEntityPresenceCases"
        );
        for (String method : methods) {
            assertEquals(1, occurrences(sources, "public static void " + method + "("), method);
        }
        assertEquals(5, occurrences(sources, "@GameTest("));
    }

    private static void assertLineBudget(Path path, long exclusiveUpperBound) throws IOException {
        try (var lines = Files.lines(path)) {
            long count = lines.count();
            assertTrue(count < exclusiveUpperBound, path + " has " + count + " lines");
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read " + path, exception);
        }
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
