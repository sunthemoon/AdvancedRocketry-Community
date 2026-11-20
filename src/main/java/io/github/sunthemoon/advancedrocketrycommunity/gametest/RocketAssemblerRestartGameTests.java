package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketAssemblerRestartGameTests {
    private static final String KEY = "RocketAssemblerData";
    private RocketAssemblerRestartGameTests() { }

    /** Census F4: every schema-1 report field survives reconstruction, unlike an entity snapshot test. */
    @GameTest(template = "empty", batch = "w415_assembler_restart", timeoutTicks = 80)
    public static void f4CurrentAssemblerRestartPreservesCodeDetailTimeAndAllStatistics(GameTestHelper helper) {
        var original = place(helper);
        var stats = new RocketStats(37, 1_234L, 5_678L, 9_012L, 2, 3, 4, 5);
        var expected = new RocketAssemblerReport(RocketValidationCode.SUCCESS, stats,
                "restart report with distinct statistics", 4_294_967_321L);
        original.setReport(expected); original.setOperatorDetail("session-only diagnostic");
        CompoundTag saved = original.saveWithFullMetadata();
        helper.assertTrue(saved.getCompound(KEY).getInt("schema_version") == 1, "Report fixture is not schema 1");
        var restored = restart(helper, original, saved);
        helper.assertTrue(restored != original && restored.report().equals(expected)
                        && restored.report().optionalStats().orElseThrow().equals(stats)
                        && restored.operatorDetail().isEmpty() && !restored.blockedByFutureData()
                        && restored.onlyOpCanSetNbt() && restored.saveWithoutMetadata().get(KEY).equals(saved.get(KEY)),
                "Fresh assembler did not restore the exact persistent report");
        helper.succeed();
    }

    /** Census F4: a future report remains raw and blocked across fresh block entities. */
    @GameTest(template = "empty", batch = "w415_assembler_restart", timeoutTicks = 80)
    public static void f4FutureAssemblerReportRestartRetainsUnknownRootUnchanged(GameTestHelper helper) {
        var original = place(helper);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2); future.putString("code", "FUTURE_REPORT");
        CompoundTag payload = new CompoundTag(); payload.putLong("unknown_stat", 9_876_543_210L);
        future.put("future_payload", payload);
        CompoundTag outer = original.saveWithFullMetadata(); outer.put(KEY, future.copy());
        var restored = restart(helper, original, outer);
        for (int reload = 0; reload < 2; reload++) {
            helper.assertTrue(restored.blockedByFutureData() && restored.report().equals(RocketAssemblerReport.idle())
                            && restored.saveWithoutMetadata().get(KEY).equals(future),
                    "Future report was interpreted, discarded or normalized");
            restored = restart(helper, restored, restored.saveWithFullMetadata());
        }
        helper.assertTrue(restored.blockedByFutureData() && restored.saveWithoutMetadata().get(KEY).equals(future),
                "Repeated reconstruction changed future report");
        helper.succeed();
    }

    private static RocketAssemblerBlockEntity place(GameTestHelper helper) {
        var position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.ROCKET_ASSEMBLER.get());
        return (RocketAssemblerBlockEntity) helper.getBlockEntity(position);
    }

    private static RocketAssemblerBlockEntity restart(GameTestHelper helper, RocketAssemblerBlockEntity old, CompoundTag data) {
        old.onChunkUnloaded(); helper.getLevel().removeBlockEntity(old.getBlockPos());
        var fresh = new RocketAssemblerBlockEntity(old.getBlockPos(), old.getBlockState());
        fresh.load(data); helper.getLevel().setBlockEntity(fresh); fresh.onLoad();
        return fresh;
    }
}
