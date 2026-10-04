package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.energy.EnergyStorage;
import org.junit.jupiter.api.Test;

class RecipeSignatureReleaseTestCommandsTest {
    private static final String ID = "advancedrocketrycommunity:rolling_iron_bars";

    private static CompoundTag partial() {
        CompoundTag root = new CompoundTag(), marker = new CompoundTag(), process = new CompoundTag();
        marker.putInt("schema_version", 1); marker.putString("format", "json_v1");
        marker.putBoolean("converted", false); marker.putString("recipe_id", ID);
        process.putInt("schema_version", 1); process.putString("definition_id", ID);
        process.putString("recipe_signature", "ab".repeat(32)); process.putInt("progress_ticks", 5);
        process.putLong("consumed_energy", 100);
        root.put("arce_recipe_signature", marker); root.put(ProcessStatePersistence.ROOT, process);
        return root;
    }

    @Test void onlyHighestPermissionConsoleIsAuthorized() {
        assertTrue(RecipeSignatureReleaseTestCommands.consolePermission(true, true, "Server"));
        assertFalse(RecipeSignatureReleaseTestCommands.consolePermission(false, true, "Server"));
        assertFalse(RecipeSignatureReleaseTestCommands.consolePermission(true, false, "Server"));
        assertFalse(RecipeSignatureReleaseTestCommands.consolePermission(true, true, "Rcon"));
        assertFalse(RecipeSignatureReleaseTestCommands.consolePermission(true, true, null));
    }
    @Test void allCellsAreDistinctInOneFixedChunk() {
        var positions = new HashSet<net.minecraft.core.BlockPos>();
        for (int row = 0; row < 7; row++) {
            for (int machine = 0; machine < 3; machine++) {
                var position = RecipeSignatureReleaseTestCommands.position(row, machine);
                assertEquals(15, position.getX() >> 4); assertEquals(15, position.getZ() >> 4);
                assertTrue(position.getY() >= 128 && position.getY() <= 224); assertTrue(positions.add(position));
            }
        }
        assertEquals(21, positions.size());
        assertThrows(IllegalArgumentException.class, () -> RecipeSignatureReleaseTestCommands.position(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> RecipeSignatureReleaseTestCommands.position(7, 0));
        assertThrows(IllegalArgumentException.class, () -> RecipeSignatureReleaseTestCommands.position(0, 3));
    }
    @Test void exactPartialEnergyDoesNotModifySnapshot() {
        var root = partial(); var before = root.copy();
        assertEquals(1900, RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
        assertEquals(before, root);
    }
    @Test void unmarkedLegacyAndOpaqueMarkerAreNeverPowered() {
        var root = partial(); root.remove("arce_recipe_signature");
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
        root.putString("arce_recipe_signature", "opaque");
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
    }
    @Test void futureWrongWidthOrConvertedMarkerIsRefused() {
        for (int variant = 0; variant < 4; variant++) {
            var root = partial(); var marker = root.getCompound("arce_recipe_signature");
            switch (variant) {
                case 0 -> marker.putInt("schema_version", 2);
                case 1 -> marker.putLong("schema_version", 1);
                case 2 -> marker.putBoolean("converted", true);
                case 3 -> marker.putString("extension", "unknown");
            }
            assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
        }
    }
    @Test void mismatchedRecipeAndMalformedSignatureAreRefused() {
        var root = partial(); root.getCompound("arce_recipe_signature").putString("recipe_id", "minecraft:other");
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
        var malformed = partial(); malformed.getCompound(ProcessStatePersistence.ROOT).putString("recipe_signature", "zero");
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(malformed, ID, 100, 20));
    }
    @Test void pendingJournalRegardlessOfTagKindIsRefused() {
        var root = partial(); root.putString("arce_process_journal", "opaque");
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
    }
    @Test void incompleteOrOverCapacityClockIsRefused() {
        var root = partial(); root.getCompound(ProcessStatePersistence.ROOT).putLong("consumed_energy", 99);
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(root, ID, 100, 20));
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(partial(), ID, 72000, 20));
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.remainingEnergy(partial(), ID, 5, 20));
    }
    @Test void boundedOrdinaryEnergyTransferConservesExactAmount() {
        var energy = new EnergyStorage(20000, 200, 200);
        RecipeSignatureReleaseTestCommands.receiveBounded(energy, 1900);
        assertEquals(1900, energy.getEnergyStored());
    }
    @Test void alreadyChargedAndUnavailableStoresAreNotChanged() {
        var energy = new EnergyStorage(20000, 200, 200, 1);
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.receiveBounded(energy, 1900));
        assertEquals(1, energy.getEnergyStored());
        var unavailable = new EnergyStorage(20000, 0, 0);
        assertThrows(IllegalStateException.class, () -> RecipeSignatureReleaseTestCommands.receiveBounded(unavailable, 1900));
        assertEquals(0, unavailable.getEnergyStored());
    }
}
