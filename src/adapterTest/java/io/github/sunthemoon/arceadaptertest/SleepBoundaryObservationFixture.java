package io.github.sunthemoon.arceadaptertest;

import static io.github.sunthemoon.arceadaptertest.SleepBoundaryTrace.error;
import static io.github.sunthemoon.arceadaptertest.SleepBoundaryTrace.object;
import static io.github.sunthemoon.arceadaptertest.SleepBoundaryTrace.position;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Opt-in, disposable Overworld observation; not sleep enforcement or connected-client evidence. */
@Mod.EventBusSubscriber(modid = AdapterTestMod.MOD_ID)
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepBoundaryObservationFixture {
    static final BlockPos HEAD = new BlockPos(12, 180, 12), FOOT = new BlockPos(11, 180, 12);
    static final List<String> NAMES = List.of(
            "day_head_observer_off", "day_head_observer_on", "day_foot_observer_off", "day_foot_observer_on",
            "night_head_observer_off", "night_head_observer_on", "night_foot_observer_off", "night_foot_observer_on",
            "packet_range_refusal", "occupied_bed_refusal", "native_admin_forced", "native_same_bed_tuple_unforced",
            "native_null_reset", "nested_native_admin_same_actor", "nested_native_admin_other_actor",
            "nested_synthetic_bus_same_actor", "nested_synthetic_bus_other_actor", "direct_and_forwarded_callbacks",
            "one_shot_observer_failure", "independent_admin_after_failure_teardown");
    private static final UUID[] IDS = { UUID.fromString("3ec99863-f1a1-4ba6-8aaa-390209dbe2d0"),
            UUID.fromString("c5c1adf3-c2b4-4f57-8c98-576bfe5bbcf5") };
    private static final BlockPos[] HOMES = { new BlockPos(8, 180, 8), new BlockPos(8, 180, 10) };
    private static final float[] ANGLES = { 23.75F, -17.5F };
    private static final String COMMAND = "arce_sleep_boundary_observe";
    private static final String PROPERTY = "arce.sleepBoundaryObservation";
    private static final ThreadLocal<Boolean> INVOKING = new ThreadLocal<>();
    private static final int FIXTURE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private SleepBoundaryObservationFixture() { }

    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), Boolean.getBoolean(PROPERTY));
    }
    private static void register(CommandDispatcher<CommandSourceStack> dispatcher, boolean enabled) {
        if (enabled) { dispatcher.register(Commands.literal(COMMAND).requires(SleepBoundaryObservationFixture::console)
                .executes(context -> run(context.getSource()))); }
    }
    private static boolean console(CommandSourceStack source) {
        // withSource returns this only for the exact native underlying console identity; NULL/RCON differ.
        return source.hasPermission(4) && source.getEntity() == null && source.withSource(source.getServer()) == source;
    }
    private static boolean eligible(CommandSourceStack source) {
        return console(source) && source.getServer().isSameThread() && INVOKING.get() == null
                && source.getServer().getPlayerList().getPlayers().isEmpty();
    }

    private static int run(CommandSourceStack source) {
        require(eligible(source), "Native console, permission 4, owning thread, no players and non-reentrancy required");
        MinecraftServer server = source.getServer();
        for (UUID id : IDS) { require(server.getPlayerList().getPlayer(id) == null, "Owned UUID already registered"); }
        INVOKING.set(Boolean.TRUE);
        SleepBoundaryTrace trace = new SleepBoundaryTrace(NAMES);
        Fixture fixture = new Fixture(server, trace);
        boolean failed = false;
        try {
            fixture.acquire();
            for (int i = 0; i < NAMES.size() && !fixture.cleanupBlocked; i++) { failed |= !fixture.runCase(i); }
        } catch (RuntimeException | Error failure) {
            failed = true; trace.lifecycle(object("stage", "fixture_acquisition_or_invocation_failed", "failure", error(failure)));
        } finally {
            try { failed |= !fixture.close(); }
            finally {
                failed |= trace.isIncomplete();
                trace.finish(failed ? "HAS_FAILURE_OR_INCOMPLETE_CASE" : "NATIVE_ACTIONS_RETURNED_NOT_A_GATE");
                try { System.out.println("ARCE_SLEEP_BOUNDARY_OBSERVATION " + trace.json()); }
                finally { trace.clear(); INVOKING.remove(); }
            }
        }
        return failed ? 0 : 1;
    }

    private record Spawn(BlockPos pos, float angle, boolean forced) {
        static Spawn read(ServerPlayer player) { return new Spawn(player.getRespawnPosition(), player.getRespawnAngle(), player.isRespawnForced()); }
    }

    /** All mutable world/action ownership ends synchronously, including partial acquisition. */
    private static final class Fixture {
        private final MinecraftServer server;
        private final ServerLevel level;
        private final SleepBoundaryTrace trace;
        private final Map<BlockPos, BlockState> cells = new LinkedHashMap<>();
        private final Actor[] actors = new Actor[2];
        private final List<Object> ownedListeners = new ArrayList<>();
        private long dayTime, gameTime;
        private boolean daylight, spawning, worldCaptured, cleanupBlocked;

        Fixture(MinecraftServer server, SleepBoundaryTrace trace) {
            this.server = server; level = server.overworld(); this.trace = trace;
        }

        void acquire() {
            require(level != null && level.dimension() == Level.OVERWORLD, "Native Overworld unavailable");
            trace.lifecycle(object("stage", "qualification_limits", "unexecuted",
                    "inheritance/custom receivers; stale state; monster/item-first/secondary use; interpreted/JIT; development runtime; real TCP/client; restart/death/prior world; six hosts/Space; M1/M2",
                    "scope", "twenty first-stage cases, not Q01-Q18 completion or exact cause/enforcement proof"));
            dayTime = level.getDayTime(); gameTime = level.getGameTime();
            daylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            spawning = level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING); worldCaptured = true;
            trace.lifecycle(object("stage", "declared_fixture_chunk_preparation", "chunkX", 0, "chunkZ", 0,
                    "beforeLoaded", level.getChunkSource().getChunkNow(0, 0) != null,
                    "ticketQualification", "explicit fixture getChunkAt; native login/teleport may load; no no-ticket proof"));
            level.getChunkAt(HEAD);
            for (BlockPos cell : List.of(HEAD, FOOT, HEAD.below(), FOOT.below(), HEAD.above(), FOOT.above(),
                    new BlockPos(11, 179, 11), new BlockPos(11, 180, 11), new BlockPos(11, 181, 11),
                    new BlockPos(10, 179, 12), new BlockPos(10, 180, 12), new BlockPos(10, 181, 12))) {
                require(cell.getX() >> 4 == 0 && cell.getZ() >> 4 == 0 && !level.isOutsideBuildHeight(cell)
                        && level.getChunkSource().getChunkNow(0, 0) != null && level.getWorldBorder().isWithinBounds(cell)
                        && level.getBlockEntity(cell) == null, "Fixture inventory requires loaded in-bounds BE-free initial cells");
                cells.put(cell, level.getBlockState(cell));
                trace.lifecycle(object("stage", "captured_initial_cell", "pos", position(cell), "state", cells.get(cell).toString()));
            }
            trace.lifecycle(object("stage", "captured_world_preparation", "gameTime", gameTime, "dayTime", dayTime,
                    "doDaylightCycle", daylight, "doMobSpawning", spawning));
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            for (int i = 0; i < actors.length; i++) {
                actors[i] = new Actor(level, i); // Keep ownership before native login can partially register.
                Actor actor = actors[i];
                trace.lifecycle(object("stage", "native_placeNewPlayer", "actor", trace.token(actor.player),
                        "connection", trace.token(actor.connection), "channel", trace.token(actor.channel),
                        "loading", "native constructor/login registration and playerdata; not TCP"));
                server.getPlayerList().placeNewPlayer(actor.connection, actor.player);
                actor.loadedSpawn = Spawn.read(actor.player);
                actor.loadedDimension = actor.player.getRespawnDimension();
                trace.bind(level, i, actor.player, actor.connection);
                require(actor.player.level() == level && actor.player.getInventory().isEmpty()
                        && !actor.player.isSleeping(), "Only new disposable Overworld actors with empty inventory are supported");
            }
            trace.verify();
        }

        boolean runCase(int index) {
            boolean observing = index >= 8 || (index & 1) == 1;
            boolean night = index >= 4;
            BlockPos target = index < 8 && (index & 2) != 0 ? FOOT : HEAD;
            trace.begin(index, observing);
            trace.route(index == 10 || index == 11 || index == 12 || index == 19 ? "public native setRespawnPosition" : "public native handleUseItemOn",
                    index >= 8 ? NAMES.get(index) : "observer off/on pair");
            Control control = index >= 13 && index <= 17 ? new Control(this, index) : null;
            boolean listener = false, intervention = false, success = false;
            try {
                prepare(index, night, target);
                if (observing) { listener = true; ownedListeners.add(trace); MinecraftForge.EVENT_BUS.register(trace); }
                if (control != null) { intervention = true; ownedListeners.add(control); MinecraftForge.EVENT_BUS.register(control); }
                if (index == 18) { trace.failureIntervention(); }
                if (index == 10 || index == 19) {
                    trace.action("native_administrative_forced_setter", false,
                            () -> actors[0].player.setRespawnPosition(Level.OVERWORLD, HEAD, 6.25F, true, false));
                } else if (index == 11) {
                    trace.action("native_forced_false_same_bed_tuple_setter", false,
                            () -> actors[0].player.setRespawnPosition(Level.OVERWORLD, HEAD, 6.25F, false, false));
                } else if (index == 12) {
                    trace.action("native_null_reset", false,
                            () -> actors[0].player.setRespawnPosition(Level.OVERWORLD, null, 0, false, false));
                } else {
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(target).add(0, -0.25D, -0.5D), Direction.NORTH, target, false);
                    trace.declared(object("route", "public handleUseItemOn", "hand", "MAIN_HAND", "target", position(target),
                            "hit", SleepBoundaryTrace.vector(hit.getLocation()), "face", "NORTH", "sequence", 1));
                    trace.action("public_native_handleUseItemOn", false,
                            () -> actors[0].player.connection.handleUseItemOn(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, hit, 1)));
                }
                trace.verify();
                if ((control != null && !control.fired) || index == 18) { trace.status("INTERVENTION_NOT_REACHED_OR_NATIVE_RETURNED"); }
                else { trace.status("ACTION_RETURNED"); success = true; }
            } catch (RuntimeException | Error failure) {
                trace.status(SleepBoundaryTrace.observerIntervention(failure) ? "OBSERVER_INTERVENTION_FAILED" : "SETUP_OR_NATIVE_ACTION_FAILED");
                trace.declared(object("caseFailure", error(failure)));
            } finally {
                if (intervention) { success &= unregister(control, "control"); }
                if (listener) { success &= unregister(trace, "observer"); }
                for (Actor actor : actors) {
                    if (actor != null) {
                        try { actor.drain(trace, false); }
                        catch (RuntimeException | Error failure) { success = false; trace.cleanup(object("stage", "outbound_drain", "failure", error(failure))); }
                    }
                }
                trace.cleanup(object("stage", "case_listener_and_trace_context_teardown", "observerRegistered", listener,
                        "controlRegistered", intervention, "depthBeforeClear", trace.depth));
                trace.end();
            }
            return success;
        }

        private boolean unregister(Object owned, String label) {
            try {
                MinecraftForge.EVENT_BUS.unregister(owned);
                ownedListeners.removeIf(value -> value == owned);
                trace.cleanup(object("stage", "unregister", "kind", label, "ownedIdentity", trace.token(owned), "outcome", "RETURNED")); return true;
            } catch (RuntimeException | Error failure) {
                cleanupBlocked = true;
                trace.cleanup(object("stage", "unregister", "kind", label, "failure", error(failure))); return false;
            }
        }

        private void prepare(int index, boolean night, BlockPos target) {
            trace.verify();
            trace.declared(object("kind", "fixture_inputs_not_native_observations", "bed", "minecraft:white_bed", "facing", "EAST",
                    "head", position(HEAD), "foot", position(FOOT), "dayTime", night ? 18000 : 6000,
                    "initialHomeA", position(HOMES[0]), "angleABits", Float.floatToRawIntBits(ANGLES[0]), "forcedA", true,
                    "initialHomeB", position(HOMES[1]), "angleBBits", Float.floatToRawIntBits(ANGLES[1]), "forcedB", false,
                    "fixtureBlockFlags", FIXTURE_FLAGS, "supportsAndClearanceCount", cells.size() - 2));
            for (int i = 0; i < actors.length; i++) {
                ServerPlayer player = actors[i].player;
                if (player.isSleeping()) { trace.action("fixture_native_wake_" + i, true, () -> player.stopSleepInBed(true, false)); }
                int actorIndex = i;
                trace.action("fixture_native_home_reset_" + i, true,
                        () -> player.setRespawnPosition(Level.OVERWORLD, HOMES[actorIndex], ANGLES[actorIndex], actorIndex == 0, false));
                trace.action("fixture_empty_hands_survival_" + i, true, () -> {
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                    player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    player.setGameMode(GameType.SURVIVAL);
                });
            }
            trace.action("fixture_bed_support_clearance_and_time", true, () -> {
                for (BlockPos cell : cells.keySet()) {
                    if (cell.equals(HEAD) || cell.equals(FOOT)) { continue; }
                    level.setBlock(cell, (cell.getY() == 179 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), FIXTURE_FLAGS);
                }
                BlockState bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                        .setValue(BedBlock.OCCUPIED, index == 9);
                level.setBlock(FOOT, bed.setValue(BedBlock.PART, BedPart.FOOT), FIXTURE_FLAGS);
                level.setBlock(HEAD, bed.setValue(BedBlock.PART, BedPart.HEAD), FIXTURE_FLAGS);
                level.setDayTime(night ? 18000 : 6000); level.updateSkyBrightness();
            });
            if (index == 11) {
                trace.action("fixture_same_tuple_forced_seed", true,
                        () -> actors[0].player.setRespawnPosition(Level.OVERWORLD, HEAD, 6.25F, true, false));
            }
            for (int i = 0; i < actors.length; i++) {
                Actor actor = actors[i]; int actorIndex = i;
                trace.action("fixture_native_teleport_" + i, true,
                        () -> actor.player.teleportTo(level, actorIndex == 0 ? (index == 8 ? 2.5D : 11.5D) : 10.5D,
                                180, actorIndex == 0 ? 11.5D : 12.5D, ANGLES[actorIndex], 0));
                actor.drain(trace, true);
            }
            trace.verify();
            for (Actor actor : actors) {
                require(actor.player.isAlive() && actor.player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL
                        && actor.player.getMainHandItem().isEmpty() && actor.player.getOffhandItem().isEmpty(),
                        "Native alive/survival/empty-hand fixture setup unavailable");
            }
            require(level.getChunkSource().getChunkNow(0, 0) != null && !level.isOutsideBuildHeight(HEAD)
                    && !level.isOutsideBuildHeight(FOOT) && level.getBlockState(HEAD).is(Blocks.WHITE_BED)
                    && level.getBlockState(FOOT).is(Blocks.WHITE_BED)
                    && level.getBlockState(HEAD).getValue(BedBlock.PART) == BedPart.HEAD
                    && level.getBlockState(FOOT).getValue(BedBlock.PART) == BedPart.FOOT
                    && level.getBlockState(HEAD).getValue(BedBlock.FACING) == Direction.EAST
                    && level.getBlockState(FOOT).getValue(BedBlock.FACING) == Direction.EAST
                    && level.getBlockState(HEAD).getValue(BedBlock.OCCUPIED) == (index == 9)
                    && level.getBlockState(FOOT).getValue(BedBlock.OCCUPIED) == (index == 9), "Bed halves unavailable before public handler");
            require(level.isDay() != night && level.mayInteract(actors[0].player, target)
                    && actors[0].player.mayInteract(level, target), "Native day/night or interaction permission setup unavailable");
            require(index == 8 ? !actors[0].player.canReach(target, 1.5D) : actors[0].player.canReach(target, 1.5D),
                    "Native packet reach precondition unavailable");
        }

        boolean close() {
            boolean success = true;
            for (Object listener : List.copyOf(ownedListeners)) {
                success &= cleanup("final_exact_owned_listener_unregister", () -> MinecraftForge.EVENT_BUS.unregister(listener));
            }
            ownedListeners.clear(); trace.end();
            for (Actor actor : actors) {
                if (actor == null) { continue; }
                String actorSuffix = "_" + actor.player.getUUID();
                success &= cleanup("owned_native_wake" + actorSuffix, () -> {
                    if (actor.player.isSleeping()) { trace.action("teardown_native_owned_wake", true, () -> actor.player.stopSleepInBed(true, false)); }
                });
                success &= cleanup("owned_native_spawn_restore" + actorSuffix, () -> {
                    if (actor.loadedSpawn != null) {
                        trace.action("teardown_fixture_restore_loaded_spawn", true,
                                () -> actor.player.setRespawnPosition(actor.loadedDimension, actor.loadedSpawn.pos(),
                                        actor.loadedSpawn.angle(), actor.loadedSpawn.forced(), false));
                        require(java.util.Objects.equals(actor.player.getRespawnPosition(), actor.loadedSpawn.pos())
                                && actor.player.getRespawnDimension() == actor.loadedDimension
                                && Float.floatToRawIntBits(actor.player.getRespawnAngle()) == Float.floatToRawIntBits(actor.loadedSpawn.angle())
                                && actor.player.isRespawnForced() == actor.loadedSpawn.forced(), "Native loaded Spawn fixture fields not restored");
                    }
                });
                success &= cleanup("native_remove_logout_save" + actorSuffix, () -> {
                    var registered = server.getPlayerList().getPlayer(actor.player.getUUID());
                    boolean listed = server.getPlayerList().getPlayers().stream().anyMatch(value -> value == actor.player);
                    require(registered == null || registered == actor.player, "Cleanup will not remove an unowned UUID occupant");
                    if (registered == actor.player || listed || actor.player.serverLevel().players().stream().anyMatch(value -> value == actor.player)) {
                        trace.lifecycle(object("stage", "native_logout_save_input", "actor", trace.token(actor.player),
                                "uuid", actor.player.getUUID().toString(), "spawnGetterProjection", SleepBoundaryTrace.spawn(actor.player)));
                        server.getPlayerList().remove(actor.player);
                    }
                    require(server.getPlayerList().getPlayer(actor.player.getUUID()) != actor.player
                            && level.players().stream().noneMatch(value -> value == actor.player), "Own actor remains registered after native removal");
                });
                success &= cleanup("own_channel_close_release" + actorSuffix, () -> {
                    actor.channel.finishAndReleaseAll();
                    require(!actor.channel.isOpen() && !actor.connection.isConnected(), "Own simulated transport remains open");
                });
            }
            for (var entry : cells.entrySet()) {
                success &= cleanup("restore_cell_" + entry.getKey().toShortString(), () -> {
                    level.setBlock(entry.getKey(), entry.getValue(), FIXTURE_FLAGS);
                    require(level.getBlockState(entry.getKey()) == entry.getValue() && level.getBlockEntity(entry.getKey()) == null,
                            "Owned cell initial state not restored");
                });
            }
            if (worldCaptured) {
                success &= cleanup("restore_owned_world_time", () -> {
                    level.setDayTime(dayTime); level.updateSkyBrightness();
                });
                success &= cleanup("restore_doDaylightCycle", () -> level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylight, server));
                success &= cleanup("restore_doMobSpawning", () -> level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(spawning, server));
                success &= cleanup("verify_owned_world_time_and_rules", () -> {
                    require(level.getDayTime() == dayTime && level.getGameTime() == gameTime
                            && level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT) == daylight
                            && level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) == spawning,
                            "Owned world preparation not restored");
                });
            }
            return success;
        }
        private boolean cleanup(String stage, Runnable cleanup) {
            try { cleanup.run(); trace.lifecycle(object("stage", stage, "outcome", "RETURNED")); return true; }
            catch (RuntimeException | Error failure) { trace.lifecycle(object("stage", stage, "failure", error(failure))); return false; }
        }
    }

    private static final class Actor {
        final ServerPlayer player;
        final Connection connection = new Connection(PacketFlow.SERVERBOUND);
        final EmbeddedChannel channel = new EmbeddedChannel(connection);
        Spawn loadedSpawn;
        net.minecraft.resources.ResourceKey<Level> loadedDimension;
        Actor(ServerLevel level, int index) {
            try { player = new ServerPlayer(level.getServer(), level, new GameProfile(IDS[index], index == 0 ? "SleepObsA" : "SleepObsB")); }
            catch (RuntimeException | Error failure) {
                try { channel.finishAndReleaseAll(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }
        void drain(SleepBoundaryTrace trace, boolean acknowledge) {
            channel.runPendingTasks(); Integer actualPositionId = null;
            int packets = 0, replies = 0;
            for (; packets < 512; packets++) {
                Object packet = channel.readOutbound(); if (packet == null) { break; }
                try {
                    if (packet instanceof ClientboundPlayerPositionPacket position) { actualPositionId = position.getId(); }
                    if (packet instanceof ClientboundSystemChatPacket chat && replies++ < 16) {
                        String json = Component.Serializer.toJson(chat.content());
                        if (json.length() <= 2048) { trace.output(object("actor", trace.token(player), "systemChat", json, "overlay", chat.overlay(),
                                "phase", acknowledge ? "fixture_preparation_before_public_ack" : "post_action_case_teardown")); }
                        else { trace.incomplete("owned native system chat size cap"); }
                    }
                } finally { ReferenceCountUtil.release(packet); }
            }
            require(packets < 512 && replies <= 16, "Owned outbound drain/reply cap reached");
            if (acknowledge) {
                require(actualPositionId != null, "Actual owned outbound position ID unavailable; no forged acknowledgement");
                int id = actualPositionId;
                trace.declared(object("kind", "public_simulated_ack_not_wire", "actor", trace.token(player), "actualOwnedOutboundId", id));
                trace.action("fixture_public_accept_actual_teleport_id", true,
                        () -> player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id)));
                require(connection.isConnected(), "Native public teleport acknowledgement disconnected actor");
            }
        }
    }

    private static final class Control {
        private final Fixture fixture;
        private final int index;
        private boolean fired;
        Control(Fixture fixture, int index) { this.fixture = fixture; this.index = index; }
        @SubscribeEvent(receiveCanceled = true) public void insideGenuineSpawnEvent(PlayerSetSpawnEvent outer) {
            if (fired || outer.getEntity() != fixture.actors[0].player) { return; }
            fired = true; var trace = fixture.trace; trace.depth = 1;
            trace.declared(object("kind", "guarded_intervention_inside_genuine_event", "outerEvent", trace.token(outer),
                    "controlReceiver", trace.token(this), "controlIndex", index + 1));
            try {
                int selected = index == 14 || index == 16 ? 1 : 0;
                ServerPlayer actor = fixture.actors[selected].player;
                if (index == 13 || index == 14) {
                    trace.context = "declared_nested_native_administrative_setter";
                    trace.action(trace.context, false, () -> actor.setRespawnPosition(Level.OVERWORLD, HEAD, 41.5F, true, false));
                } else if (index == 15 || index == 16) {
                    trace.context = "declared_synthetic_bus_post";
                    PlayerSetSpawnEvent synthetic = new PlayerSetSpawnEvent(actor, Level.OVERWORLD, FOOT, true);
                    trace.action(trace.context, false, () -> {
                        boolean canceled = MinecraftForge.EVENT_BUS.post(synthetic);
                        trace.declared(object("syntheticEvent", trace.token(synthetic), "postReturnedCanceled", canceled));
                    });
                } else {
                    PlayerSetSpawnEvent same = new PlayerSetSpawnEvent(fixture.actors[0].player, Level.OVERWORLD, FOOT, true);
                    PlayerSetSpawnEvent other = new PlayerSetSpawnEvent(fixture.actors[1].player, Level.OVERWORLD, FOOT, false);
                    trace.context = "declared_direct_callback_not_bus";
                    trace.action(trace.context, false, () -> trace.observe(same));
                    Consumer<PlayerSetSpawnEvent> forwarded = trace::observe;
                    trace.declared(object("forwarderIdentity", trace.token(forwarded), "callbackReceiver", trace.token(trace)));
                    trace.context = "declared_forwarded_callback_not_bus";
                    trace.action(trace.context, false, () -> forwarded.accept(other));
                }
            } finally { trace.depth = 0; trace.context = "native_action"; }
        }
    }

    private static void require(boolean value, String reason) { if (!value) { throw new IllegalStateException(reason); } }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_console", timeoutTicks = 20)
    public static void defaultDisabledRegistrationNeedsExplicitOptIn(GameTestHelper helper) {
        CommandDispatcher<CommandSourceStack> disabled = new CommandDispatcher<>(), enabled = new CommandDispatcher<>();
        register(disabled, false); register(enabled, true);
        helper.assertTrue(disabled.getRoot().getChild(COMMAND) == null && enabled.getRoot().getChild(COMMAND) != null,
                "Disabled or enabled fixture registration differs");
        if (!Boolean.getBoolean(PROPERTY)) {
            helper.assertTrue(helper.getLevel().getServer().getCommands().getDispatcher().getRoot().getChild(COMMAND) == null,
                    "Default-disabled fixture leaked into native commands");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_console", timeoutTicks = 20)
    public static void nativeConsoleIdentityPermissionAndReentrancyAreGuarded(GameTestHelper helper) {
        var source = helper.getLevel().getServer().createCommandSourceStack();
        helper.assertTrue(console(source) && !console(source.withPermission(3))
                && !console(source.withSource(CommandSource.NULL)), "Console identity/permission guard differs");
        INVOKING.set(Boolean.TRUE);
        try { helper.assertTrue(!eligible(source), "Reentrant command was admitted"); }
        finally { INVOKING.remove(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_console", timeoutTicks = 20)
    public static void nativeConsoleCannotInvokeFromAnotherThread(GameTestHelper helper) throws InterruptedException {
        var source = helper.getLevel().getServer().createCommandSourceStack();
        var admitted = new java.util.concurrent.atomic.AtomicReference<Boolean>();
        Thread worker = new Thread(() -> admitted.set(eligible(source)), "sleep-console-gating-fixture");
        worker.start(); worker.join(1000);
        helper.assertTrue(!worker.isAlive() && Boolean.FALSE.equals(admitted.get()), "Off-thread console was admitted");
        helper.succeed();
    }
}
