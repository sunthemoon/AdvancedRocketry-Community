package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Additive labels for the existing generic destination selector. */
public final class V140PlanetaryLanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V140PlanetaryLanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v140", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("body.advancedrocketrycommunity.mars", chinese ? "火星" : "Mars");
        add("body.advancedrocketrycommunity.venus", chinese ? "金星" : "Venus");
        add("body.advancedrocketrycommunity.gas_giant", chinese ? "气态巨行星" : "Gas Giant");
        add("biome.advancedrocketrycommunity.mars", chinese ? "红色荒原" : "Red Barrens");
        add("biome.advancedrocketrycommunity.venus", chinese ? "灼热高地" : "Scorched Highlands");
        add("environment.advancedrocketrycommunity.exposed", chinese ? "环境危险：%s" : "Environmental exposure: %s");
        add("environment.advancedrocketrycommunity.cold", chinese ? "低温" : "Cold");
        add("environment.advancedrocketrycommunity.heat", chinese ? "高温" : "Heat");
        add("environment.advancedrocketrycommunity.pressure", chinese ? "高压" : "High pressure");
        add("environment.advancedrocketrycommunity.solar", chinese ? "强日照" : "Intense sunlight");
        death("cold", chinese ? "%1$s 未能抵御严寒" : "%1$s succumbed to the planetary cold");
        death("heat", chinese ? "%1$s 未能抵御酷热" : "%1$s succumbed to the planetary heat");
        death("pressure", chinese ? "%1$s 未能抵御高压" : "%1$s succumbed to atmospheric pressure");
        death("solar", chinese ? "%1$s 未能抵御强烈日照" : "%1$s succumbed to intense sunlight");
    }

    private void death(String hazard, String message) {
        add("death.attack.advancedrocketrycommunity.planetary_" + hazard, message);
        add("death.attack.advancedrocketrycommunity.planetary_" + hazard + ".player",
                message + (chinese ? "，此前曾与 %2$s 战斗" : " while fighting %2$s"));
    }
}
