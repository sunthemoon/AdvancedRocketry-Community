package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.Test;

class RecipeSignatureMigrationTest {
    private static final String ID = "advancedrocketrycommunity:rolling_iron_bars";

    @Test void unprovenOldRootsRemainByteEquivalentWithoutConversion() {
        CompoundTag original = new CompoundTag();
        original.put("arce_process", StringTag.valueOf("old process"));
        original.put("arce_process_journal", StringTag.valueOf("old journal"));
        RecipeSignatureMigration migration = new RecipeSignatureMigration(); migration.load(original, true, ID);
        assertFalse(migration.current()); assertFalse(migration.unsupported());
        CompoundTag saved = new CompoundTag(); saved.putString("arce_process", "replacement"); migration.save(saved, ID);
        assertEquals(original, saved);
        original.putString("arce_process", "caller mutation");
        assertEquals("old process", saved.getString("arce_process"));
    }

    @Test void unprovedOldProcessAndPendingJournalRemainVerbatimAcrossTwoNbtLoads() {
        CompoundTag old = new CompoundTag(); old.putString("arce_process", "before");
        old.putString("arce_process_journal", "retained pending"); old.putString("arce_machine", "legacy input");
        RecipeSignatureMigration migration = new RecipeSignatureMigration(); migration.load(old, true, ID);
        for (int restart = 0; restart < 2; restart++) {
            CompoundTag next = new CompoundTag(); next.putString("arce_process", "attempted replacement");
            migration.save(next, ID); assertEquals(old, next); assertFalse(next.contains(RecipeSignatureMigration.ROOT));
            RecipeSignatureMigration restored = new RecipeSignatureMigration(); restored.load(next, true, ID);
            assertFalse(restored.current()); assertFalse(restored.unsupported()); migration = restored;
        }
    }

    @Test void freshIdleOmitsIdAndActiveMarkerMustMatchRetainedBoundedId() {
        RecipeSignatureMigration fresh = new RecipeSignatureMigration(); CompoundTag idle = new CompoundTag();
        fresh.save(idle, null);
        var marker = idle.getCompound(RecipeSignatureMigration.ROOT);
        assertFalse(marker.contains("recipe_id")); assertFalse(marker.getBoolean("converted"));
        RecipeSignatureMigration restored = new RecipeSignatureMigration(); restored.load(idle, false, null);
        assertTrue(restored.current());
        CompoundTag active = new CompoundTag(); fresh.save(active, ID);
        restored.load(active, true, ID); assertTrue(restored.current());
        for (String id : new String[]{"arce_test:other", "", "x".repeat(129)}) {
            restored.load(active, true, id); assertTrue(restored.unsupported());
            CompoundTag saved = new CompoundTag(); restored.save(saved, id); assertEquals(active, saved);
        }
        restored.load(active, false, null); assertTrue(restored.unsupported(), "active marker is not idle proof");
    }

    @Test void alreadyCurrentConvertedFlagAndActiveIdentitySurviveTwoNbtLoads() {
        for (boolean converted : new boolean[]{false, true}) {
            CompoundTag parent = new CompoundTag(); new RecipeSignatureMigration().save(parent, ID);
            parent.getCompound(RecipeSignatureMigration.ROOT).putBoolean("converted", converted);
            for (int restart = 0; restart < 2; restart++) {
                RecipeSignatureMigration restored = new RecipeSignatureMigration(); restored.load(parent, true, ID);
                assertTrue(restored.current()); assertFalse(restored.unsupported());
                CompoundTag saved = new CompoundTag(); restored.save(saved, ID);
                assertEquals(parent, saved); assertEquals(converted, saved.getCompound(RecipeSignatureMigration.ROOT).getBoolean("converted"));
                parent = saved;
            }
        }
    }

    @Test void futureAndMalformedBoundedMarkersPreserveAllRootsAndRefuseAcceptance() {
        for (int variant = 0; variant < 6; variant++) {
            CompoundTag original = new CompoundTag(); CompoundTag marker = new CompoundTag();
            marker.putInt("schema_version", variant == 0 ? 2 : 1);
            marker.putString("format", variant == 1 ? "json_sha256" : "json_v1");
            marker.putBoolean("converted", false); marker.putString("recipe_id", ID);
            if (variant == 2) { marker.remove("converted"); }
            if (variant == 3) { marker.putInt("converted", 1); }
            if (variant == 4) { marker.putByte("converted", (byte) 2); }
            if (variant == 5) { marker.putString("recipe_id", "not canonical"); }
            original.put(RecipeSignatureMigration.ROOT, marker); original.putString("arce_process", "retained");
            RecipeSignatureMigration migration = new RecipeSignatureMigration(); migration.load(original, true, ID);
            assertTrue(migration.unsupported()); assertFalse(migration.current()); assertFalse(migration.oversized());
            CompoundTag output = new CompoundTag(); migration.save(output, ID); assertEquals(original, output);
        }
    }

    @Test void oversizedDeepAndNodeHeavyRootsNeverCopyAndVetoOutgoingChunkWithRetry() {
        for (String root : new String[]{RecipeSignatureMigration.ROOT, "arce_process", "arce_process_journal", "arce_machine"}) {
            for (int variant = 0; variant < 3; variant++) {
                CompoundTag raw = new NoCopyTag();
                if (variant == 0) { raw.putByteArray("huge", new byte[65_537]); }
                if (variant == 1) {
                    CompoundTag node = raw;
                    for (int depth = 0; depth < 5_000; depth++) {
                        CompoundTag child = new CompoundTag(); node.put("next", child); node = child;
                    }
                }
                if (variant == 2) {
                    ListTag nodes = new ListTag();
                    for (int index = 0; index < 4_097; index++) { nodes.add(new CompoundTag()); }
                    raw.put("nodes", nodes);
                }
                CompoundTag original = new CompoundTag(); original.put(root, raw);
                RecipeSignatureMigration migration = new RecipeSignatureMigration(); migration.load(original, true, ID);
                assertTrue(migration.unsupported()); assertTrue(migration.oversized());
                CompoundTag output = new CompoundTag(); migration.save(output, ID);
                assertSame(raw, output.get(root));
                for (String controller : new String[]{"rolling_machine", "precision_assembler", "electrolyzer"}) {
                    output.putString("id", "advancedrocketrycommunity:" + controller);
                    ListTag entities = new ListTag(); entities.add(output);
                    CompoundTag chunk = new CompoundTag(); chunk.put("block_entities", entities);
                    boolean[] retry = {false};
                    assertThrows(IllegalStateException.class, () ->
                            RecipeSignatureProtection.requireBoundedChunk(chunk, 256, () -> retry[0] = true));
                    assertTrue(retry[0]); assertSame(raw, output.get(root));
                }
            }
        }
    }

    private static final class NoCopyTag extends CompoundTag {
        @Override public CompoundTag copy() { throw new AssertionError("unbounded copy"); }
    }
}
