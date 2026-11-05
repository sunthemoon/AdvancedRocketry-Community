package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.ParseResults;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.*;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketSeatRosterGameTests.*;

/** Lifecycle-owned asynchronous fixtures; each case finishes with a positive removal assertion. */
final class RocketStaleSeatFixture implements AutoCloseable {
    enum Case { DEATH, TELEPORT, SPECTATOR, DISMOUNT, PEARL, LEGACY, OFFLINE, INTERNAL, REJOIN_PENDING,
        REJOIN_ACTIVE, FROZEN, ACTIVE_TRANSFER, IN_FLIGHT, OTHER_VEHICLE, RECONNECT_PENDING, RETURNED, HELD, COMMAND }
    private static final Set<RocketStaleSeatFixture> OPEN = Collections.newSetFromMap(new IdentityHashMap<>());
    private final GameTestHelper helper;
    private final Case scenario;
    private final List<EmbeddedChannel> channels = new ArrayList<>();
    private final List<String> replies = new ArrayList<>();
    private Fixture f;
    private int started, releaseStart;
    private boolean closed;
    private RocketRuntime.PassengerMoveScope internal;
    private RocketRejoinSavedData previousRejoins, guardedRejoins;
    private RocketTransactionSavedData transactions;
    private Object previousFrozen, previousTransfer;
    private BayClaimBook claims;
    private Entity other;
    private RocketPassengerReconnectQueue reconnects;

    RocketStaleSeatFixture(GameTestHelper helper, Case scenario) { this.helper = helper; this.scenario = scenario; }
    void start() { OPEN.add(this); helper.onEachTick(this::tick); }
    static void closeOpen(ServerLevel level) {
        for (var fixture : List.copyOf(OPEN)) { if (fixture.helper.getLevel() == level) { fixture.close(); } }
    }
    private static Object field(Object target, String name) {
        try { var field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private CommandSourceStack operator() {
        return helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPermission(2)
                .withSource(new CommandSource() {
                    public void sendSystemMessage(Component message) { replies.add(message.getString()); }
                    public boolean acceptsSuccess() { return true; }
                    public boolean acceptsFailure() { return true; }
                    public boolean shouldInformAdmins() { return false; }
                });
    }
    private int command(String text) {
        replies.clear();
        return helper.getLevel().getServer().getCommands().performPrefixedCommand(operator(), text);
    }
    private String seatCommand(String player) { return "arce rocket release-seat " + f.rocket.getStringUUID() + " " + player; }
    private boolean assigned() { return f.rocket.flightData().orElseThrow().passengers().assignment(f.playerId).isPresent(); }
    private void kept(String reason) { helper.assertTrue(assigned(), scenario + ": " + reason); }

    private void initialize() throws Exception {
        f = new Fixture(helper);
        started = helper.getLevel().getServer().getTickCount(); releaseStart = 0;
        // Keep ordinary departures on solid ground instead of turning a 40-tick observation into a fall test.
        var floor = helper.absolutePos(new BlockPos(9, 1, 3));
        for (int x = -2; x <= 3; x++) { for (int z = -2; z <= 2; z++) { helper.getLevel().setBlockAndUpdate(floor.offset(x, 0, z), Blocks.STONE.defaultBlockState()); } }
        switch (scenario) {
            case DEATH -> {
                f.player.kill();
                helper.assertTrue(!f.player.isAlive(), "Death stimulus did not kill the rider"); kept("death immediately removed membership");
                releaseStart = 60;
            }
            case TELEPORT -> {
                var to = floor.above().east(2);
                helper.assertTrue(command("tp " + f.playerId + " " + to.getX() + " " + to.getY() + " " + to.getZ()) == 1, "Teleport command failed");
                helper.assertTrue(!f.player.isPassenger(), "Teleport did not detach rider");
            }
            case SPECTATOR -> {
                // The older shared mock overrides isSpectator(). Use vanilla ServerPlayer for this stimulus.
                f.disconnect(); f.player = nativeJoin();
                helper.assertTrue(f.player.startRiding(f.rocket, true), "Native spectator control did not board");
                helper.assertTrue(command("gamemode spectator " + f.playerId) == 1 && f.player.isSpectator()
                        && !f.player.isPassenger(), "Spectator command did not detach a real spectator");
            }
            case DISMOUNT -> helper.assertTrue(command("ride " + f.playerId + " dismount") == 1 && !f.player.isPassenger(), "Ride dismount command failed");
            case PEARL -> {
                var pearl = new ImpactPearl(helper.getLevel(), f.player); pearl.setPos(f.rocket.position().add(2, 0, 0)); pearl.impact();
                helper.assertTrue(!f.player.isPassenger(), "Native ender pearl impact did not detach the rider");
            }
            case LEGACY -> { f.player.stopRiding(); f.reload(); kept("legacy save was already empty"); }
            case OFFLINE -> { f.disconnect(); releaseStart = 80; }
            case INTERNAL -> { internal = RocketRuntime.internalPassengerMove(f.player); f.player.stopRiding(); releaseStart = 60; }
            case REJOIN_PENDING, REJOIN_ACTIVE -> {
                f.player.stopRiding();
                var server = helper.getLevel().getServer(); previousRejoins = RocketRejoinEvents.get(server);
                guardedRejoins = RocketRejoinSavedData.load(RocketRejoinCodec.encode(rejoinRoot(f.playerId)));
                server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), guardedRejoins);
                if (scenario == Case.REJOIN_ACTIVE) { guardedRejoins.guards(server).join(f.player); }
                helper.assertTrue(RocketRejoinProtection.required(server, f.playerId)
                        && (scenario != Case.REJOIN_ACTIVE || RocketRejoinProtection.guarded(f.player)), "Rejoin protection not installed");
                releaseStart = 60;
            }
            case FROZEN -> {
                f.player.stopRiding(); transactions = RocketTransactionSavedData.get(helper.getLevel().getServer());
                previousFrozen = replaceField(transactions, "unreadable", true); releaseStart = 60;
                helper.assertTrue(RocketRuntime.sealedFrozen(f.rocket), "Frozen control was not frozen");
            }
            case ACTIVE_TRANSFER -> {
                f.player.stopRiding(); previousTransfer = replaceField(f.rocket.flightData().orElseThrow(), "activeTransferId", UUID.randomUUID()); releaseStart = 60;
            }
            case IN_FLIGHT -> { f.inFlight(); f.player.stopRiding(); releaseStart = 60; }
            case OTHER_VEHICLE -> {
                f.player.stopRiding(); other = EntityType.BOAT.create(helper.getLevel()); other.setPos(f.rocket.position());
                helper.assertTrue(helper.getLevel().addFreshEntity(other) && f.player.startRiding(other, true), "Other-vehicle control failed");
            }
            case RECONNECT_PENDING -> {
                f.player.stopRiding();
                reconnects = (RocketPassengerReconnectQueue) field(RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()), "reconnects");
                releaseStart = 60;
            }
            case RETURNED -> { f.player.stopRiding(); releaseStart = 21; }
            case HELD -> {
                // Install the same RAM claim consulted by the real session, without advancing its deployment queue.
                claims = (BayClaimBook) field(BayDeploymentGameTestSupport.bays(helper), "claims");
                helper.assertTrue(claims.begin(BayClaim.checkpoint(f.record.logicalRocketId(), f.record.transferId(), f.rocket.getUUID(),
                        List.of(BayMember.candidate(new RocketPosition(0, 0, 0), UUID.randomUUID())))), "Could not hold the bay claim");
                helper.assertTrue(RocketEscrowGate.bayHeld(f.rocket), "Claim did not hold membership");
                helper.assertTrue(RocketFlightService.leave(f.player, f.rocket).success() && !f.player.isPassenger(), "Held LEAVE did not physically exit");
                helper.assertTrue(command(seatCommand(f.playerId.toString())) == 0, "Operator bypassed the held claim");
                releaseStart = 60;
            }
            case COMMAND -> { operatorChecks(); return; }
        }
        kept("departure removed membership before grace");
    }

    private void operatorChecks() throws Exception {
        var commands = helper.getLevel().getServer().getCommands().getDispatcher();
        var text = seatCommand(f.playerId.toString());
        var parsed = commands.parse(text, operator());
        helper.assertTrue(commands.execute(new ParseResults<>(parsed.getContext().withSource(operator().withPermission(1)),
                parsed.getReader(), parsed.getExceptions())) == 0, "Replayed parsed command bypassed permission");
        kept("permission refusal changed seat");
        helper.assertTrue(command("arce rocket release-seat " + f.playerId + " " + f.playerId) == 0, "Non-rocket target accepted");
        helper.assertTrue(command(seatCommand("@a")) == 0 && command(seatCommand("MissingProfile")) == 0
                && command(seatCommand(UUID.randomUUID().toString())) == 0, "Invalid or unassigned player accepted");
        var before = replaceField(f.rocket.flightData().orElseThrow(), "activeTransferId", UUID.randomUUID());
        try { helper.assertTrue(command(text) == 0, "Active transfer accepted operator release"); kept("transfer refusal changed seat"); }
        finally { replaceField(f.rocket.flightData().orElseThrow(), "activeTransferId", before); }
        f.inFlight(); text = seatCommand(f.playerId.toString());
        helper.assertTrue(command(text) == 0 && replies.stream().anyMatch(message -> message.contains("INVALID_STATE")),
                "In-flight release was not refused for its state"); kept("in-flight refusal changed seat");
        f.rocket.updateFlightData(f.rocket.flightData().orElseThrow().markFailed(helper.getLevel().getGameTime()).recover(true, helper.getLevel().getGameTime()));
        var data = RocketTransactionSavedData.get(helper.getLevel().getServer()); var unreadable = replaceField(data, "unreadable", true);
        try { helper.assertTrue(command(text) == 0, "Frozen root accepted operator release"); kept("frozen refusal changed seat"); }
        finally { replaceField(data, "unreadable", unreadable); }
        try (var ignored = RocketRuntime.internalPassengerMove(f.player)) {
            helper.assertTrue(command(text) == 0, "Internal move accepted operator release"); kept("internal refusal changed seat");
        }
        var profile = f.player.getGameProfile(); helper.getLevel().getServer().getProfileCache().add(profile); f.disconnect();
        helper.assertTrue(command(seatCommand(profile.getName())) == 1, "Operator could not release an offline cached name");
        helper.assertTrue(replies.stream().anyMatch(message -> message.contains("Released rocket seat:")), "Operator received no success report");
        helper.assertTrue(!assigned() && command(seatCommand(f.playerId.toString())) == 0, "Repeated release succeeded");
        f.reload(); f.login(); helper.assertTrue(!assigned() && f.player.getVehicle() == null, "Operator release was not persisted");
        done();
    }

    private ServerPlayer nativeJoin() {
        var server = helper.getLevel().getServer();
        var player = new ServerPlayer(server, helper.getLevel(), new GameProfile(f.playerId, "W300Spectator"));
        var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, player); player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(helper.getLevel(), f.rocket.getX(), f.rocket.getY(), f.rocket.getZ(), 0, 0); return player;
    }
    private void endRejoin() {
        if (guardedRejoins == null) { return; }
        guardedRejoins.close(); helper.getLevel().getServer().overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), previousRejoins);
        guardedRejoins = null;
        f.player.teleportTo(helper.getLevel(), f.rocket.getX() + 2, f.rocket.getY(), f.rocket.getZ(), 0, 0);
    }
    private void tick() {
        if (closed) { return; }
        try {
            if (f == null) {
                for (var relative : List.of(new BlockPos(3, 2, 3), new BlockPos(9, 2, 3))) {
                    if (!helper.getLevel().areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(helper.absolutePos(relative)).toLong())) { return; }
                }
                initialize(); if (closed) { return; }
            }
            int age = helper.getLevel().getServer().getTickCount() - started;
            if (scenario == Case.RETURNED && age == 20) { helper.assertTrue(f.player.startRiding(f.rocket, true), "Return did not board"); }
            if (scenario == Case.RETURNED && age == 21) { f.player.stopRiding(); }
            if (scenario == Case.OFFLINE && age == 60) {
                kept("offline seat expired"); f.reload(); f.login();
                helper.assertTrue(f.player.getVehicle() == f.rocket, "Login did not restore offline seat before cleanup");
            }
            if (scenario == Case.OFFLINE && age == 80) { f.player.stopRiding(); }
            if (age == 60) {
                if (scenario == Case.DEATH) {
                    kept("dead player lost seat before respawn");
                    f.player = f.player.getServer().getPlayerList().respawn(f.player, false);
                    f.player.teleportTo(helper.getLevel(), f.rocket.getX() + 2, f.rocket.getY(), f.rocket.getZ(), 0, 0);
                    helper.assertTrue(f.player.isAlive() && !f.player.isPassenger(), "Respawn did not produce a living detached player");
                }
                if (internal != null) { internal.close(); internal = null; }
                endRejoin();
                if (transactions != null) { replaceField(transactions, "unreadable", previousFrozen); transactions = null; }
                if (scenario == Case.ACTIVE_TRANSFER) { replaceField(f.rocket.flightData().orElseThrow(), "activeTransferId", previousTransfer); }
                if (scenario == Case.IN_FLIGHT) {
                    f.rocket.updateFlightData(f.rocket.flightData().orElseThrow().markFailed(helper.getLevel().getGameTime()).recover(true, helper.getLevel().getGameTime()));
                }
                if (claims != null) { claims.remove(f.record.logicalRocketId()); claims = null; helper.assertTrue(!RocketEscrowGate.bayHeld(f.rocket), "Bay hold did not end"); }
            }
            if (reconnects != null) {
                if (age < 60) { helper.assertTrue(reconnects.offer(f.playerId, helper.getLevel().getGameTime()), "Reconnect queue refused fixture"); }
                else { reconnects.complete(f.playerId); }
            }
            helper.getLevel().tickNonPassenger(f.rocket);
            if (age < releaseStart + 39) { kept("seat was removed before the full eligible grace"); }
            if (age >= releaseStart + 41) {
                helper.assertTrue(!assigned(), scenario + ": absent seat was not released after grace");
                if (other != null) { helper.assertTrue(f.player.getVehicle() == other, "Cleanup detached a different vehicle"); }
                f.reload(); f.reconnect(); helper.assertTrue(f.player.getVehicle() == null && !assigned(), "Released seat returned on reload/login");
                done();
            }
        } catch (Throwable failure) { close(); helper.fail(scenario + ": " + failure); }
    }
    private void done() { close(); helper.succeed(); }
    @Override public void close() {
        if (closed) { return; } closed = true; OPEN.remove(this);
        if (internal != null) { internal.close(); internal = null; }
        endRejoin();
        if (transactions != null) { replaceField(transactions, "unreadable", previousFrozen); transactions = null; }
        if (claims != null && f != null) { claims.remove(f.record.logicalRocketId()); }
        if (reconnects != null && f != null) { reconnects.complete(f.playerId); }
        if (f != null) { f.close(); }
        if (other != null) { other.discard(); }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
    }
    private static final class ImpactPearl extends ThrownEnderpearl {
        ImpactPearl(ServerLevel level, ServerPlayer player) { super(level, player); }
        void impact() { super.onHit(new BlockHitResult(position(), Direction.UP, blockPosition(), false)); }
    }
}
