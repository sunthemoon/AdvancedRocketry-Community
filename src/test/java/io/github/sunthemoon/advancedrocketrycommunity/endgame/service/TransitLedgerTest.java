package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDeviceTags;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitDestinationState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitSourceState;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitTags;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** ADR-054 section 11 through the service: the END-tick passes, observations, admission, removal and retirement. */
final class TransitLedgerTest {
    private static final String TYPE = "advancedrocketrycommunity:railgun";
    private static final ResourceLocation KIND = ResourceLocation.tryBuild("advancedrocketrycommunity", "railgun");
    private static final ResourceLocation LEVEL = ResourceLocation.tryBuild("minecraft", "overworld");
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID SOURCE = new UUID(0L, 10L);
    private static final UUID DESTINATION = new UUID(0L, 20L);
    private static final BlockPos SOURCE_POS = new BlockPos(8, 64, 8);
    private static final BlockPos DESTINATION_POS = new BlockPos(200, 64, 200);

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    /** A loaded endpoint with a receive buffer of nine stacks. */
    private static final class Endpoint implements TransitEndpoint {
        final UUID id;
        final TransitSourceState source = new TransitSourceState();
        final TransitDestinationState destination = new TransitDestinationState();
        final List<ItemStack> received = new ArrayList<>();
        boolean frozen;
        int changes;

        Endpoint(UUID id) {
            this.id = id;
        }

        @Override
        public UUID endpointId() {
            return id;
        }

        @Override
        public Optional<UUID> endpointOwner() {
            return Optional.of(OWNER);
        }

        @Override
        public boolean transitFrozen() {
            return frozen;
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
                    return received.size() + payloads.stream().mapToInt(List::size).sum() <= 9;
                }

                @Override
                public void insert(List<ItemStack> stacks) {
                    stacks.forEach(stack -> received.add(stack.copy()));
                }
            };
        }

        @Override
        public void transitChanged() {
            changes++;
        }

        /** The chunk tag a save of this endpoint's chunk would hold. */
        CompoundTag chunkTag(BlockPos pos) {
            CompoundTag section = new CompoundTag();
            source.write(section);
            destination.write(section);
            CompoundTag root = new CompoundTag();
            root.putUUID(EndgameDeviceTags.DEVICE_ID, id);
            root.put(TransitTags.SECTION, section);
            CompoundTag blockEntity = new CompoundTag();
            blockEntity.putString("id", TYPE);
            blockEntity.putInt("x", pos.getX());
            blockEntity.putInt("y", pos.getY());
            blockEntity.putInt("z", pos.getZ());
            blockEntity.put(EndgameDeviceTags.ROOT, root);
            ListTag list = new ListTag();
            list.add(blockEntity);
            CompoundTag chunk = new CompoundTag();
            chunk.put("block_entities", list);
            return chunk;
        }
    }

    private static EndgameService service(TransitLimits limits) {
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, () -> Set.of(TYPE), () -> limits);
        service.startForTest(EndgameSavedData.create());
        service.coalesced(root -> root.register(SOURCE, KIND, OWNER, LEVEL, SOURCE_POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(DESTINATION, KIND, OWNER, LEVEL, DESTINATION_POS.asLong(), false, 2048,
                64));
        persist(service);
        return service;
    }

    /** A successful root write: everything so far becomes durable. */
    private static void persist(EndgameService service) {
        service.root().orElseThrow().markPersisted();
    }

    private static void save(EndgameService service, Endpoint endpoint, BlockPos pos) {
        service.observe(LEVEL, new ChunkPos(pos).toLong(), endpoint.chunkTag(pos), false);
    }

    private static TransitPayload payload() {
        return TransitPayload.of(List.of(new ItemStack(Items.DIAMOND, 7))).orElseThrow();
    }

    @Test
    void aTransferRunsFromEscrowToPruningThroughTheTickPasses() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        Endpoint source = new Endpoint(SOURCE);
        Endpoint destination = new Endpoint(DESTINATION);
        TransitLedger ledger = service.transits();
        ledger.attach(source);
        ledger.attach(destination);
        assertEquals(EndgameCode.OK, ledger.admitEscrow(SOURCE, OWNER));
        source.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        service.tick(100L);
        assertTrue(service.root().orElseThrow().transits().records().isEmpty(), "registered before a save");
        assertTrue(source.changes > 0, "an unpersisted entry did not keep the chunk dirty");
        save(service, source, SOURCE_POS);
        service.tick(101L);
        service.tick(140L);
        assertTrue(service.root().orElseThrow().transits().records().isEmpty(), "registered before 40 ticks");
        service.tick(141L);
        TransitKey key = new TransitKey(SOURCE, 1L);
        TransitRecord record = service.root().orElseThrow().transits().record(key).orElseThrow();
        assertEquals(TransitRecord.State.IN_TRANSIT, record.state());
        assertEquals(161L, record.arriveAt());
        assertEquals(1L, service.root().orElseThrow().dispatchedThrough(SOURCE));
        persist(service);
        service.tick(142L);
        assertTrue(source.source.outbox().isEmpty(), "not released once the record was durable");
        service.tick(161L);
        assertEquals(TransitRecord.State.CLAIMED, service.root().orElseThrow().transits().record(key).orElseThrow()
                .state(), "an arrived, durable record was not claimed by its loaded destination");
        assertTrue(destination.received.isEmpty() && destination.destination.incoming().containsKey(key));
        save(service, destination, DESTINATION_POS);
        service.tick(162L);
        service.tick(202L);
        assertEquals(1, destination.received.size(), "the aged incoming payload did not move");
        assertTrue(service.root().orElseThrow().transits().record(key).orElseThrow().acknowledged());
        persist(service);
        service.tick(203L);
        assertTrue(service.root().orElseThrow().transits().record(key).orElseThrow().stub(),
                "a durably acknowledged record kept its payload");
        save(service, destination, DESTINATION_POS);
        service.tick(250L);
        assertTrue(service.root().orElseThrow().transits().record(key).isEmpty(), "the stub was not pruned");
        assertFalse(destination.destination.holdsContents(), "the destination still holds transit contents");
        assertFalse(source.source.holdsContents(), "the source still holds transit contents");
        assertTrue(service.status().contains("transits=0 stubs=0"), service.status());
    }

    @Test
    void escrowAdmissionCountsKnownOutboxEntriesAndAPendingObservationAppliesAtLoad() {
        EndgameService service = service(new TransitLimits(256, 2));
        Endpoint source = new Endpoint(SOURCE);
        TransitLedger ledger = service.transits();
        ledger.attach(source);
        source.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        assertEquals(EndgameCode.OK, ledger.admitEscrow(SOURCE, OWNER));
        source.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        assertEquals(EndgameCode.TRANSIT_LIMIT, ledger.admitEscrow(SOURCE, OWNER), "two known entries, limit two");
        assertEquals(EndgameCode.AWAITING_WORLD_SAVE, ledger.admitEscrow(new UUID(5L, 5L), OWNER),
                "an unregistered source escrowed");

        // An observation drained while the endpoint was not loaded applies when it loads.
        ledger.detach(SOURCE);
        save(service, source, SOURCE_POS);
        service.tick(10L);
        Endpoint reloaded = new Endpoint(SOURCE);
        CompoundTag section = new CompoundTag();
        source.source.write(section);
        reloaded.source.read(section);
        ledger.attach(reloaded);
        assertTrue(reloaded.source.persistedAndAged(1L, 50L), "the pending observation was lost");
    }

    @Test
    void aRemovalSettlesClaimsFromLiveStateAndARetirementReturnsThem() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        Endpoint destination = new Endpoint(DESTINATION);
        TransitLedger ledger = service.transits();
        ledger.attach(destination);
        TransitKey incoming = new TransitKey(SOURCE, 1L);
        TransitKey moved = new TransitKey(SOURCE, 2L);
        for (TransitKey key : List.of(incoming, moved)) {
            service.coalesced(root -> {
                root.registerTransit(TransitRecord.registered(SOURCE, new io.github.sunthemoon.advancedrocketrycommunity
                        .endgame.transit.OutboxEntry(key.seq(), DESTINATION, payload(), 1, 20,
                        EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L).arrived());
                return null;
            });
        }
        persist(service);
        service.tick(1L);
        assertEquals(2, destination.destination.incoming().size());
        // The second payload moved (its acknowledgement lost): only its receipt stays.
        destination.destination.takeIncoming(moved);
        service.coalesced(root -> {
            root.transits().replace(root.transits().record(moved).orElseThrow().arrived().claimed(DESTINATION));
            return null;
        });
        List<TransitPayload> drops = ledger.settleRemoval(destination, 2L);
        EndgameRoot root = service.root().orElseThrow();
        assertEquals(TransitRecord.State.ARRIVED, root.transits().record(incoming).orElseThrow().state(),
                "the incoming claim did not return to ARRIVED");
        assertTrue(root.transits().record(moved).orElseThrow().acknowledged(), "the moved claim was not acknowledged");
        assertTrue(drops.isEmpty(), "a payload paid elsewhere or returned was dropped");
        assertTrue(root.retired(DESTINATION), "the removal did not retire the ID");
        assertTrue(root.pinned(DESTINATION), "a tombstone a record names is not pinned");

        // MISSING or endpoint retire: an unacknowledged claim with no live state returns to ARRIVED.
        service.coalesced(r -> {
            r.transits().replace(r.transits().record(incoming).orElseThrow().claimed(DESTINATION));
            return null;
        });
        service.coalesced(r -> TransitLedger.retireWithoutLiveState(r, DESTINATION, true));
        assertEquals(TransitRecord.State.ARRIVED, service.root().orElseThrow().transits().record(incoming)
                .orElseThrow().state());
    }

    @Test
    void aTagCarryingTransitContentsFreezesInsteadOfRegistering() {
        Endpoint copy = new Endpoint(new UUID(0L, 99L));
        copy.source.escrow(DESTINATION, payload(), 1, 20, EndgameSystem.RAILGUN);
        CompoundTag tag = copy.chunkTag(SOURCE_POS);
        assertEquals(Optional.of(true), EndpointObservations.persisted(tag, Set.of(TYPE), copy.id,
                SOURCE_POS.asLong()));
        Endpoint empty = new Endpoint(new UUID(0L, 98L));
        assertEquals(Optional.of(false), EndpointObservations.persisted(empty.chunkTag(SOURCE_POS), Set.of(TYPE),
                empty.id, SOURCE_POS.asLong()));
        assertEquals(Set.of(copy.id), EndpointObservations.transit(tag, Set.of(TYPE)).keySet());
    }
}
