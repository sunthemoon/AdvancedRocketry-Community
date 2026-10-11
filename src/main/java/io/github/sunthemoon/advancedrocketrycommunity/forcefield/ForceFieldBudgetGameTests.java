package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldBudgetGameTests {
    @GameTest(template = "empty", batch = "force_field_budget", timeoutTicks = 200)
    public static void budgetExhaustionCannotStarveTheTailOfALargeQueue(GameTestHelper helper) {
        var data = new FieldCleanupSavedData();
        var keys = new ArrayList<FieldIdentity.Key>();
        for (int i = 0; i < 1024; i++) {
            var identity = new FieldIdentity(new UUID(400, i), new UUID(400, 2048), 1, helper.getLevel().dimension(),
                    new BlockPos(26_000_000 + i, 100, 26_000_000), Direction.EAST);
            helper.assertTrue(data.reserve(identity, 0), "Budget fixture failed admission");
            keys.add(identity.key());
        }
        step(helper, data, keys, 0);
    }
    private static void step(GameTestHelper helper, FieldCleanupSavedData data, java.util.List<FieldIdentity.Key> keys, int tick) {
        helper.runAfterDelay(1, () -> {
            data.tick(helper.getLevel().getServer());
            helper.assertTrue(data.inspectedCells() <= 64, "Global inspection budget exceeded");
            if (tick < 159) { step(helper, data, keys, tick + 1); return; }
            for (var key : keys) { helper.assertTrue(data.lastVisit(key).isPresent(), "Fair scheduler starved a queued projector"); }
            helper.succeed();
        });
    }
    private ForceFieldBudgetGameTests() { }
}
