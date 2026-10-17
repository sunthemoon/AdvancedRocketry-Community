package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumeScanOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowFlightBenchmarkGameTests {
    private EscrowFlightBenchmarkGameTests() { }

    @GameTest(template = "empty", batch = "escrow_benchmark_supply", timeoutTicks = 40)
    public static void refillUsesAutomationWithoutResettingAnActiveVent(GameTestHelper helper) {
        var vent = new OxygenVentBlockEntity(BlockPos.ZERO, ModBlocks.OXYGEN_VENT.get().defaultBlockState());
        EscrowFlightBenchmarkLoad.refill(vent);
        helper.assertTrue(vent.oxygenUnits() == 0, "Supply must await the product ticker");
        for (int i = 0; i < 4; i++) {
            OxygenVentBlockEntity.serverTick(null, BlockPos.ZERO, vent.getBlockState(), vent);
        }
        helper.assertTrue(vent.oxygenUnits() == 4000, "Normal ticker did not consume four canisters");
        vent.applySupply(VolumeScanOutcome.SEALED, true);
        helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "Supplied sealed vent is not active");
        var before = vent.saveWithoutMetadata().getCompound("arce_oxygen_vent");
        EscrowFlightBenchmarkLoad.refill(vent);
        helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE, "Refill reset the active vent");
        helper.assertTrue(before.getInt("oxygen_units") == vent.oxygenUnits(), "Refill overwrote stored oxygen");
        helper.assertTrue(before.getInt("oxygen_phase") == vent.saveWithoutMetadata().getCompound("arce_oxygen_vent").getInt("oxygen_phase"),
                "Refill reset the oxygen phase");
        helper.assertTrue(vent.energyStored() == 40000, "Energy capability did not refill the vent");
        helper.assertTrue(vent.itemHandler().getStackInSlot(OxygenVentBlockEntity.SLOT_OXYGEN).getCount() == 4
                && vent.itemHandler().getStackInSlot(OxygenVentBlockEntity.SLOT_EMPTY).isEmpty(), "Refill changed supply/output counts");
        helper.succeed();
    }
}
