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
        V180PumpLanguage.translations(chinese).forEach(this::add);
        V180RecipeMenuLanguage.translations(chinese).forEach(this::add);
        V180ClassicAdvancementLanguage.translations(chinese).forEach(this::add);
        V180AtmosphereAnalyzerLanguage.translations(chinese).forEach(this::add);
        add("itemGroup." + NS + ".materials", chinese ? "高级火箭：材料" : "Advanced Rocketry: Materials");
        add("block." + NS + ".small_plate_press", chinese ? "小型压板机" : "Small Plate Press");
        add("block." + NS + ".combustion_generator", chinese ? "燃烧发电机" : "Combustion Generator");
        add("screen." + NS + ".combustion.rate", "40 FE/t");
        add("block." + NS + ".pressurized_tank", chinese ? "加压储罐" : "Pressurized Tank");
        add("message." + NS + ".tank.empty", chinese ? "空" : "Empty");
        add("message." + NS + ".tank.contents", chinese ? "%s：%s / %s mB" : "%s: %s / %s mB");
        add("tooltip." + NS + ".tank.contents", chinese ? "%s：%s mB" : "%s: %s mB");
        add("message." + NS + ".tank.repair", chinese ? "储罐数据无法识别；请备份后由管理员修复。"
                : "Unsupported tank data; back up the world and ask an administrator to repair it.");
        add("message." + NS + ".tank.refused", chinese ? "无法交换完整的 1,000 mB：请检查容量、流体、元数据和库存空间。"
                : "Cannot exchange a full 1,000 mB: check capacity, fluid, metadata and inventory space.");
        String[] gases = {"oxygen", "hydrogen", "nitrogen", "rocket_fuel", "enriched_lava"};
        String[] fluidEnglish = {"Oxygen", "Hydrogen", "Nitrogen", "Rocket Fuel", "Enriched Lava"};
        String[] fluidChinese = {"氧气", "氢气", "氮气", "火箭燃料", "富集熔岩"};
        for (int i = 0; i < gases.length; i++) {
            add("fluid_type." + NS + "." + gases[i], chinese ? fluidChinese[i] : fluidEnglish[i]);
        }
        add("item." + NS + ".nitrogen_canister", chinese ? "氮气罐" : "Nitrogen Canister");
        add("item." + NS + ".rocket_fuel_bucket", chinese ? "火箭燃料桶" : "Rocket Fuel Bucket");
        add("item." + NS + ".enriched_lava_bucket", chinese ? "富集熔岩桶" : "Enriched Lava Bucket");
        add("block." + NS + ".rocket_fuel", chinese ? "火箭燃料" : "Rocket Fuel");
        add("block." + NS + ".enriched_lava", chinese ? "富集熔岩" : "Enriched Lava");
        for (var motor : io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition.values()) {
            add("block." + NS + "." + motor.id(), chinese ? motor.chinese() : motor.english());
        }
        // Keep the existing v1.7 registry identity; only the player-facing casing name changes.
        add("block." + NS + ".advanced_machine_casing", chinese ? "高级机器机壳" : "Advanced Machine Casing");
        add("screen." + NS + ".combustion.burn", chinese ? "剩余燃烧：%s / %s tick" : "Burn credit: %s / %s ticks");
        String[] combustionEnglish = {"No fuel", "Generating", "Buffer full", "Disabled", "Fuel too long",
                "Container blocked", "Repair required"};
        String[] combustionChinese = {"无燃料", "发电中", "缓冲区已满", "已禁用", "燃烧时间过长", "容器无法保留", "需要修复"};
        var statuses = io.github.sunthemoon.advancedrocketrycommunity.machine.combustion.CombustionBurn.Status.values();
        for (int i = 0; i < statuses.length; i++) {
            add("screen." + NS + ".combustion.status." + statuses[i].name().toLowerCase(java.util.Locale.ROOT),
                    chinese ? combustionChinese[i] : combustionEnglish[i]);
        }
        add("jei." + NS + ".small_plate_press.redstone",
                chinese ? "红石脉冲" : "Redstone pulse");
        // C15b surface blocks and biomes (ADR-063 section 5), with the legacy names where the legacy game had them.
        add("block." + NS + ".moon_turf", chinese ? "月面土" : "Moon Turf");
        add("block." + NS + ".dark_moon_turf", chinese ? "暗色月面土" : "Dark Moon Turf");
        add("block." + NS + ".ferric_sand", chinese ? "氧化铁砂" : "Oxidized Ferric Sand");
        add("block." + NS + ".charcoal_log", chinese ? "炭化原木" : "Charcoal Log");
        add("block." + NS + ".geode_shell", chinese ? "晶簇方块" : "Geode Block");
        add("biome." + NS + ".regolith_highlands", chinese ? "月面高地" : "Regolith Highlands");
        add("biome." + NS + ".regolith_lowlands", chinese ? "月面低地" : "Regolith Lowlands");
        add("biome." + NS + ".ferric_regolith", chinese ? "氧化铁风化层荒原" : "Ferric Regolith Wasteland");
        add("biome." + NS + ".volcanic", chinese ? "火山" : "Volcanic");
        add("biome." + NS + ".volcanic_lowlands", chinese ? "火山低地" : "Volcanic Lowlands");
        // C15c Tau Ceti f and g blocks and biomes (ADR-063 section 6), with the legacy names.
        add("block." + NS + ".lightwood_log", chinese ? "轻木原木" : "Lightwood Log");
        add("block." + NS + ".lightwood_leaves", chinese ? "轻木树叶" : "Lightwood Leaves");
        add("block." + NS + ".lightwood_sapling", chinese ? "轻木树苗" : "Lightwood Sapling");
        add("block." + NS + ".lightwood_planks", chinese ? "轻木木板" : "Lightwood Planks");
        add("block." + NS + ".violet_crystal_block", chinese ? "紫色水晶块" : "Violet Crystal Block");
        add("block." + NS + ".blue_crystal_block", chinese ? "蓝色水晶块" : "Blue Crystal Block");
        add("block." + NS + ".green_crystal_block", chinese ? "绿色水晶块" : "Green Crystal Block");
        add("block." + NS + ".red_crystal_block", chinese ? "红色水晶块" : "Red Crystal Block");
        add("block." + NS + ".yellow_crystal_block", chinese ? "黄色水晶块" : "Yellow Crystal Block");
        add("block." + NS + ".orange_crystal_block", chinese ? "橙色水晶块" : "Orange Crystal Block");
        add("block." + NS + ".electric_mushroom", chinese ? "电蘑菇" : "Electric Mushroom");
        add("biome." + NS + ".alien_forest", chinese ? "异星森林" : "Alien Forest");
        add("biome." + NS + ".marsh", chinese ? "湿地" : "Marsh");
        add("biome." + NS + ".deep_swamp", chinese ? "深沼泽" : "Deep Swamp");
        add("biome." + NS + ".ocean_spires", chinese ? "海上尖塔" : "Ocean Spires");
        add("biome." + NS + ".stormland", chinese ? "风暴之地" : "Stormland");
        add("biome." + NS + ".crystal_chasms", chinese ? "水晶裂谷" : "Crystal Chasms");
        add("body." + NS + ".tau_ceti_f", chinese ? "鲸鱼座τ f" : "Tau Ceti f");
        add("body." + NS + ".tau_ceti_g", chinese ? "鲸鱼座τ g" : "Tau Ceti g");
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
