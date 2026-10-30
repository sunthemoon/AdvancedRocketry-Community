package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The preload inventory is an audited code boundary, not authority or a simulated launch. */
class EscrowRelaunchClassesTest {
    private static final String PACKAGE = "io.github.sunthemoon.advancedrocketrycommunity.";
    private static int initialized;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    static final class Witness {
        static { initialized++; }
    }

    @Test void initializesRatherThanOnlyLoadsAndDoesNotRepeatTheInitializer() {
        assertEquals(0, initialized);
        String name = Witness.class.getName();
        EscrowRelaunchClasses.initializeClass(name, getClass().getClassLoader());
        assertEquals(1, initialized, "Loading alone leaves first-use initialization on the player tick");
        EscrowRelaunchClasses.initializeClass(name, getClass().getClassLoader());
        assertEquals(1, initialized);
    }

    @Test void missingClassFailsLoudlyWithItsIdentity() {
        String name = PACKAGE + "resourceescrow.MissingRelaunchClass";
        var failure = assertThrows(IllegalStateException.class,
                () -> EscrowRelaunchClasses.initializeClass(name, getClass().getClassLoader()));
        assertTrue(failure.getMessage().contains(name));
        assertInstanceOf(ClassNotFoundException.class, failure.getCause());
    }

    @Test void exactlyMatchesMeasuredInventoryAndEveryClassExists() throws Exception {
        try (var input = getClass().getResourceAsStream("/relaunch-first-use-classes.txt")) {
            assertNotNull(input);
            var expected = new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            assertEquals(55, expected.size());
            assertEquals(expected, EscrowRelaunchClasses.classNames());
            assertEquals(expected.size(), new HashSet<>(expected).size());
            for (String name : expected) {
                assertNotNull(Class.forName(PACKAGE + name, false, getClass().getClassLoader()));
            }
        }
    }

    @Test void initializersHaveOnlyAuditedValueConstructionAndNoWorldOrConfigurationAccess() throws Exception {
        for (String name : EscrowRelaunchClasses.classNames()) {
            var type = read(PACKAGE.replace('.', '/') + name.replace('.', '/'));
            assertTrue(Set.of("java/lang/Object", "java/lang/Record", "java/lang/Enum",
                    "java/lang/IllegalArgumentException").contains(type.superName), type.name);
            // None of these interfaces has a default-method initializer with runtime effects.
            for (String contract : type.interfaces) {
                assertTrue(Set.of("net/minecraft/world/level/entity/EntityPersistentStorage",
                        "java/util/function/BooleanSupplier").contains(contract), type.name + ": " + contract);
            }
            audit(type, "<clinit>", new HashSet<>());
        }
    }

    @Test void repeatedStartupNeedsNoServerAndPreservesStaticValueIdentity() throws Exception {
        EscrowRelaunchClasses.onServerStarted(null);
        var before = staticValues();
        EscrowRelaunchClasses.onServerStarted(null);
        var after = staticValues();
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) { assertSame(before.get(i), after.get(i)); }
    }

    @Test void startupIsRegisteredOnlyAtTheForgeServerStartedBoundary() throws Exception {
        // Reading the Bus enum reflectively constructs Forge's event buses outside a Forge launch.
        var type = read(EscrowRelaunchClasses.class.getName().replace('.', '/'));
        var subscription = type.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")).findFirst().orElseThrow();
        assertEquals("advancedrocketrycommunity", subscription.values.get(subscription.values.indexOf("modid") + 1));
        assertArrayEquals(new String[]{"Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber$Bus;", "FORGE"},
                (String[]) subscription.values.get(subscription.values.indexOf("bus") + 1));
        var hooks = type.methods.stream().filter(m -> m.visibleAnnotations != null && m.visibleAnnotations.stream()
                .anyMatch(a -> a.desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;"))).toList();
        assertEquals(1, hooks.size());
        assertEquals("onServerStarted", hooks.get(0).name);
        assertEquals("(Lnet/minecraftforge/event/server/ServerStartedEvent;)V", hooks.get(0).desc);
    }

    @Test void initializerAuditRejectsExternalStateReadsAndCalls() {
        var type = new ClassNode(); type.name = "test/Unsafe";
        var method = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        type.methods.add(method);
        method.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, "test/World", "state", "Ljava/lang/Object;"));
        assertThrows(AssertionError.class, () -> audit(type, "<clinit>", new HashSet<>()));
        method.instructions.clear();
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/System", "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", false));
        assertThrows(AssertionError.class, () -> audit(type, "<clinit>", new HashSet<>()));
    }

    private List<Object> staticValues() throws Exception {
        var values = new ArrayList<>();
        for (String name : EscrowRelaunchClasses.classNames()) {
            Class<?> type = Class.forName(PACKAGE + name, false, getClass().getClassLoader());
            for (var field : type.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
                    field.setAccessible(true);
                    values.add(field.get(null));
                }
            }
        }
        return values;
    }

    private static ClassNode read(String name) throws IOException {
        var type = new ClassNode();
        try (var input = EscrowRelaunchClassesTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            new ClassReader(input).accept(type, 0);
        }
        return type;
    }

    private static void audit(ClassNode type, String methodName, Set<String> visited) {
        if (!visited.add(type.name + "." + methodName)) { return; }
        for (var method : type.methods) {
            if (!method.name.equals(methodName)) { continue; }
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals(type.name)) {
                        assertTrue(Set.of("<init>", "$values").contains(call.name), call.owner + "." + call.name);
                        audit(type, call.name, visited);
                    } else {
                        String target = call.owner + "." + call.name + call.desc;
                        assertTrue(Set.of(
                                "java/lang/Enum.<init>(Ljava/lang/String;I)V",
                                "io/github/sunthemoon/advancedrocketrycommunity/ModIdentity.id(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;",
                                "net/minecraft/resources/ResourceLocation.toString()Ljava/lang/String;",
                                "net/minecraft/world/level/ChunkPos.<init>(II)V",
                                "java/util/List.of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;",
                                "java/util/Set.of(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;",
                                "java/util/Set.of(Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Set;"
                        ).contains(target), type.name + ": " + target);
                    }
                } else if (instruction instanceof FieldInsnNode field) {
                    assertEquals(type.name, field.owner, "Initializer must not read external state");
                    boolean phaseConstant = method.name.equals("<init>")
                            && type.name.equals("io/github/sunthemoon/advancedrocketrycommunity/rocket/flight/RocketTransferPhase")
                            && field.name.equals("destinationAuthoritative") && field.desc.equals("Z")
                            && field.getOpcode() == Opcodes.PUTFIELD;
                    assertTrue(phaseConstant || field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC,
                            type.name + "." + field.name);
                } else if (instruction instanceof InvokeDynamicInsnNode) {
                    fail("Unaudited initializer call site: " + type.name);
                } else if (instruction instanceof TypeInsnNode value && value.getOpcode() == Opcodes.NEW) {
                    assertTrue(value.desc.equals(type.name) || value.desc.equals("net/minecraft/world/level/ChunkPos"),
                            "Unaudited allocation: " + value.desc);
                }
            }
        }
    }
}
