package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternDiagnosticReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import java.util.Locale;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

public final class V120MachineLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V120MachineLanguageProvider(PackOutput output, String locale) {
        super(output, AdvancedRocketryCommunity.MOD_ID + "_v120", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("block.advancedrocketrycommunity.precision_assembler",
                chinese ? "精密装配机" : "Precision Assembler");
        add("block.advancedrocketrycommunity.precision_assembler_item_input_port",
                chinese ? "精密装配机物品输入口" : "Precision Assembler Item Input");
        add("block.advancedrocketrycommunity.precision_assembler_item_output_port",
                chinese ? "精密装配机物品输出口" : "Precision Assembler Item Output");
        add("block.advancedrocketrycommunity.precision_assembler_energy_input_port",
                chinese ? "精密装配机能源输入口" : "Precision Assembler Energy Input");
        add("menu.advancedrocketrycommunity.precision_assembler",
                chinese ? "精密装配机控制台" : "Precision Assembler Console");
        add("screen.advancedrocketrycommunity.precision_assembler.inputs",
                chinese ? "物品输入 0–4" : "ITEM INPUTS 0–4");
        add("screen.advancedrocketrycommunity.precision_assembler.outputs",
                chinese ? "输出 0–1" : "OUTPUT 0–1");
        add("screen.advancedrocketrycommunity.precision_assembler.formation",
                chinese ? "结构：%s" : "STRUCTURE  %s");
        add("screen.advancedrocketrycommunity.precision_assembler.process",
                chinese ? "过程：%s" : "PROCESS  %s");
        add("screen.advancedrocketrycommunity.precision_assembler.diagnostic_location",
                chinese ? "方块 %s, %s, %s（局部 %s, %s, %s）" : "Block %s, %s, %s (local %s, %s, %s)");
        add("tooltip.advancedrocketrycommunity.precision_assembler.progress",
                chinese ? "进度：%s / %s tick" : "Progress: %s / %s ticks");
        add("block.advancedrocketrycommunity.rolling_machine", chinese ? "轧制机" : "Rolling Machine");
        add("block.advancedrocketrycommunity.rolling_machine_energy_input_port",
                chinese ? "轧制机能源输入口" : "Rolling Machine Energy Input");
        add("block.advancedrocketrycommunity.rolling_machine_fluid_input_port",
                chinese ? "轧制机流体输入口" : "Rolling Machine Fluid Input");
        add("block.advancedrocketrycommunity.rolling_machine_item_input_port",
                chinese ? "轧制机物品输入口" : "Rolling Machine Item Input");
        add("block.advancedrocketrycommunity.rolling_machine_item_output_port",
                chinese ? "轧制机物品输出口" : "Rolling Machine Item Output");
        add("menu.advancedrocketrycommunity.rolling_machine", chinese ? "轧制机控制台" : "Rolling Machine Console");
        add("message.advancedrocketrycommunity.rolling_machine.formation",
                chinese ? "轧制机：%s | 世代 %s" : "Rolling Machine: %s | generation %s");
        add("message.advancedrocketrycommunity.rolling_machine.port",
                chinese ? "%s 端口：%s" : "%s port: %s");
        add("screen.advancedrocketrycommunity.rolling_machine.formation",
                chinese ? "结构：%s" : "STRUCTURE  %s");
        add("screen.advancedrocketrycommunity.rolling_machine.process",
                chinese ? "过程：%s" : "PROCESS  %s");
        add("screen.advancedrocketrycommunity.rolling_machine.diagnostic_location",
                chinese ? "方块 %s, %s, %s（局部 %s, %s, %s）" : "Block %s, %s, %s (local %s, %s, %s)");
        add("tooltip.advancedrocketrycommunity.rolling_machine.progress",
                chinese ? "进度：%s / %s tick" : "Progress: %s / %s ticks");

        for (MultiblockFormationState state : MultiblockFormationState.values()) {
            add(key("formation", state.name()), chinese ? formationChinese(state) : formationEnglish(state));
        }
        for (ProcessMachineState state : ProcessMachineState.values()) {
            add(key("process", state.name()), chinese ? processChinese(state) : processEnglish(state));
        }
        for (ProcessFailureCode code : ProcessFailureCode.values()) {
            add(key("failure", code.name()), chinese ? failureChinese(code) : failureEnglish(code));
        }
        for (PatternDiagnosticReason reason : PatternDiagnosticReason.values()) {
            add(key("diagnostic", reason.name()), chinese ? diagnosticChinese(reason) : diagnosticEnglish(reason));
        }
        for (MultiblockFormationState state : MultiblockFormationState.values()) {
            add(precisionKey("formation", state.name()),
                    chinese ? formationChinese(state) : formationEnglish(state));
        }
        for (ProcessMachineState state : ProcessMachineState.values()) {
            add(precisionKey("process", state.name()),
                    state == ProcessMachineState.RUNNING
                            ? (chinese ? "装配中" : "ASSEMBLING")
                            : (chinese ? processChinese(state) : processEnglish(state)));
        }
        for (ProcessFailureCode code : ProcessFailureCode.values()) {
            add(precisionKey("failure", code.name()),
                    code == ProcessFailureCode.MISSING_FLUID_INPUT
                            ? (chinese ? "缺少流体输入" : "Missing fluid input")
                            : (chinese ? failureChinese(code) : failureEnglish(code)));
        }
        for (PatternDiagnosticReason reason : PatternDiagnosticReason.values()) {
            add(precisionKey("diagnostic", reason.name()),
                    chinese ? diagnosticChinese(reason) : diagnosticEnglish(reason));
        }
    }

    private static String key(String group, String value) {
        return "status.advancedrocketrycommunity.rolling_machine."
                + group
                + "."
                + value.toLowerCase(Locale.ROOT);
    }

    private static String precisionKey(String group, String value) {
        return "status.advancedrocketrycommunity.precision_assembler."
                + group
                + "."
                + value.toLowerCase(Locale.ROOT);
    }

    private static String formationEnglish(MultiblockFormationState state) {
        return switch (state) {
            case UNFORMED -> "INCOMPLETE";
            case FORMED -> "FORMED";
            case WAITING_UNLOADED -> "WAITING FOR LOADED CHUNKS";
            case INVALID_DEFINITION -> "INVALID PATTERN";
            case BINDING_CONFLICT -> "PORT BINDING CONFLICT";
            case UNSUPPORTED_DATA -> "UNSUPPORTED SAVED DATA";
        };
    }

    private static String formationChinese(MultiblockFormationState state) {
        return switch (state) {
            case UNFORMED -> "结构不完整";
            case FORMED -> "已形成";
            case WAITING_UNLOADED -> "等待区块加载";
            case INVALID_DEFINITION -> "结构定义无效";
            case BINDING_CONFLICT -> "端口绑定冲突";
            case UNSUPPORTED_DATA -> "存档数据不受支持";
        };
    }

    private static String processEnglish(ProcessMachineState state) {
        return switch (state) {
            case IDLE -> "IDLE";
            case RUNNING -> "ROLLING";
            case WAITING_INPUT -> "WAITING FOR INPUT";
            case WAITING_OUTPUT -> "OUTPUT BLOCKED";
            case WAITING_ENERGY -> "WAITING FOR ENERGY";
            case REDSTONE_DISABLED -> "PAUSED BY REDSTONE";
            case INVALID_RECIPE -> "INVALID RECIPE";
            case UNSUPPORTED_DATA -> "UNSUPPORTED SAVED DATA";
            case RECOVERY_REQUIRED -> "RECOVERY REQUIRED";
        };
    }

    private static String processChinese(ProcessMachineState state) {
        return switch (state) {
            case IDLE -> "待机";
            case RUNNING -> "轧制中";
            case WAITING_INPUT -> "等待输入";
            case WAITING_OUTPUT -> "输出受阻";
            case WAITING_ENERGY -> "等待能源";
            case REDSTONE_DISABLED -> "红石信号暂停";
            case INVALID_RECIPE -> "配方无效";
            case UNSUPPORTED_DATA -> "存档数据不受支持";
            case RECOVERY_REQUIRED -> "需要恢复";
        };
    }

    private static String failureEnglish(ProcessFailureCode code) {
        return switch (code) {
            case NONE -> "READY";
            case MISSING_ITEM_INPUT -> "Missing item input";
            case MISSING_FLUID_INPUT -> "Missing water input";
            case OUTPUT_BLOCKED -> "Output is blocked";
            case INSUFFICIENT_ENERGY -> "Insufficient energy";
            case REDSTONE_DISABLED -> "Disabled by redstone";
            case INVALID_RECIPE -> "Recipe changed or is invalid";
            case STALE_TRANSACTION -> "Resources changed; retrying";
            case JOURNAL_CONFLICT -> "Transaction journal conflict";
            case RECOVERY_DIVERGED -> "Saved transaction needs recovery";
            case ARITHMETIC_OVERFLOW -> "Recipe arithmetic overflow";
        };
    }

    private static String failureChinese(ProcessFailureCode code) {
        return switch (code) {
            case NONE -> "就绪";
            case MISSING_ITEM_INPUT -> "缺少物品输入";
            case MISSING_FLUID_INPUT -> "缺少水输入";
            case OUTPUT_BLOCKED -> "输出已满";
            case INSUFFICIENT_ENERGY -> "能源不足";
            case REDSTONE_DISABLED -> "已被红石信号禁用";
            case INVALID_RECIPE -> "配方已变化或无效";
            case STALE_TRANSACTION -> "资源已变化，正在重试";
            case JOURNAL_CONFLICT -> "事务日志冲突";
            case RECOVERY_DIVERGED -> "存档事务需要恢复";
            case ARITHMETIC_OVERFLOW -> "配方算术溢出";
        };
    }

    private static String diagnosticEnglish(PatternDiagnosticReason reason) {
        return switch (reason) {
            case BLOCK_MISMATCH -> "BLOCK MISMATCH";
            case CHUNK_NOT_LOADED -> "CHUNK NOT LOADED";
            case TRANSFORM_NOT_ALLOWED -> "ORIENTATION NOT ALLOWED";
            case POSITION_OVERFLOW -> "POSITION OUT OF RANGE";
        };
    }

    private static String diagnosticChinese(PatternDiagnosticReason reason) {
        return switch (reason) {
            case BLOCK_MISMATCH -> "方块不匹配";
            case CHUNK_NOT_LOADED -> "区块未加载";
            case TRANSFORM_NOT_ALLOWED -> "朝向不允许";
            case POSITION_OVERFLOW -> "位置超出范围";
        };
    }
}
