package io.github.sunthemoon.arceadaptertest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Tests of the actual fixture lifetime guard, not proof of queued native process retirement. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeFixtureLifetimeGameTests {
    private NativeFixtureLifetimeGameTests() { }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_lifetime")
    public static void closureIsSingleAndRejectsLaterEnqueue(GameTestHelper helper) {
        NativeFixtureLifetime lifetime = new NativeFixtureLifetime(() -> 0, 60);
        helper.assertTrue(lifetime.close() && !lifetime.close() && lifetime.isClosed(), "Closure was not single");
        AtomicBoolean invoked = new AtomicBoolean();
        try {
            lifetime.enqueue(() -> { invoked.set(true); return 1; });
            throw new IllegalStateException("Closed requester admitted work");
        } catch (IllegalStateException refused) {
            helper.assertFalse(invoked.get(), "Closed requester invoked its native action");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_lifetime")
    public static void finalActionCannotRenewTheOriginalDeadline(GameTestHelper helper) {
        AtomicLong clock = new AtomicLong(Long.MAX_VALUE - 20);
        NativeFixtureLifetime lifetime = new NativeFixtureLifetime(clock::get, 60);
        clock.addAndGet(59);
        helper.assertFalse(lifetime.expired(), "Deadline expired early across nanoTime wrap");
        lifetime.enqueue(() -> { clock.incrementAndGet(); return 1; });
        helper.assertTrue(lifetime.expired() && lifetime.close() && lifetime.expired(), "Final action or cleanup renewed time");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_lifetime")
    public static void partialRegistrationIsDetachedAndCleanupPreservesPrimaryFailure(GameTestHelper helper) {
        NativeFixtureLifetime lifetime = new NativeFixtureLifetime(() -> 0, 60);
        IllegalStateException primary = new IllegalStateException("Owned registration injection");
        IllegalStateException cleanup = new IllegalStateException("Owned cleanup injection");
        AtomicReference<Throwable> terminal = new AtomicReference<>();
        Probe probe = new Probe();
        try {
            lifetime.install(() -> {
                MinecraftForge.EVENT_BUS.register(probe);
                MinecraftForge.EVENT_BUS.post(new ProbeEvent());
                throw primary;
            }, failure -> {
                lifetime.close();
                Throwable retained = NativeFixtureLifetime.cleanup(failure, () -> { throw cleanup; });
                retained = NativeFixtureLifetime.cleanup(retained, () -> MinecraftForge.EVENT_BUS.unregister(probe));
                terminal.set(retained);
            });
            MinecraftForge.EVENT_BUS.post(new ProbeEvent());
            helper.assertTrue(probe.calls == 1 && lifetime.isClosed() && terminal.get() == primary
                            && primary.getSuppressed().length == 1 && primary.getSuppressed()[0] == cleanup,
                    "Partial registration, detach or primary failure changed");
            helper.succeed();
        } finally { MinecraftForge.EVENT_BUS.unregister(probe); }
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "native_proto_lifetime")
    public static void queuedCompletionDoesNotReopenTheClosedGuard(GameTestHelper helper) {
        NativeFixtureLifetime lifetime = new NativeFixtureLifetime(() -> 0, 60);
        CompletableFuture<Integer> queued = new CompletableFuture<>();
        helper.assertTrue(lifetime.enqueue(() -> queued) == queued, "Enqueue substituted the original future");
        lifetime.close();
        queued.complete(28);
        helper.assertTrue(queued.getNow(0) == 28 && lifetime.isClosed(), "Late completion reopened the guard");
        helper.succeed();
    }

    public static final class ProbeEvent extends Event { }
    public static final class Probe {
        int calls;
        @SubscribeEvent public void observe(ProbeEvent event) { calls++; }
    }
}
