package io.github.sunthemoon.advancedrocketrycommunity.celestial.visual;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkySelectionTest {
    private final SkySelection selector = new SkySelection();

    @Test void selectionUsesVisualProfileAndUniquePhysicalMappingNotBodyName() {
        var body = body("custom", "surface", "mars");
        var snapshot = new CelestialSnapshot(2, List.of(body));
        var selected = selector.resolve(ModIdentity.id("surface"), snapshot, SkyProfiles.builtins());
        assertEquals(ModIdentity.id("custom"), selected.bodyId());
        assertEquals(ModIdentity.id("mars"), selected.profileId());
        assertEquals(0.006, selected.pressure());
        assertSame(selected, selector.resolve(ModIdentity.id("surface"), snapshot, SkyProfiles.builtins()));
    }

    @Test void dimensionSnapshotAndResourceChangesInvalidateSingleEntryCache() {
        var snapshot = new CelestialSnapshot(2, List.of(body("mars", "mars", "mars"), body("venus", "venus", "venus")));
        var first = selector.resolve(ModIdentity.id("mars"), snapshot, SkyProfiles.builtins());
        assertEquals(ModIdentity.id("venus"), selector.resolve(ModIdentity.id("venus"), snapshot, SkyProfiles.builtins()).bodyId());
        var changed = new CelestialSnapshot(2, List.of(body("mars", "mars", "venus")));
        assertNotEquals(first, selector.resolve(ModIdentity.id("mars"), changed, SkyProfiles.builtins()));
        var replacements = Map.of(ModIdentity.id("venus"), SkyProfiles.builtins().get(ModIdentity.id("moon")));
        assertEquals(replacements.get(ModIdentity.id("venus")), selector.resolve(ModIdentity.id("mars"), changed, replacements).profile());
        selector.clear();
        assertNull(selector.resolve(ModIdentity.id("mars"), null, SkyProfiles.builtins()));
    }

    @Test void unknownRemovedAmbiguousOrMissingDataNeverRetainsPreviousBody() {
        var body = body("mars", "mars", "mars");
        var good = new CelestialSnapshot(2, List.of(body));
        for (var invalid : List.of(new CelestialSnapshot(2, List.of()),
                new CelestialSnapshot(2, List.of(body, body("alias", "mars", "venus"))),
                new CelestialSnapshot(2, List.of(body("mars", "mars", "missing"))))) {
            assertNotNull(selector.resolve(ModIdentity.id("mars"), good, SkyProfiles.builtins()));
            assertNull(selector.resolve(ModIdentity.id("mars"), invalid, SkyProfiles.builtins()));
            assertNull(selector.resolve(ModIdentity.id("mars"), invalid, SkyProfiles.builtins()));
        }
        assertNull(selector.resolve(ModIdentity.id("earth"), good, SkyProfiles.builtins()));
        assertNull(selector.resolve(null, good, SkyProfiles.builtins()));
    }

    @Test void maximumCatalogIsBoundedAndUnmappedBodiesCannotSelectASurface() {
        var entries = new ArrayList<CelestialSnapshot.Entry>();
        for (int index = 0; index < 128; index++) { entries.add(body("b" + index, "l" + index, "mars")); }
        assertNotNull(selector.resolve(ModIdentity.id("l127"), new CelestialSnapshot(2, entries), SkyProfiles.builtins()));
        entries.add(body("overflow", "overflow", "mars"));
        assertNull(selector.resolve(ModIdentity.id("l127"), new CelestialSnapshot(2, entries), SkyProfiles.builtins()));
        var gas = new CelestialSnapshot.Entry(ModIdentity.id("gas"), Optional.empty(), Optional.empty(),
                1, 10, false, 200, ModIdentity.id("gas"), ModIdentity.id("venus"),
                new CelestialCapabilities(false, true, true), 0.1, 0);
        assertNull(selector.resolve(ModIdentity.id("gas"), new CelestialSnapshot(2, List.of(gas)), SkyProfiles.builtins()));
    }

    /** ADR-047: a station sky context adds the orbited body and moves the sun to its solar intensity. */
    @Test void aStationContextAddsTheOrbitedBodyAndItsSun() {
        var space = new CelestialSnapshot.Entry(ModIdentity.id("space"), Optional.empty(), Optional.of(ModIdentity.id("space")),
                0, 0, false, 3, ModIdentity.id("vacuum"), ModIdentity.id("space"),
                new CelestialCapabilities(false, false, false), 1.0, 0.1);
        var mars = body("mars", "mars", "mars");
        var earth = new CelestialSnapshot.Entry(ModIdentity.id("earth"), Optional.empty(), Optional.of(ModIdentity.id("overworld")),
                1, 1, true, 288, ModIdentity.id("earth"), ModIdentity.id("earth"),
                new CelestialCapabilities(true, true, false), 1.0, 0);
        var snapshot = new CelestialSnapshot(2, List.of(space, mars, earth));
        var plain = selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins());
        assertTrue(plain.orbit().isEmpty());
        assertEquals(1.0, plain.solarIntensity());
        var atMars = selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins(), Optional.of(ModIdentity.id("mars")));
        assertEquals(ModIdentity.id("mars"), atMars.orbit().orElseThrow().bodyId());
        assertEquals(OrbitalAppearance.packaged().get(ModIdentity.id("mars")), atMars.orbit().orElseThrow().color());
        assertEquals(0.43, atMars.solarIntensity(), "The sun follows the orbited body");
        assertEquals(ModIdentity.id("space"), atMars.bodyId(), "The Level's own profile keeps the sky");
        assertSame(atMars, selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins(), Optional.of(ModIdentity.id("mars"))));
        var atEarth = selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins(), Optional.of(ModIdentity.id("earth")));
        assertEquals(OrbitalAppearance.packaged().get(ModIdentity.id("earth")), atEarth.orbit().orElseThrow().color(),
                "Earth has no surface sky profile but a packaged orbital colour");
        assertEquals(1.0, atEarth.solarIntensity(), "The sun follows the orbited body even without a profile");
        var unknown = selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins(), Optional.of(ModIdentity.id("gone")));
        assertTrue(unknown.orbit().isEmpty());
        assertEquals(1.0, unknown.solarIntensity(), "An unknown body leaves the Level's sun");
        assertNotEquals(atMars, selector.resolve(ModIdentity.id("space"), snapshot, SkyProfiles.builtins(), Optional.empty()));
    }

    private static CelestialSnapshot.Entry body(String id, String level, String profile) {
        return new CelestialSnapshot.Entry(ModIdentity.id(id), Optional.empty(), Optional.of(ModIdentity.id(level)),
                0.38, 0.006, false, 210, ModIdentity.id("mars"), ModIdentity.id(profile),
                new CelestialCapabilities(true, true, false), 0.43, 0.05);
    }
}
