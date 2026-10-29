package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import net.minecraftforge.common.capabilities.Capability;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Supplies only Forge's missing capability-token transform to the plain JUnit device fixtures. */
public final class EscrowTurnYieldBootstrap {
    private static boolean initialized;
    private EscrowTurnYieldBootstrap() { }
    static void initialize() throws Exception {
        EscrowTestBootstrap.initialize();
        if (initialized) { return; }
        defineIndex(RocketFluidCargoRoot.class, "RocketFluidCargoIndex");
        defineIndex(LoaderTank.class, "FluidFuelLoaderIndex");
        initialized = true;
    }
    private static void defineIndex(Class<?> neighbour, String name) throws Exception {
        var type = new ClassNode();
        try (var input = neighbour.getResourceAsStream(name + ".class")) {
            assertNotNull(input); new ClassReader(input).accept(type, 0);
        }
        var instructions = type.methods.stream().filter(method -> method.name.equals("<clinit>"))
                .findFirst().orElseThrow().instructions;
        var token = Arrays.stream(instructions.toArray()).filter(node -> node instanceof TypeInsnNode allocation
                && allocation.getOpcode() == Opcodes.NEW && allocation.desc.equals(type.name + "$1")).findFirst().orElseThrow();
        var get = token.getNext().getNext().getNext();
        var call = assertInstanceOf(MethodInsnNode.class, get);
        assertEquals("net/minecraftforge/common/capabilities/CapabilityManager", call.owner); assertEquals("get", call.name);
        var end = get.getNext();
        instructions.insertBefore(token, new LdcInsnNode(type.name + "$Devices"));
        instructions.insertBefore(token, new MethodInsnNode(Opcodes.INVOKESTATIC,
                EscrowTurnYieldBootstrap.class.getName().replace('.', '/'), "capability",
                "(Ljava/lang/String;)Lnet/minecraftforge/common/capabilities/Capability;", false));
        for (var node = token; node != end; ) { var next = node.getNext(); instructions.remove(node); node = next; }
        var output = new ClassWriter(0); type.accept(output);
        MethodHandles.privateLookupIn(neighbour, MethodHandles.lookup()).defineClass(output.toByteArray());
    }
    public static Capability<?> capability(String type) throws Exception {
        var get = net.minecraftforge.common.capabilities.CapabilityManager.class.getDeclaredMethod("get", String.class, boolean.class);
        get.setAccessible(true);
        return (Capability<?>) get.invoke(net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE, type.intern(), false);
    }
}
