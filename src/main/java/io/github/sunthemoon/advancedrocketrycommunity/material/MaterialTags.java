package io.github.sunthemoon.advancedrocketrycommunity.material;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/**
 * Tags of the material set (ADR-061 section 2, ADR-063 section 1): Forge's common tags, and this project's tags for
 * the products Forge does not name (sheets, coils, fans, boules) plus the {@code coils} group that replaces the
 * legacy {@code blockCoil}.
 */
public final class MaterialTags {
    /** The coils group that replaces the legacy {@code blockCoil}; it is also the umbrella of the coil tags. */
    public static final TagKey<Item> COILS_ITEM = TagKey.create(Registries.ITEM, own("coils"));
    public static final TagKey<Block> COILS_BLOCK = TagKey.create(Registries.BLOCK, own("coils"));

    private MaterialTags() {
    }

    private static ResourceLocation own(String path) {
        return ModIdentity.id(path);
    }

    private static ResourceLocation forge(String path) {
        return Objects.requireNonNull(ResourceLocation.tryBuild("forge", path), path);
    }

    /** The tag naming one product of one material; vanilla materials use the same Forge names. */
    public static ResourceLocation productTag(Material material, Product product) {
        String path = product.tagFolder() + "/" + material.id();
        return product.isProjectTag() ? own(path) : forge(path);
    }

    /** The umbrella tag of a product kind ({@code forge:ingots}, {@code advancedrocketrycommunity:sheets}). */
    public static ResourceLocation umbrellaTag(Product product) {
        return product.isProjectTag() ? own(product.tagFolder()) : forge(product.tagFolder());
    }

    public static TagKey<Item> item(Material material, Product product) {
        return TagKey.create(Registries.ITEM, productTag(material, product));
    }

    public static TagKey<Block> block(Material material, Product product) {
        return TagKey.create(Registries.BLOCK, productTag(material, product));
    }

    /**
     * The ore tags of a material ({@code forge:ores/<name>}): rutile is both {@code rutile} and {@code titanium}, as
     * the legacy game registered it under {@code oreRutile} and {@code oreTitanium} (ADR-063 section 1).
     */
    public static List<ResourceLocation> oreTags(Material material) {
        if (material.oreKind() == MaterialCatalog.OreKind.VANILLA) {
            return List.of(forge("ores/" + material.id()));
        }
        String name = material.oreName().orElseThrow();
        return material == Material.TITANIUM ? List.of(forge("ores/" + name), forge("ores/titanium"))
                : List.of(forge("ores/" + name));
    }

    /** The ore tag the small plate press and smelting recipes read: the material's own name. */
    public static TagKey<Item> oreItem(Material material) {
        return TagKey.create(Registries.ITEM, forge("ores/" + material.id()));
    }

    public static TagKey<Item> rawItem(Material material) {
        return TagKey.create(Registries.ITEM, forge("raw_materials/" + material.oreName().orElseThrow()));
    }
}
