package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorArt;
import io.github.sunthemoon.advancedrocketrycommunity.classiccomponent.MotorDefinition;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/** Emits twelve newly authored motor faces; no source image is read. */
public final class V180MotorArt implements DataProvider {
    private final PackOutput.PathProvider output;

    public V180MotorArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "textures");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (MotorDefinition tier : MotorDefinition.values()) {
            for (String face : MotorArt.FACES) {
                byte[] png = MotorArt.png(tier, face);
                var path = output.file(ModIdentity.id("block/" + tier.id() + "_" + face), "png");
                writes.add(CompletableFuture.runAsync(() -> {
                    try { cache.writeIfNeeded(path, png, Hashing.sha1().hashBytes(png)); }
                    catch (IOException exception) { throw new UncheckedIOException(exception); }
                }));
            }
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override public String getName() { return "v1.8 original motor art"; }
}
