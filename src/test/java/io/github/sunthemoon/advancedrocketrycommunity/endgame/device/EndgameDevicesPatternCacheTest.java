package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleDataReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunSettings;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternSize;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** C13 (review C13-F7): pattern lookups are cached per catalog, so an accepted reload is seen at once. */
final class EndgameDevicesPatternCacheTest {
    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void anAcceptedReloadReplacesEveryCachedLookup() {
        MultiblockPatternCatalogManager patterns = new MultiblockPatternCatalogManager();
        EndgameDevices devices = new EndgameDevices(() -> EndgameSettings.DEFAULTS, () -> LaserDrillSettings.DEFAULTS,
                () -> GravityFieldLimits.DEFAULTS, () -> BlackHoleSettings.DEFAULTS, () -> RailgunSettings.DEFAULTS,
                () -> ElevatorSettings.DEFAULTS, patterns, new CelestialCatalogManager(),
                new LaserDrillTableReloadListener.Manager(), new BlackHoleDataReloadListener.Manager());
        assertTrue(devices.pattern("test:first").isEmpty(), "no catalog yet");
        MultiblockPatternDefinition first = definition("test:first");
        patterns.accept(MultiblockPatternCatalog.create(List.of(first)));
        assertSame(first, devices.pattern("test:first").orElseThrow(), "a cached absence outlived the catalog");
        assertTrue(devices.pattern("test:second").isEmpty());
        MultiblockPatternDefinition reloaded = definition("test:first");
        MultiblockPatternDefinition second = definition("test:second");
        patterns.accept(MultiblockPatternCatalog.create(List.of(reloaded, second)));
        assertSame(reloaded, devices.pattern("test:first").orElseThrow(), "a stale definition after the reload");
        assertSame(second, devices.pattern("test:second").orElseThrow(), "a stale absence after the reload");
        patterns.reject("broken replacement");
        assertSame(reloaded, devices.pattern("test:first").orElseThrow(), "a rejected reload keeps the catalog");
        devices.clear();
        assertSame(reloaded, devices.pattern("test:first").orElseThrow(), "a cleared cache reads the catalog again");
    }

    private static MultiblockPatternDefinition definition(String id) {
        PatternPosition origin = new PatternPosition(0, 0, 0);
        return new MultiblockPatternDefinition(id, MultiblockPatternDefinition.SCHEMA_VERSION, 1,
                new PatternSize(1, 1, 1), origin, Set.of(PatternRotation.ZERO), false,
                Map.of(origin, new PatternMatcher.Controller()));
    }
}
