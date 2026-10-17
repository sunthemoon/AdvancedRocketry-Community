package io.github.sunthemoon.advancedrocketrycommunity.testsupport;

import java.lang.reflect.Field;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

/**
 * Real Minecraft server-side objects whose world-loading constructors never run. A test sets only the fields that the
 * code under test reads, such as the owner thread behind {@code isSameThread}; every method under test runs unchanged.
 */
public final class UnstartedServer {
    private static final sun.misc.Unsafe UNSAFE;
    static {
        try {
            Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) field.get(null);
        } catch (ReflectiveOperationException unavailable) { throw new ExceptionInInitializerError(unavailable); }
    }
    private UnstartedServer() { }

    /** A dedicated server owned by the calling thread, without Levels, players or settings. */
    public static DedicatedServer owned() {
        var server = allocate(DedicatedServer.class);
        set(MinecraftServer.class, server, "serverThread", Thread.currentThread());
        return server;
    }

    @SuppressWarnings("unchecked")
    public static <T> T allocate(Class<T> type) {
        try { return (T) UNSAFE.allocateInstance(type); }
        catch (InstantiationException failed) { throw new IllegalStateException(failed); }
    }

    public static void set(Class<?> owner, Object target, String name, Object value) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); field.set(target, value); }
        catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(Class<?> owner, Object target, String name) {
        try { Field field = owner.getDeclaredField(name); field.setAccessible(true); return (T) field.get(target); }
        catch (ReflectiveOperationException failed) { throw new IllegalStateException(failed); }
    }
}
