package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** R16's retained wait observation, without issuing a disk proof or a terminal write. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowTerminalObservationGameTests {
    private enum Change { PASSENGER, MANIFEST, DISMOUNT, REMOVAL }
    private EscrowTerminalObservationGameTests() { }

    @GameTest(template = "empty", batch = "escrow_terminal_observation", timeoutTicks = 100)
    public static void retainedWaitDetectsPassengerDismountAndRemoval(GameTestHelper helper) {
        for (var change : Change.values()) { exercise(helper, change); }
        helper.succeed();
    }

    private static void exercise(GameTestHelper helper, Change change) {
        var level = helper.getLevel(); var server = level.getServer();
        var data = ResourceEscrowSavedData.forTests(server); data.bind(server);
        var position = helper.absolutePos(new BlockPos(1, 3, 1));
        var player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), "F6Observer"));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        var rocket = RocketSettlementIdleGameTests.createRocket(level, position, UUID.randomUUID());
        try {
            server.getPlayerList().placeNewPlayer(connection, player); player.setGameMode(GameType.CREATIVE);
            player.teleportTo(level, position.getX() + .5, position.getY(), position.getZ() + .5, 0, 0);
            var flight = rocket.flightData().orElseThrow();
            var manifest = flight.passengers().assign(player.getUUID()).orElseThrow();
            rocket.updateFlightData(flight.withPassengers(manifest));
            var saved = rocket.saveWithoutId(new CompoundTag());
            saved.getCompound("RocketEntityData").put("resource_escrow",
                    new RocketEscrowSection(data.ledger().id(), 0, List.of()).encode());
            rocket.load(saved);
            helper.assertTrue(level.addFreshEntity(rocket) && player.startRiding(rocket, true), "Observer fixture did not mount");
            var boundaries = new AtomicInteger();
            var wait = EscrowGovernedTerminal.waitSource(rocket,
                    () -> EscrowGovernedTerminalPassengers.present(server, manifest, rocket),
                    () -> { boundaries.incrementAndGet(); return true; });
            for (int poll = 0; poll < 2_000; poll++) {
                helper.assertTrue(wait.observe(), "Unchanged wait was invalidated");
            }
            helper.assertTrue(boundaries.get() == 2_000, "Wait skipped its live boundary");
            switch (change) {
                case PASSENGER -> player.setHealth(0);
                case MANIFEST -> rocket.updateFlightData(rocket.flightData().orElseThrow().withPassengers(manifest.remove(player.getUUID())));
                case DISMOUNT -> player.stopRiding();
                case REMOVAL -> rocket.discard();
            }
            helper.assertTrue(!wait.observe() && !wait.unchanged.get(), "Changed wait remained valid: " + change);
            if (change == Change.PASSENGER) { player.setHealth(20); }
            helper.assertTrue(!wait.observe(), "An invalidated wait was resurrected");
        } finally {
            player.setHealth(20); player.stopRiding(); server.getPlayerList().remove(player);
            rocket.discard(); channel.finishAndReleaseAll();
        }
    }
}
