package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W68 (W62 slice S5, version document 10.6 and 12.2): long automated production without duplication. One GameTest per
 * v1.8 machine kind runs twenty batches (or twenty fuel items, sources or transfers) through the machine's real
 * automation capabilities, saves and loads its block entities once mid-run, tries the extractions and insertions the
 * capabilities must refuse, and then requires exact conservation: what went in equals what came out plus the
 * consumption the recipe or device states. Each test has its own batch; none expects a log line.
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AutomationConservationGameTests {
    private static final int CLASSIC_TIMEOUT = 200;

    @GameTest(template = "empty", batch = "w68_arc_furnace", timeoutTicks = CLASSIC_TIMEOUT)
    public static void arcFurnaceTwentyTitaniumBatchesConserveRutileAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "arc_furnace", "arc_furnace_titanium", 200).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_cutting_machine", timeoutTicks = CLASSIC_TIMEOUT)
    public static void cuttingMachineTwentyPlankBatchesConserveLogsAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "cutting_machine", "cutting_oak_log", 200).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_lathe", timeoutTicks = CLASSIC_TIMEOUT)
    public static void latheTwentyRodBatchesConserveIngotsAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "lathe", "lathe_iron_rod", 400).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_crystallizer", timeoutTicks = CLASSIC_TIMEOUT)
    public static void crystallizerTwentyDilithiumBatchesConserveDustAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "crystallizer", "crystallizing_dilithium", 400).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_chemical_reactor", timeoutTicks = CLASSIC_TIMEOUT)
    public static void chemicalReactorTwentyFuelBatchesConserveGasesAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "chemical_reactor", "chemical_reacting_rocket_fuel", 200).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_laser_etcher", timeoutTicks = CLASSIC_TIMEOUT)
    public static void etcherTwentyPlateBatchesConserveInputsLensAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "precision_laser_etcher", "laser_etching_basic_circuit_plate", 1_000).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_centrifuge", timeoutTicks = CLASSIC_TIMEOUT)
    public static void centrifugeTwentyLavaBatchesConserveFluidNuggetsAndFe(GameTestHelper helper) {
        new ClassicAutomationRun(helper, "centrifuge", "centrifuging_enriched_lava", 200).start(CLASSIC_TIMEOUT);
    }

    @GameTest(template = "empty", batch = "w68_rolling_machine", timeoutTicks = CLASSIC_TIMEOUT)
    public static void rollingMachineTwentyBarBatchesConserveIngotsWaterAndGeneratorFe(GameTestHelper helper) {
        KernelAutomationRun.rolling(helper).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_precision_assembler", timeoutTicks = CLASSIC_TIMEOUT)
    public static void precisionAssemblerTwentyCircuitBatchesConserveInputsAndGeneratorFe(GameTestHelper helper) {
        KernelAutomationRun.precision(helper).start(CLASSIC_TIMEOUT);
    }
    @GameTest(template = "empty", batch = "w68_electrolyzer", timeoutTicks = CLASSIC_TIMEOUT)
    public static void electrolyzerTwentyBatchesConserveCanistersWaterAndRedstoneFe(GameTestHelper helper) {
        new ElectrolyzerAutomationRun(helper).start(CLASSIC_TIMEOUT);
    }

    private AutomationConservationGameTests() { }
}
