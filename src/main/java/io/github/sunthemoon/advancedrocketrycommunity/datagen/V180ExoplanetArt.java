package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * The C15c textures (ADR-063 section 6, revision 6), drawn for this project as palette grids like
 * {@link V180SurfaceArt}: the lightwood log, its top, leaves, sapling and planks, one crystal texture that each crystal
 * block tints with its legacy colour (drawn in light greys), and the electric mushroom. No legacy texture is used.
 */
public final class V180ExoplanetArt implements DataProvider {
    private final PackOutput output;

    public V180ExoplanetArt(PackOutput output) {
        this.output = output;
    }

    public static Map<String, V180SurfaceArt.Texture> textures() {
        Map<String, V180SurfaceArt.Texture> textures = new LinkedHashMap<>();
        textures.put("block/lightwood_log", new V180SurfaceArt.Texture(LIGHTWOOD_LOG_SIDE, 0x5B8FD6));
        textures.put("block/lightwood_log_top", new V180SurfaceArt.Texture(LIGHTWOOD_LOG_TOP, 0x7FB2E8));
        textures.put("block/lightwood_leaves", new V180SurfaceArt.Texture(LIGHTWOOD_LEAVES, 0x55E8D6));
        textures.put("block/lightwood_sapling", new V180SurfaceArt.Texture(LIGHTWOOD_SAPLING, 0x6FD8E8));
        textures.put("block/lightwood_planks", new V180SurfaceArt.Texture(LIGHTWOOD_PLANKS, 0x4A6FD0));
        textures.put("block/crystal", new V180SurfaceArt.Texture(CRYSTAL, 0xFFFFFF));
        textures.put("block/electric_mushroom", new V180SurfaceArt.Texture(ELECTRIC_MUSHROOM, 0x8FD8FF));
        return textures;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        PackOutput.PathProvider paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
        for (Map.Entry<String, V180SurfaceArt.Texture> texture : textures().entrySet()) {
            byte[] png = V180MaterialArt.png(texture.getValue().grid(), texture.getValue().tint());
            var path = paths.file(ModIdentity.id(texture.getKey()), "png");
            writes.add(CompletableFuture.runAsync(() -> {
                try {
                    cache.writeIfNeeded(path, png, Hashing.sha1().hashBytes(png));
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "v1.8 exoplanet art";
    }

    // Lightwood bark: grooved, with glowing veins and a knot.
    static final String[] LIGHTWOOD_LOG_SIDE = {
            "5566458667475665",
            "6577376467454964",
            "5679567477465855",
            "5679466568465966",
            "6478477656465856",
            "6579467668555865",
            "6488468667375856",
            "6576477567475656",
            "6676457559475765",
            "6476576679465774",
            "6577376569465764",
            "7465463459465666",
            "6475474368454765",
            "7475457568556774",
            "6576556567476766",
            "6566466556465754",
    };
    // Lightwood end grain: a bright heart and alternating rings inside the bark.
    static final String[] LIGHTWOOD_LOG_TOP = {
            "5555454444454445",
            "5876677887866774",
            "5756787677886674",
            "5668676666778654",
            "5677568888567865",
            "5887577666867874",
            "4866877987786785",
            "4876878989675684",
            "5776778988786775",
            "4876877987786784",
            "4876677677867884",
            "5686667878567865",
            "4568776666778564",
            "5766787776885575",
            "4776678788866784",
            "4444455444545554",
    };
    // Lightwood leaves: glowing clusters with gaps.
    static final String[] LIGHTWOOD_LEAVES = {
            "7886.6587.9.7778",
            "6.587.58857.6996",
            "8.68897599778655",
            "5758756687.87798",
            "685556.986...797",
            "6776876.55.55586",
            "7767985977877.78",
            "96967677.6775989",
            "98757766767.87.7",
            "88.9568.57887775",
            "76.88.87778799.9",
            ".888.59759867669",
            "75.5957.78867787",
            "76.986578888.557",
            "6787798.77777888",
            "7967855877996878",
    };
    // Lightwood sapling: a thin stem under three glowing fronds.
    static final String[] LIGHTWOOD_SAPLING = {
            "................",
            "................",
            ".......9........",
            "......898.......",
            ".....78987......",
            "......787.......",
            "...78..6...87...",
            "..6876.5..6786..",
            "...6..55.5..6...",
            ".......4........",
            ".......5........",
            ".......4........",
            ".......5........",
            ".......44.......",
            ".......54.......",
            ".......44.......",
    };
    // Lightwood planks: four boards with staggered joints and bright nail heads.
    static final String[] LIGHTWOOD_PLANKS = {
            "7784877667767676",
            "7974778777676766",
            "7774677768866678",
            "3333333333333333",
            "7675676766645666",
            "6565755676746797",
            "6666676757746656",
            "3333333333333333",
            "6867774687788787",
            "8777774697787777",
            "7877764777686767",
            "3333333333333333",
            "5667555767776457",
            "6697667765657466",
            "7755667767676466",
            "3333333333333333",
    };
    // Crystal: diagonal facets with dark edges, light greys for the block tint.
    static final String[] CRYSTAL = {
            "8759588855999957",
            "7875888855999585",
            "7759589577595888",
            "7599955777758888",
            "5999955777595885",
            "8599588575999557",
            "7755888859999557",
            "7855888575995885",
            "7599585777559889",
            "5999957877558885",
            "5999595785995857",
            "7595888559999577",
            "7758898559995857",
            "7595985775958885",
            "5999557777598995",
            "9999558775958957",
    };
    // Electric mushroom: a domed cap with bright spots and a spark above it.
    static final String[] ELECTRIC_MUSHROOM = {
            "................",
            "........9.......",
            ".......9.8......",
            ".....888898.....",
            "....88988888....",
            "...8888988988...",
            "...8898898888...",
            "....44444444....",
            ".......65.......",
            ".......55.......",
            ".......56.......",
            ".......65.......",
            ".......55.......",
            ".......56.......",
            ".......65.......",
            "......4554......",
    };
}
