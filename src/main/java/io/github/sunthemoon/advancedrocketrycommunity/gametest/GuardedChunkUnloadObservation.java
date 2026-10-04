package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in fixed-cell diagnostic; the event precedes save and BlockEntity disposal. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class GuardedChunkUnloadObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIdentity.MOD_ID);

    @SubscribeEvent
    public static void observe(ChunkEvent.Unload event) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY)
                || !(event.getLevel() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD || !level.getServer().isSameThread()
                || !(event.getChunk() instanceof LevelChunk chunk)
                || chunk.getPos().x != 11 || chunk.getPos().z != 11) {
            return;
        }
        // A queued console command runs after the enclosing server-thread unload continuation.
        LOGGER.info("ARCE_GUARD_UNLOAD_BEGIN chunk=11,11");
    }

    private GuardedChunkUnloadObservation() { }
}
