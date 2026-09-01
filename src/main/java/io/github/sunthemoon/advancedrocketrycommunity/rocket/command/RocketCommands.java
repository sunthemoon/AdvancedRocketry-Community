package io.github.sunthemoon.advancedrocketrycommunity.rocket.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.assembler.RocketAssemblerReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketFlightReleaseCheckpoint;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

/** Operator diagnostics that reuse the normal bounded, server-authoritative rocket service. */
public final class RocketCommands {
    public static final String RELEASE_TEST_HOOK_PROPERTY =
            "advancedrocketrycommunity.releaseTestHooks";
    private static final String ASSEMBLER_ARGUMENT = "assembler";
    private static final String ROCKET_ARGUMENT = "rocket";
    private static final String TRANSFER_ARGUMENT = "transfer";
    private static final SimpleCommandExceptionType INVALID_RELEASE_TEST_ROCKET =
            new SimpleCommandExceptionType(Component.literal(
                    "Release-test target is not an operational rocket in this level"
            ));
    private static final UUID SERVER_CONSOLE_OWNER =
            UUID.fromString("00000000-0000-0000-0000-000000000005");

    private final RocketManager rockets;

    public RocketCommands(RocketManager rockets) {
        this.rockets = Objects.requireNonNull(rockets, "rockets");
    }

    public void register(RegisterCommandsEvent event) {
        var root = Commands.literal("rocket")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("validate")
                        .then(Commands.argument(ASSEMBLER_ARGUMENT, BlockPosArgument.blockPos())
                                .executes(context -> queue(context, false))))
                .then(Commands.literal("assemble")
                        .then(Commands.argument(ASSEMBLER_ARGUMENT, BlockPosArgument.blockPos())
                                .executes(context -> queue(context, true))))
                .then(Commands.literal("status")
                        .then(Commands.argument(ASSEMBLER_ARGUMENT, BlockPosArgument.blockPos())
                                .executes(this::status)))
                .then(Commands.literal("inspect")
                        .then(Commands.argument(TRANSFER_ARGUMENT, UuidArgument.uuid())
                                .executes(this::inspectTransfer)))
                .then(Commands.literal("recover")
                        .then(Commands.argument(TRANSFER_ARGUMENT, UuidArgument.uuid())
                                .executes(this::recoverTransfer)));
        if (Boolean.getBoolean(RELEASE_TEST_HOOK_PROPERTY)) {
            root.then(Commands.literal("release-test")
                    .then(Commands.literal("stage-recovery")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .executes(this::stageRecovery)))
                    .then(Commands.literal("refuel")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .executes(this::refuelFlight)))
                    .then(Commands.literal("launch")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .then(Commands.argument("destination", StringArgumentType.word())
                                            .executes(context -> launchFlight(context, false))
                                            .then(Commands.argument("checkpoint", StringArgumentType.word())
                                                    .executes(context -> launchFlight(context, true))))))
                    .then(Commands.literal("launch-station")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .then(Commands.argument("station", UuidArgument.uuid())
                                            .executes(this::launchStationFlight))))
                    .then(Commands.literal("report")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .executes(this::reportFlight)))
                    .then(Commands.literal("disassemble")
                            .then(Commands.argument(ROCKET_ARGUMENT, EntityArgument.entity())
                                    .executes(this::disassembleFlight))));
        }
        event.getDispatcher().register(Commands.literal("arce").then(root));
    }

    private int queue(CommandContext<CommandSourceStack> context, boolean assemble) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos position = BlockPosArgument.getBlockPos(context, ASSEMBLER_ARGUMENT);
        UUID ownerId = source.getEntity() instanceof ServerPlayer player
                ? player.getUUID()
                : SERVER_CONSOLE_OWNER;
        RocketValidationCode result = rockets.requestAdminAssembler(
                level,
                position,
                ownerId,
                assemble
        );
        if (result != RocketValidationCode.SCAN_IN_PROGRESS) {
            source.sendFailure(Component.literal("Rocket request rejected: " + result));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Queued bounded rocket " + (assemble ? "assembly" : "validation")
                                + " at " + position.toShortString()
                ),
                true
        );
        return 1;
    }

    private int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        BlockPos position = BlockPosArgument.getBlockPos(context, ASSEMBLER_ARGUMENT);
        if (!(source.getLevel().getBlockEntity(position) instanceof RocketAssemblerBlockEntity assembler)) {
            source.sendFailure(Component.literal("No rocket assembler at " + position.toShortString()));
            return 0;
        }
        RocketAssemblerReport report = assembler.report();
        String stats = report.optionalStats()
                .map(value -> " blocks=" + value.blockCount()
                        + " mass=" + value.mass()
                        + " thrust=" + value.thrust()
                        + " fuel=" + value.fuelCapacity()
                        + " seats=" + value.seatCount())
                .orElse("");
        source.sendSuccess(
                () -> Component.literal(
                        "Rocket assembler code=" + report.code() + stats + " detail=" + report.detail()
                ),
                false
        );
        return report.code() == RocketValidationCode.SUCCESS ? 1 : 0;
    }

    private int inspectTransfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID transferId = UuidArgument.getUuid(context, TRANSFER_ARGUMENT);
        RocketTransferInspection inspection = rockets.inspectTransfer(
                source.getServer(),
                transferId
        ).orElse(null);
        if (inspection == null) {
            source.sendFailure(Component.literal(
                    "No operational rocket transfer journal entry " + transferId
            ));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Transfer " + inspection.transferId()
                                + " logical=" + inspection.logicalRocketId()
                                + " phase=" + inspection.phase()
                                + " source=" + inspection.sourceDimension()
                                + " source_matches=" + inspection.sourceMatches()
                                + " destination=" + inspection.destinationDimension()
                                + " destination_matches=" + inspection.destinationMatches()
                                + " fuel=" + inspection.fuelBefore() + "->" + inspection.fuelAfter()
                                + " passengers=" + inspection.passengerCount()
                                + " checksum=" + inspection.checksum()
                ),
                false
        );
        return 1;
    }

    private int recoverTransfer(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        UUID transferId = UuidArgument.getUuid(context, TRANSFER_ARGUMENT);
        RocketTransferRecoveryReport report = rockets.recoverTransfer(source.getServer(), transferId);
        Component result = Component.literal(
                "Transfer recovery " + report.status()
                        + " transfer=" + report.transferId()
                        + " phase=" + report.phase().map(Enum::name).orElse("none")
                        + " action=" + report.action().map(Enum::name).orElse("none")
                        + " source_matches=" + report.sourceMatches()
                        + " destination_matches=" + report.destinationMatches()
        );
        if (report.status() == RocketTransferRecoveryReport.Status.RECOVERED
                || report.status() == RocketTransferRecoveryReport.Status.WAITING_FOR_PASSENGERS) {
            source.sendSuccess(() -> result, true);
            return 1;
        }
        source.sendFailure(result);
        return 0;
    }

    private int refuelFlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RocketEntity rocket = releaseTestRocket(context);
        RocketFlightData before = rocket.flightData().orElseThrow();
        if (!before.state().acceptsFuel() || before.fuel().capacity() <= 0L) {
            context.getSource().sendFailure(Component.literal(
                    "Release-test rocket cannot accept fuel in state " + before.state()
            ));
            return 0;
        }
        var filled = before.fuel().fill(before.fuel().capacity()).state();
        rocket.updateFlightData(before.withFuel(filled, rocket.level().getGameTime()));
        RocketFlightData after = rocket.flightData().orElseThrow();
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_REFUEL entity={} logical={} dimension={} state_before={} "
                        + "state_after={} amount={} capacity={}",
                rocket.getUUID(),
                rocket.assemblyTransactionId().orElse(null),
                rocket.level().dimension().location(),
                before.state(),
                after.state(),
                after.fuel().amount(),
                after.fuel().capacity()
        );
        context.getSource().sendSuccess(
                () -> Component.literal("Release-test rocket fuel="
                        + after.fuel().amount() + "/" + after.fuel().capacity()),
                false
        );
        return 1;
    }

    private int launchFlight(
            CommandContext<CommandSourceStack> context,
            boolean hasCheckpoint
    ) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        RocketEntity rocket = releaseTestRocket(context);
        RocketDestination destination;
        RocketFlightReleaseCheckpoint checkpoint = null;
        try {
            destination = RocketDestination.valueOf(
                    StringArgumentType.getString(context, "destination").toUpperCase(Locale.ROOT)
            );
            if (hasCheckpoint) {
                checkpoint = RocketFlightReleaseCheckpoint.valueOf(
                        StringArgumentType.getString(context, "checkpoint").toUpperCase(Locale.ROOT)
                );
            }
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown release-test destination or checkpoint"));
            return 0;
        }
        UUID requestId = UUID.randomUUID();
        if (checkpoint != null) {
            rockets.armFlightCheckpointForReleaseTest(requestId, checkpoint);
        }
        long fuelBefore = rocket.flightData().orElseThrow().fuel().amount();
        RocketFlightRequestResult result = rockets.requestAdminFlight(rocket, destination, requestId);
        if (!result.success() && checkpoint != null) {
            rockets.cancelFlightCheckpointForReleaseTest(requestId);
        }
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_LAUNCH request={} entity={} logical={} source={} destination={} "
                        + "checkpoint={} code={} required_fuel={} fuel_before={}",
                requestId,
                rocket.getUUID(),
                rocket.assemblyTransactionId().orElse(null),
                rocket.level().dimension().location(),
                destination.dimensionId(),
                checkpoint == null ? "none" : checkpoint,
                result.code(),
                result.requiredFuel(),
                fuelBefore
        );
        if (!result.success()) {
            source.sendFailure(Component.literal("Release-test launch failed: " + result.code()));
            return 0;
        }
        RocketFlightReleaseCheckpoint armed = checkpoint;
        source.sendSuccess(
                () -> Component.literal("Release-test launch " + requestId
                        + " required_fuel=" + result.requiredFuel()
                        + " checkpoint=" + (armed == null ? "none" : armed)),
                false
        );
        return 1;
    }

    private int launchStationFlight(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        RocketEntity rocket = releaseTestRocket(context);
        UUID stationId = UuidArgument.getUuid(context, "station");
        UUID requestId = UUID.randomUUID();
        long fuelBefore = rocket.flightData().orElseThrow().fuel().amount();
        RocketFlightRequestResult result = rockets.requestAdminStationFlight(
                rocket,
                stationId,
                requestId
        );
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_STATION_LAUNCH request={} entity={} logical={} source={} "
                        + "station={} code={} required_fuel={} fuel_before={}",
                requestId,
                rocket.getUUID(),
                rocket.assemblyTransactionId().orElse(null),
                rocket.level().dimension().location(),
                stationId,
                result.code(),
                result.requiredFuel(),
                fuelBefore
        );
        if (!result.success()) {
            source.sendFailure(Component.literal(
                    "Release-test station launch failed: " + result.code()
            ));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal("Release-test station launch " + requestId
                        + " station=" + stationId
                        + " required_fuel=" + result.requiredFuel()),
                false
        );
        return 1;
    }

    private int reportFlight(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        RocketEntity rocket = releaseTestRocket(context);
        RocketFlightData flight = rocket.flightData().orElseThrow();
        var snapshot = rocket.snapshot().orElseThrow();
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_RELEASE_TEST_FLIGHT_REPORT entity={} logical={} snapshot={} dimension={} state={} "
                        + "fuel={} capacity={} passengers={} transfer={} origin={},{},{} blocks={}",
                rocket.getUUID(),
                rocket.assemblyTransactionId().orElse(null),
                snapshot.contentHash(),
                rocket.level().dimension().location(),
                flight.state(),
                flight.fuel().amount(),
                flight.fuel().capacity(),
                flight.passengers().assignments().size(),
                flight.activeTransferId().map(UUID::toString).orElse("none"),
                snapshot.sourceOrigin().x(),
                snapshot.sourceOrigin().y(),
                snapshot.sourceOrigin().z(),
                snapshot.blocks().size()
        );
        context.getSource().sendSuccess(
                () -> Component.literal("Release-test flight state=" + flight.state()
                        + " fuel=" + flight.fuel().amount()),
                false
        );
        return 1;
    }

    private int disassembleFlight(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        RocketEntity rocket = releaseTestRocket(context);
        RocketValidationCode code = rockets.disassembleForReleaseTest(rocket);
        if (code != RocketValidationCode.SUCCESS) {
            context.getSource().sendFailure(Component.literal(
                    "Release-test disassembly failed: " + code
            ));
            return 0;
        }
        context.getSource().sendSuccess(
                () -> Component.literal("Release-test disassembly completed"),
                false
        );
        return 1;
    }

    private static RocketEntity releaseTestRocket(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        if (!(EntityArgument.getEntity(context, ROCKET_ARGUMENT) instanceof RocketEntity rocket)
                || !rocket.operational()
                || rocket.level() != context.getSource().getLevel()) {
            throw INVALID_RELEASE_TEST_ROCKET.create();
        }
        return rocket;
    }

    /**
     * Creates a durable pre-commit journal record for packaged restart testing.
     * The command is absent unless the dedicated release-test JVM property is set.
     */
    private int stageRecovery(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!(EntityArgument.getEntity(context, ROCKET_ARGUMENT) instanceof RocketEntity rocket)
                || !rocket.operational()
                || rocket.level() != source.getLevel()) {
            source.sendFailure(Component.literal("Target is not an operational rocket in this level"));
            return 0;
        }
        RocketStructureSnapshot snapshot = rocket.snapshot().orElseThrow();
        UUID transactionId = rocket.assemblyTransactionId().orElseThrow();
        UUID ownerId = rocket.ownerId().orElseThrow();
        RocketTransactionSavedData data = RocketTransactionSavedData.get(source.getServer());
        if (!data.operational()) {
            source.sendFailure(Component.literal("Rocket transaction journal is not operational"));
            return 0;
        }
        RocketTransactionRecord record = new RocketTransactionRecord(
                transactionId,
                RocketTransactionType.ASSEMBLY,
                RocketTransactionPhase.EXTRACTING,
                snapshot.snapshotId(),
                snapshot.contentHash(),
                RocketRegion.fromSnapshot(snapshot),
                snapshot.blocks().size(),
                rocket.getUUID()
        );
        rockets.suppressRecoveryUntilStopForReleaseTest();
        data.journalFor(snapshot, ownerId).write(record);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_ROCKET_RECOVERY_STAGED transaction={} rocket={} snapshot={} blocks={}",
                transactionId,
                rocket.getUUID(),
                snapshot.contentHash(),
                snapshot.blocks().size()
        );
        source.sendSuccess(
                () -> Component.literal("Staged bounded rocket recovery record " + transactionId),
                true
        );
        return 1;
    }
}
