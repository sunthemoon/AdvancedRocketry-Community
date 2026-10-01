package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldMenu;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityTrust;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.IntentKind;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameAction;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ProtectedZone;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-058 on a running server: consent on planets, the station rules, refusals and the switch. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GravityFieldGameTests {
    private static final String PLANET = "endgame_gravity_planet";
    private static final double BASE = 0.08D;
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create("arce_gametest_gravity",
            Comparator.comparing(UUID::toString));
    private static volatile UUID veto;
    private static final String[] LAST_ERROR = {""};

    static {
        MinecraftForge.EVENT_BUS.addListener(GravityFieldGameTests::onEffect);
    }

    private GravityFieldGameTests() {
    }

    private static void onEffect(EndgameEffectEvent event) {
        if (event.effect() == EndgameEffect.ENTITY_GRAVITY && event.ownerId().equals(veto)) {
            event.setCanceled(true);
        }
    }

    @AfterBatch(batch = PLANET)
    public static void restoreSwitch(ServerLevel level) {
        CommonConfig.ENDGAME_GRAVITY_FIELD.set(true);
        veto = null;
    }

    @GameTest(template = "empty", batch = PLANET, timeoutTicks = 1200)
    public static void onAPlanetOnlyTheOwnerAndTrustingPlayersFeelTheField(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        // High enough that the 17-block box stays inside the build height (chain step 2).
        BlockPos pos = helper.absolutePos(new BlockPos(1, 14, 1));
        level.setBlockAndUpdate(pos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
        GravityFieldBlockEntity device = (GravityFieldBlockEntity) level.getBlockEntity(pos);
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer owner = join(server, joined, "fieldOwner", level, pos.east(2));
        List<String> friendReplies = new ArrayList<>();
        ServerPlayer friend = ConnectedTestPlayers.join(server, UUID.randomUUID(), "fieldFriend", level, pos.west(2),
                friendReplies);
        joined.add(friend);
        ServerPlayer stranger = join(server, joined, "fieldStranger", level, pos.north(2));
        helper.assertTrue(device.assignOwner(owner.getUUID()), "The owner was not assigned");
        device.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
        GravityFieldMenu menu = new GravityFieldMenu(3, owner.getInventory(), device);
        String zone = "gt_field_" + owner.getUUID().toString().substring(0, 8);
        // ADR-054 section 12: the field adds no chunk ticket of any type (review C11R-L7).
        Map<String, Integer> tickets = TicketCounts.near(level, new ChunkPos(pos), 2);
        helper.startSequence()
                .thenExecute(() -> {
                    helper.assertTrue(menu.clickMenuButton(owner, GravityFieldMenu.BUTTON_START), "Start refused");
                    helper.assertTrue(command(server, friend, "arce endgame field trust @a[name=fieldOwner]") == 1,
                            "The friend could not trust the owner: " + friendReplies + " " + LAST_ERROR[0]);
                    // A forked /execute reports its fork count, so the stranger's own list is what must not change.
                    command(server, server.createCommandSourceStack(), "execute as " + stranger.getUUID()
                            + " run arce endgame field trust @a[name=fieldOwner]");
                    helper.assertTrue(EndgameRuntime.devices().orElseThrow().trust().list(stranger).isEmpty(),
                            "/execute consented for another player");
                })
                .thenWaitUntil(() -> helper.assertTrue(device.active(), "Not active: " + device.describe()))
                .thenExecute(() -> {
                    expectGravity(helper, owner, BASE * 0.5D, "the owner");
                    expectGravity(helper, friend, BASE * 0.5D, "a player who trusts the owner");
                    expectGravity(helper, stranger, BASE, "a stranger keeps the Level gravity");
                    helper.assertTrue(command(server, friend, "arce endgame field untrust @a[name=fieldOwner]") == 1,
                            "Untrust failed");
                    expectGravity(helper, friend, BASE, "an untrusting player");
                    command(server, friend, "arce endgame field trust @a[name=fieldOwner]");
                    // The list is the player's own persisted data: it survives a respawn.
                    ServerPlayer respawned = new ServerPlayer(server, level, new GameProfile(friend.getUUID(),
                            "fieldFriend"));
                    respawned.restoreFrom(friend, false);
                    helper.assertTrue(respawned.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                            .getCompound(GravityTrust.KEY).getList(GravityTrust.OWNERS, 11).size() == 1
                            && respawned.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                            .getCompound(GravityTrust.KEY).getInt("schema_version") == GravityTrust.SCHEMA_VERSION,
                            "The versioned trust list did not survive a respawn");
                    // Out of energy: the field drops in the same tick.
                    device.energy().set(0);
                })
                .thenExecuteAfter(1, () -> {
                    helper.assertTrue(!device.active() && device.status() == EndgameCode.INSUFFICIENT_ENERGY,
                            "An unpaid field stayed active: " + device.describe());
                    expectGravity(helper, owner, BASE, "an inactive field");
                    device.energy().set(GravityFieldBlockEntity.ENERGY_CAPACITY);
                })
                .thenExecuteAfter(11, () -> helper.assertTrue(menu.clickMenuButton(owner,
                        GravityFieldMenu.BUTTON_RADIUS_DOWN), "A settings change was refused"))
                .thenWaitUntil(() -> helper.assertTrue(device.active() && device.radius() == 7,
                        "The settings change did not re-activate at once: " + device.describe()))
                .thenExecute(() -> {
                    BlockPos min = pos.offset(-7, 0, -7);
                    BlockPos max = pos.offset(7, 0, 7);
                    helper.assertTrue(service().barrier(root -> root.addZone(ProtectedZone.of(zone,
                            level.dimension().location(), min.getX(), min.getZ(), max.getX(), max.getZ(), List.of()),
                            256)) == EndgameCode.OK, "Zone not added");
                })
                .thenExecuteAfter(11, () -> helper.assertTrue(menu.clickMenuButton(owner,
                        GravityFieldMenu.BUTTON_RADIUS_UP), "A settings change was refused"))
                .thenWaitUntil(() -> helper.assertTrue(device.status() == EndgameCode.TARGET_PROTECTED
                        && !device.active(), "A zone did not refuse the field: " + device.describe()))
                .thenExecute(() -> {
                    service().barrier(root -> root.removeZone(zone));
                    veto = owner.getUUID();
                })
                .thenExecuteAfter(11, () -> helper.assertTrue(menu.clickMenuButton(owner,
                        GravityFieldMenu.BUTTON_RADIUS_DOWN), "A settings change was refused"))
                .thenWaitUntil(() -> helper.assertTrue(device.status() == EndgameCode.TARGET_PROTECTED,
                        "The API veto did not refuse the field"))
                .thenExecute(() -> veto = null)
                .thenExecuteAfter(11, () -> helper.assertTrue(menu.clickMenuButton(owner,
                        GravityFieldMenu.BUTTON_RADIUS_UP), "A settings change was refused"))
                .thenWaitUntil(() -> helper.assertTrue(device.active(), "Not active again"))
                .thenExecute(() -> {
                    expectGravity(helper, owner, BASE * 0.5D, "the owner again");
                    CommonConfig.ENDGAME_GRAVITY_FIELD.set(false);
                })
                .thenExecuteAfter(2, () -> {
                    expectGravity(helper, owner, BASE, "a disabled system restores gravity");
                    helper.assertTrue(!device.active(), "A disabled field stayed active");
                    CommonConfig.ENDGAME_GRAVITY_FIELD.set(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(device.active(), "Not active after the switch"))
                .thenExecute(() -> {
                    helper.assertTrue(TicketCounts.near(level, new ChunkPos(pos), 2).equals(tickets),
                            "The field changed the tickets: " + tickets + " -> "
                                    + TicketCounts.near(level, new ChunkPos(pos), 2));
                    device.onChunkUnloaded();
                    expectGravity(helper, owner, BASE, "an unloaded field");
                    joined.forEach(player -> server.getPlayerList().remove(player));
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                })
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "endgame_gravity_station", timeoutTicks = 900)
    public static void onAStationOnlyTheStationOwnersFieldRunsCappedForEveryone(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer stationOwner = join(server, joined, "fieldStationOwner", helper.getLevel(),
                helper.absolutePos(BlockPos.ZERO));
        ServerPlayer member = join(server, joined, "fieldMember", helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        ServerPlayer visitor = join(server, joined, "fieldVisitor", helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        UUID stationId = UUID.randomUUID();
        stations.reserve(stationId, stationOwner.getUUID(), "Field fixture", CelestialIds.EARTH_ID,
                helper.getLevel().getGameTime());
        StationState station = stations.commit(stationId);
        stations.invite(stationId, member.getUUID());
        stations.acceptInvitation(stationId, member.getUUID());
        BlockPos pos = new BlockPos(station.landingPad().x() + 4, StationLimits.LANDING_Y + 4,
                station.landingPad().z() + 4);
        ChunkPos chunk = new ChunkPos(pos);
        space.getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
        space.getChunkAt(pos); // Test setup only.
        space.setBlockAndUpdate(pos, ModBlocks.GRAVITY_FIELD_CONTROLLER.get().defaultBlockState());
        GravityFieldBlockEntity device = (GravityFieldBlockEntity) space.getBlockEntity(pos);
        helper.assertTrue(device.assignOwner(member.getUUID()), "Owner not assigned");
        // Multiplier 200 through a valid root: the station cap must bring it to 1.00 g.
        CompoundTag root = device.saveWithoutMetadata();
        root.getCompound("endgame").putInt("multiplier", 200);
        root.getCompound("endgame").putInt("energy", GravityFieldBlockEntity.ENERGY_CAPACITY);
        root.getCompound("endgame").putBoolean("running", true);
        device.load(root);
        for (ServerPlayer player : joined) {
            player.teleportTo(space, pos.getX() + 2.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        }
        EndgameIntentGuard.Target target = new EndgameIntentGuard.Target(space.dimension(), pos,
                device.deviceId().orElseThrow());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(device.status() == EndgameCode.STATION_OWNER_REQUIRED,
                        "A member's field ran on the station: " + device.describe()))
                .thenExecute(() -> {
                    EndgameCode code = EndgameIntentGuard.check(member, target, new EndgameIntentGuard.Intent(
                            EndgameAction.OPERATE, IntentKind.STATE, true, true)).code();
                    helper.assertTrue(code == EndgameCode.UNAUTHORIZED, "A member passed MANAGE_STATION: " + code);
                    helper.assertTrue(device.assignOwner(stationOwner.getUUID()), "Owner not reassigned");
                    GravityFieldMenu menu = new GravityFieldMenu(4, stationOwner.getInventory(), device);
                    helper.assertTrue(menu.clickMenuButton(stationOwner, GravityFieldMenu.BUTTON_RADIUS_DOWN),
                            "The station owner's change was refused");
                })
                .thenWaitUntil(() -> helper.assertTrue(device.active(), "Not active: " + device.describe()))
                .thenExecute(() -> {
                    expectGravity(helper, stationOwner, BASE, "the station owner (capped at 1.00 g)");
                    expectGravity(helper, visitor, BASE, "every player in a station field (capped)");
                    expectGravity(helper, member, BASE, "a member");
                    helper.assertTrue(device.activeBox().orElseThrow().minX() >= station.region().minimumX(),
                            "The box was not clipped to the region");
                    stations.transferOwnership(stationId, visitor.getUUID());
                })
                .thenWaitUntil(() -> helper.assertTrue(device.status() == EndgameCode.STATION_OWNER_REQUIRED
                        && !device.active(), "A transfer did not deactivate the previous owner's field"))
                .thenExecute(() -> {
                    joined.forEach(player -> server.getPlayerList().remove(player));
                    space.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, stationId);
                    stations.delete(stationId);
                    stations.flush(server);
                })
                .thenSucceed();
    }

    private static ServerPlayer join(MinecraftServer server, List<ServerPlayer> joined, String name, ServerLevel level,
                                     BlockPos pos) {
        ServerPlayer player = ConnectedTestPlayers.join(server, UUID.randomUUID(), name, level, pos, new ArrayList<>());
        joined.add(player);
        return player;
    }

    /** Runs the production-registered gravity controller for one player, as a living tick does. */
    private static void expectGravity(GameTestHelper helper, ServerPlayer player, double expected, String who) {
        MinecraftForge.EVENT_BUS.post(new LivingEvent.LivingTickEvent(player));
        double actual = player.getAttribute(ForgeMod.ENTITY_GRAVITY.get()).getValue();
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-9D, who + ": expected " + expected + " but got "
                + actual);
    }

    /** Review C11R-L6: the unversioned C11 development list still reads; the next change writes schema 1. */
    @GameTest(template = "empty", batch = "endgame_gravity_trust_format", timeoutTicks = 40)
    public static void anUnversionedTrustListIsReadAndRewrittenWithItsSchema(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        ServerPlayer player = ConnectedTestPlayers.join(server, UUID.randomUUID(), "trustFormat", level,
                helper.absolutePos(BlockPos.ZERO), new ArrayList<>());
        try {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            ListTag unversioned = new ListTag();
            unversioned.add(NbtUtils.createUUID(first));
            CompoundTag persisted = new CompoundTag();
            persisted.put(GravityTrust.KEY, unversioned);
            player.getPersistentData().put(Player.PERSISTED_NBT_TAG, persisted);
            GravityTrust trust = new GravityTrust();
            helper.assertTrue(trust.list(player).equals(List.of(first)) && trust.trusted(player).contains(first),
                    "The unversioned list was not read");
            helper.assertTrue(trust.trust(player, second) == EndgameCode.OK, "A trust was refused");
            CompoundTag stored = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG)
                    .getCompound(GravityTrust.KEY);
            helper.assertTrue(stored.getInt("schema_version") == GravityTrust.SCHEMA_VERSION
                            && stored.getList(GravityTrust.OWNERS, 11).size() == 2
                            && new GravityTrust().list(player).equals(List.of(first, second)),
                    "The list was not rewritten with its schema: " + stored);
        } finally {
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * The player's own source; the permission is raised only so the test can name the owner with a selector (the
     * GameTest server has no profile cache for names). The source and its output stay the player's own.
     */
    private static int command(MinecraftServer server, ServerPlayer player, String command) {
        return command(server, player.createCommandSourceStack().withPermission(2), command);
    }

    private static int command(MinecraftServer server, net.minecraft.commands.CommandSourceStack source,
                               String command) {
        try {
            return server.getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException exception) {
            LAST_ERROR[0] = exception.getMessage();
            return 0;
        }
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService service() {
        return EndgameRuntime.operational().orElseThrow();
    }
}
