package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.util.LinkedHashMap;
import java.util.Map;

public final class V180PropulsionLanguage {
    private V180PropulsionLanguage() { }
    public static Map<String, String> entries(boolean chinese) {
        String[] en = {"Advanced Rocket Motor", "Bipropellant Rocket Motor", "Advanced Bipropellant Rocket Motor",
                "Bipropellant Fuel Tank", "Oxidizer Fuel Tank", "Nuclear Rocket Nozzle", "Nuclear Working-fluid Tank", "Nuclear Core"};
        String[] zh = {"高级火箭发动机", "双组元火箭发动机", "高级双组元火箭发动机", "双组元燃料箱", "氧化剂箱", "核热火箭喷口", "核热工质箱", "核热核心"};
        var entries = new LinkedHashMap<String, String>();
        for (int i = 0; i < en.length; i++) { entries.put("block.advancedrocketrycommunity." + V180PropulsionData.IDS.get(i), chinese ? zh[i] : en[i]); }
        entries.put("message.advancedrocketrycommunity.propulsion.contents", chinese ? "推进资源：%s" : "Propulsion resources: %s");
        entries.put("message.advancedrocketrycommunity.propulsion.recovery_pending", chinese
                ? "火箭转移恢复尚未完成，暂时不能更改资源。请等待乘客重新连接并完成恢复。"
                : "Rocket transfer recovery is pending. Resources cannot change until recovery and passenger reconnect finish.");
        entries.put("message.advancedrocketrycommunity.propulsion.exchange_refused", chinese
                ? "无法交换整容器流体：检查模式、容量、库存空间及手动加注设置。抽象燃料不能卸为流体。"
                : "Cannot exchange a whole Fluid container: check mode, capacity, inventory space and hand-fueling settings. Abstract fuel cannot be unloaded as Fluid.");
        entries.put("message.advancedrocketrycommunity.propulsion.disposal_vector", chinese
                ? "确认将丢弃以下全部推进资源：%s。可先用空容器卸出实际流体。"
                : "Confirmation disposes of the complete propulsion vector: %s. Actual Fluid can first be unloaded into empty containers.");
        entries.put("message.advancedrocketrycommunity.propulsion.mode", chinese
                ? "推进模式：%s。燃料表显示航线基准费用；实际加注及扣除按各资源银行分别计算。%s"
                : "Propulsion mode: %s. The fuel table shows route-base cost; actual Fluid debits are computed independently per bank. %s");
        String[][] diagnostics = {
                {"mixed_propulsion_modes", "Host engines select incompatible propulsion modes.", "发动机混合了不兼容的推进模式。"},
                {"wrong_propulsion_tank_role", "A host tank/core has the wrong role for these engines.", "燃料箱或核心不适用于发动机的推进模式。"},
                {"external_component_abstract_only", "External thrust/capacity components support abstract fuel only; they cannot join typed Fluid propulsion.", "外部推力或容量组件仅支持抽象燃料，不能混入原生流体推进系统。"},
                {"missing_propulsion_tank", "This mode requires matching fuel and, for bipropellant, oxidizer tanks.", "此推进模式需要匹配的燃料箱；双组元还需要氧化剂箱。"},
                {"nuclear_core_support_required", "A downward nuclear nozzle needs a contiguous core column directly above it.", "朝下的核热喷口正上方需要连续的核心柱。"},
                {"propulsion_capacity_exceeded", "Combined propulsion capacity exceeds 2,048,000.", "推进资源总容量超过 2,048,000。"},
                {"propulsion_aggregate_overflow", "Propulsion aggregate arithmetic overflowed; no blocks were extracted.", "推进系统聚合计算溢出；未提取任何方块。"}};
        for (var diagnostic : diagnostics) { entries.put("validation.advancedrocketrycommunity.rocket." + diagnostic[0], diagnostic[chinese ? 2 : 1]); }
        return entries;
    }
}
