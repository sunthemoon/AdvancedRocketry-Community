package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.MigrationDiagnosticId;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataMigrationException;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ADR-054 section 10: the schema-1 endgame root codec, its bounds, the save epoch and the blocked load. */
class EndgameRootCodecTest {
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "laser_target");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);

    @TempDir
    Path directory;

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void everySectionRoundTripsByteStable() {
        EndgameRoot root = sample();
        CompoundTag encoded = EndgameRootCodec.encode(root, new CompoundTag());
        EndgameRoot decoded = EndgameRootCodec.decode(encoded);
        CompoundTag again = EndgameRootCodec.encode(decoded, new CompoundTag());
        assertEquals(encoded, again);
        assertEquals(root.endpoints(), decoded.endpoints());
        assertEquals(root.youngTombstones(), decoded.youngTombstones());
        assertEquals(root.settledTombstones(), decoded.settledTombstones());
        assertEquals(root.zones(), decoded.zones());
        assertEquals(1, encoded.getInt("schema_version"));
        assertEquals("v1.7.0-endgame", encoded.getString("format_epoch"));
    }

    @Test
    void theFileCarriesTheNextEpochAndAReloadMakesRegistrationsDurable() {
        EndgameRoot root = EndgameRoot.create();
        UUID id = new UUID(0L, 1L);
        root.register(id, KIND, OWNER, LEVEL, 1L, false, 2048, 64);
        CompoundTag written = EndgameRootCodec.encode(root, new CompoundTag());
        assertEquals(2L, written.getLong("save_epoch"));
        EndgameRoot reloaded = EndgameRootCodec.decode(written);
        assertEquals(2L, reloaded.saveEpoch());
        assertTrue(reloaded.registrationDurable(id), "a record read from the file is durable");
        assertFalse(root.registrationDurable(id), "the writer learns it only after the write returns");
    }

    @Test
    void eachRecordStaysWithinItsAccountedWorstCase() {
        ResourceLocation longKind = ResourceLocation.tryBuild("advancedrocketrycommunity", "k".repeat(64 - 26));
        ResourceLocation longLevel = ResourceLocation.tryBuild("advancedrocketrycommunity", "l".repeat(128 - 26));
        assertEquals(64, longKind.toString().length());
        assertEquals(128, longLevel.toString().length());
        int empty = bytes(EndgameRoot.create());

        EndgameRoot withRecord = EndgameRoot.create();
        withRecord.register(new UUID(-1L, -1L), longKind, OWNER, longLevel, Long.MIN_VALUE, false, 2048, 64);
        assertTrue(bytes(withRecord) - empty <= EndgameLimits.ENDPOINT_RECORD_BYTES, "endpoint record");

        EndgameRoot withYoung = EndgameRoot.create();
        withYoung.register(new UUID(-1L, -1L), longKind, OWNER, longLevel, Long.MIN_VALUE, false, 2048, 64);
        withYoung.remove(new UUID(-1L, -1L));
        assertTrue(bytes(withYoung) - empty <= EndgameLimits.ENDPOINT_RECORD_BYTES, "a young tombstone takes a place");

        EndgameRoot withSettled = EndgameRoot.create();
        withSettled.register(new UUID(-1L, -1L), longKind, OWNER, longLevel, Long.MIN_VALUE, false, 2048, 64);
        withSettled.retireLost(new UUID(-1L, -1L), id -> false);
        assertTrue(bytes(withSettled) - empty <= EndgameLimits.TOMBSTONE_RECORD_BYTES, "settled tombstone");

        List<UUID> sixteen = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            sixteen.add(new UUID(-1L, -i - 1L));
        }
        EndgameRoot withZone = EndgameRoot.create();
        withZone.addZone(ProtectedZone.of("z".repeat(32), longLevel, Integer.MIN_VALUE, Integer.MIN_VALUE,
                Integer.MIN_VALUE + 4095, Integer.MIN_VALUE + 4095, sixteen), 256);
        assertTrue(bytes(withZone) - empty <= EndgameLimits.ZONE_RECORD_BYTES, "zone");
    }

    @Test
    void decodingIsStrict() {
        CompoundTag valid = EndgameRootCodec.encode(sample(), new CompoundTag());
        assertRejected(valid, root -> root.putString("extra", "x"));
        assertRejected(valid, root -> root.remove("zones"));
        assertRejected(valid, root -> root.put("dispatched_through", new LongArrayTag(new long[] {1L, 2L})));
        assertRejected(valid, root -> root.put("dispatched_through", new LongArrayTag(new long[] {9L, 9L, 1L})));
        assertRejected(valid, root -> root.put("dispatched_through", new LongArrayTag(new long[] {0L, 1L, 0L})));
        assertRejected(valid, root -> {
            ListTag transits = new ListTag();
            transits.add(new CompoundTag());
            root.put("transits", transits);
        });
        assertRejected(valid, root -> firstEndpoint(root).putString("state", "PAUSED"));
        assertRejected(valid, root -> firstEndpoint(root).putString("color", "red"));
        assertRejected(valid, root -> firstEndpoint(root).putLong("registered_epoch", 99L));
        assertRejected(valid, root -> endpoints(root).add(endpoints(root).get(0).copy()));
        assertRejected(valid, root -> settled(root).put("s", new LongArrayTag(new long[] {1L, 2L, 3L})));
        assertRejected(valid, root -> root.putInt("settle_order", 0));
        assertRejected(valid, root -> root.getList("zones", 10).getCompound(0).putString("name", "Bad Name"));
        assertRejected(valid, root -> root.getList("zones", 10).getCompound(0).getList("allow", 11)
                .add(new IntArrayTag(new int[] {1, 2})));
    }

    @Test
    void aBlockedRootIsPreservedAndRewrittenUnchanged() {
        CompoundTag broken = EndgameRootCodec.encode(sample(), new CompoundTag());
        broken.putString("extra", "x");
        EndgameSavedData data = EndgameSavedData.load(broken);
        assertFalse(data.operational());
        assertEquals(broken, data.save(new CompoundTag()));
        assertThrows(IllegalStateException.class, data::view);
        CompoundTag future = EndgameRootCodec.encode(sample(), new CompoundTag());
        future.putInt("schema_version", 2);
        assertFalse(EndgameSavedData.load(future).operational());
    }

    @Test
    void theMigratorChecksTheEndgameEpochFromSchemaOne() {
        CompoundTag valid = EndgameRootCodec.encode(EndgameRoot.create(), new CompoundTag());
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.CURRENT,
                SavedDataSchemaMigrator.migrate(ManagedSavedDataType.ENDGAME, valid).status());
        CompoundTag noEpoch = valid.copy();
        noEpoch.remove("format_epoch");
        assertInvalid(noEpoch);
        CompoundTag wrongEpoch = valid.copy();
        wrongEpoch.putString("format_epoch", "v0.9.0-beta");
        assertInvalid(wrongEpoch);
        CompoundTag noDispatched = valid.copy();
        noDispatched.remove("dispatched_through");
        assertInvalid(noDispatched);
        CompoundTag schemaZero = valid.copy();
        schemaZero.putInt("schema_version", 0);
        assertInvalid(schemaZero);
        CompoundTag future = valid.copy();
        future.putInt("schema_version", 2);
        assertEquals(SavedDataSchemaMigrator.MigrationStatus.FUTURE,
                SavedDataSchemaMigrator.migrate(ManagedSavedDataType.ENDGAME, future).status());
    }

    @Test
    void aFlushAdvancesTheEpochAndClearsThePendingFlag() throws Exception {
        EndgameSavedData data = EndgameSavedData.create();
        UUID id = new UUID(0L, 1L);
        EndgameCode code = data.update(root -> root.register(id, KIND, OWNER, LEVEL, 1L, false, 2048, 64));
        assertEquals(EndgameCode.OK, code);
        assertTrue(data.isDirty());
        assertTrue(data.flushPending());
        Path file = directory.resolve(ManagedSavedDataType.ENDGAME.fileName());
        data.flush(file);
        assertFalse(data.flushPending());
        assertFalse(data.isDirty());
        assertEquals(2L, data.view().saveEpoch());
        assertTrue(data.view().registrationDurable(id));
        CompoundTag onDisk = NbtIo.readCompressed(Files.newInputStream(file)).getCompound("data");
        EndgameSavedData reloaded = EndgameSavedData.load(onDisk);
        assertTrue(reloaded.operational());
        assertTrue(reloaded.view().registrationDurable(id));
        assertEquals(onDisk, reloaded.save(new CompoundTag()), "an unchanged reload writes the same bytes");
        assertFalse(data.update(root -> root.removeZone("none")) == EndgameCode.OK);
        assertFalse(data.flushPending(), "a refused mutation changes nothing");
    }

    @Test
    void anOversizedRootIsRefusedOnBothSides() {
        CompoundTag huge = EndgameRootCodec.encode(EndgameRoot.create(), new CompoundTag());
        huge.put("dispatched_through", new LongArrayTag(new long[(int) (EndgameLimits.MAX_ROOT_BYTES / 8) + 1]));
        assertThrows(IllegalArgumentException.class, () -> EndgameRootCodec.decode(huge));
        assertArrayEquals(new long[0], EndgameRootCodec.encode(EndgameRoot.create(), new CompoundTag())
                .getLongArray("dispatched_through"));
    }

    private static EndgameRoot sample() {
        EndgameRoot root = EndgameRoot.create();
        UUID active = new UUID(0L, 1L);
        UUID missing = new UUID(0L, 2L);
        UUID young = new UUID(0L, 3L);
        UUID settled = new UUID(0L, 4L);
        for (UUID id : List.of(active, missing, young, settled)) {
            root.register(id, KIND, OWNER, LEVEL, id.getLeastSignificantBits(), false, 2048, 64);
        }
        root.markMissing(missing);
        root.remove(young);
        root.remove(settled);
        root.settle(settled, id -> false);
        root.addZone(ProtectedZone.of("spawn", LEVEL, -16, -16, 16, 16, List.of(OWNER)), 256);
        return root;
    }

    private static int bytes(EndgameRoot root) {
        return EndgameNbt.uncompressedBytes(EndgameRootCodec.encode(root, new CompoundTag()));
    }

    private static ListTag endpoints(CompoundTag root) {
        return root.getList("endpoints", 10);
    }

    private static CompoundTag firstEndpoint(CompoundTag root) {
        return endpoints(root).getCompound(0);
    }

    private static CompoundTag settled(CompoundTag root) {
        for (int i = 0; i < endpoints(root).size(); i++) {
            if (endpoints(root).getCompound(i).contains("s")) {
                return endpoints(root).getCompound(i);
            }
        }
        throw new AssertionError("no settled tombstone");
    }

    private static void assertRejected(CompoundTag valid, Consumer<CompoundTag> change) {
        CompoundTag copy = valid.copy();
        change.accept(copy);
        assertThrows(RuntimeException.class, () -> EndgameRootCodec.decode(copy));
        assertFalse(EndgameSavedData.load(copy).operational());
    }

    private static void assertInvalid(CompoundTag root) {
        SavedDataMigrationException refused = assertThrows(SavedDataMigrationException.class,
                () -> SavedDataSchemaMigrator.migrate(ManagedSavedDataType.ENDGAME, root));
        assertEquals(MigrationDiagnosticId.INVALID_SCHEMA, refused.diagnosticId());
    }
}
