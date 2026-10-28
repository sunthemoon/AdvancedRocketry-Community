package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.RegisterCommandsEvent;

/** W185 native fixture actions; every entry is operator-only and requires the existing release-test gate. */
final class BayCopyReleaseTestCommands {
    static final UUID PASSENGER = UUID.fromString("00000000-0000-4000-8000-000000185001");
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private BayCopyReleaseTestCommands() { }

    static void register(RegisterCommandsEvent event) {
        var branch = Commands.literal("release-test")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("copies").then(Commands.argument("logical", UuidArgument.uuid())
                        .then(Commands.argument("variant", StringArgumentType.word()).executes(context -> prepare(context.getSource(),
                                UuidArgument.getUuid(context, "logical"), StringArgumentType.getString(context, "variant"))))))
                .then(Commands.literal("census").then(Commands.argument("logical", UuidArgument.uuid())
                        .executes(context -> census(context.getSource(), UuidArgument.getUuid(context, "logical")))));
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("bay").then(branch)));
    }

    private static int prepare(CommandSourceStack source, UUID logical, String variant) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        if (!List.of("source-after", "source-before", "absent", "protected", "unexpected", "outside", "both").contains(variant)) {
            throw new IllegalArgumentException("Unknown copy fixture variant");
        }
        var coordinator = RocketRuntime.installedBays().orElseThrow();
        var prepared = coordinator.preparedForTests(logical);
        var before = prepared.before(); var after = prepared.after();
        var sourceLevel = RocketTransferEntities.level(server, before.sourceSnapshot().sourceDimension());
        var destinationLevel = RocketTransferEntities.level(server, before.destinationSnapshot().sourceDimension());
        if (sourceLevel == null || destinationLevel == null || !RocketTransferEntities.endAlreadyLoaded(server, before.destinationSnapshot())) {
            LOGGER.info("ARCE_RELEASE_BAY_COPIES_WAIT logical={}", logical); return 0;
        }
        var exact = sourceLevel.getEntity(before.sourceEntityId());
        if (!(exact instanceof RocketEntity)) { throw new IllegalStateException("No source for copy fixture"); }
        if (variant.equals("source-after") || variant.equals("source-before")) {
            exact.discard();
            publish(server, variant.equals("source-after") ? after : before, false, before.sourceEntityId(), "neutral");
        } else if (variant.equals("absent")) { exact.discard(); }
        // Both halves at both ends; no fixture registration or receipt/journal mutation.
        for (var image : List.of(before, after)) {
            for (boolean destination : new boolean[] {false, true}) {
                publish(server, image, destination, UUID.randomUUID(), destination && image == before ? variant : "neutral");
            }
        }
        LOGGER.info("ARCE_RELEASE_BAY_COPIES logical={} variant={} source={} before={} after={} passenger={}", logical, variant,
                before.sourceEntityId(), before.sourceSnapshot().contentHash(), after.sourceSnapshot().contentHash(),
                before.sourceFlightData().passengers().assignment(PASSENGER).isPresent());
        return 1;
    }

    private static void publish(MinecraftServer server, RocketTransferRecord record, boolean destination, UUID id, String variant) {
        RocketStructureSnapshot snapshot = destination ? record.destinationSnapshot() : record.sourceSnapshot();
        ServerLevel level = RocketTransferEntities.level(server, snapshot.sourceDimension());
        var rocket = ModEntities.ROCKET.get().create(level);
        rocket.setUUID(id);
        rocket.initializeTransferred(snapshot, record.logicalRocketId(), record.ownerId(),
                destination ? record.destinationFlightData() : record.sourceFlightData());
        RocketTransferEntities.positionAtAltitude(rocket, snapshot.sourceOrigin());
        if (variant.equals("protected")) { rocket = BayDeploymentGameTestSupport.RocketEscrowSectionPatch.BOUND.apply(level, rocket); }
        if (variant.equals("unexpected")) { rocket.setPos(rocket.getX() + 1, rocket.getY(), rocket.getZ()); }
        if (variant.equals("outside")) { rocket.setPos(rocket.getX() + 32, rocket.getY(), rocket.getZ()); }
        if (!level.addFreshEntity(rocket)) { throw new IllegalStateException("Copy fixture publication refused"); }
    }

    private static int census(CommandSourceStack source, UUID logical) {
        var server = source.getServer(); EscrowTestEndpoints.requireEnabled(server);
        var record = RocketTransferSavedData.get(server).findByLogicalRocket(logical).orElse(null);
        // Test-only diagnostics: this observes the fixture UUIDs after ordinary flight has left the recorded boxes.
        var found = new java.util.ArrayList<RocketEntity>();
        for (var level : server.getAllLevels()) {
            found.addAll(level.getEntities(ModEntities.ROCKET.get(), rocket -> BayCopyCensus.names(rocket, logical)));
        }
        var rows = found.stream().map(rocket -> rocket.getUUID() + ":" + rocket.level().dimension().location() + ":"
                + rocket.snapshot().orElseThrow().contentHash() + ":" + rocket.flightData().orElseThrow().passengers()
                .assignment(PASSENGER).isPresent()).sorted().collect(java.util.stream.Collectors.joining(","));
        LOGGER.info("ARCE_RELEASE_BAY_CENSUS logical={} count={} transfer={} assigned={} rows=[{}]", logical, found.size(),
                record == null ? "NONE" : record.phase(), record != null && record.sourceFlightData().passengers()
                        .assignment(PASSENGER).isPresent() && record.destinationFlightData().passengers().assignment(PASSENGER).isPresent(), rows);
        return found.size();
    }
}
