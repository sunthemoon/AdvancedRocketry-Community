package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * Original pixel art for the v1.8 material set and the small plate press (ADR-063 sections 1 and 3), drawn for this
 * project ({@code NEW}). Material templates are greyscale and tinted at runtime with the legacy material colours;
 * the press is drawn in fixed colours. Each texture is a 16 x 16 grid of palette characters, written as a
 * deterministic PNG into the DataGen output, so no image file is imported or copied.
 */
public final class V180MaterialArt implements DataProvider {
    /**
     * Greyscale levels for the digits 0..9; '.' is transparent. Each level is a grey that few vanilla textures use
     * (within a few steps of an even 24-step scale), so the derivation screen does not meet chance equal colours:
     * the even scale's 120, 168 and 216 are greys of the vanilla bucket and flagged the boule.
     */
    private static final int[] GREYS = {20, 44, 72, 93, 117, 141, 169, 191, 210, 238};
    /** The press's steel-blue cast (multiplied into its greys). */
    private static final int PRESS_TINT = 0xB4BECC;

    private final PackOutput output;

    public V180MaterialArt(PackOutput output) {
        this.output = output;
    }

    /** Every texture path (under {@code textures/}) and its grid, in a stable order. */
    public static Map<String, String[]> textures() {
        Map<String, String[]> textures = new LinkedHashMap<>();
        textures.put("item/material/ingot", INGOT);
        textures.put("item/material/nugget", NUGGET);
        textures.put("item/material/dust", DUST);
        textures.put("item/material/plate", PLATE);
        textures.put("item/material/sheet", SHEET);
        textures.put("item/material/rod", ROD);
        textures.put("item/material/gear", GEAR);
        textures.put("item/material/fan", FAN);
        textures.put("item/material/crystal", CRYSTAL);
        textures.put("item/material/boule", BOULE);
        textures.put("item/material/raw", RAW);
        textures.put("block/material/storage", STORAGE);
        textures.put("block/material/coil_side", COIL_SIDE);
        textures.put("block/material/coil_top", COIL_TOP);
        textures.put("block/material/ore_overlay", ORE_OVERLAY);
        textures.put("block/material/crystal_ore_overlay", CRYSTAL_ORE_OVERLAY);
        textures.put("block/small_plate_press_top", PRESS_TOP);
        textures.put("block/small_plate_press_side", PRESS_SIDE);
        textures.put("block/small_plate_press_bottom", PRESS_BOTTOM);
        return textures;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        PackOutput.PathProvider paths = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
        for (Map.Entry<String, String[]> texture : textures().entrySet()) {
            boolean press = texture.getKey().startsWith("block/small_plate_press");
            byte[] png = png(texture.getValue(), press ? PRESS_TINT : 0xFFFFFF);
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
        return "v1.8 material art";
    }

    /** RGBA pixels of a grid: digits map to greys multiplied by {@code tint}, '.' is transparent. */
    public static int[] pixels(String[] grid, int tint) {
        if (grid.length != 16) {
            throw new IllegalArgumentException("A texture grid has 16 rows");
        }
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) {
            if (grid[y].length() != 16) {
                throw new IllegalArgumentException("Row " + y + " is not 16 characters wide");
            }
            for (int x = 0; x < 16; x++) {
                char c = grid[y].charAt(x);
                if (c == '.') {
                    pixels[y * 16 + x] = 0;
                    continue;
                }
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException("Unknown palette character " + c);
                }
                int grey = GREYS[c - '0'];
                int r = grey * ((tint >> 16) & 0xFF) / 255;
                int g = grey * ((tint >> 8) & 0xFF) / 255;
                int b = grey * (tint & 0xFF) / 255;
                pixels[y * 16 + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }
        return pixels;
    }

    /** A deterministic 16 x 16 RGBA PNG (no ancillary chunks, fixed compression). */
    public static byte[] png(String[] grid, int tint) {
        int[] pixels = pixels(grid, tint);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < 16; y++) {
            raw.write(0);
            for (int x = 0; x < 16; x++) {
                int argb = pixels[y * 16 + x];
                raw.write((argb >> 16) & 0xFF);
                raw.write((argb >> 8) & 0xFF);
                raw.write(argb & 0xFF);
                raw.write((argb >>> 24) & 0xFF);
            }
        }
        Deflater deflater = new Deflater(9);
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        while (!deflater.finished()) {
            compressed.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        png.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        chunk(png, "IHDR", ByteBuffer.allocate(13).putInt(16).putInt(16).put((byte) 8).put((byte) 6)
                .put((byte) 0).put((byte) 0).put((byte) 0).array());
        chunk(png, "IDAT", compressed.toByteArray());
        chunk(png, "IEND", new byte[0]);
        return png.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] body) {
        byte[] kind = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeBytes(ByteBuffer.allocate(4).putInt(body.length).array());
        out.writeBytes(kind);
        out.writeBytes(body);
        CRC32 crc = new CRC32();
        crc.update(kind);
        crc.update(body);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }

    // Item templates (tinted per material).
    static final String[] INGOT = {
            "................",
            "................",
            "................",
            "................",
            "....99999999....",
            "...9888888889...",
            "..988888888889..",
            ".98888888888889.",
            ".66666666666665.",
            ".55555555555554.",
            ".44444444444443.",
            ".33333333333332.",
            "................",
            "................",
            "................",
            "................",
    };
    static final String[] NUGGET = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "......99........",
            ".....9887.......",
            "....988876......",
            "....877765..99..",
            ".....66654.9876.",
            "..99..443.98765.",
            ".9876......8654.",
            ".8765......544..",
            "..543...........",
            "................",
    };
    static final String[] DUST = {
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "................",
            "......7.8.......",
            ".....898797.....",
            "....89787898....",
            "...7887978987...",
            "..678777878876..",
            ".56676676677665.",
            "................",
    };
    static final String[] PLATE = {
            "................",
            "................",
            "..999999999999..",
            "..988888888886..",
            "..987888888786..",
            "..988888888886..",
            "..988888888886..",
            "..988888888886..",
            "..988888888886..",
            "..988888888886..",
            "..988888888886..",
            "..987888888786..",
            "..988888888886..",
            "..966666666665..",
            "................",
            "................",
    };
    static final String[] SHEET = {
            "................",
            ".99999999999....",
            ".988888888879...",
            ".9888888888779..",
            ".98888888887779.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".98888888888886.",
            ".96666666666665.",
            "................",
    };
    static final String[] ROD = {
            "................",
            "................",
            "............99..",
            "...........9886.",
            "..........9876..",
            ".........9876...",
            "........9876....",
            ".......9876.....",
            "......9876......",
            ".....9876.......",
            "....9876........",
            "...9876.........",
            "..9876..........",
            ".8765...........",
            "..54............",
            "................",
    };
    static final String[] GEAR = {
            "................",
            "......9..8......",
            "...8.988887.6...",
            "...98888888876..",
            "...888876688875.",
            ".9888.......8885",
            "..886.......776.",
            "..887.......765.",
            ".9887.......7765",
            "..887.......765.",
            "..776.......654.",
            ".98776.....66544",
            "...777666666654.",
            "...86766666654..",
            "...6.655554.4...",
            "......5..4......",
    };
    static final String[] FAN = {
            "................",
            "....99..........",
            "...9887.....88..",
            "...98876...9876.",
            "....8877..98765.",
            ".....877.98765..",
            "......87877654..",
            ".......866655...",
            "...9988877......",
            "..98776.8876....",
            ".98765...7765...",
            ".9876.....6654..",
            "..76.......654..",
            "...........44...",
            "................",
            "................",
    };
    static final String[] CRYSTAL = {
            "................",
            ".......9........",
            "......989.......",
            ".....98889......",
            ".....98878......",
            "....9888776.....",
            "....9887776.....",
            "....8877765.....",
            "....8877665.....",
            "....8776655.....",
            "....7766554.....",
            ".....766544.....",
            ".....66544......",
            "......5543......",
            ".......43.......",
            "................",
    };
    static final String[] BOULE = {
            "................",
            "......9999......",
            ".....988887.....",
            "....98888876....",
            "....98888876....",
            "....88888876....",
            "....88888776....",
            "....88887776....",
            "....88877765....",
            "....88777665....",
            "....87776655....",
            "....77766554....",
            "....77666554....",
            ".....666544.....",
            "......5554......",
            "................",
    };
    static final String[] RAW = {
            "................",
            "................",
            "................",
            ".......99.......",
            ".....998879.....",
            "....98887789....",
            "...988777688....",
            "..9887766778....",
            "..87766677666...",
            "..876666676655..",
            "...7666656655...",
            "...7665555544...",
            "....65554443....",
            ".....5443.......",
            "................",
            "................",
    };

    // Block templates (tinted per material, except the ore base, which is vanilla stone).
    static final String[] STORAGE = {
            "9999999999999998",
            "9888888888888886",
            "9878888888888786",
            "9888888888888886",
            "9888888888888886",
            "9888888888888886",
            "9888888888888886",
            "9888866666688886",
            "9888866666688886",
            "9888888888888886",
            "9888888888888886",
            "9888888888888886",
            "9888888888888886",
            "9878888888888786",
            "9888888888888886",
            "8666666666666665",
    };
    static final String[] COIL_SIDE = {
            "6555555555555554",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "5999999999999993",
            "5777777777777773",
            "4333333333333332",
    };
    static final String[] COIL_TOP = {
            "6555555555555554",
            "5999999999999993",
            "5977777777777793",
            "5979999999999793",
            "5979777777779793",
            "5979799999979793",
            "5979793333979793",
            "5979793223979793",
            "5979793223979793",
            "5979793333979793",
            "5979799999979793",
            "5979777777779793",
            "5979999999999793",
            "5977777777777793",
            "5999999999999993",
            "4333333333333332",
    };
    static final String[] ORE_OVERLAY = {
            "................",
            "...98...........",
            "..9876......97..",
            "...76......9876.",
            "...........876..",
            "......98........",
            ".....9887.......",
            "......765.......",
            "..............9.",
            ".98..........987",
            "9876....97....76",
            ".76....9876.....",
            ".......876......",
            "..98............",
            ".9876.....987...",
            "..76.......76...",
    };
    static final String[] CRYSTAL_ORE_OVERLAY = {
            "................",
            "..9.............",
            ".989.......9....",
            ".989......989...",
            "..8.......989...",
            "..........787...",
            "....9......7....",
            "...989..........",
            "...989.......9..",
            "...787......989.",
            "....7.......787.",
            ".............7..",
            "..9.....9.......",
            ".989...989......",
            ".787...787......",
            "..7.....7.......",
    };

    // The small plate press (fixed colours, steel-blue cast).
    static final String[] PRESS_TOP = {
            "7777777777777776",
            "7966666666666956",
            "7666666666666656",
            "7666665555666656",
            "7666554444556656",
            "7665448888445656",
            "7665488778845656",
            "7654877667784556",
            "7654877667784556",
            "7665488778845656",
            "7665448888445656",
            "7666554444556656",
            "7666665555666656",
            "7666666666666656",
            "7966666666666956",
            "6555555555555555",
    };
    static final String[] PRESS_SIDE = {
            "3333333333333333",
            "3777777777777773",
            "3766666666666673",
            "3333333333333333",
            "3933111111111393",
            "3833111111111383",
            "3833111999111383",
            "3833111878111383",
            "3833111878111383",
            "3833111878111383",
            "3833198888889383",
            "3833187777778383",
            "3833166666666383",
            "3333333333333333",
            "3888888888888883",
            "3333333333333333",
    };
    static final String[] PRESS_BOTTOM = {
            "3333333333333333",
            "3774477447744773",
            "3764476447644763",
            "3444444444444443",
            "3444444444444443",
            "3774477447744773",
            "3764476447644763",
            "3444444444444443",
            "3444444444444443",
            "3774477447744773",
            "3764476447644763",
            "3444444444444443",
            "3444444444444443",
            "3774477447744773",
            "3764476447644763",
            "3333333333333333",
    };
}
