package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.StationPermissionMatrix.Action;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.StationPermissionMatrix.Actor;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * ADR-046 UI-03 (A1): every cell of the expected-outcome table, through the real commands, events and
 * access predicates. Each cell also checks that no chunk was loaded in any Level and that only allowed
 * team cells changed the registry (and are reset). One {@code ARCE_STATION_PERMISSION_MATRIX} line per
 * cell carries the expected and the actual value; the test fails on any difference.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StationPermissionMatrixGameTests {
    private static final Pattern CODE = Pattern.compile(" code=(\\w+)");
    private static final Pattern CREATED = Pattern.compile("Created .* id=([0-9a-f-]{36}) ");
    /** Players stand around the warp core, within its 5-block reach. */
    private static final int[][] SPOTS = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2},
            {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {3, 1}};

    private StationPermissionMatrixGameTests() {
    }

    @GameTest(template = "empty", batch = "station_permission_matrix", timeoutTicks = 900)
    public static void everyCellOfTheStationAuthorityMatrixMatchesTheTable(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper);
        boolean scheduled = false;
        try {
            fixture.setUp();
            scheduled = true;
            // The players' chunk tickets must settle first, so that a cell's chunk delta is its own.
            awaitStableChunks(helper, fixture, 0, -1, () -> {
                try (AuditLogCapture audit = new AuditLogCapture()) {
                    fixture.run(audit);
                } finally {
                    fixture.close();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                fixture.close();
            }
        }
    }

    /** Runs {@code then} once the loaded-chunk counts of every Level were unchanged for 40 ticks. */
    private static void awaitStableChunks(GameTestHelper helper, Fixture fixture, int stableTicks, int lastTotal,
                                          Runnable then) {
        helper.runAfterDelay(1, () -> {
            int total = fixture.loadedChunks().values().stream().mapToInt(Integer::intValue).sum();
            int stable = total == lastTotal ? stableTicks + 1 : 0;
            if (stable >= 40) {
                then.run();
            } else {
                awaitStableChunks(helper, fixture, stable, total, then);
            }
        });
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final ServerLevel space;
        final StationRegistrySavedData data;
        final StationPlatformGenerator platforms = new StationPlatformGenerator();
        final StationAccessService access = new StationAccessService();
        final Map<Actor, UUID> ids = new EnumMap<>(Actor.class);
        final Map<Actor, ServerPlayer> players = new EnumMap<>(Actor.class);
        final Map<Actor, List<String>> replies = new EnumMap<>(Actor.class);
        final List<String> console = new ArrayList<>();
        final List<ServerPlayer> online = new ArrayList<>();
        final List<StationState> created = new ArrayList<>();
        final UUID candidate = UUID.randomUUID();
        final UUID victim = UUID.randomUUID();
        final UUID offlineMember = UUID.randomUUID();
        StationState station;
        BlockPos core;
        BlockPos support;
        FakePlayer fake;
        /** Full chunks inside the region of the station an operator create/delete cell made; -1 if none. */
        int createdCellFullChunks = -1;
        List<ServerLevel> levels = new ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            this.server = helper.getLevel().getServer();
            this.space = server.getLevel(CelestialIds.SPACE_LEVEL);
            this.data = StationRegistrySavedData.get(server);
            server.getAllLevels().forEach(levels::add);
            for (Actor actor : Actor.values()) {
                ids.put(actor, UUID.randomUUID());
            }
            for (Actor owner : List.of(Actor.EXECUTE_AS_OWNER, Actor.SILENT_OWNER, Actor.FAKE_PLAYER_OWNER,
                    Actor.STALE_OWNER)) {
                ids.put(owner, ids.get(Actor.OWNER));
            }
        }

        void setUp() {
            helper.assertTrue(space != null && data.updatesAvailable(), "Station authority or Space is unavailable");
            StationCreationService creation = new StationCreationService(platforms, body -> true);
            station = creation.create(server, ids.get(Actor.PREVIOUS_OWNER), "Matrix", CelestialIds.EARTH_ID, false)
                    .station().orElseThrow();
            created.add(station);
            created.add(creation.create(server, ids.get(Actor.OTHER_STATION_OWNER), "Matrix other",
                    CelestialIds.EARTH_ID, false).station().orElseThrow());
            UUID id = station.stationId();
            data.transferOwnership(id, ids.get(Actor.OWNER));
            data.addMember(id, ids.get(Actor.MEMBER));
            data.invite(id, ids.get(Actor.INVITEE));
            data.addMember(id, ids.get(Actor.REMOVED_MEMBER));
            data.removeMember(id, ids.get(Actor.REMOVED_MEMBER));
            data.addMember(id, victim);
            data.addMember(id, offlineMember);
            for (Actor operator : List.of(Actor.OPERATOR, Actor.OPERATOR_OUTSIDE, Actor.DEOPPED_OPERATOR)) {
                server.getPlayerList().getOps().add(new ServerOpListEntry(profile(operator), 4, false));
            }
            server.getPlayerList().getOps().remove(profile(Actor.DEOPPED_OPERATOR));
            data.flush(server);
            station = data.find(id).orElseThrow();
            helper.assertTrue(station.members().contains(ids.get(Actor.PREVIOUS_OWNER)),
                    "The previous owner must remain a member after the transfer");

            BlockPos pad = new BlockPos(station.landingPad().x(), StationLimits.LANDING_Y, station.landingPad().z());
            core = pad;
            space.getChunkAt(core); // Test setup only.
            space.setBlockAndUpdate(core, ModBlocks.WARP_CORE.get().defaultBlockState());
            int spot = 0;
            // The stale owner joins first and logs out; the live owner then joins as a new object.
            join(Actor.STALE_OWNER, "mxStaleOwner", pad, SPOTS[spot++], false);
            server.getPlayerList().remove(players.get(Actor.STALE_OWNER));
            online.remove(players.get(Actor.STALE_OWNER));
            for (Actor actor : List.of(Actor.OWNER, Actor.MEMBER, Actor.INVITEE, Actor.OUTSIDER, Actor.OPERATOR,
                    Actor.OTHER_STATION_OWNER, Actor.REMOVED_MEMBER, Actor.PREVIOUS_OWNER, Actor.DEOPPED_OPERATOR)) {
                boolean ops = actor == Actor.OPERATOR || actor == Actor.DEOPPED_OPERATOR;
                join(actor, "mx" + actor.ordinal() + actor.name().substring(0, Math.min(8, actor.name().length())),
                        pad, SPOTS[spot++], ops);
            }
            joinExtra(candidate, "mxCandidate", pad.offset(SPOTS[spot][0], 0, SPOTS[spot++][1]));
            joinExtra(victim, "mxVictim", pad.offset(SPOTS[spot][0], 0, SPOTS[spot][1]));
            // Outside every station: the gap west of this cell, on a support block.
            BlockPos outside = new BlockPos(station.region().minimumX() - 32, StationLimits.LANDING_Y, pad.getZ());
            support = outside.below();
            space.getChunkAt(support); // Test setup only.
            space.setBlockAndUpdate(support, Blocks.STONE.defaultBlockState());
            helper.assertTrue(data.findAt(outside.getX(), outside.getZ()).isEmpty(), "The gap is inside a station");
            join(Actor.OPERATOR_OUTSIDE, "mxOpOutside", outside, new int[]{0, 0}, true);
            fake = new FakePlayer(space, new GameProfile(ids.get(Actor.OWNER), "mxFakeOwner"));
            fake.setPos(pad.getX() + 1.5D, pad.getY(), pad.getZ() + 1.5D);
            players.put(Actor.FAKE_PLAYER_OWNER, fake);
            players.put(Actor.SILENT_OWNER, players.get(Actor.OWNER));
            players.put(Actor.EXECUTE_AS_OWNER, players.get(Actor.OWNER));
        }

        void run(AuditLogCapture audit) {
            List<String> mismatches = new ArrayList<>();
            int executed = 0;
            int notApplicable = 0;
            for (Action action : Action.values()) {
                for (Actor actor : Actor.values()) {
                    String expected = StationPermissionMatrix.expected(action, actor);
                    if (expected.equals(StationPermissionMatrix.NOT_APPLICABLE)) {
                        notApplicable++;
                        log(action, actor, expected, expected, "no", Map.of(), -1);
                        continue;
                    }
                    executed++;
                    CompoundTag before = data.save(new CompoundTag());
                    Map<ResourceKey<Level>, Integer> chunks = loadedChunks();
                    audit.clear();
                    String actual = observe(action, actor, audit);
                    CompoundTag after = data.save(new CompoundTag());
                    Map<ResourceKey<Level>, Integer> delta = delta(chunks, loadedChunks());
                    boolean changed = !before.equals(after);
                    boolean mutates = action == Action.ADMIN_CREATE_DELETE || StationPermissionMatrix.ALLOWED
                            .equals(actual) && (action.kind == StationPermissionMatrix.Kind.TEAM
                            || action.kind == StationPermissionMatrix.Kind.ANSWER);
                    reset(action, actual);
                    int cellFull = createdCellFullChunks;
                    String problem = !expected.equals(actual) ? "expected " + expected
                            : changed && !mutates ? "the registry changed"
                            : !chunksAllowed(action, delta) ? "chunks changed " + delta
                            : mutates && action != Action.ADMIN_CREATE_DELETE
                            && !before.equals(data.save(new CompoundTag())) ? "the reset did not restore the registry"
                            : null;
                    if (problem != null) {
                        mismatches.add(action + "/" + actor + ": " + problem + ", actual " + actual);
                    }
                    log(action, actor, expected, problem == null ? actual : actual + " (" + problem + ")",
                            changed ? "yes" + (mutates ? " (reset)" : "") : "no", delta,
                            action == Action.ADMIN_CREATE_DELETE ? cellFull : -1);
                }
            }
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_STATION_PERMISSION_MATRIX_SUMMARY table={} cells={} executed={} not_applicable={} pass={} fail={}",
                    StationPermissionMatrix.TABLE_REVISION, Action.values().length * Actor.values().length, executed,
                    notApplicable, Action.values().length * Actor.values().length - mismatches.size(),
                    mismatches.size());
            helper.assertTrue(mismatches.isEmpty(), mismatches.size() + " cells differ: " + mismatches);
        }

        private String observe(Action action, Actor actor, AuditLogCapture audit) {
            return switch (action.kind) {
                case BUILD -> {
                    BlockPos target = core.offset(4, -1, 4);
                    var event = new BlockEvent.BreakEvent(space, target, space.getBlockState(target),
                            players.get(actor));
                    yield MinecraftForge.EVENT_BUS.post(event) ? "CANCELLED" : StationPermissionMatrix.ALLOWED;
                }
                case VISIT -> {
                    ServerPlayer player = players.get(actor);
                    yield access.allowed(data.find(station.stationId()).orElseThrow(), player.getUUID(),
                            player.hasPermissions(2), StationAccessAction.VISIT)
                            ? StationPermissionMatrix.ALLOWED : "UNAUTHORIZED";
                }
                default -> command(action, actor, audit);
            };
        }

        private String command(Action action, Actor actor, AuditLogCapture audit) {
            if (action == Action.WARP_REQUEST && players.get(actor) != null) {
                players.get(actor).lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(core));
            }
            String command = actor == Actor.EXECUTE_AS_OWNER
                    ? "execute as " + ids.get(Actor.OWNER) + " at " + ids.get(Actor.OWNER) + " run " + text(action)
                    : text(action);
            CommandSourceStack source = source(actor);
            List<String> sink = sink(actor);
            int mark = sink.size();
            var parse = server.getCommands().getDispatcher().parse(command, source);
            if (parse.getReader().canRead()) {
                server.getCommands().performPrefixedCommand(source, command);
                return StationPermissionMatrix.UNKNOWN_COMMAND;
            }
            if (action == Action.ADMIN_CREATE_DELETE && actor != Actor.OPERATOR && actor != Actor.CONSOLE) {
                return StationPermissionMatrix.REACHED; // Parsed with the operator requirement; not executed.
            }
            int result = server.getCommands().performPrefixedCommand(source, command);
            List<String> said = List.copyOf(sink.subList(mark, sink.size()));
            if (said.stream().anyMatch(line -> line.contains("A player is required"))) {
                return StationPermissionMatrix.NOT_PLAYER;
            }
            UUID actorId = ids.get(actor);
            return switch (action.kind) {
                case LOCAL -> audit.lines().stream()
                        .filter(line -> line.contains(" actor=" + actorId))
                        .map(CODE::matcher).filter(Matcher::find).map(matcher -> matcher.group(1))
                        .reduce((first, second) -> second).orElse("NO_AUDIT result=" + result + " " + said);
                case STATUS -> said.isEmpty() ? (result == 1 ? action.allowed : StationPermissionMatrix.REJECTED)
                        : said.get(0).startsWith("Warp status;") ? action.allowed : describe(said.get(0));
                case ENVIRONMENT -> said.isEmpty()
                        ? (result == 1 ? StationPermissionMatrix.ALLOWED : StationPermissionMatrix.REJECTED)
                        : said.get(0).contains("id=" + station.stationId()) ? action.allowed
                        : said.get(0).contains("not a member of") ? "ANONYMOUS"
                        : said.get(0).startsWith("Not inside a station region") ? "NOT_IN_STATION" : said.get(0);
                case TEAM, ANSWER -> access(audit, actorId, action, said);
                case LIST -> said.isEmpty() ? (result >= 1 ? action.allowed : "HIDDEN")
                        : said.stream().anyMatch(line -> line.contains(station.stationId().toString()))
                        ? action.allowed : "HIDDEN";
                case ADMIN -> admin(action, result, said, source);
                default -> throw new IllegalStateException(action.toString());
            };
        }

        private String access(AuditLogCapture audit, UUID actorId, Action action, List<String> said) {
            for (String line : audit.lines()) {
                if (line.startsWith("ARCE_STATION_ACCESS ") && line.contains(" actor=" + actorId + " ")) {
                    return line.endsWith("allowed=true") ? StationPermissionMatrix.ALLOWED : "UNAUTHORIZED";
                }
            }
            if (said.stream().anyMatch(line -> line.contains("Station invitation is missing"))) {
                return "INVITATION_MISSING";
            }
            return said.isEmpty() ? StationPermissionMatrix.REJECTED : "REJECTED " + said;
        }

        private String admin(Action action, int result, List<String> said, CommandSourceStack source) {
            if (action != Action.ADMIN_CREATE_DELETE) {
                return result > 0 ? StationPermissionMatrix.REACHED : "FAILED " + said;
            }
            Matcher matcher = said.isEmpty() ? null : CREATED.matcher(said.get(0));
            if (result != 1 || matcher == null || !matcher.find()) {
                return "CREATE_FAILED " + said;
            }
            createdCellFullChunks = data.find(UUID.fromString(matcher.group(1)))
                    .map(made -> fullChunks(made.region())).orElse(-1);
            int deleted = server.getCommands().performPrefixedCommand(source,
                    "arce station admin delete " + matcher.group(1) + " confirm");
            return deleted == 1 ? StationPermissionMatrix.REACHED : "DELETE_FAILED";
        }

        private static String describe(String reply) {
            for (StationManagementCode code : StationManagementCode.values()) {
                if (reply.contains(code.description())) {
                    return code.name();
                }
            }
            return reply;
        }

        private String text(Action action) {
            UUID id = station.stationId();
            return switch (action) {
                case EXPAND_CONFIRM -> "arce station expand confirm " + id;
                case EXPAND -> "arce station expand";
                case GRAVITY -> "arce station gravity " + station.environment().gravityMilli() / 10;
                case WARP_CONFIRM -> "arce station warp confirm " + id;
                case WARP_CANCEL -> "arce station warp cancel";
                case WARP_REQUEST -> "arce station warp " + CelestialIds.MOON_ID;
                case WARP_STATUS -> "arce station warp status";
                case ENVIRONMENT -> "arce station environment";
                case INVITE -> "arce station invite " + id + " mxCandidate";
                case REMOVE -> "arce station remove " + id + " mxVictim";
                case REMOVE_UUID -> "arce station remove " + id + " uuid " + offlineMember;
                case ACCEPT -> "arce station accept " + id;
                case DECLINE -> "arce station decline " + id;
                case LIST -> "arce station list";
                case ADMIN_INSPECT -> "arce station admin inspect " + id;
                case ADMIN_DUMP -> "arce station admin dump";
                case ADMIN_RECOVER_RESERVATIONS -> "arce station admin recover-reservations";
                case ADMIN_WARP -> "arce station admin warp " + id;
                case ADMIN_ELEVATOR_CHECK -> "arce station admin elevator check " + id + " " + CelestialIds.EARTH_ID
                        + " 0 0";
                case ADMIN_TRANSFER -> "arce station admin transfer " + id + " " + ids.get(Actor.OWNER);
                case ADMIN_CREATE_DELETE -> "arce station admin create " + UUID.randomUUID() + " "
                        + CelestialIds.EARTH_ID + " Matrix temporary";
                case BUILD, VISIT -> throw new IllegalStateException(action.toString());
            };
        }

        private CommandSourceStack source(Actor actor) {
            return switch (actor) {
                case CONSOLE, EXECUTE_AS_OWNER -> server.createCommandSourceStack()
                        .withSource(ConnectedTestPlayers.capture(console));
                case SILENT_OWNER -> players.get(Actor.OWNER).createCommandSourceStack().withSuppressedOutput();
                default -> players.get(actor).createCommandSourceStack();
            };
        }

        private List<String> sink(Actor actor) {
            return switch (actor) {
                case CONSOLE, EXECUTE_AS_OWNER -> console;
                case SILENT_OWNER, FAKE_PLAYER_OWNER -> new ArrayList<>();
                default -> replies.get(actor);
            };
        }

        /** Restores the registry after an allowed team cell, so every cell starts from the same state. */
        private void reset(Action action, String actual) {
            if (!StationPermissionMatrix.ALLOWED.equals(actual)) {
                return;
            }
            UUID id = station.stationId();
            switch (action) {
                case INVITE -> data.declineInvitation(id, candidate);
                case REMOVE -> data.addMember(id, victim);
                case REMOVE_UUID -> data.addMember(id, offlineMember);
                case ACCEPT -> {
                    data.removeMember(id, ids.get(Actor.INVITEE));
                    data.invite(id, ids.get(Actor.INVITEE));
                }
                case DECLINE -> data.invite(id, ids.get(Actor.INVITEE));
                default -> {
                    return;
                }
            }
            data.flush(server);
        }

        /**
         * Every other cell creates no chunk holder in any Level. An operator create/delete loads only the
         * platform chunks of its cell (at most 2 x 2 full chunks in the region) and nothing outside Space.
         * The Space holder count is not compared for it: vanilla adds a light ticket around every chunk it
         * loads, which creates temporary holders for neighbouring chunks.
         */
        private boolean chunksAllowed(Action action, Map<ResourceKey<Level>, Integer> delta) {
            if (action != Action.ADMIN_CREATE_DELETE || createdCellFullChunks < 0) {
                return delta.isEmpty();
            }
            int full = createdCellFullChunks;
            createdCellFullChunks = -1;
            return delta.keySet().stream().allMatch(CelestialIds.SPACE_LEVEL::equals) && full >= 1 && full <= 4;
        }

        private int fullChunks(io.github.sunthemoon.advancedrocketrycommunity.station.model.StationRegion region) {
            int full = 0;
            for (int x = region.minimumX() >> 4; x <= region.maximumX() >> 4; x++) {
                for (int z = region.minimumZ() >> 4; z <= region.maximumZ() >> 4; z++) {
                    if (space.getChunkSource().getChunkNow(x, z) != null) {
                        full++;
                    }
                }
            }
            return full;
        }

        private void log(Action action, Actor actor, String expected, String actual, String changed,
                         Map<ResourceKey<Level>, Integer> delta, int cellFullChunks) {
            AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_STATION_PERMISSION_MATRIX table={} action={} actor={} precondition=\"{}\" expected={}"
                            + " actual={} result={} registry_changed={} chunk_delta={}{}",
                    StationPermissionMatrix.TABLE_REVISION, action, actor, action.precondition, expected, actual,
                    expected.equals(actual) ? "PASS" : "FAIL", changed, delta.isEmpty() ? "none" : delta,
                    cellFullChunks < 0 ? "" : " cell_full_chunks=" + cellFullChunks);
        }

        private void join(Actor actor, String name, BlockPos pad, int[] spot, boolean ops) {
            List<String> sink = new ArrayList<>();
            replies.put(actor, sink);
            BlockPos position = pad.offset(spot[0], 0, spot[1]);
            space.getChunkAt(position); // Test setup only.
            ServerPlayer player = ConnectedTestPlayers.join(server, ids.get(actor), name, space, position, sink, ops);
            players.put(actor, player);
            online.add(player);
        }

        private void joinExtra(UUID id, String name, BlockPos position) {
            space.getChunkAt(position); // Test setup only.
            online.add(ConnectedTestPlayers.join(server, id, name, space, position, new ArrayList<>()));
        }

        private GameProfile profile(Actor actor) {
            return new GameProfile(ids.get(actor), "mx" + actor.name().toLowerCase(java.util.Locale.ROOT));
        }

        private Map<ResourceKey<Level>, Integer> loadedChunks() {
            Map<ResourceKey<Level>, Integer> counts = new LinkedHashMap<>();
            levels.forEach(level -> counts.put(level.dimension(), level.getChunkSource().getLoadedChunksCount()));
            return counts;
        }

        private static Map<ResourceKey<Level>, Integer> delta(Map<ResourceKey<Level>, Integer> before,
                                                               Map<ResourceKey<Level>, Integer> after) {
            Map<ResourceKey<Level>, Integer> delta = new LinkedHashMap<>();
            after.forEach((level, count) -> {
                int change = count - before.getOrDefault(level, 0);
                if (change != 0) {
                    delta.put(level, change);
                }
            });
            return delta;
        }

        void close() {
            online.forEach(server.getPlayerList()::remove);
            online.clear();
            for (Actor operator : List.of(Actor.OPERATOR, Actor.OPERATOR_OUTSIDE, Actor.DEOPPED_OPERATOR)) {
                server.getPlayerList().getOps().remove(profile(operator));
            }
            if (core != null) {
                space.setBlockAndUpdate(core, Blocks.AIR.defaultBlockState());
            }
            if (support != null) {
                space.setBlockAndUpdate(support, Blocks.AIR.defaultBlockState());
            }
            for (StationState state : created) {
                data.delete(state.stationId());
                platforms.removeTemplate(space, state.cell());
            }
            data.flush(server);
        }
    }
}
