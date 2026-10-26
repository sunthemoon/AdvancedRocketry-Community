package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Original uniform closed panel; no source block image, inventory window, item form or recipe. */
public final class V180SealedRocketData implements DataProvider {
    private static final String ASSETS = "assets/advancedrocketrycommunity/";
    private final PackOutput output;
    private final boolean client, server;

    public V180SealedRocketData(PackOutput output, boolean client, boolean server) {
        this.output = output; this.client = client; this.server = server;
    }
    public static Map<String, JsonObject> clientFiles() {
        return Map.of(ASSETS + "blockstates/sealed_rocket_block.json", json("""
                {"variants":{"":{"model":"advancedrocketrycommunity:block/sealed_rocket_block"}}}
                """), ASSETS + "models/block/sealed_rocket_block.json", json("""
                {"parent":"minecraft:block/cube_all","textures":{"all":"advancedrocketrycommunity:block/sealed_rocket_block"}}
                """));
    }
    public static Map<String, JsonObject> serverFiles() {
        // Complete older project contributions, unchanged in order, plus the new sealed member.
        String immunity = """
                {"replace":false,"values":["advancedrocketrycommunity:laser_target","advancedrocketrycommunity:railgun",
                 "advancedrocketrycommunity:elevator_anchor","advancedrocketrycommunity:elevator_terminal",
                 "advancedrocketrycommunity:sealed_rocket_block"]}
                """;
        return Map.of("data/minecraft/tags/blocks/wither_immune.json", json(immunity),
                "data/minecraft/tags/blocks/dragon_immune.json", json(immunity),
                "data/advancedrocketrycommunity/tags/blocks/rocket_forbidden.json", json("""
                {"replace":false,"values":["minecraft:command_block","minecraft:chain_command_block",
                 "minecraft:repeating_command_block","minecraft:structure_block","minecraft:jigsaw","minecraft:spawner",
                 "minecraft:end_portal","minecraft:end_portal_frame","minecraft:nether_portal","minecraft:moving_piston",
                 "minecraft:piston_head","advancedrocketrycommunity:sealed_rocket_block"]}
                """));
    }
    public static Map<String, String> language(boolean chinese) {
        return Map.of("block.advancedrocketrycommunity.sealed_rocket_block", chinese ? "火箭封存方块" : "Sealed Rocket Block");
    }
    public static byte[] texture() {
        // Authored after docs/provenance/v1.8.0-sealed-rocket-preauthoring.md; no bitmap inputs.
        String[] grid = {
                "bbbbbbbbbbbbbbbb", "bssssssssssssssb", "bsrpppppppppprsb", "bsppppppppppppsb",
                "bsppppddppppppsb", "bsppppddppppppsb", "bAAAAAAAAAAAAAAb", "bAaAAaAAaAAaAAab",
                "bAaaAAaaAAaaAAab", "bAAAAAAAAAAAAAAb", "bsppppddppppppsb", "bsppppddppppppsb",
                "bsppppppppppppsb", "bsrpppppppppprsb", "bssssssssssssssb", "bbbbbbbbbbbbbbbb"
        };
        int[] pixels = new int[256];
        for (int y = 0; y < 16; y++) { for (int x = 0; x < 16; x++) {
            pixels[y * 16 + x] = switch (grid[y].charAt(x)) {
                case 'b' -> 0xFF252C32;
                case 's' -> 0xFF69747C;
                case 'r' -> 0xFFB5C2C8;
                case 'p' -> 0xFF424C55;
                case 'd' -> 0xFF333C44;
                case 'A' -> 0xFFE3AB43;
                case 'a' -> 0xFF8C612A;
                default -> throw new IllegalStateException("Unknown sealed-panel pixel");
            };
        } }
        return V180ClosureArt.encode(16, 16, pixels);
    }
    @Override public CompletableFuture<?> run(CachedOutput cache) {
        var writes = new ArrayList<CompletableFuture<?>>();
        if (client) {
            clientFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path))));
            writes.add(CompletableFuture.runAsync(() -> {
                byte[] png = texture();
                try { cache.writeIfNeeded(output.getOutputFolder().resolve(ASSETS + "textures/block/sealed_rocket_block.png"),
                        png, Hashing.sha1().hashBytes(png)); }
                catch (IOException failure) { throw new UncheckedIOException(failure); }
            }));
        }
        if (server) { serverFiles().forEach((path, value) -> writes.add(DataProvider.saveStable(cache, value, output.getOutputFolder().resolve(path)))); }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }
    @Override public String getName() { return "v1.8 original sealed rocket resources"; }
    private static JsonObject json(String value) { return JsonParser.parseString(value).getAsJsonObject(); }
}
