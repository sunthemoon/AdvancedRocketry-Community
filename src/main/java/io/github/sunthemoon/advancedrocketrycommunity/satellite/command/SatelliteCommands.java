package io.github.sunthemoon.advancedrocketrycommunity.satellite.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationResult;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.AsteroidInstance;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.ResourceTables;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.RewardVerifier;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
    private final ResourceTableReloadListener.Manager resourceTables;

    public SatelliteCommands(SatelliteManager satellites, ResourceTableReloadListener.Manager resourceTables) {
        this.satellites = Objects.requireNonNull(satellites, "satellites");
        this.resourceTables = Objects.requireNonNull(resourceTables, "resourceTables");
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
                                .then(Commands.literal("verify")
                                        .then(Commands.argument("id", UuidArgument.uuid())
                                                .executes(this::verify)))
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("mission_id", UuidArgument.uuid())
                                                .executes(this::mission)))
                                .then(Commands.literal("release")
                                        .then(Commands.argument("mission_id", UuidArgument.uuid())
                                                .executes(this::release)))
                                .then(Commands.literal("cancel")
                                        .then(Commands.argument("mission_id", UuidArgument.uuid())
                                                .executes(this::cancel)))
                                .then(Commands.argument("mission_id", UuidArgument.uuid())
                                        .executes(this::mission)))
                        .then(Commands.literal("satellite")
                                .then(Commands.literal("recover")
                                        .then(Commands.argument("satellite_id", UuidArgument.uuid())
                                                .executes(this::recover))))
                        .then(Commands.literal("instance")
                                .then(Commands.literal("inspect")
                                        .then(Commands.argument("instance_id", UuidArgument.uuid())
                                                .executes(this::instance))))
                        .then(Commands.literal("diagnostics").executes(this::diagnostics))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("mission_id", UuidArgument.uuid())
                                        .executes(this::cancel)))
                        .then(Commands.literal("recover-chip")
                                .then(Commands.argument("satellite_id", UuidArgument.uuid())
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(this::recoverChip))))
                        .then(Commands.literal("unlink")
                                .then(Commands.argument("satellite_id", UuidArgument.uuid())
                                        .executes(this::unlink)))
                        .then(Commands.literal("blank-chip")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(this::blankChip)))
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
                                    .executes(this::releaseTestClaim)))
                    .then(Commands.literal("batch")
                            .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                    .executes(this::releaseTestBatch))));
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
        return audit(context, "mission_cancel", missionId, result);
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
        // A recovered chip carries the satellite's kind and blueprint, as the assembled chip did (ADR-049 section 6).
        SatelliteItemData.write(chip, new SatelliteIdentity(
                state.satelliteId(),
                state.ownerId(),
                state.definitionId(),
                state.kind(),
                state.kind() == SatelliteKind.DATA ? List.of() : state.blueprint().components()
        ));
        ItemHandlerHelper.giveItemToPlayer(player, chip);
        context.getSource().sendSuccess(() -> Component.literal(
                "Recovered bound chip for " + satelliteId + " to " + player.getScoreboardName()
        ), true);
        return 1;
    }

    /**
     * ADR-049 section 7: blanks the control chip in the player's main hand when its satellite no longer
     * exists (decommissioned, or unreadable). A chip of a registered satellite is refused, so an operator
     * cannot strand a live satellite; recover-chip stays the way to replace a lost chip.
     */
    private int blankChip(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.SATELLITE_CONTROL_CHIP.get())) {
            context.getSource().sendFailure(Component.literal("The player is not holding a satellite control chip"));
            return 0;
        }
        SatelliteItemData.DecodeResult decoded = SatelliteItemData.read(held);
        if (decoded.status() == SatelliteItemData.DecodeStatus.EMPTY) {
            context.getSource().sendFailure(Component.literal("The held chip is already blank"));
            return 0;
        }
        SatelliteMissionSavedData registry = SatelliteMissionSavedData.get(context.getSource().getServer());
        if (!registry.operational()) {
            // C7-L1: a blocked registry cannot tell whether the satellite still exists.
            context.getSource().sendFailure(Component.literal("The satellite registry is blocked; nothing was blanked"));
            return 0;
        }
        UUID satelliteId = decoded.identity().map(SatelliteIdentity::satelliteId)
                .or(() -> SatelliteItemData.rawSatelliteId(held)).orElse(null);
        if (satelliteId != null && registry.satellite(satelliteId).isPresent()) {
            context.getSource().sendFailure(Component.literal(
                    "Satellite " + satelliteId + " is still registered; decommission it first"));
            return 0;
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get(), held.getCount()));
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_SATELLITE_BLANK_CHIP player={} satellite={} decode={} by={}",
                player.getUUID(), satelliteId == null ? "unreadable" : satelliteId, decoded.status(),
                actor(context.getSource()));
        context.getSource().sendSuccess(() -> Component.literal(
                "Blanked the control chip held by " + player.getScoreboardName()), true);
        return 1;
    }

    /** ADR-049 section 9: an operator clears a solar satellite's receiver link by ID. */
    private int unlink(CommandContext<CommandSourceStack> context) {
        UUID satelliteId = UuidArgument.getUuid(context, "satellite_id");
        SatelliteOperationResult result = satellites.unlinkAdmin(context.getSource().getServer(), satelliteId,
                actor(context.getSource()));
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal("Unlink rejected: " + result.code()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("Satellite link cleared: " + satelliteId
                + " (" + result.code() + ")"), true);
        return 1;
    }

    /**
     * ADR-052 section 7: recomputes a mission's or an instance's reward from its stored seed and inputs. Read-only;
     * one bounded audit line, and a mismatch is logged as a defect.
     */
    private int verify(CommandContext<CommandSourceStack> context) {
        UUID id = UuidArgument.getUuid(context, "id");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(context.getSource().getServer());
        ResourceTables tables = resourceTables.current().orElse(ResourceTables.EMPTY);
        Optional<MissionState> mission = data.mission(id);
        Optional<AsteroidInstance> instance = data.instances().stream()
                .filter(candidate -> candidate.instanceId().equals(id)).findFirst();
        if (mission.isEmpty() && instance.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No mission or instance " + id));
            return 0;
        }
        RewardVerifier.Report report = mission.isPresent()
                ? RewardVerifier.verifyMission(mission.get(),
                        instanceId -> data.instances().stream()
                                .filter(candidate -> candidate.instanceId().equals(instanceId)).findFirst(),
                        data::satellite, tables)
                : RewardVerifier.verifyInstance(instance.get(), tables);
        String kind = mission.map(value -> "mission/" + value.kind().id()).orElse("instance");
        if (report.outcome() == RewardVerifier.Outcome.MISMATCH) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_SATELLITE_VERIFY id={} record={} outcome={} detail={} by={}",
                    id, kind, report.outcome(), report.detail(), actor(context.getSource()));
        } else {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_VERIFY id={} record={} outcome={} detail={} by={}",
                    id, kind, report.outcome(), report.detail(), actor(context.getSource()));
        }
        context.getSource().sendSuccess(() -> Component.literal(
                kind + " " + id + ": " + report.outcome() + " (" + report.detail() + ")"), false);
        return report.outcome() == RewardVerifier.Outcome.MATCH ? 1 : 0;
    }

    /** ADR-050 sections 4 and 9: returns a QUARANTINED mission to its previous status once its invariants hold. */
    private int release(CommandContext<CommandSourceStack> context) {
        UUID missionId = UuidArgument.getUuid(context, "mission_id");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(context.getSource().getServer());
        if (!data.operational()) {
            context.getSource().sendFailure(Component.literal("The satellite registry is blocked"));
            return 0;
        }
        SatelliteOperationResult result = data.releaseQuarantine(missionId);
        return audit(context, "mission_release", missionId, result);
    }

    /** ADR-050 section 8: returns a RECOVERY_REQUIRED satellite to service. */
    private int recover(CommandContext<CommandSourceStack> context) {
        UUID satelliteId = UuidArgument.getUuid(context, "satellite_id");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(context.getSource().getServer());
        if (!data.operational()) {
            context.getSource().sendFailure(Component.literal("The satellite registry is blocked"));
            return 0;
        }
        SatelliteOperationResult result = data.recoverSatellite(satelliteId);
        return audit(context, "satellite_recover", satelliteId, result);
    }

    private int instance(CommandContext<CommandSourceStack> context) {
        UUID instanceId = UuidArgument.getUuid(context, "instance_id");
        AsteroidInstance instance = SatelliteMissionSavedData.get(context.getSource().getServer())
                .instance(instanceId).orElse(null);
        if (instance == null) {
            context.getSource().sendFailure(Component.literal("Instance not found"));
            return 0;
        }
        long items = instance.yield().stream().mapToLong(entry -> entry.count()).sum();
        context.getSource().sendSuccess(() -> Component.literal("instance=" + instance.instanceId()
                + " owner=" + instance.ownerId() + " state=" + instance.state() + " type=" + instance.asteroidType()
                + " system=" + instance.system() + " version=" + instance.tableVersion()
                + " entries=" + instance.yield().size() + " items=" + items
                + " expires=" + (instance.expiresAt().isPresent() ? instance.expiresAt().getAsLong() : "none")
                + " source=" + instance.sourceMission()
                + " allocated=" + instance.allocatedMission().map(UUID::toString).orElse("none")), false);
        return 1;
    }

    /** ADR-050 sections 5 and 7: counts, queue sizes, reserved bytes and coalesced flushes. */
    private int diagnostics(CommandContext<CommandSourceStack> context) {
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(context.getSource().getServer());
        if (!data.operational()) {
            context.getSource().sendFailure(Component.literal("The satellite registry is blocked"));
            return 0;
        }
        String line = data.diagnostics() + " flush_pending=" + data.flushPending()
                + " coalesced_flushes=" + satellites.coalescedFlushes();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_DIAGNOSTICS {}", line);
        context.getSource().sendSuccess(() -> Component.literal(line), false);
        return 1;
    }

    /** One bounded audit line per operator lifecycle action (ADR-050 section 9). */
    private int audit(CommandContext<CommandSourceStack> context, String action, UUID id, SatelliteOperationResult result) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SATELLITE_ADMIN action={} id={} result={} by={}",
                action, id, result.code(), actor(context.getSource()));
        if (!result.success()) {
            context.getSource().sendFailure(Component.literal(action + " rejected: " + result.code()));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(action + " " + id + ": " + result.code()), true);
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

    private int releaseTestBatch(CommandContext<CommandSourceStack> context) {
        int requested = IntegerArgumentType.getInteger(context, "count");
        SatelliteManager.ReleaseTestBatchResult result = satellites.releaseTestBatch(
                context.getSource().getServer(), requested
        );
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_SATELLITE_BATCH requested={} created={} rejected={} "
                        + "code={} elapsed_nanos={} chunk_tickets=0 scheduler=deadline_queue",
                result.requested(), result.created(), result.rejected(), result.code(),
                result.elapsedNanos()
        );
        if (result.code() != io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode.SUCCESS) {
            context.getSource().sendFailure(Component.literal(
                    "Release-test satellite batch rejected: " + result.code()
            ));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(
                "Release-test satellite batch created: " + result.created()
        ), true);
        return result.created();
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
                + " kind=" + state.kind().id()
                + state.orbitBody().map(body -> " orbit=" + body).orElse("")
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
