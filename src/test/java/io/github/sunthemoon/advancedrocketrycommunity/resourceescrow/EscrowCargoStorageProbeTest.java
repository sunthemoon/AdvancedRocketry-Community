package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowCargoStorageProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void storageFixturesRequireBothPropertiesAndNeverActivateOnRecovery() {
        for (String suffix : List.of("E1_OWN_STORE", "E2_CAPTURE", "E2_ROCKET_EXTERNAL", "E4L1_FAIL", "E4L1_UNKNOWN", "E4L2_UNKNOWN")) {
            String name = "FC_LOAD_" + suffix;
            assertTrue(EscrowCargoStorageProbe.selected(true, name, false));
            assertFalse(EscrowCargoStorageProbe.selected(false, name, false));
            assertFalse(EscrowCargoStorageProbe.selected(true, name, true));
            assertFalse(EscrowCargoStorageProbe.selected(true, "IC_LOAD_" + suffix, false));
            assertFalse(EscrowCargoStorageProbe.selected(true, "FC_UNLOAD_" + suffix, false));
        }
        for (String name : new String[] {null, "", "FC_LOAD_K1", "FC_LOAD_E2"}) {
            assertFalse(EscrowCargoStorageProbe.selected(true, name, false));
        }
    }
    @Test void d2ObservationsUseExactCasesLabelsAndSecondForceOnly() {
        for (String direction : List.of("LOAD", "UNLOAD")) {
            for (String[] pair : List.of(new String[] {"C2", "C2_DESTINATION_AFTER"}, new String[] {"C5", "BARRIER_FORCE_AFTER"},
                    new String[] {"C6", "C6_JOINT_READBACK"}, new String[] {"C7", "K9"})) {
                String name = "FC_" + direction + "_D2_" + pair[0];
                assertTrue(EscrowCargoStorageProbe.d2Cut(true, name, false, 2, pair[1]));
                assertFalse(EscrowCargoStorageProbe.d2Cut(false, name, false, 2, pair[1]));
                assertFalse(EscrowCargoStorageProbe.d2Cut(true, name, true, 2, pair[1]));
                assertFalse(EscrowCargoStorageProbe.d2Cut(true, name, false, 2, "OTHER"));
                assertFalse(EscrowCargoStorageProbe.d2Cut(true, name.replace("FC_", "IC_"), false, 2, pair[1]));
            }
            for (int stores : List.of(0, 1, 3)) {
                assertFalse(EscrowCargoStorageProbe.d2Cut(true, "FC_" + direction + "_D2_C5", false, stores, "BARRIER_FORCE_AFTER"));
            }
        }
        assertFalse(EscrowCargoStorageProbe.d2Cut(true, null, false, 2, "K9"));
    }
    @Test void committerAliasesSelectOnlyTheTwoUnknownCargoCases() {
        assertEquals("E4L1_UNKNOWN", EscrowCargoStorageProbe.writerCase("FC_LOAD_E4L1_UNKNOWN"));
        assertEquals("E4L2_UNKNOWN", EscrowCargoStorageProbe.writerCase("FC_LOAD_E4L2_UNKNOWN"));
        for (String name : List.of("", "FC_LOAD_E4L1_FAIL", "FC_UNLOAD_E4L1_UNKNOWN", "IC_LOAD_E4L2_UNKNOWN", "E4L1_UNKNOWN")) {
            assertEquals(name, EscrowCargoStorageProbe.writerCase(name));
        }
    }
    @Test void cargoOwnStoreFaultRetainsAllExistingExactSlotAndStartGates() {
        var slot = new ChunkPos(4, 4); String name = EscrowCargoStorageProbe.E1;
        assertTrue(EscrowStoreFault.selected(true, name, "start", "minecraft:overworld", slot));
        assertFalse(EscrowStoreFault.selected(false, name, "start", "minecraft:overworld", slot));
        assertFalse(EscrowStoreFault.selected(true, name, "recover", "minecraft:overworld", slot));
        assertFalse(EscrowStoreFault.selected(true, name, "start", "minecraft:the_nether", slot));
        assertFalse(EscrowStoreFault.selected(true, name, "start", "minecraft:overworld", new ChunkPos(4, 5)));
        assertFalse(EscrowStoreFault.selected(true, "FC_UNLOAD_E1_OWN_STORE", "start", "minecraft:overworld", slot));
    }
}
