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
    }
}
