package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/**
 * v1.8 (ADR-063) labels in their own namespace. Material names are built from the material table, so every
 * registered product, ore and raw item has a name in both languages; a missing one fails DataGen.
 */
public final class V180LanguageProvider extends LanguageProvider {
    private static final String NS = ModIdentity.MOD_ID;
    private final boolean chinese;

    public V180LanguageProvider(PackOutput output, String locale) {
        super(output, ModIdentity.MOD_ID + "_v180", locale);
        if (!locale.equals("en_us") && !locale.equals("zh_cn")) {
            throw new IllegalArgumentException("Unsupported locale " + locale);
        }
        chinese = locale.equals("zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup." + NS + ".materials", chinese ? "高级火箭：材料" : "Advanced Rocketry: Materials");
        add("block." + NS + ".small_plate_press", chinese ? "小型压板机" : "Small Plate Press");
        add("jei." + NS + ".small_plate_press.redstone",
                chinese ? "红石脉冲" : "Redstone pulse");
        Map<Material, String[]> names = materialNames();
        for (Entry entry : MaterialCatalog.entries()) {
            String key = (entry.isBlock() ? "block." : "item.") + NS + "." + entry.id();
            add(key, name(entry, names.get(entry.material())));
        }
    }

    private String name(Entry entry, String[] material) {
        String english = material[0];
        String han = material[1];
        String ore = entry.material().oreName().map(stem -> stem.equals("rutile") ? new String[] {"Rutile", "金红石"}
                : new String[] {english, han}).orElse(new String[] {english, han})[chinese ? 1 : 0];
        return switch (entry.kind()) {
            case STONE_ORE -> chinese ? ore + "矿石" : ore + " Ore";
            case DEEPSLATE_ORE -> chinese ? "深层" + ore + "矿石" : "Deepslate " + ore + " Ore";
            case RAW -> chinese ? "粗" + ore : "Raw " + ore;
            case PRODUCT -> product(entry.product().orElseThrow(), english, han);
        };
    }

    private String product(Product product, String english, String han) {
        if (product == Product.BLOCK) {
            return chinese ? han + "块" : "Block of " + english;
        }
        String[] noun = switch (product) {
            case INGOT -> new String[] {"Ingot", "锭"};
            case NUGGET -> new String[] {"Nugget", "粒"};
            case DUST -> new String[] {"Dust", "粉"};
            case PLATE -> new String[] {"Plate", "板"};
            case SHEET -> new String[] {"Sheet", "薄板"};
            case ROD -> new String[] {"Rod", "棒"};
            case GEAR -> new String[] {"Gear", "齿轮"};
            case FAN -> new String[] {"Fan", "风扇"};
            case CRYSTAL -> new String[] {"Crystal", "晶体"};
            case BOULE -> new String[] {"Boule", "晶锭"};
            case COIL -> new String[] {"Coil", "线圈"};
            case BLOCK -> throw new IllegalStateException("handled above");
        };
        return chinese ? han + noun[1] : english + " " + noun[0];
    }

    private static Map<Material, String[]> materialNames() {
        Map<Material, String[]> names = new EnumMap<>(Material.class);
        names.put(Material.TITANIUM, new String[] {"Titanium", "钛"});
        names.put(Material.ALUMINUM, new String[] {"Aluminum", "铝"});
        names.put(Material.TIN, new String[] {"Tin", "锡"});
        names.put(Material.STEEL, new String[] {"Steel", "钢"});
        names.put(Material.IRIDIUM, new String[] {"Iridium", "铱"});
        names.put(Material.DILITHIUM, new String[] {"Dilithium", "二锂"});
        names.put(Material.SILICON, new String[] {"Silicon", "硅"});
        names.put(Material.TITANIUM_ALUMINIDE, new String[] {"Titanium Aluminide", "钛铝合金"});
        names.put(Material.TITANIUM_IRIDIUM, new String[] {"Titanium-Iridium Alloy", "钛铱合金"});
        names.put(Material.COPPER, new String[] {"Copper", "铜"});
        names.put(Material.IRON, new String[] {"Iron", "铁"});
        names.put(Material.GOLD, new String[] {"Gold", "金"});
        if (names.size() != Material.values().length) {
            throw new IllegalStateException("every material needs a name");
        }
        return names;
    }
}
