package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Entry;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Kind;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Material;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialCatalog.Product;
import io.github.sunthemoon.advancedrocketrycommunity.material.MaterialContent;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.model.generators.BlockModelBuilder;
import net.minecraftforge.client.model.generators.BlockStateProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * v1.8 block states and models for the material set and the small plate press (ADR-063). Shared template models
 * carry tint index 0, which the client colour handlers fill with each material's colour; ore models keep the vanilla
 * stone or deepslate texture as an untinted base (referenced by resource location, never copied).
 */
public final class V180MaterialModels extends BlockStateProvider {
    private static final ExistingFileHelper.ResourceType TEXTURE =
            new ExistingFileHelper.ResourceType(PackType.CLIENT_RESOURCES, ".png", "textures");

    private final ExistingFileHelper existingFiles;

    public V180MaterialModels(PackOutput output, ExistingFileHelper existingFiles) {
        super(output, AdvancedRocketryCommunity.MOD_ID, existingFiles);
        this.existingFiles = existingFiles;
    }

    @Override
    protected void registerStatesAndModels() {
        // The textures come from V180MaterialArt in the same run.
        for (String texture : V180MaterialArt.textures().keySet()) {
            existingFiles.trackGenerated(modLoc(texture), TEXTURE);
        }
        BlockModelBuilder tintedCube = models().getBuilder("block/material/tinted_cube_all")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .texture("particle", "#all");
        tintedCube.element().from(0, 0, 0).to(16, 16, 16)
                .allFaces((direction, face) -> face.texture("#all").tintindex(0).cullface(direction)).end();
        BlockModelBuilder tintedColumn = models().getBuilder("block/material/tinted_cube_column")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .texture("particle", "#side");
        tintedColumn.element().from(0, 0, 0).to(16, 16, 16)
                .allFaces((direction, face) -> face.texture(direction.getAxis() == Direction.Axis.Y ? "#end" : "#side")
                        .tintindex(0).cullface(direction)).end();
        BlockModelBuilder tintedOre = models().getBuilder("block/material/tinted_ore")
                .parent(models().getExistingFile(mcLoc("block/block")))
                .renderType("cutout_mipped")
                .texture("particle", "#base");
        tintedOre.element().from(0, 0, 0).to(16, 16, 16)
                .allFaces((direction, face) -> face.texture("#base").cullface(direction)).end();
        tintedOre.element().from(0, 0, 0).to(16, 16, 16)
                .allFaces((direction, face) -> face.texture("#overlay").tintindex(0).cullface(direction)).end();

        ModelFile storage = models().getBuilder("block/material/storage_block").parent(tintedCube)
                .texture("all", modLoc("block/material/storage"));
        ModelFile coil = models().getBuilder("block/material/coil").parent(tintedColumn)
                .texture("side", modLoc("block/material/coil_side")).texture("end", modLoc("block/material/coil_top"));
        ModelFile stoneOre = ore(tintedOre, "block/material/stone_ore", "block/stone", "block/material/ore_overlay");
        ModelFile deepslateOre = ore(tintedOre, "block/material/deepslate_ore", "block/deepslate",
                "block/material/ore_overlay");
        ModelFile stoneCrystalOre = ore(tintedOre, "block/material/stone_crystal_ore", "block/stone",
                "block/material/crystal_ore_overlay");
        ModelFile deepslateCrystalOre = ore(tintedOre, "block/material/deepslate_crystal_ore", "block/deepslate",
                "block/material/crystal_ore_overlay");

        for (Entry entry : MaterialCatalog.entries()) {
            String id = entry.id();
            if (entry.isBlock()) {
                Block block = MaterialContent.block(id);
                boolean crystal = entry.material() == Material.DILITHIUM;
                ModelFile model = switch (entry.kind()) {
                    case STONE_ORE -> crystal ? stoneCrystalOre : stoneOre;
                    case DEEPSLATE_ORE -> crystal ? deepslateCrystalOre : deepslateOre;
                    default -> entry.product().orElseThrow() == Product.COIL ? coil : storage;
                };
                simpleBlock(block, model);
                simpleBlockItem(block, model);
            } else {
                String template = entry.kind() == Kind.RAW ? "raw" : entry.product().orElseThrow().suffix();
                itemModels().withExistingParent(id, mcLoc("item/generated"))
                        .texture("layer0", modLoc("item/material/" + template));
            }
        }

        ModelFile press = models().cubeBottomTop("small_plate_press", modLoc("block/small_plate_press_side"),
                modLoc("block/small_plate_press_bottom"), modLoc("block/small_plate_press_top"));
        simpleBlock(MaterialContent.SMALL_PLATE_PRESS.get(), press);
        simpleBlockItem(MaterialContent.SMALL_PLATE_PRESS.get(), press);
        V180SurfaceModels.register(this, existingFiles);
        V180ExoplanetModels.register(this, existingFiles);
    }

    private ModelFile ore(ModelFile template, String name, String base, String overlay) {
        return models().getBuilder(name).parent(template)
                .texture("base", mcLoc(base)).texture("overlay", modLoc(overlay));
    }
}
