package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Marker codec and same-snapshot preservation guard; existing process/journal codecs remain unchanged. */
public final class RecipeSignatureMigration {
    public static final String ROOT = "arce_recipe_signature";
    public static final int SCHEMA = 1;
    public static final int MAX_BYTES = 1_024;
    private static final String PROCESS_ROOT = "arce_process";
    private static final String JOURNAL_ROOT = "arce_process_journal";
    private static final String LEGACY_ROOT = "arce_machine";
    private static final Set<String> IDLE_FIELDS = Set.of("schema_version", "format", "converted");
    private static final Set<String> ACTIVE_FIELDS = Set.of("schema_version", "format", "converted", "recipe_id");
    private boolean current = true;
    private boolean unsupported;
    private boolean oversized;
    private boolean converted;
    private Tag rawMarker;
    private Tag oldProcess;
    private Tag oldJournal;
    private Tag oldLegacy;

    public void reset() {
        current = true; unsupported = false; oversized = false; converted = false;
        rawMarker = null; oldProcess = null; oldJournal = null; oldLegacy = null;
    }

    /** Must run before existing recursive codecs, copying, equality or item decoding. */
    public static boolean bounded(CompoundTag parent) {
        return boundedRoot(parent, ROOT) && boundedRoot(parent, PROCESS_ROOT)
                && boundedRoot(parent, JOURNAL_ROOT) && boundedRoot(parent, LEGACY_ROOT);
    }

    private static boolean boundedRoot(CompoundTag parent, String key) {
        Tag raw = parent.get(key);
        return raw == null || BoundedNbt.fits(raw, ROOT.equals(key) ? MAX_BYTES : 65_536,
                ROOT.equals(key) ? 16 : 20, ROOT.equals(key) ? 256 : 4_096);
    }

    public void load(CompoundTag parent, boolean hasWork, @Nullable String retainedRecipeId) {
        reset();
        current = false;
        oversized = !bounded(parent);
        // Hold unbounded roots by reference only; veto their outgoing chunk before storage writes.
        // Throwing inside saveAdditional would instead let LevelChunk omit the BlockEntity.
        rawMarker = capture(parent, ROOT);
        oldProcess = capture(parent, PROCESS_ROOT);
        oldJournal = capture(parent, JOURNAL_ROOT);
        oldLegacy = capture(parent, LEGACY_ROOT);
        if (oversized) { unsupported = true; return; }
        if (rawMarker == null) {
            if (!hasWork) { accept(); }
            return;
        }
        if (!(rawMarker instanceof CompoundTag marker)
                || !(hasWork ? ACTIVE_FIELDS : IDLE_FIELDS).equals(marker.getAllKeys())
                || !marker.contains("schema_version", Tag.TAG_INT)
                || marker.getInt("schema_version") != SCHEMA
                || !marker.contains("format", Tag.TAG_STRING)
                || !"json_v1".equals(marker.getString("format"))
                || !marker.contains("converted", Tag.TAG_BYTE)
                || marker.getByte("converted") < 0 || marker.getByte("converted") > 1
                || hasWork && (!marker.contains("recipe_id", Tag.TAG_STRING)
                    || !validRecipeId(marker.getString("recipe_id"))
                    || !marker.getString("recipe_id").equals(retainedRecipeId))) {
            unsupported = true;
            return;
        }
        converted = marker.getBoolean("converted");
        accept();
    }

    public boolean current() { return current; }
    public boolean unsupported() { return unsupported; }
    public boolean oversized() { return oversized; }

    private void accept() {
        if (unsupported) { throw new IllegalStateException("unsupported signature marker cannot be converted"); }
        current = true;
        rawMarker = null; oldProcess = null; oldJournal = null; oldLegacy = null;
    }

    /** Called after normal process/journal encoding, in the same controller snapshot. */
    public void save(CompoundTag parent, @Nullable String retainedRecipeId) {
        if (current) {
            CompoundTag marker = new CompoundTag();
            marker.putInt("schema_version", SCHEMA);
            marker.putString("format", "json_v1");
            marker.putBoolean("converted", converted);
            if (retainedRecipeId != null) {
                if (!validRecipeId(retainedRecipeId)) { throw new IllegalStateException("invalid marker recipe ID"); }
                marker.putString("recipe_id", retainedRecipeId);
            }
            parent.put(ROOT, marker);
            return;
        }
        restore(parent, ROOT, rawMarker);
        restore(parent, PROCESS_ROOT, oldProcess);
        restore(parent, JOURNAL_ROOT, oldJournal);
        restore(parent, LEGACY_ROOT, oldLegacy);
    }

    private Tag capture(CompoundTag parent, String key) {
        Tag raw = parent.get(key);
        return raw == null || !boundedRoot(parent, key) ? raw : raw.copy();
    }

    private void restore(CompoundTag parent, String key, Tag raw) {
        if (raw == null) { parent.remove(key); }
        else { parent.put(key, oversized ? raw : raw.copy()); }
    }

    private static boolean validRecipeId(String value) {
        ResourceLocation id = value.length() <= io.github.sunthemoon.advancedrocketrycommunity.machine.process
                .ProcessResourceKey.MAX_RESOURCE_ID_CHARS ? ResourceLocation.tryParse(value) : null;
        return id != null && id.toString().equals(value);
    }
}
