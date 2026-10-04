package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.command.ReleaseTestCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Opt-in fixed-cell diagnostic; the event precedes save and BlockEntity disposal. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class GuardedChunkUnloadObservation {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModIdentity.MOD_ID);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (Boolean.getBoolean(ReleaseTestCommands.PROPERTY)) {
            event.getDispatcher().register(Commands.literal("arce-guard-state")
                    .requires(source -> source.hasPermission(4) && source.getEntity() == null)
                    .executes(context -> state(context.getSource())));
        }
    }

    private static int state(CommandSourceStack source) {
        if (!Boolean.getBoolean(ReleaseTestCommands.PROPERTY) || !source.getServer().isSameThread()
                || !source.hasPermission(4) || source.getEntity() != null) {
            return 0;
        }
        ServerLevel level = source.getServer().overworld();
        GuardedChunkDiagnosticText.Encoded holder = GuardedChunkDiagnosticText.encode(
                level.getChunkSource().getChunkDebugData(new ChunkPos(11, 11)));
        LOGGER.info("ARCE_GUARD_STATE chunk=11,11 no_save={} holder=\"{}\" truncated={}",
                level.noSave(), holder.text(), holder.truncated());
        return 1;
    }

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
