package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.CheckedRootWriter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Queue;
import java.util.function.IntSupplier;

/** Transparent test-only capture/write/publication timing. Exceptions and values are forwarded unchanged. */
final class W208WriterTiming {
    static void attach(RocketTransactionSavedData data, Queue<Map<String, Object>> events, IntSupplier tick) {
        try {
            var writer = W208NativeAvailability.field(RocketTransactionSavedData.class, "writer").get(data);
            var root = W208NativeAvailability.field(CheckedRootWriter.class, "root");
            var original = root.get(writer);
            root.set(writer, Proxy.newProxyInstance(CheckedRootWriter.class.getClassLoader(), new Class<?>[] {CheckedRootWriter.Root.class},
                    (proxy, method, args) -> {
                        boolean measured = method.getName().equals("capture") || method.getName().equals("write") || method.getName().equals("publish");
                        long started = System.nanoTime();
                        try { return method.invoke(original, args); }
                        catch (InvocationTargetException failure) { throw failure.getCause(); }
                        finally {
                            if (measured) { events.add(Map.of("label", "ROOT_" + method.getName(), "tick", tick.getAsInt(),
                                    "ns", System.nanoTime() - started, "thread", Thread.currentThread().getName())); }
                        }
                    }));
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    private W208WriterTiming() { }
}
