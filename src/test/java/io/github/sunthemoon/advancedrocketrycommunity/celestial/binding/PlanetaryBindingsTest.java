package io.github.sunthemoon.advancedrocketrycommunity.celestial.binding;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialDefaults;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteAnchor;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.model.RouteDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalog;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlanetaryBindingsTest {
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void adoptsAndRoundTripsExplicitMappedAndUnmappedIdentities() throws Exception {
        var bindings = PlanetaryBindings.adopt(catalog(body("a", "a"), body("gas", null)));
        assertEquals(5, bindings.entries().size());
        assertEquals(bindings.entries(), PlanetaryBindingsCodec.decode(PlanetaryBindingsCodec.encode(bindings)).entries());
        assertTrue(bindings.entries().stream().filter(b -> b.bodyId().equals(id("gas"))).findFirst().orElseThrow().level().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> bindings.entries().clear());
    }

    @Test void metadataChangesAndRetirementDoNotReleaseIdentity() {
        var first = PlanetaryBindings.adopt(catalog(body("a", "a")));
        assertSame(first, first.reconcile(catalog()));
        var changed = body("a", "a");
        changed = new CelestialBodyDefinition(changed.id(), changed.parentId(), changed.levelKey(), 0.5,
                changed.atmosphere(), changed.orbit(), changed.visualProfile(), new CelestialCapabilities(false, false, false), 0.5, 0.2);
        assertSame(first, first.reconcile(catalog(changed)));
        assertThrows(IllegalArgumentException.class, () -> first.reconcile(catalog(body("a", "b"))));
        assertEquals(4, first.entries().size());
    }

    @Test void reverseReservationsRejectAliasesRenamesAndSwaps() {
        assertThrows(IllegalArgumentException.class, () -> PlanetaryBindings.adopt(catalog(body("a", "moon"))));
        var first = PlanetaryBindings.adopt(catalog(body("a", "a"), body("b", "b")));
        var removed = first.reconcile(catalog());
        assertThrows(IllegalArgumentException.class, () -> removed.reconcile(catalog(body("c", "a"))));
        assertThrows(IllegalArgumentException.class, () -> first.reconcile(catalog(body("a", "b"), body("b", "a"))));
    }

    @Test void explicitUnmappedIsNotPermissionToBindLater() {
        var first = PlanetaryBindings.adopt(catalog(body("a", "a"), body("gas", null)));
        assertThrows(IllegalArgumentException.class, () -> first.reconcile(catalog(body("a", null))));
        assertThrows(IllegalArgumentException.class, () -> first.reconcile(catalog(body("gas", "gas"))));
    }

    @Test void reverseConflictNamesBothBodiesWithoutInventingHistoricalPrecedence() {
        var first = PlanetaryBindings.adopt(catalog(body("retained", "planet")));
        var retired = first.reconcile(catalog());
        var failure = assertThrows(IllegalArgumentException.class,
                () -> retired.reconcile(catalog(body("alias", "planet"))));
        assertEquals("Level advancedrocketrycommunity:planet has conflicting body bindings: "
                + "advancedrocketrycommunity:alias and advancedrocketrycommunity:retained", failure.getMessage());
        assertEquals(first.entries(), retired.entries());
    }

    @Test void lifetimeCapacityIncludesRemovedBodiesAndRejectsWholeDelta() {
        var bodies = new ArrayList<CelestialBodyDefinition>();
        for (int i = 0; i < 125; i++) { bodies.add(body("test_" + i, "test_" + i)); }
        var full = PlanetaryBindings.adopt(catalog(bodies.toArray(CelestialBodyDefinition[]::new)));
        assertEquals(128, full.entries().size());
        var removed = full.reconcile(catalog());
        assertThrows(IllegalArgumentException.class, () -> removed.reconcile(catalog(body("replacement", "replacement"))));
        assertSame(full, full.reconcile(catalog(body("test_1", "test_1"))));
    }

    @Test void rejectsMissingFixedBindingsAndDuplicateBodies() {
        var baseline = PlanetaryBindings.adopt(catalog()).entries();
        assertThrows(IllegalArgumentException.class, () -> PlanetaryBindings.restore(baseline.subList(0, 2)));
        var duplicate = new ArrayList<>(baseline);
        duplicate.add(baseline.get(0));
        assertThrows(IllegalArgumentException.class, () -> PlanetaryBindings.restore(duplicate));
    }

    @Test void strictCodecRejectsWrongSchemaKeysTypesAndCanonicalIds() throws Exception {
        String valid = new String(PlanetaryBindingsCodec.encode(PlanetaryBindings.adopt(catalog())), StandardCharsets.UTF_8);
        for (String value : List.of("0", "2", "1.0", "1e0", "\"1\"", "null", "true")) {
            assertThrows(IOException.class, () -> decode(valid.replace("\"schema_version\":1", "\"schema_version\":" + value)));
        }
        for (String value : List.of("{}", "[]", "{\"schema_version\":1,\"bindings\":null}",
                valid.replace("\"bindings\"", "\"unknown\""), valid.replace("minecraft:overworld", "overworld"),
                valid.replace("\"level\":\"minecraft:overworld\"", "\"level\":null"),
                valid.replace("\"body_id\"", "\"body_id\":0,\"body_id\""), valid + "{}")) {
            assertThrows(IOException.class, () -> decode(value), value);
        }
        var json = JsonParser.parseString(valid).getAsJsonObject();
        json.getAsJsonArray("bindings").add(json.getAsJsonArray("bindings").get(0).deepCopy());
        assertThrows(IOException.class, () -> decode(json.toString()));
    }

    @Test void codecEnforcesRawBytesDepthUtf8AndSerializedBudget() throws Exception {
        assertThrows(IOException.class, () -> PlanetaryBindingsCodec.decode(new byte[32769]));
        assertThrows(IOException.class, () -> PlanetaryBindingsCodec.decode(new byte[] {(byte) 0xc3, 0x28}));
        assertThrows(IOException.class, () -> decode("[".repeat(17) + "0" + "]".repeat(17)));
        var longIds = new ArrayList<PlanetaryBindings.Binding>(PlanetaryBindings.adopt(catalog()).entries());
        for (int i = 0; i < 125; i++) {
            longIds.add(new PlanetaryBindings.Binding(ResourceLocation.tryParse("test:" + ("a".repeat(120)) + i),
                    Optional.of(ResourceLocation.tryParse("test:" + "b".repeat(120) + i))));
        }
        var full = PlanetaryBindings.restore(longIds);
        assertThrows(IOException.class, () -> PlanetaryBindingsCodec.encode(full));
    }

    @Test void realFileCommitsBeforeReturnAndMetadataDoesNotRewrite() throws Exception {
        var store = PlanetaryBindingStore.open(root, catalog(body("a", "a")));
        byte[] original = Files.readAllBytes(file());
        var time = Files.getLastModifiedTime(file());
        store.accept(catalog());
        assertEquals(time, Files.getLastModifiedTime(file()));
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertEquals(store.current().entries(), PlanetaryBindingStore.open(root, catalog(body("a", "a"))).current().entries());
        assertThrows(IllegalArgumentException.class, () -> PlanetaryBindingStore.open(root, catalog(body("a", "b"))));
        assertArrayEquals(original, Files.readAllBytes(file()));
    }

    @Test void rejectsChangedOrDeletedDiskEvenOnMetadataOnlyReload() throws Exception {
        var store = PlanetaryBindingStore.open(root, catalog());
        byte[] original = Files.readAllBytes(file());
        Files.writeString(file(), "{}");
        assertThrows(IOException.class, () -> store.accept(catalog()));
        assertEquals("{}", Files.readString(file()));
        Files.delete(file());
        assertThrows(IOException.class, () -> store.accept(catalog()));
        assertFalse(Files.exists(file()));
        Files.write(file(), original);
        store.accept(catalog());
    }

    @Test void corruptFutureNonRegularAndPendingFilesNeverInitializeEmptyAuthority() throws Exception {
        Files.createDirectory(root.resolve("data"));
        for (String invalid : List.of("", "{", "{\"schema_version\":2,\"bindings\":[]}")) {
            Files.writeString(file(), invalid);
            assertThrows(IOException.class, () -> PlanetaryBindingStore.open(root, catalog()));
            assertEquals(invalid, Files.readString(file()));
        }
        Files.delete(file());
        Files.createDirectory(file());
        assertThrows(IOException.class, () -> PlanetaryBindingStore.open(root, catalog()));
        Files.delete(file());
        Path pending = root.resolve("data").resolve(PlanetaryBindingStore.PENDING_NAME);
        Files.writeString(pending, "interrupted");
        assertThrows(IOException.class, () -> PlanetaryBindingStore.open(root, catalog()));
        assertEquals("interrupted", Files.readString(pending));
        assertFalse(Files.exists(file()));
    }

    @Test void failedAtomicCommitKeepsOldBytesAndCleansOnlyOwnedStage() throws Exception {
        AtomicBoolean fail = new AtomicBoolean();
        var store = PlanetaryBindingStore.open(root, catalog(), (from, to) -> {
            if (fail.get()) { throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "fixture"); }
            Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        });
        byte[] original = Files.readAllBytes(file());
        var previous = store.current();
        fail.set(true);
        assertThrows(AtomicMoveNotSupportedException.class, () -> store.accept(catalog(body("a", "a"))));
        assertSame(previous, store.current());
        assertArrayEquals(original, Files.readAllBytes(file()));
        assertFalse(Files.exists(root.resolve("data").resolve(PlanetaryBindingStore.PENDING_NAME)));
        fail.set(false);
        store.accept(catalog(body("a", "a")));
        assertEquals(4, PlanetaryBindingsCodec.decode(Files.readAllBytes(file())).entries().size());
    }

    @Test void firstCommitFailureLeavesNoAuthorityAndInvalidCandidateDoesNotConsumeBindings() throws Exception {
        assertThrows(IOException.class, () -> PlanetaryBindingStore.open(root, catalog(), (from, to) -> {
            throw new IOException("fixture");
        }));
        assertFalse(Files.exists(file()));
        assertFalse(Files.exists(root.resolve("data").resolve(PlanetaryBindingStore.PENDING_NAME)));
        var store = PlanetaryBindingStore.open(root, catalog());
        assertThrows(IllegalArgumentException.class, () -> store.accept(catalog(body("new", "new"), body("alias", "moon"))));
        assertEquals(3, store.current().entries().size());
    }

    @Test void indeterminateFileAttributesAreNeverTreatedAsAbsence() throws Exception {
        PlanetaryBindingStore.open(root, catalog());
        byte[] original = Files.readAllBytes(file());
        AtomicBoolean deny = new AtomicBoolean(true);
        PlanetaryBindingStore.Inspector inspector = path -> {
            if (deny.get() && path.equals(file())) { throw new AccessDeniedException(path.toString()); }
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        };
        PlanetaryBindingStore.Committer committer = (from, to) -> fail("No commit is permitted by this fixture");
        assertThrows(AccessDeniedException.class, () -> PlanetaryBindingStore.open(root, catalog(), committer, inspector));
        assertArrayEquals(original, Files.readAllBytes(file()));
        deny.set(false);
        var store = PlanetaryBindingStore.open(root, catalog(), committer, inspector);
        deny.set(true);
        assertThrows(AccessDeniedException.class, () -> store.accept(catalog()));
        assertArrayEquals(original, Files.readAllBytes(file()));
    }

    @Test void realSymbolicLinkAuthorityAndAncestryAreRefused() throws Exception {
        Path target = root.resolve("outside.json");
        Files.writeString(target, "keep");
        Files.createDirectory(root.resolve("data"));
        Files.createSymbolicLink(file(), target);
        assertThrows(IOException.class, () -> PlanetaryBindingStore.open(root, catalog()));
        assertEquals("keep", Files.readString(target));
        Path linkedWorld = root.resolve("linked-world");
        Files.createSymbolicLink(linkedWorld, root);
        assertThrows(IOException.class, () -> PlanetaryBindingStore.open(linkedWorld, catalog()));
        assertEquals("keep", Files.readString(target));
    }

    @Test void actualManagerRetainsGenerationPairAndCacheWhenBindingFails() throws Exception {
        var manager = new PlanetaryCatalogManager();
        assertTrue(manager.applyCandidate(DataResult.success(pair(catalog(body("a", "a"))))));
        assertEquals(4, manager.bindWorld(root));
        var before = manager.capture().orElseThrow();
        var routes = before.catalog().routes();
        var from = RouteAnchor.bodySurface(CelestialIds.EARTH_ID);
        var to = RouteAnchor.bodySurface(CelestialIds.MOON_ID);
        var plan = routes.plan(from, to).result().orElseThrow();
        byte[] original = Files.readAllBytes(file());
        assertFalse(manager.applyCandidate(DataResult.success(pair(catalog(body("a", "b"))))));
        assertEquals(before, manager.capture().orElseThrow());
        assertSame(plan, routes.plan(from, to).result().orElseThrow());
        assertFalse(manager.status().lastReloadAccepted());
        assertArrayEquals(original, Files.readAllBytes(file()));
        Files.writeString(file(), "{}");
        assertFalse(manager.applyCandidate(DataResult.success(pair(catalog()))));
        assertEquals(before, manager.capture().orElseThrow());
        Files.write(file(), original);
        assertTrue(manager.applyCandidate(DataResult.success(pair(catalog(body("new", "new"))))));
        assertEquals(2, manager.capture().orElseThrow().generation());
        assertEquals(5, PlanetaryBindingsCodec.decode(Files.readAllBytes(file())).entries().size());
    }

    @Test void failedStartAndStopDoNotKeepAPreviousWorldAttachment() throws Exception {
        var manager = new PlanetaryCatalogManager();
        assertTrue(manager.applyCandidate(DataResult.success(pair(catalog()))));
        manager.bindWorld(root);
        assertThrows(IllegalStateException.class, () -> manager.bindWorld(root));
        manager.clear();
        assertTrue(manager.capture().isEmpty());
        Path second = Files.createDirectory(root.resolve("second"));
        assertTrue(manager.applyCandidate(DataResult.success(pair(catalog(body("a", "a"))))));
        assertEquals(4, manager.bindWorld(second));
        assertEquals(3, PlanetaryBindingsCodec.decode(Files.readAllBytes(file())).entries().size());
        manager.clear();
        Files.writeString(file(), "{}");
        assertTrue(manager.applyCandidate(DataResult.success(pair(catalog()))));
        assertThrows(IOException.class, () -> manager.bindWorld(root));
        assertFalse(manager.status().ready());
        assertTrue(manager.capture().isEmpty());
    }

    private Path file() { return root.resolve("data").resolve(PlanetaryBindingStore.FILE_NAME); }
    private static ResourceLocation id(String path) { return ModIdentity.id(path); }
    private static PlanetaryBindings decode(String json) throws IOException {
        return PlanetaryBindingsCodec.decode(json.getBytes(StandardCharsets.UTF_8));
    }
    private static CelestialBodyDefinition body(String name, String level) {
        var moon = CelestialDefaults.definitions().get(1);
        return new CelestialBodyDefinition(id(name), moon.parentId(), level == null ? Optional.empty()
                : Optional.of(ResourceKey.create(Registries.DIMENSION, id(level))), moon.gravityMultiplier(),
                moon.atmosphere(), moon.orbit(), moon.visualProfile(),
                new CelestialCapabilities(level != null, true, level == null), 1, 0);
    }
    private static CelestialCatalog catalog(CelestialBodyDefinition... extra) {
        var bodies = new ArrayList<>(CelestialDefaults.definitions());
        bodies.addAll(List.of(extra));
        return CelestialCatalog.create(bodies).result().orElseThrow();
    }
    private static PlanetaryCatalog pair(CelestialCatalog catalog) {
        var definition = new RouteDefinition(1, id("earth_moon"), RouteAnchor.bodySurface(CelestialIds.EARTH_ID),
                RouteAnchor.bodySurface(CelestialIds.MOON_ID), 50, true);
        var routes = RouteCatalog.create(List.of(definition), catalog.definitions().stream().map(CelestialBodyDefinition::id).toList())
                .result().orElseThrow();
        return PlanetaryCatalog.create(catalog, routes).result().orElseThrow();
    }
}
