package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry.Outcome.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketMonitorWarmupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private final RocketWarmupRegistryTest fixture = new RocketWarmupRegistryTest();
    private final RocketWarmupRegistry registry = fixture.registry;
    private static final ResourceLocation MOON = ResourceLocation.tryParse("advancedrocketrycommunity:moon");

    private RocketWarmupRegistry.Request request(RocketWarmupRegistryTest.Rocket rocket, int pad) {
        var ordinary = rocket.request(UUID.randomUUID(), RocketWarmupRegistryTest.pad(MOON, pad));
        return new RocketWarmupRegistry.Request(ordinary.logical(), ordinary.player(), ordinary.requestId(), ordinary.sourceLevel(),
                ordinary.sourceEntity(), ordinary.source(), ordinary.target(), ordinary.destination(), false);
    }
    private RocketWarmupRegistry.Decision cold(RocketWarmupRegistry.Request request, long now) {
        return registry.decide(request, true, () -> false, now, RocketWarmupSettings.DEFAULTS);
    }
    @Test void offlineOwnerColdDestinationKeepsTicketsUntilReadyAndRequiresAFreshRequest() {
        var rocket = fixture.new Rocket(UUID.randomUUID()); var first = request(rocket, 0);
        assertEquals(LOADING, cold(first, 10).outcome());
        var probe = new RocketWarmupRegistryTest.Probe(); registry.tick(11, probe);
        assertEquals(4, fixture.tickets.count(rocket.logical));
        assertEquals(rocket.player, registry.warmup(rocket.logical).orElseThrow().player());
        probe.ready.add(rocket.logical); registry.tick(12, probe);
        assertEquals(RocketWarmupRegistry.State.READY, registry.warmup(rocket.logical).orElseThrow().state());
        assertTrue(registry.startedBy(first.requestId()));
        assertNull(registry.warmup(rocket.logical).orElseThrow().transfer(), "Readiness launched without a new edge");
        var next = request(rocket, 0);
        var decision = registry.decide(next, true, () -> true, 13, RocketWarmupSettings.DEFAULTS);
        assertEquals(PROCEED, decision.outcome()); registry.afterBegin(decision, true, 13);
        assertEquals(next.requestId(), registry.warmup(rocket.logical).orElseThrow().transfer());
        assertEquals(10, registry.warmup(rocket.logical).orElseThrow().started());
        assertEquals(4, fixture.tickets.calls.size(), "Retry renewed tickets");
    }
    @Test void offlineMonitorOwnersStillConsumeBothCaps() {
        var owner = UUID.randomUUID();
        assertEquals(LOADING, cold(request(fixture.new Rocket(owner), 0), 10).outcome());
        assertEquals(LOADING, cold(request(fixture.new Rocket(owner), 1), 10).outcome());
        registry.tick(11, new RocketWarmupRegistryTest.Probe());
        assertEquals(LIMIT, cold(request(fixture.new Rocket(owner), 2), 12).outcome());
        for (int i = 2; i < RocketWarmupSettings.DEFAULTS.globalCap(); i++) {
            assertEquals(LOADING, cold(request(fixture.new Rocket(UUID.randomUUID()), i), 12).outcome());
        }
        assertEquals(LIMIT, cold(request(fixture.new Rocket(UUID.randomUUID()), 9), 12).outcome());
        assertEquals(RocketWarmupSettings.DEFAULTS.globalCap(), registry.live().size());
    }
    @Test void offlineWarmupsStillExpireAndReleaseWhenTheSourceIsRemoved() {
        var removed = fixture.new Rocket(UUID.randomUUID()); var expired = fixture.new Rocket(UUID.randomUUID());
        cold(request(removed, 0), 10); cold(request(expired, 1), 10);
        var probe = new RocketWarmupRegistryTest.Probe(); probe.removed.add(removed.entity);
        registry.tick(11, probe);
        assertTrue(registry.warmup(removed.logical).isEmpty());
        assertTrue(registry.warmup(expired.logical).isPresent());
        assertTrue(fixture.log.stream().anyMatch(line -> line.endsWith("reason=ROCKET_REMOVED")));
        registry.tick(10 + RocketWarmupSettings.DEFAULTS.lifetimeTicks(), probe);
        assertTrue(registry.live().isEmpty()); assertEquals(0, fixture.tickets.count(expired.logical));
        assertTrue(fixture.log.stream().anyMatch(line -> line.endsWith("reason=EXPIRED")));
    }
}
