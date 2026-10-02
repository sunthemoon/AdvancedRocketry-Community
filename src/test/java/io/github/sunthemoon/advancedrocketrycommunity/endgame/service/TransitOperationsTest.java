package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-054 sections 9 and 11 operator and owner actions: redirect, purge, resettle and resolve. */
final class TransitOperationsTest {
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "railgun");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID STRANGER = new UUID(2L, 2L);
    private static final UUID SOURCE = new UUID(0L, 10L);
    private static final UUID DESTINATION = new UUID(0L, 20L);
    private static final UUID SPARE = new UUID(0L, 30L);
    private static final UUID FOREIGN = new UUID(0L, 40L);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    private static EndgameService service() {
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, Set::of,
                () -> TransitLimits.DEFAULTS);
        service.startForTest(EndgameSavedData.create(), true);
        for (UUID id : List.of(SOURCE, DESTINATION, SPARE)) {
            service.coalesced(root -> root.register(id, KIND, OWNER, LEVEL, id.getLeastSignificantBits(), false, 2048,
                    64));
        }
        service.coalesced(root -> root.register(FOREIGN, KIND, STRANGER, LEVEL, 40L, false, 2048, 64));
        service.barrier(root -> null);
        return service;
    }

    private static TransitPayload payload() {
        return TransitPayload.of(List.of(new ItemStack(Items.DIAMOND, 7))).orElseThrow();
    }

    private static TransitKey record(EndgameService service, long seq, boolean claimed) {
        service.barrier(root -> {
            TransitRecord record = TransitRecord.registered(SOURCE, new OutboxEntry(seq, DESTINATION, payload(), 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L).arrived();
            root.registerTransit(claimed ? record.claimed(DESTINATION) : record);
            return null;
        });
        return new TransitKey(SOURCE, seq);
    }

    @Test
    void aRedirectWaitsForTheDestinationsDurableRemovalAndFollowsTheRouteRule() {
        EndgameService service = service();
        TransitOperations operations = service.transitOperations();
        TransitKey key = record(service, 1L, false);
        assertEquals(EndgameCode.TRANSFER_NOT_FOUND, operations.redirect(new TransitKey(SOURCE, 9L), SPARE, OWNER,
                true, 0L));
        assertEquals(EndgameCode.DESTINATION_ACTIVE, operations.redirect(key, SPARE, OWNER, true, 0L),
                "a redirect away from a live destination");
        service.coalesced(root -> root.remove(DESTINATION));
        assertEquals(EndgameCode.ROUTE_REFUSED, operations.redirect(key, SPARE, OWNER, true, 0L),
                "a system without a route rule redirected");
        operations.route(EndgameSystem.RAILGUN, (root, record, target, operator) -> EndgameCode.OK);
        assertEquals(EndgameCode.ROUTE_REFUSED, operations.redirect(key, new UUID(9L, 9L), OWNER, true, 0L),
                "a redirect to an unknown endpoint");
        assertEquals(EndgameCode.UNAUTHORIZED, operations.redirect(key, SPARE, STRANGER, false, 0L));
        assertEquals(EndgameCode.TARGET_FOREIGN, operations.redirect(key, FOREIGN, OWNER, false, 0L));
        assertTrue(service.writePending(), "the removal is still only in memory");
        assertEquals(EndgameCode.OK, operations.redirect(key, SPARE, OWNER, false, 100L));
        assertFalse(service.writePending(), "the redirect is not a barrier");
        TransitRecord moved = service.root().orElseThrow().transits().record(key).orElseThrow();
        assertTrue(moved.redirected() && moved.destination().equals(SPARE));
        TransitKey second = record(service, 2L, false);
        service.coalesced(root -> root.remove(SPARE));
        operations.route(EndgameSystem.RAILGUN, (root, record, target, operator) -> EndgameCode.OK);
        assertEquals(EndgameCode.ROOT_BUSY, operations.redirect(second, SOURCE, OWNER, false, 110L),
                "two owner barriers inside the 20-tick spacing");
        TransitKey claimed = record(service, 3L, true);
        assertEquals(EndgameCode.DESTINATION_ACTIVE, operations.redirect(claimed, SOURCE, OWNER, true, 200L),
                "a claimed record was redirected");
    }

    /**
     * Review C12R-M3: an owner redirect to a station's endpoint enters that station's 100-tick cooldown and the shared
     * spacing; a refusal changes nothing; an operator's redirect is exempt and counted in the status.
     */
    @Test
    void ownerRedirectsEnterTheStationsCooldownAndOperatorBarriersAreCounted() {
        EndgameService service = service();
        TransitOperations operations = service.transitOperations();
        UUID station = new UUID(5L, 5L);
        operations.route(EndgameSystem.RAILGUN, new TransitOperations.Route() {
            @Override
            public EndgameCode check(EndgameRoot root, TransitRecord record, EndpointRecord target, boolean operator) {
                return EndgameCode.OK;
            }

            @Override
            public Optional<UUID> station(EndgameRoot root, EndpointRecord target) {
                return Optional.of(station);
            }
        });
        TransitKey first = record(service, 1L, false);
        TransitKey second = record(service, 2L, false);
        TransitKey third = record(service, 3L, false);
        service.barrier(root -> root.remove(DESTINATION));
        long exempt = service.barrierSpacing().exemptFlushes();
        assertEquals(EndgameCode.OK, operations.redirect(first, SPARE, OWNER, false, 1_000L));
        assertEquals(exempt, service.barrierSpacing().exemptFlushes(), "an owner redirect is a spaced barrier");
        assertEquals(EndgameCode.ROOT_BUSY, operations.redirect(second, SPARE, OWNER, false, 1_050L),
                "inside the station's 100-tick cooldown");
        assertEquals(DESTINATION, service.root().orElseThrow().transits().record(second).orElseThrow().destination(),
                "a refused redirect changed the record");
        assertEquals(EndgameCode.OK, operations.redirect(second, SPARE, OWNER, true, 1_050L), "operators are exempt");
        assertEquals(exempt + 1, service.barrierSpacing().exemptFlushes());
        assertTrue(service.status().contains("exempt_barriers=" + (exempt + 1)), service.status());
        assertEquals(EndgameCode.OK, operations.redirect(third, SPARE, OWNER, false, 1_100L),
                "the cooldown ends 100 ticks after the station's last owner redirect");
    }

    @Test
    void purgeAndResettleChangeOnlyWhatTheyName() {
        EndgameService service = service();
        TransitOperations operations = service.transitOperations();
        TransitKey key = record(service, 1L, false);
        assertEquals(EndgameCode.OK, operations.purge(key, OWNER, 0L));
        assertTrue(service.root().orElseThrow().transits().record(key).isEmpty());
        assertEquals(1L, service.root().orElseThrow().dispatchedThrough(SOURCE), "a purge lowered dispatched_through");
        TransitKey claimed = record(service, 2L, true);
        assertEquals(EndgameCode.DESTINATION_ACTIVE, operations.resettle(claimed, false, OWNER, 0L),
                "a claim at a live endpoint was resettled");
        service.coalesced(root -> root.remove(DESTINATION));
        assertEquals(EndgameCode.OK, operations.resettle(claimed, false, OWNER, 0L));
        assertEquals(TransitRecord.State.ARRIVED, service.root().orElseThrow().transits().record(claimed)
                .orElseThrow().state());
        TransitKey other = record(service, 3L, true);
        assertEquals(EndgameCode.OK, operations.resettle(other, true, OWNER, 0L));
        assertTrue(service.root().orElseThrow().transits().record(other).orElseThrow().acknowledged());
        assertEquals(EndgameCode.TRANSFER_NOT_FOUND, operations.resettle(other, true, OWNER, 0L), "resettled twice");
        assertEquals(1, operations.list(0).stream().filter(line -> line.contains("DESTINATION_MISSING")).count(),
                operations.list(0).toString());
    }

    /** A retired endpoint brought back by a crash, as section 9 resolves it. */
    private static final class Retired implements TransitEndpoint {
        final TransitSourceState source = new TransitSourceState();
        final TransitDestinationState destination = new TransitDestinationState();
        final List<ItemStack> input = new ArrayList<>();
        final List<ItemStack> received = new ArrayList<>();

        @Override
        public UUID endpointId() {
            return DESTINATION;
        }

        @Override
        public Optional<UUID> endpointOwner() {
            return Optional.of(OWNER);
        }

        @Override
        public boolean transitFrozen() {
            return true;
        }

        @Override
        public TransitSourceState source() {
            return source;
        }

        @Override
        public TransitDestinationState destination() {
            return destination;
        }

        @Override
        public TransitDestinationState.ReceiveBuffer receiveBuffer() {
            return new TransitDestinationState.ReceiveBuffer() {
                @Override
                public boolean fits(List<List<ItemStack>> payloads) {
                    return true;
                }

                @Override
                public void insert(List<ItemStack> stacks) {
                    received.addAll(stacks);
                }
            };
        }

        @Override
        public void transitChanged() {
        }

        @Override
        public boolean returnToInput(List<ItemStack> stacks) {
            input.addAll(stacks);
            return true;
        }
    }

    @Test
    void aResolveReturnsOnlyWhatProvesToBeTheEndpointsOwn() {
        EndgameService service = service();
        TransitKey ownClaim = record(service, 1L, true);
        TransitKey elsewhere = new TransitKey(SOURCE, 2L);
        // The endpoint was removed: its ID is retired, its tombstone records dispatched_through 1 (seq 1 registered).
        service.barrier(root -> {
            root.registerTransit(TransitRecord.registered(DESTINATION, new OutboxEntry(1L, SPARE, payload(), 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L));
            root.remove(DESTINATION);
            return null;
        });
        Retired copy = new Retired();
        CompoundTag section = new CompoundTag();
        section.putLong("next_seq", 3L);
        ListTag outbox = new ListTag();
        outbox.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeEntry(
                new OutboxEntry(1L, SPARE, payload(), 1, 20, EndgameSystem.RAILGUN)));
        outbox.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeEntry(
                new OutboxEntry(2L, SPARE, payload(), 1, 20, EndgameSystem.RAILGUN)));
        section.put("outbox", outbox);
        ListTag incoming = new ListTag();
        incoming.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeIncoming(
                ownClaim, payload()));
        incoming.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeIncoming(
                elsewhere, payload()));
        section.put("incoming", incoming);
        ListTag receipts = new ListTag();
        receipts.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeKey(ownClaim));
        section.put("receipts", receipts);
        section.put("conflicts", new ListTag());
        copy.source.read(section);
        copy.destination.read(section);
        TransitOperations.Resolved resolved = service.transitOperations().resolve(copy, OWNER, 0L, null);
        assertEquals(new TransitOperations.Resolved(1, 1, 2, 1, 0), resolved);
        assertEquals(1, copy.input.size(), "only the unregistered entry (seq 2) goes back to the input");
        assertEquals(1, copy.received.size(), "only the payload claimed here joins the receive buffer");
        EndgameRoot root = service.root().orElseThrow();
        assertTrue(root.transits().record(ownClaim).orElseThrow().acknowledged(), "the claim was not acknowledged");
        assertFalse(copy.source.holdsContents() || copy.destination.holdsContents());
    }

    /**
     * Review C12R-L2: a MISSING endpoint keeps its index record and dispatched_through; a resolve of its returning
     * copy gives back the entry above dispatched_through and discards the registered one.
     */
    @Test
    void aResolveOfAReturningMissingEndpointReturnsWhatItsDispatchedThroughProvesUnregistered() {
        EndgameService service = service();
        service.barrier(root -> {
            root.registerTransit(TransitRecord.registered(DESTINATION, new OutboxEntry(1L, SPARE, payload(), 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L));
            root.markMissing(DESTINATION);
            return null;
        });
        EndgameRoot root = service.root().orElseThrow();
        assertTrue(root.endpoint(DESTINATION).isPresent() && root.tombstone(DESTINATION).isEmpty(),
                "a MISSING endpoint keeps its index record and has no tombstone");
        Retired copy = new Retired();
        CompoundTag section = new CompoundTag();
        section.putLong("next_seq", 3L);
        ListTag outbox = new ListTag();
        outbox.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeEntry(
                new OutboxEntry(1L, SPARE, payload(), 1, 20, EndgameSystem.RAILGUN)));
        outbox.add(io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec.encodeEntry(
                new OutboxEntry(2L, SPARE, payload(), 1, 20, EndgameSystem.RAILGUN)));
        section.put("outbox", outbox);
        section.put("incoming", new ListTag());
        section.put("receipts", new ListTag());
        section.put("conflicts", new ListTag());
        copy.source.read(section);
        copy.destination.read(section);
        TransitOperations.Resolved resolved = service.transitOperations().resolve(copy, OWNER, 0L, null);
        assertEquals(new TransitOperations.Resolved(1, 0, 1, 0, 0), resolved);
        assertEquals(1, copy.input.size(), "the unregistered entry (seq 2) did not go back to the input");
        assertFalse(copy.source.holdsContents());
    }
}
