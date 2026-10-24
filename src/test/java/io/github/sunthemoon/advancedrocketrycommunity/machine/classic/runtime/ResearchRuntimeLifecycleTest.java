package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.server.ServerStoppedEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Real stop callback, with unstarted server objects; native starts cover Forge event delivery. */
class ResearchRuntimeLifecycleTest {
    @BeforeAll static void bootstrap() throws Exception {
        MinecraftBootstrap.initialize();
        // As in RocketEscrowAuditDirectionTest, plain JUnit needs only Forge's
        // missing capability-token resolution. No lifecycle method is altered.
        var type = new ClassNode();
        try (var input = ResearchRuntimeLifecycleTest.class.getResourceAsStream("ResearchRuntime.class")) {
            assertNotNull(input); new ClassReader(input).accept(type, 0);
        }
        var instructions = type.methods.stream().filter(method -> method.name.equals("<clinit>"))
                .findFirst().orElseThrow().instructions;
        var token = Arrays.stream(instructions.toArray()).filter(node -> node instanceof TypeInsnNode allocation
                && allocation.getOpcode() == Opcodes.NEW && allocation.desc.equals(type.name + "$1")).findFirst().orElseThrow();
        var get = token.getNext().getNext().getNext();
        var call = assertInstanceOf(MethodInsnNode.class, get);
        assertEquals("net/minecraftforge/common/capabilities/CapabilityManager", call.owner);
        assertEquals("get", call.name);
        var end = get.getNext();
        instructions.insertBefore(token, new MethodInsnNode(Opcodes.INVOKESTATIC,
                ResearchRuntimeLifecycleTest.class.getName().replace('.', '/'), "capability",
                "()Lnet/minecraftforge/common/capabilities/Capability;", false));
        for (var node = token; node != end; ) { var next = node.getNext(); instructions.remove(node); node = next; }
        var output = new ClassWriter(0); type.accept(output);
        MethodHandles.lookup().defineClass(output.toByteArray());
    }

    static Capability<?> capability() throws ReflectiveOperationException {
        var get = CapabilityManager.class.getDeclaredMethod("get", String.class, boolean.class);
        get.setAccessible(true);
        return (Capability<?>) get.invoke(CapabilityManager.INSTANCE,
                "io/github/sunthemoon/advancedrocketrycommunity/machine/classic/runtime/ResearchService", false);
    }

    @Test void stopAfterPreLevelRefusalDoesNotAccessAService() { Checks.noOverworld(); }
    @Test void stopAfterPartialStartupDoesNotRequireAnAttachedService() { Checks.missingCapability(); }
    @Test void normalStopClosesExistingServiceAndIsIdempotent() { Checks.installedService(); }
    @Test void stopAfterLevelInvalidationDoesNotRequireAnAttachedService() { Checks.invalidatedCapability(); }
    @Test void ordinaryServiceAccessStillRequiresAnInstalledCapability() { Checks.strictAccess(); }

    // Defer runtime resolution until the plain-JUnit token bootstrap above.
    private static final class Checks {
        static void noOverworld() {
            var server = UnstartedServer.owned();
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of());
            assertNull(server.overworld());
            assertDoesNotThrow(() -> ResearchRuntime.stopped(new ServerStoppedEvent(server)));
            assertDoesNotThrow(() -> ResearchRuntime.stopped(new ServerStoppedEvent(server)));
            assertNull(server.overworld(), "Stopping must not create a Level or service");
        }

        static void missingCapability() {
            var level = level();
            assertDoesNotThrow(() -> ResearchRuntime.stopped(new ServerStoppedEvent(level.getServer())));
            assertFalse(level.value.isPresent());
        }

        static void installedService() {
            var level = level();
            var service = new ResearchService(level);
            level.value = LazyOptional.of(() -> service);
            assertSame(service, ResearchRuntime.service(level));
            assertTrue(service.allowIntent(UUID.randomUUID()));
            assertFalse(UnstartedServer.<Boolean>get(ResearchService.class, service, "closed"));
            ResearchRuntime.stopped(new ServerStoppedEvent(level.getServer()));
            assertTrue(UnstartedServer.<Boolean>get(ResearchService.class, service, "closed"));
            assertTrue(UnstartedServer.<Map<?, ?>>get(ResearchService.class, service, "intents").isEmpty());
            assertDoesNotThrow(() -> ResearchRuntime.stopped(new ServerStoppedEvent(level.getServer())));
        }

        static void invalidatedCapability() {
            var level = level();
            var service = new ResearchService(level);
            level.value = LazyOptional.of(() -> service);
            // The attachment invalidation listener closes before invalidating.
            service.close(); level.value.invalidate();
            assertDoesNotThrow(() -> ResearchRuntime.stopped(new ServerStoppedEvent(level.getServer())));
            assertFalse(level.value.isPresent());
        }

        static void strictAccess() {
            assertThrows(IllegalStateException.class, () -> ResearchRuntime.service(level()));
        }

        private static TestLevel level() {
            var server = UnstartedServer.owned();
            var level = UnstartedServer.allocate(TestLevel.class);
            UnstartedServer.set(ServerLevel.class, level, "server", server);
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(Level.OVERWORLD, level));
            level.value = LazyOptional.empty();
            return level;
        }

        private static final class TestLevel extends ServerLevel {
            LazyOptional<ResearchService> value;
            TestLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
            @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) {
                return capability == UnstartedServer.get(ResearchRuntime.class, null, "SERVICE") ? value.cast() : LazyOptional.empty();
            }
        }
    }
}
