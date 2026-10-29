package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketWorldBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Installed ordinary player route, through native B2 completion and exact cargo materialization. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSealedConsentGameTests {
    private static final List<Scenario> OWNED = new ArrayList<>();
    private RocketSealedConsentGameTests() { }
    @GameTest(template = "rocket_test", batch = "w164_cargo_empty", timeoutTicks = 1200)
    public static void emptyFuelRestoresExactCargoWithoutAQuote(GameTestHelper helper) { start(helper, false); }
    @GameTest(template = "rocket_test", batch = "w164_cargo_fueled", timeoutTicks = 1200)
    public static void nonOperatorConsentPublishesFuelOnceAfterNativeCompletion(GameTestHelper helper) { start(helper, true); }
    private static void start(GameTestHelper helper, boolean fueled) {
        var scenario = new Scenario(helper, fueled); OWNED.add(scenario); helper.onEachTick(scenario::tick);
    }
    @AfterBatch(batch = "w164_cargo_empty") public static void emptyCleanup(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "w164_cargo_fueled") public static void fueledCleanup(ServerLevel level) { cleanup(); }
    private static void cleanup() { OWNED.forEach(Scenario::close); OWNED.clear(); }

    private static final class Scenario {
        final GameTestHelper helper; final boolean fueled; final GameTestTickPacer pacer;
        RocketAdapterFailureFixture fixture; RocketEntity rocket; RocketDisassemblyGameTests.ConsentPlayer player;
        boolean closed;
        Scenario(GameTestHelper helper, boolean fueled) {
            this.helper = helper; this.fueled = fueled; pacer = new GameTestTickPacer(helper.getLevel().getServer());
        }
        void tick() {
            if (closed) { return; } pacer.pace(); var level = helper.getLevel(); var server = level.getServer();
            if (fixture == null) {
                var at = helper.absolutePos(RocketAdapterFailureFixture.ORIGIN);
                if (!level.areEntitiesLoaded(ChunkPos.asLong(at.getX() >> 4, at.getZ() >> 4))) { return; }
                fixture = RocketAdapterFailureFixture.ordinaryCargo(helper); rocket = fixture.assemble();
                if (fueled) { var flight = rocket.flightData().orElseThrow();
                    rocket.updateFlightData(flight.withFuel(flight.fuel().fill(256).state(), level.getGameTime())); }
                player = new RocketDisassemblyGameTests.ConsentPlayer(level, fixture.ownerId); player.setPos(rocket.position());
                RocketRuntime.requestDisassembly(player, rocket);
                if (fueled) {
                    var token = player.token; RocketRuntime.requestDisassembly(player, rocket);
                    helper.assertTrue(token != null && token.equals(player.token), "Repeated offer changed consent");
                    helper.assertTrue(!server.getCommands().getDispatcher().getRoot().getChild("arce").getChild("rocket")
                            .canUse(player.createCommandSourceStack().withPermission(0)), "Consent exposed operator diagnostics");
                    helper.assertTrue(confirm() == 1, "Non-operator consent was refused");
                } else { helper.assertTrue(player.token == null, "Empty fuel required consent"); }
                helper.assertTrue(rocket.isAlive() && player.discarded == 0, "Request completed before native proof");
                fixture.assertSourceEmpty(); return;
            }
            if (rocket.isAlive()) {
                helper.assertTrue(player.discarded == 0 && rocket.flightData().orElseThrow().fuel().amount() == (fueled ? 256 : 0),
                        "Pending request discarded fuel"); return;
            }
            var world = fixture.world();
            if (fixture.snapshot.blocks().stream().anyMatch(block -> world.readBlock(fixture.snapshot.sourceOrigin().add(block.position()))
                    .filter(RocketWorldBlock.fromSnapshotBlock(block)::equals).isEmpty())) { return; }
            fixture.assertSourceRestored();
            helper.assertTrue(player.discarded == (fueled ? 256 : 0), "Reported disposal differs from consent");
            var data = RocketTransactionSavedData.get(server);
            helper.assertTrue(data.acknowledgedCurrent() && data.entries().stream().noneMatch(entry -> entry.record()
                    .rocketEntityIdOptional().filter(rocket.getUUID()::equals).isPresent()), "Cargo publication preceded checked U absence");
            if (fueled) { helper.assertTrue(confirm() == 0, "Consent replay succeeded"); fixture.assertSourceRestored(); }
            close(); helper.succeed();
        }
        int confirm() {
            return helper.getLevel().getServer().getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(0), player.command.substring(1));
        }
        void close() { if (!closed) { closed = true; if (fixture != null) { fixture.close(); } } }
    }
}
