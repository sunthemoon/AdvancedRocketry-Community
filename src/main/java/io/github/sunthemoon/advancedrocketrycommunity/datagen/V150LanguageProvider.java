package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/** Additive v1.5 labels: the example star system and the warp core (own namespace, like earlier versions). */
public final class V150LanguageProvider extends LanguageProvider {
    private final boolean chinese;

    public V150LanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v150", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("body.advancedrocketrycommunity.tau_ceti", chinese ? "鲸鱼座τ" : "Tau Ceti");
        add("body.advancedrocketrycommunity.tau_ceti_e", chinese ? "鲸鱼座τ e" : "Tau Ceti e");
        add("block.advancedrocketrycommunity.warp_core", chinese ? "跃迁核心" : "Warp Core");
    }
}
