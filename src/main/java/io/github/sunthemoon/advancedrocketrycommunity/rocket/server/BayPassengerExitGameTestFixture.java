package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayDeploymentGameTestSupport.*;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayStepQueue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Real service and vanilla landing fixture; pause only this test's D0 queue, never another rocket. */
final class BayPassengerExitGameTestFixture {
    enum Case { SAFE, MENU, CREW, CANCELLED, BYPASS, STALE_BYPASS, ORDINARY, FIRST_TICK, DRIPSTONE, NON_FALL, DIRECT_FALL,
        AIRBORNE_CAUSE, ZERO_LANDING, TELEPORT, UPWARD, FAILED_MOVE, RELEASED_MOVE, HISTORY, LOGOUT, DEATH,
        CONTENTION, EXPIRY, UNLOADED, NON_FALL_LANDING, CANCELLED_FALL, FLUID, GANTRY, LOCAL_ONLY, REMOTE,
        HAZARD, HEADROOM, PARTIAL, BUDGET, REENTRANT_DAMAGE, AFTER_TELEPORT, RELEASED_SUCCESS, CLIMB,
        CONTENTION_REVERSE, ONE_BOX, ONE_BOX_REVERSE, LAST_UNIT, LAST_UNIT_REVERSE, DELAYED_ONE_BOX, DELAYED_LAST, FIFO,
        EARLY_CONTINUOUS, SECOND_CORRECTION, EARLY_DISCONTINUITY, AMBIENT_FLUID, CACHE_EVICTION }
    final GameTestHelper helper;
    final Case scenario;
    final List<ServerPlayer> players = new ArrayList<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final Set<Long> forcedColumns = new HashSet<>();
    final List<LivingAttackEvent> attacks = new ArrayList<>();
    Flight flight;
    Flight otherFlight;
    ServerPlayer otherPlayer;
    ServerPlayer player;
    BayPassengerExitService exits;
    BlockPos origin;
    BlockPos gantry;
    int ticks, stage, falls, joinedTick;
    int acknowledgedCorrection = -1;
    float fallDistance, multiplier;
    boolean closed, injected;
    boolean diagnosed;
    BayPassengerExitState.Slot offer;
    BayPassengerExitState.Slot arbitrationA, arbitrationB;
    io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget.Usage arbitrationBefore;
    io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget.Usage arbitrationAfter;
    Object arbitrationTickets;
    int arbitrationLoaded;
    String arbitrationAudit;
    long firstDeadline;
    Runnable cacheListenerCleanup = () -> { };
    BayPassengerExitGameTestAudit.Mount probeMount;

    BayPassengerExitGameTestFixture(GameTestHelper helper, Case scenario) { this.helper = helper; this.scenario = scenario; }
    void start() { MinecraftForge.EVENT_BUS.register(this); helper.onEachTick(this::tick); }
    private static Object field(Object target, Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private void pause() {
        if (flight == null) { return; }
        try {
            var queue = (BayStepQueue) field(bays(helper), BayTransferRecovery.class, "queue");
            queue.remove(flight.record().logicalRocketId());
            if (otherFlight != null) { queue.remove(otherFlight.record().logicalRocketId()); }
        }
        catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeTick(TickEvent.ServerTickEvent event) { if (!closed) { pause(); } }
    @SubscribeEvent(priority = EventPriority.LOW)
    public void beforeDrain(TickEvent.ServerTickEvent event) {
        if (!closed && stage == 5 && event.phase == TickEvent.Phase.END) {
            try { arbitrationLoaded = helper.getLevel().getChunkSource().getLoadedChunksCount(); arbitrationTickets = ticketSnapshot(); }
            catch (Exception error) { arbitrationAudit = error.toString(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterDrain(TickEvent.ServerTickEvent event) {
        if (!closed && scenario == Case.ORDINARY && offer != null && offer.descent.closed && !diagnosed) {
            diagnosed = true;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_BAY_EXIT_CLOSED authority={} generation={} tick={} previous={} sample={} contact={} inspected={} usage={} cause={}",
                    offer.permit.authority(), offer.descent.corrections.current.equals(exits.motion.generation(player)), player.getServer().getTickCount(),
                    offer.descent.continuity.last, BayPassengerExitMotion.sample(player, player.getServer().getTickCount()),
                    offer.world.contact, offer.world.inspected, exits.budget.usage(), offer.world.firstContact);
        }
        if (!closed && stage == 5 && event.phase == TickEvent.Phase.END && arbitrationA.completed && arbitrationB.completed) {
            arbitrationAfter = exits.budget.usage();
            try {
                if (arbitrationLoaded != helper.getLevel().getChunkSource().getLoadedChunksCount() || !arbitrationTickets.equals(ticketSnapshot())) {
                    arbitrationAudit = "END drain loaded chunks or added tickets";
                }
            } catch (Exception error) { arbitrationAudit = error.toString(); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void probeMount(EntityMountEvent event) {
        if (!closed && event.isDismounting() && players.contains(event.getEntityMounting())
                && event.getEntityBeingMounted() instanceof io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity rocket) {
            probeMount = BayPassengerExitGameTestAudit.mount(helper.getLevel(), rocket);
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void cancelMount(EntityMountEvent event) {
        if (event.getEntityMounting() == player && (scenario == Case.CANCELLED && event.isDismounting()
                || scenario == Case.AFTER_TELEPORT && otherFlight != null && event.isMounting())) { event.setCanceled(true); }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void attack(LivingAttackEvent event) {
        if (event.getEntity() == player) {
            attacks.add(event);
            if (scenario == Case.ORDINARY) { landingTrace("attack " + event.getSource().type().msgId() + ":" + event.isCanceled()); }
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void nestedAttack(LivingAttackEvent event) {
        if (event.getEntity() == player && scenario == Case.REENTRANT_DAMAGE && !injected) {
            injected = true; player.hurt(player.damageSources().fall(), 1);
        }
    }
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void fall(LivingFallEvent event) {
        if (event.getEntity() == player) { falls++; fallDistance = event.getDistance(); multiplier = event.getDamageMultiplier(); }
        if (event.getEntity() == player && scenario == Case.ORDINARY) { landingTrace("fall " + event.getDistance()); }
        if (event.getEntity() == player && scenario == Case.NON_FALL_LANDING && stage == 2 && !injected) {
            injected = true; player.causeFallDamage(6, 1, player.damageSources().flyIntoWall());
        }
    }
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void cancelFall(LivingFallEvent event) { if (event.getEntity() == player && scenario == Case.CANCELLED_FALL) { event.setCanceled(true); } }
    private void landingTrace(String event) {
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BAY_EXIT_LANDING event={} closed={} token={} live={} previous={} frames={}", event,
                offer.descent.closed, offer.descent.token, exits.state.live.containsKey(player), offer.descent.continuity.last,
                StackWalker.getInstance().walk(stream -> stream.limit(65).map(frame -> frame.getClassName() + "." + frame.getMethodName()).toList()));
    }
    ServerPlayer join(UUID id) {
        var level = helper.getLevel();
        var joined = new ServerPlayer(level.getServer(), level, new GameProfile(id, "exit" + id.toString().substring(0, 8)));
        var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection));
        level.getServer().getPlayerList().placeNewPlayer(connection, joined);
        joined.setGameMode(GameType.SURVIVAL);
        joined.connection.teleport(origin.getX() + .5, origin.getY(), origin.getZ() + .5, 25, 10);
        players.add(joined); return joined;
    }
    private void setup() {
        var level = helper.getLevel(); var server = level.getServer();
        origin = helper.absolutePos(new BlockPos(3, 4, 3));
        boolean ready = true;
        for (int x = (origin.getX() >> 4) - 1; x <= (origin.getX() >> 4) + 1; x++) {
            for (int z = (origin.getZ() >> 4) - 1; z <= (origin.getZ() >> 4) + 1; z++) {
                long key = net.minecraft.world.level.ChunkPos.asLong(x, z);
                if (!level.getForcedChunks().contains(key)) { level.setChunkForced(x, z, true); forcedColumns.add(key); }
                ready &= level.areEntitiesLoaded(key) && level.isPositionEntityTicking(new BlockPos(x * 16, origin.getY(), z * 16));
            }
        }
        if (!ready) { return; } // Fixture tickets precede the measured exit; production never requests them.
        player = join(UUID.randomUUID());
        UUID owner = scenario == Case.CREW ? UUID.randomUUID() : player.getUUID();
        var pkg = dataPackage(owner);
        flight = transit(helper, origin, level.dimension().location(), position(helper.absolutePos(new BlockPos(12, 4, 3))),
                owner, List.of(pkg), RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), player.getUUID());
        helper.assertTrue(player.startRiding(flight.source(), true), "Fixture rider could not board before hold");
        RocketTransferSavedData.get(server).put(flight.record());
        bays(helper).ascentComplete(server, RocketTransferSavedData.get(server), flight.record(), flight.source());
        if (arbitrationCase()) {
            otherPlayer = join(UUID.randomUUID());
            otherFlight = transit(helper, origin, level.dimension().location(), position(origin.offset(10, 0, 0)), otherPlayer.getUUID(),
                    List.of(dataPackage(otherPlayer.getUUID())), RocketEscrowSectionPatch.NEUTRAL, RocketFlightPlanner.EARTH.bodyId(), otherPlayer.getUUID());
            helper.assertTrue(otherPlayer.startRiding(otherFlight.source(), true), "Second rider could not board");
            RocketTransferSavedData.get(server).put(otherFlight.record());
            bays(helper).ascentComplete(server, RocketTransferSavedData.get(server), otherFlight.record(), otherFlight.source());
        }
        helper.assertTrue(claim(helper, flight.record().logicalRocketId()).isPresent(), "D0 did not claim fixture");
        pause(); exits = RocketRuntime.passengerExits().orElseThrow();
        exits.probeLocationForTests((rider, rocket) -> BayPassengerExitGameTestAudit.probe(this, rider, rocket));
        helper.assertTrue(exits.motion.generation(player) != null && exits.landing.available(), "Pinned mapped observers unavailable");
        for (int x = -3; x <= 3; x++) { for (int z = -3; z <= 3; z++) {
            for (int y = -2; y <= 54; y++) {
                if (y <= 3 || y >= 46 || Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    level.setBlockAndUpdate(origin.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        } }
        if (Set.of(Case.SAFE, Case.MENU, Case.CREW, Case.CONTENTION, Case.CONTENTION_REVERSE, Case.FIFO).contains(scenario)) { pad(origin); }
        if (Set.of(Case.ONE_BOX, Case.ONE_BOX_REVERSE, Case.DELAYED_ONE_BOX).contains(scenario)) {
            level.setBlockAndUpdate(origin.below(), Blocks.STONE.defaultBlockState());
        }
        if (Set.of(Case.LAST_UNIT, Case.LAST_UNIT_REVERSE, Case.DELAYED_LAST, Case.GANTRY, Case.LOCAL_ONLY).contains(scenario)) {
            buildGantry();
        }
        if (Set.of(Case.HAZARD, Case.HEADROOM, Case.PARTIAL).contains(scenario)) {
            pad(origin);
            for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
                level.setBlockAndUpdate(origin.offset(x, scenario == Case.HEADROOM ? 1 : -1, z),
                        (scenario == Case.HAZARD ? Blocks.MAGMA_BLOCK : scenario == Case.PARTIAL ? Blocks.STONE_SLAB : Blocks.STONE).defaultBlockState());
            } }
        }
        BayPassengerExitGameTestDescentAudit.enclose(this);
        cacheListenerCleanup = BayPassengerExitGameTestDescentAudit.evictOnDismount(this);
        joinedTick = server.getTickCount(); stage = 1;
    }
    void pad(BlockPos feet) {
        for (int x = -1; x <= 1; x++) { for (int z = -1; z <= 1; z++) {
            helper.getLevel().setBlockAndUpdate(feet.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        } }
    }
    private void buildGantry() {
        gantry = flight.source().blockPosition().offset(2, 0, 0);
        for (int x = -2; x <= 2; x++) { for (int z = -2; z <= 2; z++) { for (int y = -2; y <= 3; y++) {
            helper.getLevel().setBlockAndUpdate(gantry.offset(x, y, z), Blocks.AIR.defaultBlockState());
        } } }
        pad(gantry);
    }
    private Object ticketSnapshot() throws ReflectiveOperationException {
        Object manager = helper.getLevel().getChunkSource().chunkMap.getDistanceManager();
        var entries = (Map<?, ?>) field(manager, DistanceManager.class, "tickets");
        var snapshot = new TreeMap<String, String>();
        entries.forEach((key, value) -> snapshot.put(key.toString(), value.toString())); return snapshot;
    }
    private void detach() throws Exception {
        if (arbitrationCase()) { arbitrate(); return; }
        int loaded = helper.getLevel().getChunkSource().getLoadedChunksCount(); Object tickets = ticketSnapshot();
        var manifest = flight.source().flightData().orElseThrow().passengers();
        var snapshot = flight.source().snapshot().orElseThrow(); var fuel = flight.source().flightData().orElseThrow().fuel();
        if (scenario == Case.REMOTE) {
            var bystander = join(UUID.randomUUID());
            bystander.setPos(flight.source().getX() + 1, flight.source().getY(), flight.source().getZ());
            var at = bystander.position(); RocketRuntime.openFlightMenu(bystander, flight.source());
            RocketRuntime.requestFlightIntent(bystander, flight.source().getId(), RocketFlightAction.LEAVE,
                    flight.record().sourceFlightData().currentTarget().orElseThrow(), UUID.randomUUID());
            helper.assertTrue(at.equals(bystander.position()) && player.getVehicle() == flight.source()
                    && exits.state.history(bystander.getUUID(), flight.record().transferId()) == null, "Non-rider acquired an exit effect"); done(); return;
        }
        if (scenario == Case.LOCAL_ONLY) {
            flight.source().updateFlightData(flight.source().flightData().orElseThrow().withPassengers(manifest.remove(player.getUUID())));
            player.stopRiding(); var local = exits.state.live.get(player);
            helper.assertTrue(local != null && local.permit.transfer == null && local.permit.anchor == null
                    && local.result.ordinal() == 1 && local.descent == null
                    && exits.state.history(player.getUUID(), flight.record().transferId()) == null, "Unassigned rider gained checked-anchor authority"); done(); return;
        }
        if (scenario == Case.BUDGET) {
            while (exits.budget.usage().candidates() < 128) { exits.budget.openProbe().orElseThrow().close(); }
        }
        if (scenario == Case.CANCELLED) {
            player.stopRiding();
            helper.assertTrue(player.getVehicle() == flight.source() && exits.state.history(player.getUUID(), flight.record().transferId()) == null, "Cancelled detach had effects");
            done(); return;
        }
        if (scenario == Case.MENU) {
            RocketRuntime.openFlightMenu(player, flight.source());
            RocketRuntime.requestFlightIntent(player, flight.source().getId(), RocketFlightAction.LEAVE,
                    flight.record().sourceFlightData().currentTarget().orElseThrow(), UUID.randomUUID());
        } else if (scenario == Case.BYPASS || scenario == Case.STALE_BYPASS) {
            player.removeVehicle();
            if (scenario == Case.STALE_BYPASS) {
                var next = player.position().add(0, -.1, 0);
                player.connection.teleport(next.x, next.y, next.z, 25, 10);
            }
            stage = 3; return;
        } else { player.stopRiding(); }
        helper.assertTrue(!player.isPassenger(), "Physical exit was cancelled");
        helper.assertTrue(flight.source().snapshot().orElseThrow() == snapshot && flight.source().flightData().orElseThrow().fuel().equals(fuel)
                && flight.source().flightData().orElseThrow().passengers().equals(manifest), "Exit mutated rocket authority");
        helper.assertTrue(RocketTransferSavedData.get(player.getServer()).find(flight.record().transferId()).orElseThrow() == flight.record(), "Exit rewrote T");
        int afterLoaded = helper.getLevel().getChunkSource().getLoadedChunksCount(); Object afterTickets = ticketSnapshot();
        helper.assertTrue(loaded == afterLoaded && tickets.equals(afterTickets), "Exit loaded a chunk or added a ticket: chunks "
                + loaded + " -> " + afterLoaded + "; tickets " + BayPassengerExitGameTestAudit.difference(tickets, afterTickets));
        helper.assertTrue(bays(helper).sessionForTests().receipts().stream().noneMatch(row -> row.rocket().equals(flight.record().logicalRocketId())), "Exit wrote a receipt");
        offer = exits.state.live.get(player);
        helper.assertTrue(offer != null && offer.completed, "Detach hook did not complete a permit");
        var history = exits.state.history(player.getUUID(), flight.record().transferId());
        helper.assertTrue(history != null, "Checked first exit did not close history"); firstDeadline = history.deadline();
        if (scenario == Case.GANTRY) {
            helper.assertTrue(offer.result.ordinal() == 1 && player.getY() == flight.source().getY() && !history.issued(),
                    "Loaded gantry did not precede anchor search: " + offer.result + "; rocket=" + flight.source().position() + "; gantry=" + gantry); done(); return;
        }
        if (Set.of(Case.SAFE, Case.MENU, Case.CREW, Case.CONTENTION).contains(scenario)) {
            helper.assertTrue(offer.result.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.SAFE
                    && player.getY() == origin.getY() && player.getHealth() == 20 && !history.issued(), "Checked ground placement failed");
            helper.assertTrue(player.getYRot() == 25 && player.getXRot() == 10, "Exit changed view direction");
            if (scenario == Case.CONTENTION) {
                otherPlayer.stopRiding(); var second = exits.state.live.get(otherPlayer);
                helper.assertTrue(second != null && second.result.ordinal() == 8 && offer.result.ordinal() == 5
                        && second.result.attemptSequence() > offer.result.attemptSequence()
                        && offer.result.work().candidates() == 5 && second.result.work().candidates() == 8
                        && !player.getBoundingBox().intersects(otherPlayer.getBoundingBox()), "Ordered real arbitration did not choose exact next anchor");
            }
            done(); return;
        }
        if (scenario == Case.BUDGET) {
            helper.assertTrue(offer.result.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.EXIT_BUDGET_EXHAUSTED
                    && offer.result.work().candidates() == 0 && offer.result.work().reads() == 0 && offer.result.work().visits() == 0
                    && offer.descent == null && !history.issued(), "Exhaustion spent extra work or offered unprovable protection"); done(); return;
        }
        helper.assertTrue(offer.descent != null && !offer.descent.closed && history.issued(), "Fallback did not offer one descent: " + offer.result);
        BayPassengerExitGameTestDescentAudit.acknowledge(this);
        switch (scenario) {
            case CACHE_EVICTION -> { helper.assertTrue(injected, "Dismount listener did not run"); done(); }
            case FIRST_TICK -> { BayPassengerExitGameTestAudit.firstTickLanding(this); done(); }
            case HAZARD, HEADROOM, PARTIAL -> { helper.assertTrue(offer.result.status() != io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.SAFE,
                    "Unsafe contact geometry passed selection"); done(); }
            case TELEPORT -> stage = 6;
            case EARLY_CONTINUOUS, SECOND_CORRECTION, EARLY_DISCONTINUITY -> { BayPassengerExitGameTestDescentAudit.correction(this); done(); }
            case UPWARD -> { player.setPos(player.getX(), player.getY() - 1, player.getZ()); exits.sample(player);
                player.setPos(player.getX(), player.getY() + .1, player.getZ()); exits.sample(player); expectRevoked(); done(); }
            case FAILED_MOVE -> { RocketTransferEntities.movePassenger(player, flight.source(), position(origin)); expectRevoked();
                helper.assertTrue(!player.isPassenger(), "Held ordinary move unexpectedly mounted"); done(); }
            case AFTER_TELEPORT, RELEASED_SUCCESS -> {
                otherFlight = transit(helper, origin.offset(5, 0, 0), helper.getLevel().dimension().location(), position(origin.offset(10, 0, 0)),
                        player.getUUID(), List.of(dataPackage(player.getUUID())), RocketEscrowSectionPatch.NEUTRAL,
                        RocketFlightPlanner.EARTH.bodyId(), player.getUUID());
                var generation = exits.motion.generation(player);
                if (scenario == Case.AFTER_TELEPORT) { RocketTransferEntities.movePassenger(player, otherFlight.source(), position(origin.offset(5, 0, 0))); }
                else { helper.assertTrue(new BayReleasedResume(player.getServer()).move(player, otherFlight.source(), otherFlight.record(), () -> true), "Released helper did not move assignment"); }
                expectRevoked();
                helper.assertTrue(!generation.equals(exits.motion.generation(player)) && !exits.state.internal(player)
                        && (scenario == Case.AFTER_TELEPORT ? !player.isPassenger() : player.getVehicle() == otherFlight.source()), "Move did not traverse teleport/mount/finally"); done();
            }
            case RELEASED_MOVE -> { boolean moved = new BayReleasedResume(player.getServer()).move(player, flight.source(), flight.record(), () -> false);
                helper.assertTrue(!moved, "Refused released move succeeded"); expectRevoked(); done(); }
            case DIRECT_FALL -> { player.hurt(player.damageSources().fall(), 4); helper.assertTrue(player.getHealth() < 20, "Direct FALL was protected"); done(); }
            case AIRBORNE_CAUSE -> { player.causeFallDamage(10, 1, player.damageSources().fall()); helper.assertTrue(player.getHealth() < 20, "Airborne causeFallDamage was protected"); done(); }
            case NON_FALL -> { player.causeFallDamage(10, 1, player.damageSources().flyIntoWall()); helper.assertTrue(player.getHealth() < 20
                    && attacks.stream().noneMatch(LivingAttackEvent::isCanceled), "Non-FALL was protected"); done(); }
            case LOGOUT -> { var id = player.getUUID();
                player.getServer().getPlayerList().remove(player); players.remove(player); player = join(id); expectRevoked();
                helper.assertTrue(exits.state.admit(id, flight.record().transferId(), flight.record().logicalRocketId(), ticks) == null, "Relogin renewed history"); done(); }
            case DEATH -> { var previous = player; player.hurt(player.damageSources().genericKill(), Float.MAX_VALUE);
                helper.assertTrue(player.isDeadOrDying(), "Fixture death did not occur");
                player = player.getServer().getPlayerList().respawn(player, false); players.remove(previous); players.add(player);
                helper.assertTrue(player != previous && player.getUUID().equals(previous.getUUID()), "Respawn did not replace incarnation");
                expectRevoked(); helper.assertTrue(exits.state.admit(player.getUUID(), flight.record().transferId(), flight.record().logicalRocketId(), ticks) == null, "Death renewed history"); done(); }
            case HISTORY -> { UUID b = UUID.randomUUID(); exits.state.admit(player.getUUID(), b, UUID.randomUUID(), ticks);
                helper.assertTrue(exits.state.admit(player.getUUID(), flight.record().transferId(), flight.record().logicalRocketId(), ticks) == null
                        && exits.state.history(player.getUUID(), flight.record().transferId()).deadline() == firstDeadline, "T-A/B/A refreshed offer"); done(); }
            case UNLOADED -> { int reads = offer.world.reads; helper.assertTrue(!offer.world.loadedColumn(1_000_000, 1_000_000)
                    && offer.world.reads == reads, "Missing column caused reads/loads"); done(); }
            case FLUID -> { helper.getLevel().setBlockAndUpdate(player.blockPosition(), Blocks.WATER.defaultBlockState()); exits.sample(player); expectRevoked(); done(); }
            case CLIMB -> { helper.getLevel().setBlockAndUpdate(player.blockPosition(), Blocks.LADDER.defaultBlockState()); exits.sample(player); expectRevoked(); done(); }
            case EXPIRY -> stage = 4;
            default -> { pad(origin); if (scenario == Case.DRIPSTONE) { helper.getLevel().setBlockAndUpdate(origin.below(), Blocks.POINTED_DRIPSTONE.defaultBlockState()); }
                stage = 2; }
        }
    }
    void expectRevoked() {
        helper.assertTrue(!exits.state.live.containsKey(player) || exits.state.live.get(player).descent == null
                || exits.state.live.get(player).descent.closed, "Old live capability survived invalidation");
        helper.assertTrue(exits.state.history(player.getUUID(), flight.record().transferId()).deadline() == firstDeadline, "Invalidation refreshed history");
    }
    private boolean arbitrationCase() {
        return Set.of(Case.CONTENTION, Case.CONTENTION_REVERSE, Case.ONE_BOX, Case.ONE_BOX_REVERSE, Case.LAST_UNIT,
                Case.LAST_UNIT_REVERSE, Case.DELAYED_ONE_BOX, Case.DELAYED_LAST, Case.FIFO).contains(scenario);
    }
    private boolean lastUnit() { return Set.of(Case.LAST_UNIT, Case.LAST_UNIT_REVERSE, Case.DELAYED_LAST).contains(scenario); }
    private void arbitrate() throws Exception {
        // Establish an idle starting tick before either request; never reset or defer an actual exit.
        if (exits.budget.usage().candidates() != 0 || exits.budget.usage().reads() != 0 || exits.budget.usage().visits() != 0) { return; }
        if (Set.of(Case.CONTENTION_REVERSE, Case.ONE_BOX_REVERSE, Case.LAST_UNIT_REVERSE).contains(scenario)) {
            var previous = player; player = otherPlayer; otherPlayer = previous;
            var previousFlight = flight; flight = otherFlight; otherFlight = previousFlight;
        }
        if (lastUnit()) { buildGantry(); }
        for (var entry : List.of(Map.entry(player, flight), Map.entry(otherPlayer, otherFlight))) {
            helper.assertTrue(exits.state.admit(entry.getKey().getUUID(), entry.getValue().record().transferId(),
                    entry.getValue().record().logicalRocketId(), player.getServer().getTickCount()) != null, "Closed history could not be seeded");
        }
        helper.assertTrue(exits.budget.usage().candidates() == 0 && exits.budget.usage().reads() == 0
                && exits.budget.usage().visits() == 0, "Arbitration did not start with a fresh shared tick budget");
        if (lastUnit()) { for (int i = 0; i < 127; i++) { exits.budget.openProbe().orElseThrow().close(); } }
        arbitrationBefore = exits.budget.usage(); arbitrationLoaded = helper.getLevel().getChunkSource().getLoadedChunksCount();
        arbitrationTickets = ticketSnapshot();
        if (scenario == Case.DELAYED_ONE_BOX || scenario == Case.DELAYED_LAST || scenario == Case.FIFO) { player.removeVehicle(); }
        else { player.stopRiding(); }
        arbitrationA = exits.state.live.get(player);
        helper.assertTrue(arbitrationA != null, "First arbitration capture missing");
        if (scenario == Case.FIFO) { otherPlayer.removeVehicle(); } else { otherPlayer.stopRiding(); }
        arbitrationB = exits.state.live.get(otherPlayer);
        helper.assertTrue(arbitrationB != null, "Second arbitration capture missing");
        if (!arbitrationA.completed || !arbitrationB.completed) {
            helper.assertTrue(arbitrationA.context.captureSequence() < arbitrationB.context.captureSequence(), "Delayed capture order changed");
            stage = 5;
        } else { checkArbitration(); }
    }
    private void checkArbitration() throws Exception {
        helper.assertTrue(arbitrationA.completed && arbitrationB.completed, "END did not complete the captured FIFO");
        boolean delayed = scenario == Case.DELAYED_ONE_BOX || scenario == Case.DELAYED_LAST;
        var winner = delayed ? arbitrationB : arbitrationA; var loser = delayed ? arbitrationA : arbitrationB;
        var first = winner.result; var second = loser.result;
        boolean oneBox = Set.of(Case.ONE_BOX, Case.ONE_BOX_REVERSE, Case.DELAYED_ONE_BOX).contains(scenario);
        helper.assertTrue(first.status() == io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.SAFE
                && first.ordinal() == (lastUnit() ? 1 : 5) && first.work().candidates() == (lastUnit() ? 1 : 5), "Exact first winner changed: " + first);
        helper.assertTrue(second.status() == (lastUnit()
                    ? io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.EXIT_BUDGET_EXHAUSTED
                    : oneBox ? io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.NO_SAFE_EXIT
                    : io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector.Status.SAFE)
                && second.ordinal() == (lastUnit() || oneBox ? 0 : 8)
                && second.work().candidates() == (lastUnit() ? 0 : oneBox ? 31 : 8), "Exact losing result changed: " + second);
        helper.assertTrue(first.attemptSequence() < second.attemptSequence()
                && arbitrationA.result.captureSequence() < arbitrationB.result.captureSequence(), "Attempt/capture order changed");
        if (lastUnit()) { helper.assertTrue(second.work().reads() == 0 && second.work().visits() == 0, "Losing zero-probe exit spent work"); }
        var total = stage == 5 ? arbitrationAfter : exits.budget.usage();
        helper.assertTrue(total != null && arbitrationAudit == null, "END instrumentation unavailable: " + arbitrationAudit);
        helper.assertTrue(total.candidates() - arbitrationBefore.candidates() == first.work().candidates() + second.work().candidates()
                && total.reads() - arbitrationBefore.reads() == first.work().reads() + second.work().reads()
                && total.visits() - arbitrationBefore.visits() == first.work().visits() + second.work().visits()
                && total.candidates() <= 128 && total.reads() <= 4096 && total.visits() <= 256, "Shared instrumentation totals differ");
        for (var slot : List.of(arbitrationA, arbitrationB)) {
            helper.assertTrue(slot.descent == null && slot.world.reads == slot.result.work().reads()
                    && slot.world.entityVisits <= slot.result.work().visits()
                    && slot.result.work().reads() <= slot.result.work().candidates() * 96
                    && slot.result.work().visits() <= slot.result.work().candidates() * 16, "Raw adapter work escaped its model budget");
            var record = slot.permit.transfer;
            helper.assertTrue(record != null && slot.permit.rocket.snapshot().orElseThrow() == record.sourceSnapshot()
                    && slot.permit.rocket.flightData().orElseThrow().passengers().equals(record.sourceFlightData().passengers())
                    && slot.permit.rocket.flightData().orElseThrow().fuel().equals(record.sourceFlightData().fuel())
                    && RocketTransferSavedData.get(player.getServer()).find(record.transferId()).orElseThrow() == record
                    && bays(helper).sessionForTests().receipts().stream().noneMatch(row -> row.rocket().equals(record.logicalRocketId())), "Arbitration mutated authority");
        }
        // Delayed cases use the END before/after snapshots rather than unrelated work across the next tick.
        if (stage != 5) {
            helper.assertTrue(arbitrationLoaded == helper.getLevel().getChunkSource().getLoadedChunksCount()
                    && arbitrationTickets.equals(ticketSnapshot()), "Ordered synchronous exits loaded chunks or added tickets: "
                            + BayPassengerExitGameTestAudit.difference(arbitrationTickets, ticketSnapshot()));
        }
        io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_BAY_EXIT_ARBITRATION case={} first={} second={} total={}", scenario, first, second, total);
        done();
    }
    private void tick() {
        if (closed) { return; }
        try {
            ticks++;
            BayPassengerExitGameTestDescentAudit.beforeStimulus(this);
            if (stage == 0) { setup(); }
            else if (stage == 1 && player.getServer().getTickCount() - joinedTick > 70 && RocketTransferSavedData.get(player.getServer()).actionable(flight.record().logicalRocketId())
                    && (otherFlight == null || RocketTransferSavedData.get(player.getServer()).actionable(otherFlight.record().logicalRocketId()))) { detach(); }
            else if (stage == 2) { BayPassengerExitGameTestDescentAudit.land(this); }
            else if (stage == 3) {
                if (scenario == Case.BYPASS) { helper.assertTrue(exits.state.history(player.getUUID(), flight.record().transferId()) != null, "END bypass did not complete"); }
                else { helper.assertTrue(exits.state.history(player.getUUID(), flight.record().transferId()) == null, "Newer teleport did not discard delayed placement"); }
                done();
            } else if (stage == 4 && player.getServer().getTickCount() >= firstDeadline) { exits.sample(player); expectRevoked(); done(); }
            else if (stage == 5) { checkArbitration(); }
            else if (stage == 6 && player.getServer().getTickCount() - offer.descent.corrections.start > 2) {
                var at = player.position(); player.connection.teleport(at.x + .1, at.y, at.z, 25, 10);
                exits.sample(player); expectRevoked(); done();
            }
        } catch (Throwable error) { cleanup(); helper.fail("Bay exit " + scenario + ": " + error); }
    }
    void done() { cleanup(); helper.succeed(); }
    private void cleanup() {
        if (closed) { return; } closed = true; MinecraftForge.EVENT_BUS.unregister(this);
        cacheListenerCleanup.run();
        if (exits != null) { exits.probeLocationForTests(null); }
        if (flight != null) { bays(helper).forgetForTests(flight.record().logicalRocketId()); }
        if (otherFlight != null) { bays(helper).forgetForTests(otherFlight.record().logicalRocketId()); }
        for (var joined : players) {
            if (exits != null) { exits.revoke(joined); }
            try (var ignored = RocketRuntime.internalPassengerMove(joined)) { joined.stopRiding(); }
            joined.getServer().getPlayerList().remove(joined);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        for (long column : forcedColumns) {
            var pos = new net.minecraft.world.level.ChunkPos(column); helper.getLevel().setChunkForced(pos.x, pos.z, false);
        }
        if (flight != null) {
            flight.source().discard(); var journal = RocketTransferSavedData.get(helper.getLevel().getServer()); journal.remove(flight.record().transferId());
            RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), flight.record());
            if (otherFlight != null) { otherFlight.source().discard(); journal.remove(otherFlight.record().transferId());
                RocketTransferEntities.releaseFlightTickets(helper.getLevel().getServer(), otherFlight.record()); }
        }
    }
}
