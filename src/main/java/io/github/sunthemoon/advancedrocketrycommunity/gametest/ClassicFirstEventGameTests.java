package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements.Milestone;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventOutbox;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.FirstWarpRequest;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationFirstWarpGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData.CheckedUpdate;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W44 A1: the isolated world-first station warp batch. Each test elects in a detached registry, journal and progression
 * store bound to its own files (W44 section 10), so a shared GameTest election cannot steal its slot. The positives use
 * the real participant capture, real connected players and the real trigger after the durable receipt; the negatives
 * keep precommit, cancelled, ineligible, outside-region, unauthorized and later-joiner coverage.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicFirstEventGameTests {
    private static final int COST = 2_000_000;

    private ClassicFirstEventGameTests() {
    }

    @GameTest(template = "empty", batch = "classic_first_event", timeoutTicks = 200)
    public static void theFirstWarpAwardsOnlyPresentAuthorizedParticipantsAfterTheDurableReceipt(GameTestHelper helper) {
        var fixture = new StationWarpGameTests.Fixture(helper, "First warp A1", CelestialIds.EARTH_ID, 0);
        var driver = new GameTestTickDriver(helper);
        List<ServerPlayer> extra = new ArrayList<>();
        UUID memberId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID remoteId = UUID.randomUUID();
        fixture.data.invite(fixture.id(), memberId);
        fixture.data.acceptInvitation(fixture.id(), memberId);
        fixture.data.invite(fixture.id(), remoteId);
        fixture.data.acceptInvitation(fixture.id(), remoteId);
        fixture.join(fixture.ownerId, "firstWarpOwner");
        fixture.join(memberId, "firstWarpMember", fixture.pad.east(1), false);
        fixture.join(outsiderId, "firstWarpOutsider", fixture.pad.east(2), false);
        extra.add(ConnectedTestPlayers.join(fixture.server, remoteId, "firstWarpRemote", fixture.space,
                fixture.pad.east(StationLimits.REGION_SIZE / 2 + 32), new ArrayList<>()));
        DetachedFirstEventRoots roots = DetachedFirstEventRoots.fromWorld(helper);
        Runnable cleanup = () -> {
            roots.close();
            extra.forEach(fixture.server.getPlayerList()::remove);
            fixture.close();
        };
        try {
            MinecraftServer server = fixture.server;
            ServerPlayer owner = player(server, fixture.ownerId);
            ServerPlayer member = player(server, memberId);
            ServerPlayer outsider = player(server, outsiderId);
            ServerPlayer remote = player(server, remoteId);
            roots.stations.foldWarpCredits(Map.of(fixture.id(), 3 * COST));
            StationState station = roots.stations.find(fixture.id()).orElseThrow();
            roots.delivery.tick();
            helper.assertTrue(noneDone(server, Milestone.FIRST_STATION_WARP, owner, member, outsider, remote),
                    "A world-first was awarded before any warp");

            // DEFER refuses before any write or debit; a refused replacement elects nothing.
            helper.assertTrue(relocate(roots, station, request(fixture.ownerId, List.of(fixture.ownerId),
                    FirstEventDisposition.DEFER, FirstEventDisposition.DEFER)) == CheckedUpdate.FIRST_EVENT_UNAVAILABLE,
                    "A deferred first warp was not refused");
            helper.assertTrue(StationFirstWarpGameTestSupport.refusedRelocation(server, roots.stations,
                    roots.file(ManagedSavedDataType.STATIONS), station, StationWarpGameTests.MOON, COST,
                    request(fixture.ownerId, List.of(fixture.ownerId), FirstEventDisposition.ALLOW,
                            FirstEventDisposition.ALLOW)) == CheckedUpdate.WRITE_FAILED, "The refused warp committed");
            helper.assertTrue(roots.stations.firstWarpRam().isEmpty()
                    && roots.stations.warpEnergy(fixture.id()) == 3 * COST, "A refused warp changed the slot or FE");

            // The real capture: present, in the region, with live VISIT access, and eligible.
            List<UUID> participants = fixture.warp.eligibleVisitors(server, station);
            helper.assertTrue(participants.equals(java.util.stream.Stream.of(fixture.ownerId, memberId).sorted().toList()),
                    "The capture differs: " + participants);
            FirstEventDisposition allow = roots.delivery.disposition(FirstEventKind.FIRST_STATION_WARP,
                    roots.stations.durableFirstWarp(), roots.stations.firstWarpRam());
            helper.assertTrue(allow == FirstEventDisposition.ALLOW, "The detached slot is not open: " + allow);
            helper.assertTrue(relocate(roots, station, new FirstWarpRequest(fixture.ownerId, participants, allow,
                    () -> roots.delivery.disposition(FirstEventKind.FIRST_STATION_WARP, roots.stations.durableFirstWarp(),
                            roots.stations.firstWarpRam()))) == CheckedUpdate.COMMITTED, "The first warp did not commit");
            FirstEvent event = roots.stations.firstWarpRam().event();
            helper.assertTrue(roots.stations.firstWarpRam().state() == FirstEventOutbox.State.ELECTED
                    && event.participants().equals(participants), "The warp did not elect its captured participants");
            helper.assertTrue(noneDone(server, Milestone.FIRST_STATION_WARP, owner, member),
                    "The world-first was awarded before its durable receipt");

            roots.delivery.tick();
            helper.assertTrue(roots.receiptOnDisk(FirstEventKind.FIRST_STATION_WARP).filter(event::equals).isPresent(),
                    "The receipt of the source event is not on disk");
            helper.assertTrue(done(server, owner, Milestone.FIRST_STATION_WARP) && done(server, member,
                    Milestone.FIRST_STATION_WARP), "A present authorized participant missed the world-first");
            helper.assertTrue(noneDone(server, Milestone.FIRST_STATION_WARP, outsider, remote),
                    "A player without VISIT or outside the region earned the world-first");

            // A later joiner with VISIT access in the region never joins the winning set.
            UUID lateId = UUID.randomUUID();
            fixture.data.invite(fixture.id(), lateId);
            fixture.data.acceptInvitation(fixture.id(), lateId);
            fixture.join(lateId, "firstWarpLate", fixture.pad.east(1), false);
            ServerPlayer late = player(server, lateId);
            roots.delivery.onLogin(late);
            awaitDelivered(helper, driver, roots, 40, () -> {
                try {
                    helper.assertTrue(!done(server, late, Milestone.FIRST_STATION_WARP),
                            "A later joiner earned the world-first");
                    helper.assertTrue(roots.stations.firstWarpRam().equals(FirstEventOutbox.elected(event).delivered()),
                            "The station source was not acknowledged with the same event");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            }, cleanup);
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
    }

    /**
     * Section 3.5: a participant who is spectating or dead keeps one pending bit and is granted within the queue bound
     * once eligible, without reconnecting; a disconnect removes the bit, and a reconnect is granted at login.
     */
    @GameTest(template = "empty", batch = "classic_first_event", timeoutTicks = 200)
    public static void deferredGrantsFollowEligibilityAndADisconnectKeepsNothing(GameTestHelper helper) {
        var fixture = new StationWarpGameTests.Fixture(helper, "Deferred A1", CelestialIds.EARTH_ID, 0);
        var driver = new GameTestTickDriver(helper);
        MinecraftServer server = fixture.server;
        List<ServerPlayer> joined = new ArrayList<>();
        boolean[] spectating = {true};
        UUID spectatorId = UUID.randomUUID();
        UUID deadId = UUID.randomUUID();
        UUID quitterId = UUID.randomUUID();
        DetachedFirstEventRoots roots = DetachedFirstEventRoots.fromWorld(helper);
        Runnable cleanup = () -> {
            roots.close();
            joined.forEach(player -> server.getPlayerList().remove(player));
            fixture.close();
        };
        try {
            ServerPlayer spectator = join(server, spectatorId, "firstSpectator", fixture.space, fixture.pad, spectating,
                    joined);
            boolean[] deadState = {false, true};
            ServerPlayer dead = join(server, deadId, "firstDead", fixture.space, fixture.pad, deadState, joined);
            ServerPlayer quitter = join(server, quitterId, "firstQuitter", fixture.space, fixture.pad, spectating,
                    joined);
            roots.stations.foldWarpCredits(Map.of(fixture.id(), 3 * COST));
            StationState station = roots.stations.find(fixture.id()).orElseThrow();
            List<UUID> participants = java.util.stream.Stream.of(spectatorId, deadId, quitterId).sorted().toList();
            helper.assertTrue(relocate(roots, station, request(fixture.ownerId, participants, FirstEventDisposition.ALLOW,
                    FirstEventDisposition.ALLOW)) == CheckedUpdate.COMMITTED, "The fixture warp did not commit");
            roots.delivery.tick();
            helper.assertTrue(roots.delivery.deferred(FirstEventKind.FIRST_STATION_WARP) == 3,
                    "Ineligible participants were not deferred: " + roots.delivery.deferred(FirstEventKind.FIRST_STATION_WARP));
            helper.assertTrue(noneDone(server, Milestone.FIRST_STATION_WARP, spectator, dead, quitter),
                    "An ineligible participant was granted");
            roots.delivery.onLogout(quitter);
            server.getPlayerList().remove(quitter);
            joined.remove(quitter);
            helper.assertTrue(roots.delivery.deferred(FirstEventKind.FIRST_STATION_WARP) == 2,
                    "A disconnect kept its pending entry");
            spectating[0] = false;
            deadState[1] = false;
            long eligibleAt = helper.getTick();
            // 20 + ceil(2P / 16) ticks for P connected players (section 3.5).
            long bound = 20L + (2L * server.getPlayerCount() + 15L) / 16L;
            awaitGrants(helper, driver, roots, eligibleAt, bound, spectator, dead, () -> {
                try {
                    helper.assertTrue(roots.delivery.deferred(FirstEventKind.FIRST_STATION_WARP) == 0,
                            "Granted entries stayed queued");
                    ServerPlayer rejoined = join(server, quitterId, "firstQuitter", fixture.space, fixture.pad,
                            new boolean[1], joined);
                    roots.delivery.onLogin(rejoined);
                    helper.assertTrue(done(server, rejoined, Milestone.FIRST_STATION_WARP),
                            "A reconnecting participant was not granted at login");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            }, cleanup);
        } catch (RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
    }

    private static void awaitGrants(GameTestHelper helper, GameTestTickDriver driver, DetachedFirstEventRoots roots,
                                    long start, long bound, ServerPlayer first, ServerPlayer second, Runnable then,
                                    Runnable cleanup) {
        driver.after(1, () -> {
            try {
                roots.delivery.tick();
                MinecraftServer server = roots.server;
                if (done(server, first, Milestone.FIRST_STATION_WARP) && done(server, second, Milestone.FIRST_STATION_WARP)) {
                    then.run();
                    return;
                }
                helper.assertTrue(helper.getTick() - start <= bound, "Deferred grants exceeded the queue bound");
            } catch (RuntimeException failure) {
                cleanup.run();
                throw failure;
            }
            awaitGrants(helper, driver, roots, start, bound, first, second, then, cleanup);
        });
    }

    private static void awaitDelivered(GameTestHelper helper, GameTestTickDriver driver, DetachedFirstEventRoots roots,
                                       int remaining, Runnable then, Runnable cleanup) {
        driver.after(1, () -> {
            try {
                roots.delivery.tick();
                if (roots.stations.firstWarpRam().state() == FirstEventOutbox.State.DELIVERED || remaining <= 0) {
                    then.run();
                    return;
                }
            } catch (RuntimeException failure) {
                cleanup.run();
                throw failure;
            }
            awaitDelivered(helper, driver, roots, remaining - 1, then, cleanup);
        });
    }

    private static CheckedUpdate relocate(DetachedFirstEventRoots roots, StationState station, FirstWarpRequest request) {
        return StationFirstWarpGameTestSupport.relocate(roots.server, roots.stations,
                roots.file(ManagedSavedDataType.STATIONS), station, StationWarpGameTests.MOON, COST, request);
    }

    private static FirstWarpRequest request(UUID actor, List<UUID> participants, FirstEventDisposition disposition,
                                            FirstEventDisposition recheck) {
        return new FirstWarpRequest(actor, participants, disposition, () -> recheck);
    }

    static boolean done(MinecraftServer server, ServerPlayer player, Milestone milestone) {
        var advancement = server.getAdvancements().getAdvancement(ResourceLocation.tryParse(milestone.id()));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private static boolean noneDone(MinecraftServer server, Milestone milestone, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            if (done(server, player, milestone)) {
                return false;
            }
        }
        return true;
    }

    private static ServerPlayer player(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player == null) {
            throw new IllegalStateException("Fixture player " + id + " is not connected");
        }
        return player;
    }

    /**
     * A connected mock player whose eligibility the test controls: {@code state[0]} is spectating and {@code state[1]},
     * when present, is dead (the shared helper's player is always a living non-spectator).
     */
    private static ServerPlayer join(MinecraftServer server, UUID id, String name, ServerLevel level, BlockPos position,
                                     boolean[] state, List<ServerPlayer> joined) {
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(id, name)) {
            @Override public boolean isSpectator() { return state[0]; }

            @Override public boolean isCreative() { return !state[0]; }

            @Override public boolean isAlive() { return !(state.length > 1 && state[1]) && super.isAlive(); }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player);
        player.teleportTo(level, position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D, 0.0F, 0.0F);
        joined.add(player);
        return player;
    }
}
