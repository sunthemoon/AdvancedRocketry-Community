package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowTerminalCutCleanupGameTests {
    private EscrowTerminalCutCleanupGameTests() { }

    @GameTest(template = "empty", batch = "escrow_terminal_cleanup_failure", timeoutTicks = 40)
    public static void assertionFailureReleasesTheCutAndPausedWriter(GameTestHelper helper) { exercise(helper, false); }

    @GameTest(template = "empty", batch = "escrow_terminal_cleanup_timeout", timeoutTicks = 40)
    public static void timeoutReleasesTheCutAndPausedWriter(GameTestHelper helper) { exercise(helper, true); }

    @GameTest(template = "empty", batch = "escrow_terminal_cleanup_stale", timeoutTicks = 40)
    public static void delayedCleanupCannotReleaseAReinstalledCallback(GameTestHelper helper) {
        var server = helper.getLevel().getServer(); var runtime = EscrowRuntime.of(server);
        java.util.function.Consumer<String> callback = cut -> { };
        try (var old = EscrowGameTestTerminalCut.install(helper, callback)) {
            runtime.terminalCutForTests(server, null);
            try (var next = EscrowGameTestTerminalCut.install(helper, callback)) {
                old.close();
                boolean exclusive = false;
                try { runtime.terminalCutForTests(server, callback); }
                catch (IllegalArgumentException refused) { exclusive = true; }
                helper.assertTrue(exclusive, "Delayed cleanup released a newer installation of the same callback");
            }
        }
        helper.succeed();
    }

    private static void exercise(GameTestHelper helper, boolean timeout) {
        var server = helper.getLevel().getServer();
        var resumed = new AtomicInteger();
        var hook = new EscrowGameTestTerminalCut[1];
        var expected = new GameTestAssertException("Deliberate nested fixture failure");
        // This private GameTestInfo has no reporter: its failure is the assertion under test, not a skipped test.
        var nested = new GameTestInfo(new TestFunction("w161_nested", "cleanup", "empty", 2, 0, true, inner -> {
            hook[0] = new EscrowGameTestTerminalCut(inner, cut -> { }, resumed::incrementAndGet);
            if (!timeout) { throw expected; }
        }), Rotation.NONE, helper.getLevel());
        try {
            var start = GameTestInfo.class.getDeclaredMethod("startExecution"); start.setAccessible(true); start.invoke(nested);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        helper.onEachTick(() -> {
            nested.tick();
            if (!nested.isDone()) { return; }
            try {
                helper.assertTrue(nested.hasFailed() && (timeout ? nested.getError().getMessage().contains("within 2 ticks")
                        : nested.getError() == expected), "Nested fixture did not reach its requested failure");
                helper.assertTrue(resumed.get() == 1, "Failed terminal fixture did not release its paused writer");
                try (var next = EscrowGameTestTerminalCut.install(helper, cut -> { })) {
                    hook[0].close();
                    boolean exclusive = false;
                    try { EscrowRuntime.of(server).terminalCutForTests(server, cut -> { }); }
                    catch (IllegalArgumentException refused) { exclusive = true; }
                    helper.assertTrue(exclusive && resumed.get() == 1, "Stale cleanup released the next fixture's hook");
                }
                helper.succeed();
            } finally { if (hook[0] != null) { hook[0].close(); } }
        });
    }
}
