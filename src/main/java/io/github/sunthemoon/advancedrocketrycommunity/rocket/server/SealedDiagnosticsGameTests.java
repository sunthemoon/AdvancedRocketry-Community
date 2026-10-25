package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedDiagnosticsGameTests {
    @GameTest(template = "rocket_test", batch = "w152_diagnostics", timeoutTicks = 40)
    public static void repeatedHoldAndCompletionEmitOnlyBoundedTransitions(GameTestHelper helper) {
        var lines = new ArrayList<String>();
        var diagnostics = new SealedDiagnostics((warning, line) -> {
            lines.add(line);
            if (warning) { AdvancedRocketryCommunity.LOGGER.warn(line); }
            else { AdvancedRocketryCommunity.LOGGER.info(line); }
        });
        for (int tick = 0; tick < 20; tick++) {
            diagnostics.hold("fixture", "STAGE", "BARRIER_IO"); diagnostics.flush(tick);
        }
        helper.assertTrue(lines.size() == 1 && lines.get(0).equals("ARCE_DISASSEMBLY_DURABILITY_HELD transaction=fixture phase=STAGE reason=BARRIER_IO additional=[] overflow=0"),
                "Repeated holds changed the exact diagnostic contract");
        diagnostics.complete("fixture", "SEALED"); diagnostics.flush(19);
        helper.assertTrue(lines.size() == 1, "Completion bypassed aggregate line cadence");
        diagnostics.flush(20);
        helper.assertTrue(lines.size() == 2 && lines.get(1).equals("ARCE_DISASSEMBLY_DURABILITY_COMPLETE transaction=fixture phase=DONE reason=SEALED additional=[] overflow=0")
                && diagnostics.size() == 0, "Completion was not emitted once and forgotten");
        helper.succeed();
    }
    private SealedDiagnosticsGameTests() { }
}
