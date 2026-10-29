package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.util.stream.Stream;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.common.capabilities.Capability;

/** Plain JUnit's shared escrow setup; no individual test class owns class-definition ordering. */
public final class EscrowTestBootstrap {
    private static boolean runtimeDefined;
    private EscrowTestBootstrap() { }

    public static void initialize() {
        MinecraftBootstrap.initialize();
        synchronized (EscrowTestBootstrap.class) {
            var id = ResourceLocation.tryParse(EscrowAdapterIds.ROCKET);
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
                        EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).build(id.toString()));
            }
        }
    }

    /** Called first by MinecraftBootstrap, before another fixture can resolve EscrowRuntime without the transform. */
    public static synchronized void defineRuntimeCapability() throws ReflectiveOperationException {
        if (runtimeDefined) { return; }
        var type = new org.objectweb.asm.tree.ClassNode();
        try (var input = EscrowTestBootstrap.class.getResourceAsStream("EscrowRuntime.class")) {
            assertNotNull(input); new org.objectweb.asm.ClassReader(input).accept(type, 0);
        } catch (IOException failure) { throw new IllegalStateException("Cannot read escrow runtime fixture", failure); }
        var initializer = type.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElseThrow();
        var instructions = initializer.instructions;
        var token = Stream.of(instructions.toArray()).filter(node -> node instanceof org.objectweb.asm.tree.TypeInsnNode allocation
                && allocation.getOpcode() == org.objectweb.asm.Opcodes.NEW && allocation.desc.equals(type.name + "$1"))
                .findFirst().orElseThrow();
        var get = token.getNext().getNext().getNext();
        var call = assertInstanceOf(org.objectweb.asm.tree.MethodInsnNode.class, get);
        assertEquals("net/minecraftforge/common/capabilities/CapabilityManager", call.owner);
        assertEquals("get", call.name);
        var end = get.getNext();
        instructions.insertBefore(token, new org.objectweb.asm.tree.MethodInsnNode(org.objectweb.asm.Opcodes.INVOKESTATIC,
                EscrowTestBootstrap.class.getName().replace('.', '/'), "runtimeCapability",
                "()Lnet/minecraftforge/common/capabilities/Capability;", false));
        for (var node = token; node != end; ) { var next = node.getNext(); instructions.remove(node); node = next; }
        var output = new org.objectweb.asm.ClassWriter(0); type.accept(output);
        java.lang.invoke.MethodHandles.lookup().defineClass(output.toByteArray());
        runtimeDefined = true;
    }

    static Capability<?> runtimeCapability() throws ReflectiveOperationException {
        var get = net.minecraftforge.common.capabilities.CapabilityManager.class.getDeclaredMethod("get", String.class, boolean.class);
        get.setAccessible(true);
        return (Capability<?>) get.invoke(net.minecraftforge.common.capabilities.CapabilityManager.INSTANCE,
                EscrowTestBootstrap.class.getPackageName().replace('.', '/') + "/EscrowRuntime", false);
    }
}
