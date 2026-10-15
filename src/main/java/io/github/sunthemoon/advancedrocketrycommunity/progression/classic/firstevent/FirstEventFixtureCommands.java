package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;

/** Empty-server-only controls for the native first-event campaign; absent from ordinary servers. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class FirstEventFixtureCommands {
    private FirstEventFixtureCommands() { }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { FirstEventCutSeams.fixtureTick(event.getServer()); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stopping(ServerStoppingEvent event) { FirstEventCutSeams.stopFixture(); }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean(EscrowTestEndpoints.HOOKS) || FirstEventCutSeams.configured().isEmpty()) { return; }
        event.getDispatcher().register(Commands.literal("arce").then(Commands.literal("progression")
                .then(Commands.literal("release-test").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("export-cases").executes(context -> {
                            FirstEventCaseExports.write(context.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("moon-extra").executes(context -> {
                            io.github.sunthemoon.advancedrocketrycommunity.rocket.server.FirstEventRocketFixtures
                                    .additionalDescent(context.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("release-reservation").then(Commands.argument("entity", UuidArgument.uuid())
                                .executes(context -> {
                                    var server = context.getSource().getServer();
                                    EscrowTestEndpoints.requireEnabled(server);
                                    var id = UuidArgument.getUuid(context, "entity");
                                    for (var level : server.getAllLevels()) {
                                        if (level.getEntity(id) instanceof io.github.sunthemoon.advancedrocketrycommunity
                                                .rocket.entity.RocketEntity rocket) {
                                            boolean ready = io.github.sunthemoon.advancedrocketrycommunity.rocket.server
                                                    .RocketRuntime.releaseLandedReservationForFirstEventTest(rocket);
                                            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                                                    "ARCE_RELEASE_TEST first_event_fixture reservation_requested entity={} ready={}", id, ready);
                                            return 1;
                                        }
                                    }
                                    throw new IllegalStateException("Fixture rocket is not loaded");
                                })))
                        .then(Commands.literal("stations").executes(context -> {
                            io.github.sunthemoon.advancedrocketrycommunity.station.warp.FirstEventStationFixtures
                                    .create(context.getSource().getServer());
                            return 1;
                        }))
                        .then(Commands.literal("warp").then(Commands.argument("station", UuidArgument.uuid())
                                .executes(context -> {
                                    io.github.sunthemoon.advancedrocketrycommunity.station.warp.FirstEventStationFixtures
                                            .warp(context.getSource().getServer(), UuidArgument.getUuid(context, "station"));
                                    return 1;
                                })))
                        .then(Commands.literal("moon-two").executes(context -> {
                            io.github.sunthemoon.advancedrocketrycommunity.rocket.server.FirstEventRocketFixtures
                                    .twoDescents(context.getSource().getServer());
                            return 1;
                        }))
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
