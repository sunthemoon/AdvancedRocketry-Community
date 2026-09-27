package io.github.sunthemoon.advancedrocketrycommunity.client.starmap;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialCapabilities;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StarMapLayoutTest {
    @Test
    void viewportReceivesClicksBeforeTheAlwaysConsumingContainerAndToolbarStillWorks() {
        var calls = new ArrayList<String>();
        assertTrue(StarMapInput.click(true, () -> { calls.add("map"); return true; }, () -> { calls.add("container"); return true; }));
        assertEquals(List.of("map"), calls);
        calls.clear();
        assertTrue(StarMapInput.click(true, () -> { calls.add("outside"); return false; }, () -> { calls.add("toolbar"); return true; }));
        assertEquals(List.of("outside", "toolbar"), calls);
        calls.clear();
        assertTrue(StarMapInput.click(false, () -> { fail("Closed map must not receive clicks"); return true; }, () -> { calls.add("console"); return true; }));
        assertEquals(List.of("console"), calls);
    }

    @Test
    void hundredAndMaximumBodyCatalogsAreDeterministicAndEveryBodyCanBeFocused() {
        for (int count : List.of(100, 128)) {
            var entries = new ArrayList<CelestialSnapshot.Entry>();
            for (int i = 0; i < count; i++) { entries.add(entry(i, i == 0 ? -1 : (i - 1) / 3)); }
            var layout = StarMapLayout.create(new CelestialSnapshot(2, entries));
            Collections.reverse(entries);
            assertEquals(layout, StarMapLayout.create(new CelestialSnapshot(2, entries)));
            assertEquals(count, layout.nodes().size());
            assertEquals(count, layout.nodes().stream().map(n -> n.x() + ":" + n.y()).distinct().count());
            var viewport = new StarMapViewport(layout, 10, 44, 288, 84);
            for (var node : layout.nodes()) {
                viewport.focus(node);
                assertTrue(viewport.contains(viewport.screenX(node.x()), viewport.screenY(node.y())));
            }
        }
    }

    @Test
    void deepTreesRemainBoundedAndMalformedGraphsReject() {
        var entries = new ArrayList<CelestialSnapshot.Entry>();
        for (int i = 0; i < 128; i++) { entries.add(entry(i, i - 1)); }
        assertEquals(128, StarMapLayout.create(new CelestialSnapshot(2, entries)).nodes().size());
        entries.add(entry(128, 0));
        assertThrows(IllegalArgumentException.class, () -> StarMapLayout.create(new CelestialSnapshot(2, entries)));
        for (var invalid : List.of(List.of(entry(0, 1), entry(1, 0)), List.of(entry(0, 1)), List.of(entry(0, -1), entry(0, -1)))) {
            assertThrows(IllegalArgumentException.class, () -> StarMapLayout.create(new CelestialSnapshot(2, invalid)));
        }
    }

    @Test
    void viewportClipsHitsAndClampsZoomPanAndNonfiniteInput() {
        var layout = StarMapLayout.create(new CelestialSnapshot(2, List.of(entry(0, -1), entry(1, 0))));
        var viewport = new StarMapViewport(layout, 10, 20, 200, 100);
        assertTrue(viewport.contains(10, 20));
        assertFalse(viewport.contains(210, 20));
        assertFalse(viewport.contains(10, 120));
        viewport.zoom(Double.POSITIVE_INFINITY, 110, 70);
        assertEquals(1, viewport.zoom());
        viewport.zoom(1000, 0, 0);
        assertEquals(1, viewport.zoom());
        viewport.zoom(1000, 110, 70);
        assertEquals(StarMapViewport.MAX_ZOOM, viewport.zoom());
        viewport.zoom(-1000, 110, 70);
        assertEquals(StarMapViewport.MIN_ZOOM, viewport.zoom());
        viewport.drag(Double.MAX_VALUE, -Double.MAX_VALUE);
        assertTrue(Double.isFinite(viewport.screenX(0)) && Double.isFinite(viewport.screenY(0)));
        double before = viewport.screenX(0);
        viewport.drag(Double.NaN, 0);
        assertEquals(before, viewport.screenX(0));
        for (var node : layout.nodes()) {
            viewport.focus(node);
            assertTrue(viewport.contains(viewport.screenX(node.x()), viewport.screenY(node.y())));
        }
    }

    private static CelestialSnapshot.Entry entry(int id, int parent) {
        return new CelestialSnapshot.Entry(id(id), parent < 0 ? Optional.empty() : Optional.of(id(parent)), Optional.empty(),
                1, 0, false, 270, id(900), id(901), new CelestialCapabilities(false, true, false), 1, 0);
    }

    private static ResourceLocation id(int value) { return new ResourceLocation("test", "body_" + value); }
}
