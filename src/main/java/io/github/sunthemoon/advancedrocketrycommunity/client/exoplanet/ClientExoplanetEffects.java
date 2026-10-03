package io.github.sunthemoon.advancedrocketrycommunity.client.exoplanet;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ElectricMushroomBlock;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.ExoplanetBlocks.Crystal;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.exoplanet.worldgen.ExoplanetWorldgen;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client effects of the C15c blocks (ADR-063 section 6, revision 6): the crystal blocks tint their one drawn texture
 * with the legacy colours, and electric mushrooms may flash the sky during rain in a stormland.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class ClientExoplanetEffects {
    /** Game ticks between two flashes on this client: at most one every five seconds. */
    static final int FLASH_INTERVAL_TICKS = 100;

    private ClientExoplanetEffects() {
    }

    @SubscribeEvent
    public static void blocks(RegisterColorHandlersEvent.Block event) {
        for (Crystal crystal : Crystal.values()) {
            int colour = 0xFF000000 | crystal.tint();
            event.register((state, level, position, tintIndex) -> tintIndex == 0 ? colour : 0xFFFFFFFF,
                    ExoplanetBlocks.crystal(crystal).get());
        }
    }

    @SubscribeEvent
    public static void items(RegisterColorHandlersEvent.Item event) {
        for (Crystal crystal : Crystal.values()) {
            int colour = 0xFF000000 | crystal.tint();
            event.register((stack, tintIndex) -> tintIndex == 0 ? colour : 0xFFFFFFFF,
                    ExoplanetBlocks.crystal(crystal).get().asItem());
        }
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        ElectricMushroomBlock.installClientFlash(new Flashes());
    }

    /** A sky flash and distant thunder on this client only; the server never sees it. */
    private static final class Flashes implements ElectricMushroomBlock.Flash {
        private long nextFlash = Long.MIN_VALUE;

        @Override
        public void maybeFlash(Level level, BlockPos position, RandomSource random) {
            if (!(level instanceof ClientLevel client) || !ClientConfig.electricMushroomFlashes() || !client.isRaining()) {
                return;
            }
            long now = client.getGameTime();
            if (now < nextFlash || random.nextInt(40) != 0
                    || !client.getBiome(position).is(ExoplanetWorldgen.STORMLAND)) {
                return;
            }
            nextFlash = now + FLASH_INTERVAL_TICKS;
            client.setSkyFlashTime(2);
            client.playLocalSound(position.getX() + random.nextInt(25) - 12, position.getY(),
                    position.getZ() + random.nextInt(25) - 12, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    2.0F, 0.8F + random.nextFloat() * 0.2F, false);
        }
    }
}
