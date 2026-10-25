package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;

/** Owns one test hook and its optional paused writer, never a later fixture's hook. */
final class EscrowGameTestTerminalCut implements AutoCloseable, GameTestListener {
    private final Runnable uninstall, resume;
    private boolean closed;

    static EscrowGameTestTerminalCut install(GameTestHelper helper, Consumer<String> cut) {
        return new EscrowGameTestTerminalCut(helper, cut, () -> { });
    }

    EscrowGameTestTerminalCut(GameTestHelper helper, Consumer<String> cut, Runnable resume) {
        var server = helper.getLevel().getServer();
        this.resume = resume;
        uninstall = EscrowRuntime.of(server).terminalCutForTests(server, cut);
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            close(); throw new IllegalStateException("Cannot register terminal-cut completion cleanup", failure);
        }
    }

    @Override public void close() {
        if (closed) { return; } closed = true;
        try { resume.run(); } finally { uninstall.run(); }
    }
    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(); }
    @Override public void testFailed(GameTestInfo info) {
        try { close(); }
        catch (RuntimeException | Error failure) {
            if (info.getError() == null) { throw failure; }
            info.getError().addSuppressed(failure);
        }
    }
}
