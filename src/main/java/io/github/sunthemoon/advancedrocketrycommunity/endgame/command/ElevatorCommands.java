package io.github.sunthemoon.advancedrocketrycommunity.endgame.command;

import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPair;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPairs;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/**
 * ADR-059 sections 4 and 9: {@code /arce endgame elevator unbind <station_id>} for the station owner, the anchor owner
 * (each from their own connected command source) or an operator, whatever the pair's validity, never loading the far
 * end; and {@code /arce endgame elevator inspect <station_id>} for operators, with the stored pair and its live
 * validity.
 */
public final class ElevatorCommands {
    private static final UUID CONSOLE = new UUID(0L, 0L);
    private final EndgameService service;
    private final EndgameDevices devices;

    public ElevatorCommands(EndgameService service, EndgameDevices devices) {
        this.service = Objects.requireNonNull(service, "service");
        this.devices = Objects.requireNonNull(devices, "devices");
    }

    public void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("endgame")
                .then(Commands.literal("elevator")
                        .then(Commands.literal("unbind").then(Commands.argument("station", UuidArgument.uuid())
                                .executes(this::unbind)))
                        .then(Commands.literal("inspect").requires(source -> source.hasPermission(2))
                                .then(Commands.argument("station", UuidArgument.uuid()).executes(this::inspect))))));
    }

    private int unbind(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        boolean operator = source.hasPermission(2);
        UUID actor;
        if (operator) {
            actor = source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE;
        } else {
            Optional<ServerPlayer> player = EndgameCommandSources.ownPlayer(source);
            if (player.isEmpty()) {
                return refuse(source, EndgameCode.NOT_A_PLAYER);
            }
            actor = player.get().getUUID();
        }
        UUID station = UuidArgument.getUuid(context, "station");
        Optional<ElevatorPair> pair = service.root().flatMap(root -> root.pairs().forStation(station));
        if (pair.isEmpty()) {
            return refuse(source, service.root().isEmpty() ? EndgameCode.ROOT_UNAVAILABLE : EndgameCode.NOT_BOUND);
        }
        ElevatorRules.Check check = ElevatorPairs.unbind(source.getServer(), service, devices, pair.get(), actor,
                operator, source.getServer().overworld().getGameTime());
        if (!check.ok()) {
            return refuse(source, check.code());
        }
        source.sendSuccess(() -> Component.literal("elevator unbind station=" + station + " pair="
                + pair.get().pairId()), true);
        return 1;
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID station = UuidArgument.getUuid(context, "station");
        Optional<EndgameRoot> root = service.root();
        Optional<ElevatorPair> pair = root.flatMap(found -> found.pairs().forStation(station));
        if (pair.isEmpty()) {
            return refuse(source, root.isEmpty() ? EndgameCode.ROOT_UNAVAILABLE : EndgameCode.NOT_BOUND);
        }
        ElevatorRules.Check validity = ElevatorPairs.validity(source.getServer(), root.get(), devices, pair.get());
        ElevatorPair found = pair.get();
        source.sendSuccess(() -> Component.literal("elevator pair=" + found.pairId() + " station=" + found.stationId()
                + " terminal=" + found.terminalId() + " anchor=" + found.anchorId() + " body=" + found.bodyId()
                + " level=" + found.levelKey() + " column=" + found.x() + "," + found.z() + " anchor_y="
                + found.anchorY() + " bound_at=" + found.boundAt() + " bound_by=" + found.boundBy() + " validity="
                + validity.describe()), false);
        return 1;
    }

    private static int refuse(CommandSourceStack source, EndgameCode code) {
        source.sendFailure(Component.translatable(code.translationKey()).append(" (" + code.name() + ")"));
        return 0;
    }
}
