package io.github.sunthemoon.arceadaptertest;

import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/** Only timing facts/pacing cross the test bridge; requests, inventories and recovery remain native/public. */
final class AdapterAsyncFixture {
    private static final String ACCESS = "io.github.sunthemoon.advancedrocketrycommunity.rocket.server.SealedAdapterTestAccess";

    static TestFunction test(String batch, String name, int members, int operations, boolean reload,
            Consumer<GameTestHelper> body) {
        return new TestFunction(batch, name.toLowerCase(java.util.Locale.ROOT), "advancedrocketrycommunity:rocket_test",
                Rotation.NONE, ticks(members, operations, reload), 0L, true, helper -> {
                    var pace = (Runnable) invoke("pacer", new Class<?>[] {ServerLevel.class}, helper.getLevel());
                    helper.onEachTick(pace);
                    body.accept(helper);
                });
    }

    static int ticks(int members, int operations, boolean reload) {
        return (int) invoke("completionTicks", new Class<?>[] {int.class, int.class, boolean.class}, members, operations, reload);
    }
    static int retryTicks() { return (int) invoke("retryTicks", new Class<?>[0]); }

    private static Object invoke(String name, Class<?>[] signature, Object... args) {
        try { return Class.forName(ACCESS).getMethod(name, signature).invoke(null, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Adapter fixture timing unavailable", failure); }
    }
    private AdapterAsyncFixture() { }
}
