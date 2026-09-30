package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionService;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-050 section 9 and ADR-051 section 9 operator commands for resource missions (permission level 2):
 * {@code mission rebind|purge} and {@code instance release}. Each writes one bounded audit line; the service
 * adds the {@code ARCE_MISSION_DELIVERY} line for rebinds and purges.
 */
public final class ResourceMissionCommands {
    private static final UUID CONSOLE_ACTOR = new UUID(0L, 0L);
    private final ResourceMissionService resources;

    public ResourceMissionCommands(ResourceMissionService resources) {
        this.resources = Objects.requireNonNull(resources, "resources");
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("satellite")
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("mission")
                                .then(Commands.literal("rebind")
                                        .then(Commands.argument("mission_id", UuidArgument.uuid())
                                                .then(Commands.argument("terminal_id", UuidArgument.uuid())
                                                        .executes(this::rebind))))
                                .then(Commands.literal("purge")
                                        .then(Commands.argument("mission_id", UuidArgument.uuid())
                                                .executes(context -> run(context, "mission_purge", "mission_id",
                                                        resources::purge)))))
                        .then(Commands.literal("instance")
                                .then(Commands.literal("release")
                                        .then(Commands.argument("instance_id", UuidArgument.uuid())
                                                .executes(context -> run(context, "instance_release",
                                                        "instance_id", resources::releaseInstance))))))));
    }

    private int rebind(CommandContext<CommandSourceStack> context) {
        UUID terminalId = UuidArgument.getUuid(context, "terminal_id");
        return run(context, "mission_rebind", "mission_id",
                (server, missionId) -> resources.rebind(server, missionId, terminalId));
    }

    private int run(CommandContext<CommandSourceStack> context, String action, String argument,
                    BiFunction<MinecraftServer, UUID, SatelliteOperationResult> operation) {
        UUID id = UuidArgument.getUuid(context, argument);
        CommandSourceStack source = context.getSource();
        SatelliteOperationResult result = operation.apply(source.getServer(), id);
        UUID actor = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE_ACTOR;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_ADMIN action={} id={} result={} by={}",
                action, id, result.code(), actor);
        if (!result.success()) {
            source.sendFailure(Component.literal(action + " rejected: " + result.code()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(action + " " + id + ": " + result.code()), true);
        return 1;
    }
}
