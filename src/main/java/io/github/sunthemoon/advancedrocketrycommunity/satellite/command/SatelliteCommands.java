package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.items.ItemHandlerHelper;

/** Bounded player status and permission-level-2 recovery/diagnostic commands. */
public final class SatelliteCommands {
    private static final UUID CONSOLE_ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";
    private final SatelliteManager satellites;

    public SatelliteCommands(SatelliteManager satellites) {
        this.satellites = Objects.requireNonNull(satellites, "satellites");
    }

    public void register(RegisterCommandsEvent event) {
        var root = Commands.literal("satellite")
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("research").executes(this::research))
                .then(Commands.literal("inspect")
                        .then(Commands.argument("satellite_id", UuidArgument.uuid())
                                .executes(this::inspect)))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("mission")
                                .then(Commands.argument("mission_id", UuidArgument.uuid())
                                        .executes(this::mission)))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("mission_id", UuidArgument.uuid())
                                        .executes(this::cancel)))
                        .then(Commands.literal("recover-chip")
                                .then(Commands.argument("satellite_id", UuidArgument.uuid())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(this::recoverChip))))
                        .then(Commands.literal("evidence").executes(this::evidence)));
        if (Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            root.then(Commands.literal("release-test")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("launch")
                            .then(Commands.argument("owner", UuidArgument.uuid())
                                    .then(Commands.argument("target", StringArgumentType.word())
                                            .executes(this::releaseTestLaunch))))
                    .then(Commands.literal("claim")
                            .then(Commands.argument("mission_id", UuidArgument.uuid())
                                    .executes(this::releaseTestClaim))));
        }
        event.getDispatcher().register(Commands.literal("arce").then(root));
    }

    private int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        List<SatelliteState> visible = satellites.satellites(source.getServer());
        if (source.getEntity() instanceof ServerPlayer player && !source.hasPermission(2)) {
            visible = visible.stream().filter(state -> state.ownerId().equals(player.getUUID())).toList();
        }
        List<SatelliteState> result = visible;
        source.sendSuccess(() -> Component.literal("Satellites: " + result.size()), false);
        result.forEach(state -> source.sendSuccess(() -> summary(state), false));
        return result.isEmpty() ? 1 : result.size();
    }

    private int research(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
            context.getSource().sendFailure(Component.literal("A player is required"));
            return 0;
        }
        int balance = satellites.researchBalance(context.getSource().getServer(), player.getUUID());
        context.getSource().sendSuccess(() -> Component.literal("Research balance: " + balance), false);
        return Math.max(1, balance);
    }

    private int inspect(CommandContext<CommandSourceStack> context) {
        UUID satelliteId = UuidArgument.getUuid(context, "satellite_id");
        SatelliteState state = satellites.satellite(context.getSource().getServer(), satelliteId).orElse(null);
        if (state == null) {
            context.getSource().sendFailure(Component.literal("Satellite not found"));
            return 0;
        }
        if (context.getSource().getEntity() instanceof ServerPlayer player
                && !context.getSource().hasPermission(2)
                && !state.ownerId().equals(player.getUUID())) {
            context.getSource().sendFailure(Component.literal("Satellite access denied"));
            return 0;
        }
        context.getSource().sendSuccess(() -> summary(state), false);
        state.currentMissionId().flatMap(id -> satellites.mission(context.getSource().getServer(), id))
                .ifPresent(mission -> context.getSource().sendSuccess(() -> missionSummary(mission), false));
        return 1;
    }

    private int mission(CommandContext<CommandSourceStack> context) {
        UUID missionId = UuidArgument.getUuid(context, "mission_id");
        MissionState state = satellites.mission(context.getSource().getServer(), missionId).orElse(null);
        if (state == null) {
            context.getSource().sendFailure(Component.literal("Mission not found"));
            return 0;
        }
        context.getSource().sendSuccess(() -> missionSummary(state), false);
        return 1;
    }

    private int cancel(CommandContext<CommandSourceStack> context) {
        UUID missionId = UuidArgument.getUuid(context, "mission_id");
        SatelliteOperationResult result = satellites.cancelAdmin(
                context.getSource().getServer(),
                missionId,
                actor(context.getSource())
        );
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal("Cancel rejected: " + result.code()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Mission cancelled: " + missionId), true);
        return 1;
    }

    private int recoverChip(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        UUID satelliteId = UuidArgument.getUuid(context, "satellite_id");
        SatelliteState state = satellites.satellite(context.getSource().getServer(), satelliteId).orElse(null);
        if (state == null) {
            context.getSource().sendFailure(Component.literal("Satellite not found"));
            return 0;
        }
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ItemStack chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(chip, new SatelliteIdentity(
                state.satelliteId(),
                state.ownerId(),
                state.definitionId()
        ));
        ItemHandlerHelper.giveItemToPlayer(player, chip);
        context.getSource().sendSuccess(() -> Component.literal(
                "Recovered bound chip for " + satelliteId + " to " + player.getScoreboardName()
        ), true);
        return 1;
    }

    private int evidence(CommandContext<CommandSourceStack> context) {
        List<SatelliteState> states = satellites.satellites(context.getSource().getServer());
        List<MissionState> missions = satellites.missions(context.getSource().getServer());
        long unfinished = missions.stream().filter(mission -> mission.status().unfinished()).count();
        long active = count(missions, io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.ACTIVE);
        long ready = count(missions, io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.READY);
        long pending = count(missions, io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.CLAIM_PENDING_DISCOVERY);
        long claimed = count(missions, io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.CLAIMED);
        long cancelled = count(missions, io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus.CANCELLED);
        long research = states.stream().map(SatelliteState::ownerId).distinct()
                .mapToLong(owner -> satellites.researchBalance(context.getSource().getServer(), owner))
                .sum();
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_SATELLITE_EVIDENCE satellites={} missions={} active={} ready={} pending={} "
                        + "claimed={} cancelled={} unfinished={} research={} chunk_tickets=0 scheduler=deadline_queue",
                states.size(), missions.size(), active, ready, pending,
                claimed, cancelled, unfinished, research
        );
        context.getSource().sendSuccess(() -> Component.literal(
                "Satellite evidence logged: satellites=" + states.size()
                        + " missions=" + missions.size()
                        + " unfinished=" + unfinished
                        + " chunk_tickets=0 scheduler=deadline_queue"
        ), true);
        return 1;
    }

    private int releaseTestLaunch(CommandContext<CommandSourceStack> context) {
        UUID ownerId = UuidArgument.getUuid(context, "owner");
        String rawTarget = StringArgumentType.getString(context, "target");
        ResourceLocation target = rawTarget.indexOf(':') >= 0
                ? ResourceLocation.tryParse(rawTarget)
                : ModIdentity.id(rawTarget);
        if (target == null) {
            context.getSource().sendFailure(Component.literal("Invalid satellite target"));
            return 0;
        }
        SatelliteOperationResult result = satellites.releaseTestLaunch(
                context.getSource().getServer(), ownerId, target
        );
        MissionState mission = result.mission().orElse(null);
        SatelliteState satellite = result.satellite().orElse(null);
        if (!result.success() || mission == null || satellite == null) {
            context.getSource().sendFailure(Component.literal(
                    "Release-test satellite launch rejected: " + result.code()
            ));
            return 0;
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_SATELLITE_LAUNCH satellite={} mission={} owner={} target={} "
                        + "code={} deadline={}",
                satellite.satelliteId(), mission.missionId(), ownerId, target,
                result.code(), mission.completesAtLogicalTime()
        );
        context.getSource().sendSuccess(() -> Component.literal(
                "Release-test satellite launched: " + mission.missionId()
        ), true);
        return 1;
    }

    private int releaseTestClaim(CommandContext<CommandSourceStack> context) {
        UUID missionId = UuidArgument.getUuid(context, "mission_id");
        SatelliteOperationResult result = satellites.releaseTestClaim(
                context.getSource().getServer(), missionId
        );
        MissionState mission = result.mission().orElse(null);
        if (!result.success() || mission == null) {
            context.getSource().sendFailure(Component.literal(
                    "Release-test satellite claim rejected: " + result.code()
            ));
            return 0;
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_SATELLITE_CLAIM mission={} owner={} target={} code={} "
                        + "status={} research={} discovered={}",
                mission.missionId(), mission.ownerId(), mission.targetBodyId(), result.code(),
                mission.status(), result.researchBalance(),
                satellites.discovered(context.getSource().getServer(), mission.targetBodyId())
        );
        context.getSource().sendSuccess(() -> Component.literal(
                "Release-test satellite claim: " + result.code()
        ), true);
        return 1;
    }

    private static long count(
            List<MissionState> missions,
            io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus status
    ) {
        return missions.stream().filter(mission -> mission.status() == status).count();
    }

    private static Component summary(SatelliteState state) {
        return Component.literal("satellite=" + state.satelliteId()
                + " owner=" + state.ownerId()
                + " type=" + state.definitionId()
                + " status=" + state.status()
                + " mission=" + state.currentMissionId().map(UUID::toString).orElse("none"));
    }

    private static Component missionSummary(MissionState state) {
        return Component.literal("mission=" + state.missionId()
                + " satellite=" + state.satelliteId()
                + " owner=" + state.ownerId()
                + " target=" + state.targetBodyId()
                + " status=" + state.status()
                + " deadline=" + state.completesAtLogicalTime()
                + " research=" + state.researchYield());
    }

    private static UUID actor(CommandSourceStack source) {
        return source.getEntity() instanceof ServerPlayer player ? player.getUUID() : CONSOLE_ACTOR;
    }
}
