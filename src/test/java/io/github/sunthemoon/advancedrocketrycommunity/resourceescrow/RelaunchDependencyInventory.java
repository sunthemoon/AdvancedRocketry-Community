package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/**
 * Test-only, conservative bytecode inventory; never loads or initializes the inspected classes.
 * Follows declared call targets and lambda handles, not arbitrary virtual implementations or reflective strings.
 * Descriptors and exception-verification edges intentionally over-approximate an actual first-use load window.
 */
final class RelaunchDependencyInventory {
    static final String PREFIX = "io/github/sunthemoon/advancedrocketrycommunity/";

    @FunctionalInterface interface Bytecode { ClassNode read(String name) throws IOException; }
    private record Method(String owner, String name, String descriptor) { }

    private final Bytecode bytecode;
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final Set<String> reached = new TreeSet<>();
    private final Set<Method> visited = new HashSet<>();
    private final ArrayDeque<Method> pending = new ArrayDeque<>();

    RelaunchDependencyInventory(Bytecode bytecode) { this.bytecode = bytecode; }

    static ClassNode read(String name) throws IOException {
        try (var input = RelaunchDependencyInventory.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, "Missing relaunch bytecode: " + name + "; initializer re-audit required");
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return node;
        }
    }

    Set<String> inventory() throws IOException {
        // Real request entry plus the two internal capture boundaries: overloads are included, not every method.
        root("rocket/server/RocketTransferService", "prepareLaunch");
        root("resourceescrow/EscrowGovernedLaunch", "prepare");
        root("resourceescrow/EscrowFlightCheckpoint", "begin");
        root("resourceescrow/EscrowFlightSource", "<init>");
        while (!pending.isEmpty()) {
            assertTrue(visited.size() < 10_000, "Relaunch walk exceeded 10000 methods; re-audit its boundaries");
            var method = pending.removeFirst();
            if (!visited.add(method) || (stop(method.owner()) && !runtimeStore(method)) || lifecycleLookup(method)) { continue; }
            var owner = node(method.owner());
            var body = owner.methods.stream().filter(m -> m.name.equals(method.name())
                    && m.desc.equals(method.descriptor())).findFirst();
            if (body.isEmpty()) {
                if (owner.superName != null) { call(owner.superName, method.name(), method.descriptor()); }
                for (String contract : owner.interfaces) { call(contract, method.name(), method.descriptor()); }
                continue;
            }
            scan(body.get());
        }
        return Set.copyOf(reached);
    }

    private void root(String owner, String name) throws IOException {
        var methods = node(PREFIX + owner).methods.stream().filter(m -> m.name.equals(name)).toList();
        assertFalse(methods.isEmpty(), "Missing relaunch entry: " + owner + "." + name + "; initializer re-audit required");
        for (var method : methods) { call(PREFIX + owner, name, method.desc); }
    }

    private ClassNode node(String name) throws IOException {
        var result = classes.get(name);
        if (result == null) { result = bytecode.read(name); classes.put(name, result); }
        return result;
    }

    private void reference(String name) throws IOException {
        if (name.startsWith("[")) { descriptor(name); return; }
        if (!name.startsWith(PREFIX) || !reached.add(name.substring(PREFIX.length()).replace('/', '.'))) { return; }
        assertTrue(reached.size() <= 2_000, "Relaunch walk exceeded 2000 classes; re-audit its boundaries");
        if (stop(name)) { return; }
        var type = node(name);
        if (type.superName != null) { reference(type.superName); }
        for (String contract : type.interfaces) { reference(contract); }
        for (var method : type.methods) {
            if (method.name.equals("<clinit>")) { pending.add(new Method(name, method.name, method.desc)); }
            // Verification can resolve exception subtypes in methods that this request does not execute.
            // This includes W212's MarkerLimit/StaleCapture loads when SettlementCapacity is linked.
            for (var handler : method.tryCatchBlocks) { if (handler.type != null) { reference(handler.type); } }
            for (var instruction : method.instructions) {
                if (instruction instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                        && allocation.desc.startsWith(PREFIX) && throwable(allocation.desc)) { reference(allocation.desc); }
            }
        }
    }

    private boolean throwable(String name) throws IOException {
        if (name.equals("java/lang/Throwable")) { return true; }
        var parent = node(name).superName;
        return parent != null && throwable(parent);
    }

    private void call(String owner, String name, String descriptor) throws IOException {
        reference(owner); descriptor(descriptor);
        if (owner.startsWith(PREFIX)) { pending.add(new Method(owner, name, descriptor)); }
        // Vanilla calls the decorator back through EntityPersistentStorage during captureAndStore.
        // Only follow this callback if the real capture method is reachable, never seed its class as a root.
        if (owner.equals(PREFIX + "resourceescrow/EscrowEntityStore") && name.equals("captureAndStore")) {
            root("resourceescrow/EscrowEntityStore", "storeEntities");
        }
    }

    private void descriptor(String descriptor) throws IOException { type(Type.getType(descriptor)); }

    private void type(Type type) throws IOException {
        switch (type.getSort()) {
            case Type.OBJECT -> reference(type.getInternalName());
            case Type.ARRAY -> type(type.getElementType());
            case Type.METHOD -> {
                for (var argument : type.getArgumentTypes()) { type(argument); }
                type(type.getReturnType());
            }
            default -> { }
        }
    }

    private void constant(Object value) throws IOException {
        if (value instanceof Type type) { type(type); }
        else if (value instanceof Handle handle) {
            if (handle.getTag() <= Opcodes.H_PUTSTATIC) { reference(handle.getOwner()); descriptor(handle.getDesc()); }
            else { call(handle.getOwner(), handle.getName(), handle.getDesc()); }
        } else if (value instanceof ConstantDynamic dynamic) {
            descriptor(dynamic.getDescriptor()); constant(dynamic.getBootstrapMethod());
            for (int i = 0; i < dynamic.getBootstrapMethodArgumentCount(); i++) { constant(dynamic.getBootstrapMethodArgument(i)); }
        }
    }

    private void scan(MethodNode method) throws IOException {
        for (var handler : method.tryCatchBlocks) { if (handler.type != null) { reference(handler.type); } }
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode invoke) { call(invoke.owner, invoke.name, invoke.desc); }
            else if (instruction instanceof FieldInsnNode field) { reference(field.owner); descriptor(field.desc); }
            else if (instruction instanceof TypeInsnNode type) { reference(type.desc); }
            else if (instruction instanceof MultiANewArrayInsnNode array) { descriptor(array.desc); }
            else if (instruction instanceof LdcInsnNode ldc) { constant(ldc.cst); }
            else if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                descriptor(dynamic.desc); constant(dynamic.bsm);
                for (Object argument : dynamic.bsmArgs) { constant(argument); }
            }
        }
    }

    private static boolean stop(String name) {
        // External method bodies are never walked (exception superclass metadata may be read). Mod adapters
        // expose their referenced types, but their
        // lifecycle/registration/entity implementations are outside the governed relaunch helper boundary.
        String relative = name.substring(PREFIX.length());
        return !(relative.startsWith("resourceescrow/") || relative.startsWith("rocket/flight/")
                || relative.startsWith("rocket/model/") || relative.startsWith("rocket/persistence/")
                || relative.startsWith("rocket/server/") || relative.startsWith("rocket/transfer/")
                || relative.startsWith("rocket/transaction/") || relative.startsWith("persistence/")
                || relative.startsWith("travel/"))
                // Lifecycle owners and post-F2/ordinary-flight orchestration, not the F1 helpers themselves.
                || Set.of("resourceescrow/EscrowRuntime", "resourceescrow/ResourceEscrowSavedData",
                        "rocket/server/RocketRuntime",
                        "rocket/server/RocketManager", "rocket/server/RocketTransferRecoveryService",
                        "rocket/server/RocketTransferBayDispatch", "rocket/server/RocketOrdinaryLaunch",
                        "rocket/server/RocketFlightFeedback", "rocket/server/RocketFillReconciliation",
                        "rocket/server/RocketLandedDisassemblies", "rocket/server/RocketDestinationRetirement",
                        "rocket/server/RocketReplacementRetirement", "rocket/server/RocketLandedDisassemblyRules",
                        "rocket/persistence/RocketTransactionSavedData").contains(relative);
    }

    private static boolean lifecycleLookup(Method method) {
        // Keep journal validation/insertion code, but do not traverse world loading through SavedData.get.
        return method.owner().equals(PREFIX + "rocket/flight/persistence/RocketTransferSavedData")
                && method.name().equals("get");
    }

    private static boolean runtimeStore(Method method) {
        // The lazy F1 store factory is inside the path; the runtime's capability/event/probe lifecycle is not.
        return method.owner().equals(PREFIX + "resourceescrow/EscrowRuntime")
                && (method.name().equals("store") || method.name().startsWith("lambda$store$"));
    }

    static void assertMatches(Set<String> actual, Set<String> measured, Set<String> other) {
        var overlap = new TreeSet<>(measured); overlap.retainAll(other);
        assertTrue(overlap.isEmpty(), "Measured classes cannot be exempted: " + overlap);
        var expected = new TreeSet<>(measured); expected.addAll(other);
        var added = new TreeSet<>(actual); added.removeAll(expected);
        var removed = new TreeSet<>(expected); removed.removeAll(actual);
        assertTrue(added.isEmpty() && removed.isEmpty(), () -> "Relaunch dependency change; initializer re-audit required. "
                + "New classes missing from the audited inventory: " + added + "; classes no longer used: " + removed);
    }
}
