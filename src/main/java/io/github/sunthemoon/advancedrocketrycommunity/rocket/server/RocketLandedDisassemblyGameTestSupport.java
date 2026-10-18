package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketFailureInjector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;

/**
 * W85 section 7 A1 bridge: one session's real disassembly, flight and transaction services, optionally with a v0.5
 * fault injector, against the world journal or an isolated checked journal whose writes the test performs. It refuses
 * every server except a GameTestServer and is never reachable from gameplay.
 */
public final class RocketLandedDisassemblyGameTestSupport {
    private final GameTestHelper helper;
    private final RocketFlightService flights = new RocketFlightService(null, null);
    private final RocketDisassemblyService disassembly;

    private RocketLandedDisassemblyGameTestSupport(GameTestHelper helper, RocketFailureInjector faults) {
        if (!(helper.getLevel().getServer() instanceof GameTestServer) || !helper.getLevel().getServer().isSameThread()) {
            throw new IllegalStateException("The landed disassembly bridge requires a GameTestServer");
        }
        this.helper = helper;
        disassembly = new RocketDisassemblyService(
                new RocketTransactionExecutor(RocketBlockEntityAdapters.defaults(), faults), flights);
    }

    public static RocketLandedDisassemblyGameTestSupport create(GameTestHelper helper) {
        return create(helper, RocketFailureInjector.NONE);
    }

    public static RocketLandedDisassemblyGameTestSupport create(GameTestHelper helper, RocketFailureInjector faults) {
        return new RocketLandedDisassemblyGameTestSupport(helper, faults);
    }

    public void requestDisassembly(Requester player, RocketEntity rocket) {
        disassembly.requestDisassembly(player, rocket);
    }

    public boolean confirmDisassembly(Requester player, UUID token) {
        return disassembly.confirmDisassembly(player, token);
    }

    public RocketValidationCode disassembleForReleaseTest(RocketEntity rocket, long expectedDiscardFuel) {
        return disassembly.disassembleForReleaseTest(rocket, expectedDiscardFuel);
    }

    public boolean pending(RocketEntity rocket) {
        return flights.landedDisassemblyPending(rocket);
    }

    /** P's stage for the rocket's logical ID, or {@code NONE}. */
    public String stage(UUID logical) {
        var entry = flights.transfers().landedDisassemblies().find(logical);
        return entry == null ? "NONE" : entry.stage().name();
    }

    public boolean resourceMutationAllowed(RocketEntity rocket) {
        return flights.resourceMutationAllowed(rocket);
    }

    /** prepareLaunch's reservation input and its slot count for a new flight. */
    public List<RocketRegion> launchReservations(RocketTransferSavedData journal) {
        return flights.transfers().launchReservations(helper.getLevel().getServer(), journal);
    }

    public int slotsInUse(RocketTransferSavedData journal) {
        return journal.entries().size() + flights.transfers().landedDisassemblies().heldSlots(journal);
    }

    public void reportUnacknowledged() {
        flights.transfers().landedDisassemblies().reportUnacknowledged();
    }

    /** Settles a landed record in this session as restart recovery does, through the world journal. */
    public io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport recover(UUID transferId) {
        return RocketJournalGameTestSupport.recoverFixture(helper,
                () -> flights.transfers().recover(helper.getLevel().getServer(), transferId));
    }

    /**
     * W92-R1: M0's read-only decision against the world journal, with or without its record clause. DISASSEMBLY_PENDING
     * means that M0 would release T; any other code is the one the request answers. Nothing changes.
     */
    public RocketValidationCode admission(RocketEntity rocket, boolean recordClause) {
        var server = helper.getLevel().getServer();
        var journal = RocketTransferSavedData.get(server);
        var transfers = flights.transfers();
        var snapshot = rocket.snapshot().orElseThrow();
        var record = journal.findByLogicalRocket(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
        return transfers.landedDisassemblies().admission(rocket, record, journal, transfers.landedPort(server, journal),
                () -> disassembly.preflight((ServerLevel) rocket.level(), rocket, snapshot), recordClause).code();
    }

    /**
     * M0 and M1 against an isolated checked journal: the production request, preflight, P and port, except that the
     * continuation is queued without a flush, so the test performs each write with the committer it chooses. A null
     * requester is the release-test hook.
     */
    public RocketValidationCode releaseDetached(RocketEntity rocket, Requester requester, RocketTransferSavedData journal) {
        var server = helper.getLevel().getServer();
        var transfers = flights.transfers();
        RocketLandedDisassemblies.Port production = transfers.landedPort(server, journal);
        RocketLandedDisassemblies.Port port = new RocketLandedDisassemblies.Port() {
            @Override public long now() { return production.now(); }
            @Override public boolean bayAllowsRemoval(RocketTransferRecord record) { return production.bayAllowsRemoval(record); }
            @Override public boolean queue(RocketTransferSavedData.Reservation reservation, BooleanSupplier action,
                                           Runnable discarded) {
                return RocketJournalGameTestSupport.queueDetached(helper, journal, reservation, action, discarded);
            }
            @Override public void released(RocketTransferRecord record, UUID entity) { production.released(record, entity); }
            @Override public void restored(RocketTransferRecord record, UUID entity, boolean settle) {
                production.restored(record, entity, settle);
            }
            @Override public void probe(String boundary, RocketTransferRecord record) { }
        };
        var snapshot = rocket.snapshot().orElseThrow();
        var request = disassembly.landedRequest(rocket, requester, requester == null);
        return transfers.landedDisassemblies().begin(rocket, journal, port,
                () -> disassembly.preflight((ServerLevel) rocket.level(), rocket, snapshot), request);
    }

    /** A requester that records consent offers, action-bar codes and disposal reports. */
    public static final class Requester extends FakePlayer {
        public UUID token;
        public String command;
        public long discarded;
        public int offers;
        public final List<String> actionBar = new ArrayList<>();
        public final List<String> messages = new ArrayList<>();

        public Requester(ServerLevel level, UUID id) {
            super(level, new GameProfile(id, "ARCELanded"));
        }

        public String lastActionBar() {
            return actionBar.isEmpty() ? null : actionBar.get(actionBar.size() - 1);
        }

        @Override
        public void displayClientMessage(Component message, boolean overlay) {
            if (message.getContents() instanceof TranslatableContents text) { actionBar.add(text.getKey()); }
        }

        @Override
        public void sendSystemMessage(Component message) {
            ClickEvent click = message.getStyle().getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                command = click.getValue();
                token = UUID.fromString(command.substring(command.lastIndexOf(' ') + 1));
                offers++;
            }
            if (message.getContents() instanceof TranslatableContents text) {
                messages.add(text.getKey());
                if (text.getKey().endsWith("disassembly_fuel_discarded")) {
                    discarded = ((Number) text.getArgs()[0]).longValue();
                }
            }
        }
    }
}
