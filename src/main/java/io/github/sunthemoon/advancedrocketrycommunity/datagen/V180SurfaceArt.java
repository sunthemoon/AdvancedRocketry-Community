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
 * Original pixel art for the C15b surface blocks (ADR-063 section 5), drawn for this project ({@code NEW}): moon
 * turf, dark moon turf, ferric sand, the charcoal log and the geode shell. Each texture is a 16 x 16 grid of the
 * {@link V180MaterialArt} palette, multiplied by one fixed colour and written as a deterministic PNG; no legacy or
 * vanilla texture is copied or recoloured.
 */
public final class V180SurfaceArt implements DataProvider {
    private final PackOutput output;

    public V180SurfaceArt(PackOutput output) {
        this.output = output;
    }

    /** One texture: its grid and the colour its greys are multiplied by. */
    public record Texture(String[] grid, int tint) {
    }

    public static Map<String, Texture> textures() {
        Map<String, Texture> textures = new LinkedHashMap<>();
        textures.put("block/moon_turf", new Texture(MOON_TURF, 0xE4E2DC));
        textures.put("block/dark_moon_turf", new Texture(MOON_TURF_DARK, 0x9A9A9E));
        textures.put("block/ferric_sand", new Texture(FERRIC_SAND, 0xD9784A));
        textures.put("block/charcoal_log", new Texture(CHARCOAL_LOG_SIDE, 0x6A5E58));
        textures.put("block/charcoal_log_top", new Texture(CHARCOAL_LOG_TOP, 0x8A7466));
        textures.put("block/geode_shell", new Texture(GEODE_SHELL, 0x8C7CA6));
        return textures;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        PackOutput.PathProvider paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
        for (Map.Entry<String, Texture> texture : textures().entrySet()) {
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
        return "v1.8 surface art";
    }

    // Moon turf: fine regolith with a few small impact pits (a dark rim and a pale floor).
    static final String[] MOON_TURF = {
            "8787887878878787",
            "7898787887789878",
            "8775678878878788",
            "7856687887787879",
            "8865787878897887",
            "7787878787887878",
            "8878788789788787",
            "7887879878778878",
            "8788787887787887",
            "7878886787878797",
            "8987856678878878",
            "8788665787887887",
            "7878778878787878",
            "8887878788789788",
            "7878897878878787",
            "8787878787787878",
    };
    static final String[] MOON_TURF_DARK = {
            "6565665656656565",
            "5676565665567656",
            "6553456656656566",
            "5634465665565657",
            "6643565656675665",
            "5565656565665656",
            "6656566567566565",
            "5665657656556656",
            "6566565665565665",
            "5656664565656575",
            "6765634456656656",
            "6566443565665665",
            "5656556656565656",
            "6665656566567566",
            "5656675656656565",
            "6565656565565656",
    };
    // Ferric sand: coarse oxidised grains, darker clusters of iron oxide.
    static final String[] FERRIC_SAND = {
            "7868786978687868",
            "8676857687868687",
            "6787686768787876",
            "7868787857686789",
            "8687678676868767",
            "7768687868777868",
            "6876867687685876",
            "8787978768687687",
            "7676868687876868",
            "6868757868687786",
            "8687686787668678",
            "7876868676878767",
            "6788687868687869",
            "8676878687568687",
            "7868687676878676",
            "6787868787687868",
    };
    // Charcoal log side: charred bark plates split by vertical cracks with glowing-free grey ash.
    static final String[] CHARCOAL_LOG_SIDE = {
            "3423142342314234",
            "3423142342314234",
            "4523152452315234",
            "4523152452315234",
            "3412142341214234",
            "3423142342314134",
            "3423152342315234",
            "4524152452415234",
            "4523142452314224",
            "3423142342314234",
            "3413142341314234",
            "3423152342315234",
            "4523152452315234",
            "4524142452414234",
            "3423142342314234",
            "3423142342314134",
    };
    // Charcoal log top: uneven growth rings around an off-centre core, split by one radial crack.
    static final String[] CHARCOAL_LOG_TOP = {
            "3333333333333322",
            "3444444447777553",
            "3666666644447753",
            "3633336666444773",
            "3333333336664473",
            "3555555533366443",
            "3777777555336643",
            "3774477775533661",
            "3444444777551113",
            "3466664471113363",
            "3661161117753363",
            "3611116447753363",
            "3661166447753363",
            "3466664477553363",
            "3444444777553663",
            "3333333333333333",
    };
    // Geode shell: dense banded rock with angular crystal facets.
    static final String[] GEODE_SHELL = {
            "4455443344554433",
            "4566544345665443",
            "5678654456786544",
            "4567543345675433",
            "3456432234564322",
            "3345433223454332",
            "4434544332345443",
            "5543655443456554",
            "6654766554567665",
            "5543655443456554",
            "4432544332345443",
            "3345433223454332",
            "3456432234564322",
            "4567543345675433",
            "5678654456786544",
            "4566544345665443",
    };
}
