package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.command.RocketDisassemblyCommands;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlanner;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketManager;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Production disassembly consent, command permissions and failure resource ownership. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketDisassemblyGameTests {
    private RocketDisassemblyGameTests() {
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void offhandAndRepeatedMainHandNeverConfirmDisposal(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                player.setShiftKeyDown(true);
                try {
                    rocket.interact(player, InteractionHand.OFF_HAND);
                    helper.assertTrue(player.token == null, "Offhand created an offer");
                    rocket.interact(player, InteractionHand.MAIN_HAND);
                    UUID first = player.token;
                    rocket.interact(player, InteractionHand.OFF_HAND);
                    rocket.interact(player, InteractionHand.MAIN_HAND);
                    helper.assertTrue(first != null && first.equals(player.token) && player.offers == 2,
                            "Interaction did not keep one explicit confirmation");
                    helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256,
                            "Interaction consumed fuel without command submission");
                    fixture.assertRocketRetained(rocket);
                    fixture.assertSourceEmpty();
                } finally {
                    MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                }
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void serviceRateLimitsOffersAndConfirmationTogether(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                for (int request = 0; request < 9; request++) {
                    manager.requestDisassembly(player, rocket);
                }
                helper.assertTrue(player.offers == 8, "Consent offers bypassed the intent limit");
                helper.assertTrue(!manager.confirmDisassembly(player, player.token),
                        "Confirmation bypassed shared limit");
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256,
                        "Rate rejection consumed fuel");
                fixture.assertRocketRetained(rocket);
                fixture.assertSourceEmpty();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void countdownInvalidatesPreviouslyOfferedConsent(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var flight = rocket.flightData().orElseThrow();
                flight = flight.withFuel(flight.fuel().fill(744).state(), fixture.level.getGameTime());
                rocket.updateFlightData(flight);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                var plan = RocketFlightPlanner.plan(fixture.snapshot.stats(), flight.fuel(),
                        RocketFlightPlanner.EARTH, RocketFlightPlanner.MOON, UUID.randomUUID(), fixture.level.getGameTime());
                var countdown = flight.withPlan(plan.optionalPlan().orElseThrow()).startCountdown(fixture.level.getGameTime());
                rocket.updateFlightData(countdown);
                helper.assertTrue(!manager.confirmDisassembly(player, player.token),
                        "Active flight allowed disassembly");
                helper.assertTrue(rocket.flightData().orElseThrow().equals(countdown),
                        "Rejection changed flight authority");
                fixture.assertRocketRetained(rocket);
                fixture.assertSourceEmpty();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void changedPlayerEligibilityAndMissingEntityRejectConsent(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                player.spectator = true;
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Spectator disposed fuel");
                player.spectator = false;
                manager.requestDisassembly(player, rocket);
                player.dead = true;
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Dead player disposed fuel");
                player.dead = false;
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256,
                        "Invalid player changed fuel");
                fixture.assertRocketRetained(rocket);
                manager.requestDisassembly(player, rocket);
                rocket.discard();
                helper.assertTrue(!manager.confirmDisassembly(player, player.token),
                        "Missing loaded entity was reconstructed");
                fixture.assertSourceEmpty();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void zeroFuelStillRestoresCargoWithoutConfirmation(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fixture.assemble();
                var player = player(fixture, rocket);
                new RocketManager(fixture.adapters).requestDisassembly(player, rocket);
                helper.assertTrue(rocket.isRemoved() && player.token == null, "Empty rocket required consent");
                fixture.assertSourceRestored();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void fueledCargoRequiresSeparateNonOperatorCommand(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                var flight = rocket.flightData().orElseThrow();
                manager.requestDisassembly(player, rocket);
                UUID token = player.token;
                manager.requestDisassembly(player, rocket);
                helper.assertTrue(token != null && token.equals(player.token), "Repeated interaction changed offer");
                helper.assertTrue(rocket.isAlive() && rocket.flightData().orElseThrow().equals(flight),
                        "Offer mutated fuel");
                fixture.assertSourceEmpty();
                helper.assertTrue(!fixture.level.getServer().getCommands().getDispatcher().getRoot()
                        .getChild("arce").getChild("rocket").canUse(player.createCommandSourceStack().withPermission(0)),
                        "Player confirmation exposed operator diagnostics");
                helper.assertTrue(confirmCommand(manager, player) == 1, "Non-operator confirmation was rejected");
                helper.assertTrue(rocket.isRemoved() && player.discarded == 256, "Confirmed amount not reported");
                fixture.assertSourceRestored();
                helper.assertTrue(confirmCommand(manager, player) == 0, "Confirmation replay succeeded");
                fixture.assertSourceRestored();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void changedFuelInvalidatesQuoteWithoutMutation(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                var flight = rocket.flightData().orElseThrow();
                rocket.updateFlightData(flight.withFuel(flight.fuel().fill(1).state(), fixture.level.getGameTime()));
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Stale amount was accepted");
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 257, "Rejection altered fuel");
                fixture.assertRocketRetained(rocket);
                fixture.assertSourceEmpty();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void otherPlayerAndDistantOwnerCannotUseConsent(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var owner = player(fixture, rocket);
                var stranger = new ConsentPlayer(fixture.level, UUID.randomUUID());
                stranger.setPos(rocket.position());
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(stranger, rocket);
                helper.assertTrue(stranger.token == null, "Stranger obtained consent");
                manager.requestDisassembly(owner, rocket);
                helper.assertTrue(!manager.confirmDisassembly(stranger, owner.token), "Token used by another player");
                owner.setPos(rocket.getX() + 9, rocket.getY(), rocket.getZ());
                helper.assertTrue(!manager.confirmDisassembly(owner, owner.token), "Remote disposal succeeded");
                owner.setPos(rocket.position());
                helper.assertTrue(!manager.confirmDisassembly(owner, owner.token),
                        "Failed confirmation was replayable");
                fixture.assertRocketRetained(rocket);
                fixture.assertSourceEmpty();
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256,
                        "Denied disposal lost fuel");
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void logoutAndRestartInvalidateConsentButPreserveFuel(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                manager.onPlayerLoggedOut(new PlayerEvent.PlayerLoggedOutEvent(player));
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Logout retained consent");
                manager.requestDisassembly(player, rocket);
                CompoundTag saved = new CompoundTag();
                helper.assertTrue(rocket.save(saved), "Fuel-bearing entity did not save");
                rocket.discard();
                manager.clear();
                RocketEntity restored = ModEntities.ROCKET.get().create(fixture.level);
                restored.load(saved);
                helper.assertTrue(fixture.level.addFreshEntity(restored), "Reloaded entity did not spawn");
                try {
                    var restarted = new RocketManager(fixture.adapters);
                    helper.assertTrue(!manager.confirmDisassembly(player, player.token)
                            && !restarted.confirmDisassembly(player, player.token), "Restart retained consent");
                    helper.assertTrue(restored.flightData().orElseThrow().fuel().amount() == 256, "Reload lost fuel");
                    fixture.assertRocketRetained(restored);
                    fixture.assertSourceEmpty();
                } finally {
                    restored.discard();
                }
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void occupiedTargetDoesNotDiscardConfirmedFuel(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                fixture.level.setBlockAndUpdate(fixture.chestPosition, Blocks.STONE.defaultBlockState());
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Occupied target was overwritten");
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256,
                        "Rejected transaction lost fuel");
                fixture.assertRocketRetained(rocket);
                fixture.clearChest();
                fixture.assertSourceEmpty();
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    @GameTest(template = "rocket_test", timeoutTicks = 40)
    public static void restorationFailureRollsBackWithoutDiscardingFuel(GameTestHelper helper) {
        EntitySections.whenLoaded(helper, () -> {
            try (var fixture = new RocketAdapterFailureFixture(helper, true)) {
                var rocket = fueled(fixture);
                var player = player(fixture, rocket);
                var manager = new RocketManager(fixture.adapters);
                manager.requestDisassembly(player, rocket);
                fixture.adapter.fault = RocketAdapterFailureFixture.Fault.RESTORE_FALSE;
                helper.assertTrue(!manager.confirmDisassembly(player, player.token), "Faulty restore committed");
                fixture.assertRocketRetained(rocket);
                fixture.assertSourceEmpty();
                helper.assertTrue(rocket.flightData().orElseThrow().fuel().amount() == 256 && player.discarded == 0,
                        "Rollback consumed or reported disposal of remaining fuel");
            }
        }, RocketAdapterFailureFixture.ORIGIN);
    }

    static int confirmCommand(RocketManager manager, ConsentPlayer player) {
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        RocketDisassemblyCommands.register(dispatcher, manager);
        try {
            return dispatcher.execute(player.command.substring(1), player.createCommandSourceStack().withPermission(0));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Player confirmation command is unavailable", exception);
        }
    }

    private static RocketEntity fueled(RocketAdapterFailureFixture fixture) {
        RocketEntity rocket = fixture.assemble();
        var flight = rocket.flightData().orElseThrow();
        rocket.updateFlightData(flight.withFuel(flight.fuel().fill(256).state(), fixture.level.getGameTime()));
        return rocket;
    }

    private static ConsentPlayer player(RocketAdapterFailureFixture fixture, RocketEntity rocket) {
        var player = new ConsentPlayer(fixture.level, fixture.ownerId);
        player.setPos(rocket.position());
        return player;
    }

    static final class ConsentPlayer extends FakePlayer {
        UUID token;
        String command;
        long discarded;
        int offers;
        boolean spectator;
        boolean dead;

        ConsentPlayer(ServerLevel level, UUID owner) {
            super(level, new GameProfile(owner, "ARCEDisassembly"));
        }

        @Override
        public boolean isSpectator() {
            return spectator;
        }

        @Override
        public boolean isAlive() {
            return !dead && super.isAlive();
        }

        @Override
        public void sendSystemMessage(Component message) {
            ClickEvent click = message.getStyle().getClickEvent();
            if (click != null) {
                if (click.getAction() != ClickEvent.Action.SUGGEST_COMMAND) {
                    throw new IllegalStateException("Disposal must require explicit command submission");
                }
                command = click.getValue();
                token = UUID.fromString(command.substring(command.lastIndexOf(' ') + 1));
                offers++;
            }
            if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                    && text.getKey().endsWith("disassembly_fuel_discarded")) {
                discarded = ((Number) text.getArgs()[0]).longValue();
            }
        }
    }
}
