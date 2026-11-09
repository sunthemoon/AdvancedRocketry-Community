package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

/**
 * Structural evidence for the pre-classification join adapter, not cold-start evidence.
 * Inspects compiled bytecode without loading Minecraft classes. Pins direct calls only, not callee behavior,
 * startup classification, branch execution or a real UNCLASSIFIED join.
 */
final class RocketReleaseJoinStructureTest {
    private static final String PREFIX = "io/github/sunthemoon/advancedrocketrycommunity/";
    private static final String ACCESS = PREFIX + "rocket/server/RocketReleaseAccess";
    private static final String ROCKET = PREFIX + "rocket/entity/RocketEntity";
    private static final String FLIGHT = PREFIX + "rocket/flight/RocketFlightData";
    private static final String MANIFEST = PREFIX + "rocket/flight/RocketPassengerManifest";

    private record Call(int opcode, String owner, String name, String descriptor) { }

    @Test void joinedStructurallyOnlyFiltersChecksIdleAndUpdatesPassengers() throws IOException {
        var type = new ClassNode();
        try (var input = getClass().getResourceAsStream("/" + ACCESS + ".class")) {
            assertNotNull(input, "Missing compiled join adapter");
            new ClassReader(input).accept(type, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        var entries = type.methods.stream().filter(method -> method.name.equals("joined")).toList();
        assertEquals(1, entries.size(), "Re-audit the structural boundary if the join entries change");
        var joined = entries.get(0);
        assertEquals("(L" + ROCKET + ";)V", joined.desc);
        var actual = new ArrayList<Call>();
        for (var instruction : joined.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                actual.add(new Call(call.getOpcode(), call.owner, call.name, call.desc));
            } else if (instruction instanceof InvokeDynamicInsnNode) {
                fail("Join adapter gained an indirect call; re-audit for resource transfer, admission and ledger writes");
            } else if (instruction instanceof FieldInsnNode field) {
                assertFalse(field.getOpcode() == Opcodes.PUTFIELD || field.getOpcode() == Opcodes.PUTSTATIC,
                        "Join adapter must not gain direct field writes");
            }
        }
        // Exact ordered calls reject additions, removals, duplicate calls and changed overloads/owners.
        var expected = List.of(
                new Call(Opcodes.INVOKEVIRTUAL, ROCKET, "level", "()Lnet/minecraft/world/level/Level;"),
                new Call(Opcodes.INVOKEVIRTUAL, ROCKET, "operational", "()Z"),
                new Call(Opcodes.INVOKEVIRTUAL, ROCKET, "flightData", "()Ljava/util/Optional;"),
                new Call(Opcodes.INVOKEVIRTUAL, "java/util/Optional", "orElseThrow", "()Ljava/lang/Object;"),
                new Call(Opcodes.INVOKEVIRTUAL, ROCKET, "getServer", "()Lnet/minecraft/server/MinecraftServer;"),
                new Call(Opcodes.INVOKEVIRTUAL, FLIGHT, "logicalRocketId", "()Ljava/util/UUID;"),
                new Call(Opcodes.INVOKEVIRTUAL, FLIGHT, "passengers", "()L" + MANIFEST + ";"),
                new Call(Opcodes.INVOKESTATIC, ACCESS, "filter",
                        "(Lnet/minecraft/server/MinecraftServer;Ljava/util/UUID;L" + MANIFEST + ";)L" + MANIFEST + ";"),
                new Call(Opcodes.INVOKEVIRTUAL, FLIGHT, "passengers", "()L" + MANIFEST + ";"),
                new Call(Opcodes.INVOKEVIRTUAL, MANIFEST, "equals", "(Ljava/lang/Object;)Z"),
                new Call(Opcodes.INVOKESTATIC, PREFIX + "resourceescrow/RocketEscrowGate", "idle", "(L" + ROCKET + ";)Z"),
                new Call(Opcodes.INVOKEVIRTUAL, FLIGHT, "withPassengers", "(L" + MANIFEST + ";)L" + FLIGHT + ";"),
                new Call(Opcodes.INVOKEVIRTUAL, ROCKET, "updateFlightData", "(L" + FLIGHT + ";)V"));
        assertEquals(expected, actual,
                "Join adapter direct calls changed; re-audit for resource transfer, admission and ledger writes");
    }
}
