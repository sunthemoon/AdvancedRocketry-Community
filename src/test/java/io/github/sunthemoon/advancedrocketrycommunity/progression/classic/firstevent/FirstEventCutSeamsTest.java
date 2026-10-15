package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.AtomicSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** W44 A0 case g: every seam is its production value unless both the cut and the hooks property are set. */
class FirstEventCutSeamsTest {
    @AfterEach void clear() {
        System.clearProperty(FirstEventCutSeams.PROPERTY);
        System.clearProperty(EscrowTestEndpoints.HOOKS);
        System.clearProperty(FirstEventCutSeams.PARTICIPANTS_PROPERTY);
    }

    @Test void absentPropertyOrDisabledHooksKeepEverySeamAtItsProductionValue() {
        assertProduction();
        System.setProperty(FirstEventCutSeams.PROPERTY, "FW1");
        assertProduction(); // hooks disabled: the cut property alone changes nothing
        System.setProperty(FirstEventCutSeams.PROPERTY, "FP9");
        assertTrue(FirstEventCutSeams.configured().isEmpty(), "hooks disabled");
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        assertEquals(FirstEventCutSeams.Cut.FP9, FirstEventCutSeams.configured().orElseThrow());
        // An unrelated root is unaffected by the load-fault cut.
        assertDoesNotThrow(() -> FirstEventCutSeams.beforeLoad(null, ManagedSavedDataType.STATIONS));
        System.setProperty(FirstEventCutSeams.PROPERTY, "FX-Ma");
        assertThrows(IllegalStateException.class, FirstEventCutSeams::configured, "an unknown ID fails at startup");
    }

    /** A null server proves the production path never consults the empty-server gate. */
    private static void assertProduction() {
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            assertDoesNotThrow(() -> FirstEventCutSeams.beforeLoad(null, type));
            io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.Committer normal =
                    (staged, target) -> fail("The policy resolution never performs IO");
            assertSame(normal, FirstEventCutSeams.ordinaryCommitter(type, normal));
        }
        assertTrue(FirstEventCutSeams.participants(null, FirstEventKind.FIRST_MOON_LANDING).isEmpty());
        assertTrue(FirstEventCutSeams.warpVisitorCount(null).isEmpty());
        assertTrue(FirstEventCutSeams.journalPolicy(null).isEmpty());
        assertSame(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, FirstEventCutSeams.directoryForce(null));
        assertSame(CheckedSavedDataPolicy.DirectoryForce.PLATFORM, FirstEventCutSeams.journalDirectoryForce(null));
        assertEquals(CheckedSavedDataPolicy.atomic(ManagedSavedDataType.STATIONS).type(),
                FirstEventCutSeams.stationPolicy(null, () -> fail("the production policy never runs the hook")).type());
        assertDoesNotThrow(() -> FirstEventCutSeams.boundary(null, FirstEventCutSeams.RECEIPT_CONFIRMED,
                FirstEventKind.FIRST_STATION_WARP, null));
        assertDoesNotThrow(() -> FirstEventCutSeams.beforeMoonElection(null, () -> fail("production does not save a fixture")));
        assertDoesNotThrow(() -> FirstEventCutSeams.fixtureTick(null));
    }

    @Test void aParticipantOnlyFixtureStillRequiresHooksAndTheEmptyServerGate() {
        System.setProperty(FirstEventCutSeams.PARTICIPANTS_PROPERTY, "true");
        assertTrue(FirstEventCutSeams.participants(null, FirstEventKind.FIRST_MOON_LANDING).isEmpty());
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        assertThrows(NullPointerException.class,
                () -> FirstEventCutSeams.participants(null, FirstEventKind.FIRST_MOON_LANDING));
        assertTrue(FirstEventCutSeams.configured().isEmpty(), "participant-only recovery cannot arm an IO cut");
        assertDoesNotThrow(() -> FirstEventCutSeams.beforeMoonElection(null, () -> fail("no cut is armed")));
    }

    /**
     * W72: production startup restores the persistence package's normal committer, and a configured cut installs its
     * selector only after the empty-server gate, so a refused gate leaves ordinary saves on the normal committer.
     */
    @Test void startupInstallsTheOrdinaryCommitterSelectorOnlyForAGatedCut(@TempDir Path root) throws Exception {
        MinecraftBootstrap.initialize();
        try {
            AtomicSavedData.installCommitterSelector((type, normal) -> fail("a stale selector was kept"));
            FirstEventCutSeams.validateAtStartup(null);
            assertProgressionFlushWrites(root.resolve("production"));
            System.setProperty(EscrowTestEndpoints.HOOKS, "true");
            System.setProperty(FirstEventCutSeams.PROPERTY, "FM12a");
            AtomicSavedData.installCommitterSelector((type, normal) -> fail("a stale selector was kept"));
            assertThrows(NullPointerException.class, () -> FirstEventCutSeams.validateAtStartup(null),
                    "the empty-server gate refuses before the selector is installed");
            assertProgressionFlushWrites(root.resolve("refused-gate"));
        } finally {
            AtomicSavedData.installCommitterSelector(null);
        }
    }

    private static void assertProgressionFlushWrites(Path directory) throws Exception {
        Files.createDirectories(directory);
        Path file = directory.resolve(ManagedSavedDataType.CLASSIC_PROGRESSION.fileName());
        ClassicProgressionSavedData.provenAbsent().flush(file);
        assertTrue(Files.isRegularFile(file));
    }

    @Test void theInventoryNamesExactlyTheThirtyNineHeadlessRows() {
        Set<String> ids = Arrays.stream(FirstEventCutSeams.Cut.values()).map(FirstEventCutSeams.Cut::id)
                .collect(Collectors.toSet());
        assertEquals(39, ids.size());
        assertTrue(ids.containsAll(Set.of("FM1", "FM2a", "FM12b", "FW4b", "FW11", "FP7a", "FP11")));
        assertFalse(ids.contains("FM10a"), "X rows use no seam");
        assertFalse(ids.contains("FW10a"), "V rows use no seam");
    }
}
