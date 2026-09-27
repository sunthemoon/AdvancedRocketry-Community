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

    private static CelestialSnapshot.Entry body(String id, String level, String profile) {
        return new CelestialSnapshot.Entry(ModIdentity.id(id), Optional.empty(), Optional.of(ModIdentity.id(level)),
                0.38, 0.006, false, 210, ModIdentity.id("mars"), ModIdentity.id(profile),
                new CelestialCapabilities(true, true, false), 0.43, 0.05);
    }
}
