package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.model.geometry.StandaloneGeometryBakingContext;
import net.minecraftforge.client.model.obj.ObjModel;
import net.minecraftforge.client.model.obj.ObjTokenizer;
import net.minecraftforge.client.model.renderable.CompositeRenderable;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The physical-client OBJ path (ledger unit {@code event:ClientProxy.modelBakeEvent}, ADR-066 section 7.1). Models come
 * only from the compiled catalog's resource locations; there is no runtime source path, download or URL.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientObjModels {
    public static final ObjModelCache<CompositeRenderable> CACHE = new ObjModelCache<>();
    public static final ObjModelReloadListener<CompositeRenderable> RESOURCES = new ObjModelReloadListener<>(
            ObjModelFixture.CATALOG, CACHE, ClientObjModels::compile,
            released -> released.forEach(Minecraft.getInstance().getTextureManager()::release),
            AdvancedRocketryCommunity.LOGGER::warn);

    private ClientObjModels() { }

    /** Forge's parser and baker run only on checked bytes. A mesh binds its texture directly, outside the block atlas. */
    static CompositeRenderable compile(ObjModelFixture fixture, byte[] parserInput) throws IOException {
        var settings = new ObjModel.ModelSettings(fixture.model(), ObjModelFixture.AUTOMATIC_CULLING,
                ObjModelFixture.SHADE_QUADS, fixture.flipV(), ObjModelFixture.EMISSIVE_AMBIENT, null);
        try (var tokenizer = new ObjTokenizer(new ByteArrayInputStream(parserInput))) {
            return ObjModel.parse(tokenizer, settings).bakeRenderable(StandaloneGeometryBakingContext.create(fixture.model()));
        }
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(RESOURCES);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PRECISION_ASSEMBLER.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.PRECISION_ASSEMBLER, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.ROLLING_MACHINE.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.ROLLING_MACHINE, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.RAILGUN.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.RAILGUN, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.CENTRIFUGE.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.CENTRIFUGE, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.CHEMICAL_REACTOR.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.CHEMICAL_REACTOR, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.CRYSTALLIZER.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.CRYSTALLIZER, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.CUTTING_MACHINE.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.CUTTING_MACHINE, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.LATHE.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.LATHE, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.ASTROBODY_DATA_PROCESSOR.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.ASTROBODY_DATA_PROCESSOR, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.OBSERVATORY.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.OBSERVATORY, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.PLANETARY_BEACON.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.PLANETARY_BEACON, CACHE));
        event.registerBlockEntityRenderer(ModBlockEntities.SOLAR_ARRAY.get(),
                context -> new ClassicObjModelRenderer<>(ObjModelFixture.SOLAR_ARRAY, CACHE));
    }
}
