package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

class SealedDiagnosticsTest {
    @Test void repeatedHoldDoesNotLogAgainAndTransitionsWaitTwentyTicks() throws Exception {
        var f = new Fixture(); f.hold("first", "STAGE", "BARRIER_IO"); f.flush(0);
        for (int tick = 1; tick < 100; tick++) { f.hold("first", "STAGE", "BARRIER_IO"); f.flush(tick); }
        assertEquals(1, f.lines.size());
        f.hold("first", "STAGE", "SAVE_OFF"); f.flush(100); assertEquals(2, f.lines.size());
        f.hold("first", "STAGE", "UNLOADED"); f.flush(119); assertEquals(2, f.lines.size());
        f.flush(120); assertEquals(3, f.lines.size());
    }
    @Test void sixtyFourIdentitiesAndOverflowShareOneAggregateLine() throws Exception {
        var f = new Fixture();
        for (int i = 0; i < 70; i++) { f.hold("id-" + i, "STAGE", "CONFLICT"); }
        assertEquals(64, f.call("size", new Class<?>[0])); f.flush(0);
        assertEquals(1, f.lines.size()); assertTrue(f.lines.get(0).contains("overflow=6"));
        assertTrue(f.lines.get(0).contains("transaction=id-63")); assertFalse(f.lines.get(0).contains("transaction=id-64"));
    }
    @Test void CompletionIsInfoAndReleasesItsTrackedIdentityAfterEmission() throws Exception {
        var f = new Fixture(); f.call("complete", new Class<?>[] {String.class, String.class}, "finished", "SEALED");
        f.flush(0); assertEquals(0, f.call("size", new Class<?>[0]));
        assertFalse(f.warning.get(0)); assertTrue(f.lines.get(0).contains("ARCE_DISASSEMBLY_DURABILITY_COMPLETE"));
    }
    @Test void invalidOrUnboundedTextCannotBecomePayloadLogging() throws Exception {
        var f = new Fixture();
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> f.hold("x".repeat(193), "STAGE", "BARRIER_IO"));
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> f.hold("id", "STAGE", "payload={diamond:7}"));
    }
    private static final class Fixture {
        final List<String> lines = new ArrayList<>(); final List<Boolean> warning = new ArrayList<>(); final Object diagnostics;
        Fixture() throws Exception {
            var type = Class.forName("io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedDiagnostics");
            var constructor = type.getDeclaredConstructor(BiConsumer.class); constructor.setAccessible(true);
            diagnostics = constructor.newInstance((BiConsumer<Boolean, String>) (warn, line) -> { warning.add(warn); lines.add(line); });
        }
        Object call(String name, Class<?>[] types, Object... values) throws Exception {
            var method = diagnostics.getClass().getDeclaredMethod(name, types); method.setAccessible(true); return method.invoke(diagnostics, values);
        }
        void hold(String id, String phase, String reason) throws Exception { call("hold", new Class<?>[] {String.class, String.class, String.class}, id, phase, reason); }
        void flush(long tick) throws Exception { call("flush", new Class<?>[] {long.class}, tick); }
    }
}
