package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowConsumerTimingProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void defaultRuntimeAndEitherUnsetGateKeepTheNoOpSink() throws Exception {
        // Plain JUnit has no Forge CapabilityToken transformer. Inspect the actual compiled
        // initializer instead of loading EscrowRuntime's unrelated capability static initializer.
        var type = new org.objectweb.asm.tree.ClassNode();
        try (var input = getClass().getResourceAsStream("EscrowRuntime.class")) {
            assertNotNull(input); new org.objectweb.asm.ClassReader(input).accept(type, 0);
        }
        var constructor = type.methods.stream().filter(method -> method.name.equals("<init>")).findFirst().orElseThrow();
        var assignment = java.util.Arrays.stream(constructor.instructions.toArray())
                .filter(node -> node instanceof org.objectweb.asm.tree.FieldInsnNode field
                        && field.name.equals("consumerTimings") && field.getOpcode() == org.objectweb.asm.Opcodes.PUTFIELD)
                .findFirst().orElseThrow();
        var initial = assertInstanceOf(org.objectweb.asm.tree.FieldInsnNode.class, assignment.getPrevious());
        assertEquals(org.objectweb.asm.Opcodes.GETSTATIC, initial.getOpcode());
        assertEquals("NONE", initial.name);
        assertEquals(EscrowTimingSink.class.getName().replace('.', '/'), initial.owner);
        for (boolean hooks : List.of(false, true)) {
            for (boolean requested : List.of(false, true)) {
                if (hooks && requested) { continue; }
                assertSame(EscrowTimingSink.NONE, EscrowConsumerTimingProbe.select(hooks, requested,
                        () -> fail("Disabled observation consulted the server")));
            }
        }
    }

    @Test void enabledSelectionRequiresTheServerGateAndNeverSwallowsItsRefusal() {
        var calls = new AtomicInteger();
        assertInstanceOf(EscrowConsumerTimingProbe.class, EscrowConsumerTimingProbe.select(true, true, calls::incrementAndGet));
        assertEquals(1, calls.get());
        assertThrows(IllegalArgumentException.class, () -> EscrowConsumerTimingProbe.select(true, true,
                () -> { throw new IllegalArgumentException("Gate refused"); }));
    }

    @Test void concurrentSamplesRemainBoundedAndOverflowInvalidatesEvidence() throws Exception {
        var probe = new EscrowConsumerTimingProbe();
        Runnable write = () -> { for (int i = 0; i < EscrowConsumerTimingProbe.LIMIT; i++) { probe.sample("ledger_io", i); } };
        var writer = new Thread(write); var reader = new Thread(write);
        writer.start(); reader.start(); writer.join(); reader.join();
        assertEquals(true, probe.snapshot().get("capped"));
        assertEquals(EscrowConsumerTimingProbe.LIMIT, ((List<?>) probe.snapshot().get("events")).size());
    }

    @Test void validSamplesRetainDurationAndHistoricalPreF2CutsAreNotConsumerVariants() {
        var probe = new EscrowConsumerTimingProbe(); probe.sample("f1.barrier", 123);
        assertEquals(false, probe.snapshot().get("capped"));
        var sample = (EscrowConsumerTimingProbe.Event) ((List<?>) probe.snapshot().get("events")).get(0);
        assertEquals(123, sample.value()); assertEquals("f1.barrier", sample.phase());
        for (String cut : List.of("", "PF_ARM_ACK", "PF_ARM_REPLACED", "PF_GOVERNED_T_ACK", "PF_LIVE_F2_ACK")) {
            assertFalse(EscrowPreF2Probe.consumerCase(cut));
        }
        assertTrue(EscrowPreF2Probe.consumerCase("PF_CONSUMER_F1"));
        assertTrue(EscrowPreF2Probe.consumerCase("PF_CONSUMER_CANCEL"));
    }
}
