package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Empty-server-only controls for the native first-event campaign; absent from ordinary servers. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class FirstEventFixtureCommands {
    private FirstEventFixtureCommands() { }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS) || FirstEventCutSeams.configured().isEmpty()) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("progression")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("release").executes(context -> {
                            FirstEventCutSeams.release(context.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("halt").executes(context -> {
                            var server = context.getSource().getServer();
                            EscrowTestEndpoints.requireEnabled(server);
                            FirstEventCutSeams.halt(FirstEventCutSeams.configured().orElseThrow(), "fixture_stop", null);
                            return 1;
                        })))));
    }
}
