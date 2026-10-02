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
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitCodec;
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
import java.util.function.UnaryOperator;
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
    private static final UUID OTHER = new UUID(0L, 30L);
    private static final UUID LATE_SOURCE = new UUID(0L, 50L);
    private static final BlockPos OTHER_POS = new BlockPos(400, 64, 400);
    private static final BlockPos LATE_SOURCE_POS = new BlockPos(600, 64, 600);
    private static final UUID SECOND_HUB = new UUID(0L, 40L);
    private static final UUID IDLE_SOURCE = new UUID(0L, 60L);

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

        final List<ItemStack> input = new ArrayList<>();

        @Override
        public boolean returnToInput(List<ItemStack> stacks) {
            if (input.size() + stacks.size() > 4) {
                return false;
            }
            stacks.forEach(stack -> input.add(stack.copy()));
            return true;
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

    /**
     * A stub is pruned by a save of its paid endpoint's chunk at least 40 ticks after the acknowledgement. When the
     * only save came earlier, the ledger keeps that chunk dirty from the 40th tick on until a later save prunes it
     * (found by the C13R-F4 crash cuts: a quiet chunk was never saved again, and the stub stayed).
     */
    @Test
    void aStubWhoseOnlySaveCameTooEarlyKeepsItsChunkDirtyUntilPruned() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        Endpoint destination = new Endpoint(DESTINATION);
        TransitLedger ledger = service.transits();
        ledger.attach(destination);
        TransitKey key = transit(service, SOURCE, 1, DESTINATION, TransitRecord::arrived);
        durable(service);
        service.tick(100L);
        assertTrue(destination.destination.incoming().containsKey(key), "not claimed");
        persist(service);
        save(service, destination, DESTINATION_POS);
        service.tick(101L);
        service.tick(141L);
        assertEquals(1, destination.received.size(), "the aged incoming payload did not move");
        persist(service);
        service.tick(142L);
        assertTrue(service.root().orElseThrow().transits().record(key).orElseThrow().stub(), "not a stub");
        save(service, destination, DESTINATION_POS);
        service.tick(143L);
        assertTrue(service.root().orElseThrow().transits().record(key).isPresent(),
                "pruned by a save 2 ticks after the acknowledgement");
        destination.changes = 0;
        for (long now = 144L; now < 181L; now++) {
            service.tick(now);
        }
        assertEquals(0, destination.changes, "the chunk was marked dirty before the acknowledgement aged");
        service.tick(181L);
        service.tick(182L);
        assertTrue(destination.changes > 0, "nothing keeps the quiet chunk dirty for the save that prunes the stub");
        save(service, destination, DESTINATION_POS);
        service.tick(183L);
        assertTrue(service.root().orElseThrow().transits().record(key).isEmpty(), "the aged save did not prune it");
        destination.changes = 0;
        service.tick(184L);
        service.tick(185L);
        assertEquals(0, destination.changes, "the chunk stays dirty after the stub was pruned");
    }

    /**
     * Review C13R2-N3: an incoming payload frozen as a conflict (here one whose item no longer decodes) is frozen and
     * audited once, not on every pass of its endpoint.
     */
    @Test
    void aFrozenIncomingPayloadIsAuditedOnce() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        TransitKey key = transit(service, SOURCE, 1, DESTINATION,
                record -> record.arrived().claimed(DESTINATION).acknowledged(record.dispatchEpoch()));
        durable(service);
        ListTag raw = new ListTag();
        CompoundTag gizmo = new CompoundTag();
        gizmo.putString("id", "removedmod:gizmo");
        gizmo.putByte("Count", (byte) 1);
        raw.add(gizmo);
        CompoundTag section = new CompoundTag();
        ListTag incoming = new ListTag();
        incoming.add(TransitCodec.encodeIncoming(key, TransitPayload.raw(raw)));
        ListTag receipts = new ListTag();
        receipts.add(TransitCodec.encodeKey(key));
        section.put("incoming", incoming);
        section.put("receipts", receipts);
        section.put("conflicts", new ListTag());
        Endpoint destination = new Endpoint(DESTINATION);
        destination.destination.read(section);
        service.transits().attach(destination);
        save(service, destination, DESTINATION_POS);
        for (long now = 100L; now <= 300L; now++) {
            service.tick(now);
        }
        assertTrue(destination.destination.incoming().containsKey(key), "the undecodable payload was dropped");
        int lines = 0;
        for (int page = 0; page < 64; page++) {
            List<String> audit = service.audit().page(null, page);
            if (audit.isEmpty()) {
                break;
            }
            lines += (int) audit.stream().filter(line -> line.contains("action=INCOMING_QUARANTINED ")).count();
        }
        assertEquals(1, lines, "a frozen payload was frozen and audited again on later passes");
    }

    /**
     * Review C13R3 (the safety side of C13R2-N3): a payload frozen as {@code REDIRECT_CONFLICT}, because the ledger
     * names another endpoint (after an operator restored an older file), stays frozen after that endpoint delivered
     * it and its record was pruned. Before the fix, the next pass found no record and moved the stale copy into the
     * receive buffer: a second delivery.
     */
    @Test
    void aRedirectConflictStaysFrozenAfterItsRecordIsPruned() {
        EndgameService service = hubService();
        TransitKey key = transit(service, SOURCE, 1, OTHER, record -> record.arrived().claimed(OTHER));
        durable(service);
        CompoundTag section = new CompoundTag();
        ListTag incoming = new ListTag();
        incoming.add(TransitCodec.encodeIncoming(key, payload()));
        ListTag receipts = new ListTag();
        receipts.add(TransitCodec.encodeKey(key));
        section.put("incoming", incoming);
        section.put("receipts", receipts);
        section.put("conflicts", new ListTag());
        Endpoint stale = new Endpoint(DESTINATION);
        stale.destination.read(section);
        service.transits().attach(stale);
        save(service, stale, DESTINATION_POS);
        for (long now = 100L; now <= 150L; now++) {
            service.tick(now);
        }
        assertTrue(stale.destination.conflicts().contains(key), "the stale payload was not frozen");
        service.coalesced(root -> root.transits().remove(key)); // The other endpoint delivered it.
        for (long now = 151L; now <= 250L; now++) {
            service.tick(now);
        }
        assertTrue(stale.received.isEmpty() && stale.destination.incoming().containsKey(key),
                "the frozen stale payload moved into the receive buffer once its record was gone (a second delivery)");
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
        List<ItemStack> drops = ledger.settleRemoval(destination, 2L);
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
        assertEquals(List.of(copy.id), EndpointObservations.transit(tag, Set.of(TYPE)).stream()
                .map(EndpointObservations.ShownAt::id).toList());
    }

    /**
     * Review C12R-L3: a same-ID copy of the source in another (indexed) chunk is inert; its chunk's save does not
     * count as the source's own, so the escrowed entry waits for the source's chunk.
     */
    @Test
    void aCopysChunkSaveDoesNotPersistTheRegisteredSourcesEntry() {
        EndgameService service = hubService();
        Endpoint source = new Endpoint(SOURCE);
        service.transits().attach(source);
        assertEquals(EndgameCode.OK, service.transits().admitEscrow(SOURCE, OWNER));
        source.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        BlockPos copyAt = OTHER_POS.east();
        CompoundTag chunk = new Endpoint(OTHER).chunkTag(OTHER_POS);
        chunk.getList("block_entities", 10).add(source.chunkTag(copyAt).getList("block_entities", 10).get(0));
        service.observe(LEVEL, new ChunkPos(copyAt).toLong(), chunk, false);
        service.tick(10L);
        service.tick(50L);
        TransitKey key = new TransitKey(SOURCE, 1L);
        assertTrue(service.root().orElseThrow().transits().record(key).isEmpty(),
                "the copy's chunk save registered the source's entry");
        save(service, source, SOURCE_POS);
        service.tick(51L);
        service.tick(91L);
        assertTrue(service.root().orElseThrow().transits().record(key).isPresent(),
                "the source's own chunk save did not register the entry");
    }

    /**
     * Review C12R-H1: an entry escrowed to a destination that is then removed, settled and evicted still registers
     * (ADR-054 section 11 step 2), and the root that names the unknown destination is written and loads again.
     */
    @Test
    void aRegistrationToAnEvictedDestinationKeepsTheRootLoadable() {
        EndgameSavedData data = EndgameSavedData.create();
        EndgameService service = new EndgameService(() -> EndgameSettings.DEFAULTS, () -> Set.of(TYPE),
                () -> TransitLimits.DEFAULTS);
        service.startForTest(data);
        service.coalesced(root -> root.register(SOURCE, KIND, OWNER, LEVEL, SOURCE_POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(DESTINATION, KIND, OWNER, LEVEL, DESTINATION_POS.asLong(), false, 2048,
                64));
        persist(service);
        Endpoint source = new Endpoint(SOURCE);
        service.transits().attach(source);
        assertEquals(EndgameCode.OK, service.transits().admitEscrow(SOURCE, OWNER));
        source.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        service.endpointRemoved(DESTINATION, 5L);
        service.coalesced(root -> root.settle(DESTINATION, root::pinned));
        assertEquals(List.of(DESTINATION), service.barrier(root -> root.evictOwner(OWNER, root::pinned)),
                "no record pins the destination yet");
        save(service, source, SOURCE_POS);
        service.tick(10L);
        service.tick(50L);
        EndgameRoot root = service.root().orElseThrow();
        TransitRecord registered = root.transits().record(new TransitKey(SOURCE, 1L)).orElseThrow();
        assertEquals(DESTINATION, registered.destination());
        assertTrue(root.endpoint(DESTINATION).isEmpty() && root.tombstone(DESTINATION).isEmpty());
        EndgameSavedData reloaded = EndgameSavedData.load(data.save(new CompoundTag()));
        assertTrue(reloaded.operational(), "the written root does not load");
        assertEquals(DESTINATION, reloaded.view().transits().record(new TransitKey(SOURCE, 1L)).orElseThrow()
                .destination());
    }

    /**
     * Review C12R-L4: {@code endpoint retire} of an ID without an index record (a young tombstone here) is refused
     * before anything changes: the stub paid there stays until that endpoint's chunk saved the move.
     */
    @Test
    void aRefusedRetireChangesNothing() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        TransitKey key = new TransitKey(SOURCE, 1L);
        service.coalesced(root -> {
            root.registerTransit(TransitRecord.registered(SOURCE, new OutboxEntry(1L, DESTINATION, payload(), 1, 20,
                    EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L).arrived().claimed(DESTINATION)
                    .acknowledged(root.saveEpoch()).asStub());
            return null;
        });
        persist(service);
        service.endpointRemoved(DESTINATION, 5L);
        EndgameRoot.Change change = service.barrier(root -> TransitLedger.retire(root, DESTINATION));
        assertEquals(EndgameCode.ENDPOINT_NOT_FOUND, change.code());
        assertTrue(service.root().orElseThrow().transits().record(key).isPresent(),
                "the refused retire pruned the stub paid at the removed endpoint");
        EndgameRoot.Change retired = service.barrier(root -> TransitLedger.retire(root, SOURCE));
        assertEquals(EndgameCode.OK, retired.code(), "an indexed endpoint retires");
        assertTrue(service.root().orElseThrow().endpoint(SOURCE).isEmpty());
    }

    // ---- Review C12R-M1: one busy endpoint does not starve the others -------------------------------------------

    /** The destination (the hub, first in ID order) and {@link #OTHER} and {@link #LATE_SOURCE} are registered. */
    private static EndgameService hubService() {
        EndgameService service = service(TransitLimits.DEFAULTS);
        service.coalesced(root -> root.register(OTHER, KIND, OWNER, LEVEL, OTHER_POS.asLong(), false, 2048, 64));
        service.coalesced(root -> root.register(LATE_SOURCE, KIND, OWNER, LEVEL, LATE_SOURCE_POS.asLong(), false,
                2048, 64));
        persist(service);
        return service;
    }

    /** A record from {@code source} registered in the root, shaped by {@code state}, durable after two writes. */
    private static TransitKey transit(EndgameService service, UUID source, long seq, UUID destination,
                                      UnaryOperator<TransitRecord> state) {
        service.coalesced(root -> {
            root.registerTransit(state.apply(TransitRecord.registered(source, new OutboxEntry(seq, destination,
                    payload(), 1, 20, EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), 0L)));
            return null;
        });
        return new TransitKey(source, seq);
    }

    private static void durable(EndgameService service) {
        persist(service);
        persist(service);
    }

    @Test
    void recordsWithoutAnApplicableRowCostNoReconciliation() {
        EndgameService service = hubService();
        for (long seq = 1; seq <= 64; seq++) {
            transit(service, SOURCE, seq, DESTINATION, seq <= 32
                    ? record -> new TransitRecord(record.key(), record.system(), record.owner(),
                    record.destination(), record.payload(), record.paidFe(), record.dispatchEpoch(), 1_000_000L,
                    TransitRecord.State.IN_TRANSIT, null, false, 0L, false)
                    : record -> record.arrived().claimed(DESTINATION).acknowledged(record.dispatchEpoch())
                    .asStub());
        }
        TransitKey key = transit(service, SOURCE, 65, OTHER, TransitRecord::arrived);
        durable(service);
        Endpoint hub = new Endpoint(DESTINATION);
        Endpoint other = new Endpoint(OTHER);
        service.transits().attach(hub);
        service.transits().attach(other);
        service.tick(100L);
        assertTrue(other.destination.incoming().containsKey(key),
                "the hub's stubs and records in transit spent the budget the other endpoint's claim needed");
    }

    @Test
    void aHubThatSpendsTheBudgetGivesTheNextEndpointTheFirstTurn() {
        EndgameService service = hubService();
        for (long seq = 1; seq <= 64; seq++) {
            transit(service, SOURCE, seq, DESTINATION, TransitRecord::arrived);
        }
        TransitKey key = transit(service, SOURCE, 65, OTHER, TransitRecord::arrived);
        durable(service);
        Endpoint hub = new Endpoint(DESTINATION);
        for (int i = 0; i < 9; i++) {
            hub.received.add(new ItemStack(Items.STONE)); // A full receive buffer: every claim of the hub waits.
        }
        Endpoint other = new Endpoint(OTHER);
        service.transits().attach(hub);
        service.transits().attach(other);
        service.tick(100L);
        assertTrue(other.destination.incoming().isEmpty(), "the hub goes first and spends all 64 reconciliations");
        service.tick(101L);
        assertTrue(other.destination.incoming().containsKey(key), "the next tick did not start at the next endpoint");
        assertEquals(TransitRecord.State.ARRIVED, service.root().orElseThrow().transits()
                .record(new TransitKey(SOURCE, 1L)).orElseThrow().state());
    }

    /**
     * Review C13-F1: two hubs that spend the whole budget without changing the table make the cursor alternate between
     * them, so every sweep tick starts at the same parity. An idle endpoint between them in ID order that a new record
     * names must still claim it once it arrives, as before the idle set: within a tick, not never.
     */
    @Test
    void anIdleEndpointBetweenTwoBudgetSpendingHubsClaimsItsArrival() {
        for (long registerAt : new long[] {300L, 301L}) {
            for (int travel : new int[] {20, 21}) {
                long latency = claimLatency(registerAt, travel);
                assertTrue(latency >= 0L && latency <= 1L, "registered at " + registerAt + " with travel " + travel
                        + ": claimed " + latency + " ticks after arriving (-1: never)");
            }
        }
    }

    /** Ticks from the arrival of a record registered at {@code registerAt} for the idle endpoint to its claim, or -1. */
    private static long claimLatency(long registerAt, int travel) {
        EndgameService service = hubService();
        service.coalesced(root -> root.register(SECOND_HUB, KIND, OWNER, LEVEL, new BlockPos(800, 64, 800).asLong(),
                false, 2048, 64));
        service.coalesced(root -> root.register(IDLE_SOURCE, KIND, OWNER, LEVEL, new BlockPos(1000, 64, 1000)
                .asLong(), false, 2048, 64));
        for (long seq = 1; seq <= 64; seq++) {
            transit(service, SOURCE, seq, DESTINATION, TransitRecord::arrived);
            transit(service, LATE_SOURCE, seq, SECOND_HUB, TransitRecord::arrived);
        }
        durable(service);
        Endpoint hubA = new Endpoint(DESTINATION);
        Endpoint idle = new Endpoint(OTHER);
        Endpoint hubB = new Endpoint(SECOND_HUB);
        for (int i = 0; i < 9; i++) {
            hubA.received.add(new ItemStack(Items.STONE)); // Full receive buffers: every claim of the hubs waits.
            hubB.received.add(new ItemStack(Items.STONE));
        }
        service.transits().attach(hubA);
        service.transits().attach(idle);
        service.transits().attach(hubB);
        TransitKey key = new TransitKey(IDLE_SOURCE, 1L);
        for (long now = 100L; now <= registerAt + 600L; now++) {
            if (now == registerAt) {
                long at = now;
                service.coalesced(root -> {
                    root.registerTransit(TransitRecord.registered(IDLE_SOURCE, new OutboxEntry(1L, OTHER, payload(), 1,
                            travel, EndgameSystem.RAILGUN), OWNER, root.saveEpoch(), at));
                    return null;
                });
                durable(service);
            }
            service.tick(now);
            if (idle.destination.incoming().containsKey(key)) {
                return now - (registerAt + travel);
            }
        }
        return -1L;
    }

    /**
     * The C13 idle set over consecutive ticks (review C13-F7): an endpoint with nothing to reconcile is skipped until
     * an escrow admission, a table change naming it or its attachment wakes it; a change outside the table (here a raw
     * escrow, as a block-entity reload makes one) is seen by the next sweep, within 20 ticks.
     */
    @Test
    void idleEndpointsAreWokenAndSweptWithinTwentyTicks() {
        EndgameService service = hubService();
        TransitLedger ledger = service.transits();
        Endpoint other = new Endpoint(OTHER);
        ledger.attach(other);
        assertFalse(ledger.idleForTest(OTHER), "an attached endpoint starts awake");
        service.tick(100L);
        assertTrue(ledger.idleForTest(OTHER), "nothing to reconcile");
        service.tick(101L);
        assertTrue(ledger.idleForTest(OTHER));
        assertEquals(EndgameCode.OK, ledger.admitEscrow(OTHER, OWNER));
        assertFalse(ledger.idleForTest(OTHER), "an escrow admission wakes the source");
        service.tick(102L);
        assertTrue(ledger.idleForTest(OTHER), "idle again: the admission was not followed by an escrow");
        TransitKey key = transit(service, SOURCE, 1, OTHER, record -> record);
        service.tick(103L);
        assertFalse(ledger.idleForTest(OTHER), "a record naming it woke it in the next tick");
        service.coalesced(root -> {
            root.transits().remove(key);
            return null;
        });
        service.tick(104L);
        assertTrue(ledger.idleForTest(OTHER), "the record is gone");
        ledger.detach(OTHER, other);
        ledger.attach(other);
        assertFalse(ledger.idleForTest(OTHER), "a new attachment wakes it");
        service.tick(105L);
        assertTrue(ledger.idleForTest(OTHER));
        other.source.escrow(DESTINATION, payload(), 25_000, 20, EndgameSystem.RAILGUN);
        service.tick(106L);
        assertTrue(ledger.idleForTest(OTHER), "a change outside the table waits for a sweep");
        long woken = -1L;
        for (long now = 107L; now <= 126L && woken < 0; now++) {
            service.tick(now);
            if (!ledger.idleForTest(OTHER)) {
                woken = now;
            }
        }
        assertTrue(woken >= 0L, "no sweep within 20 ticks saw the escrow");
    }

    @Test
    void aPassTheBudgetCutShortResumesAtTheRecordItStoppedAt() {
        EndgameService service = hubService();
        TransitKey late = transit(service, LATE_SOURCE, 1, DESTINATION, TransitRecord::arrived);
        durable(service);
        Endpoint hub = new Endpoint(DESTINATION);
        service.transits().attach(hub);
        service.tick(100L);
        assertTrue(hub.destination.receipts().contains(late), "the hub did not claim the late source's record");
        // The ledger falls behind the claim (an operator's restore of an older root), and 64 records that cannot
        // land come first in key order.
        service.coalesced(root -> {
            root.transits().replace(root.transits().record(late).orElseThrow().returned());
            return null;
        });
        for (int i = 0; i < 9; i++) {
            hub.received.add(new ItemStack(Items.STONE));
        }
        for (long seq = 1; seq <= 64; seq++) {
            transit(service, SOURCE, seq, DESTINATION, TransitRecord::arrived);
        }
        durable(service);
        service.tick(101L);
        assertEquals(TransitRecord.State.ARRIVED, service.root().orElseThrow().transits().record(late).orElseThrow()
                .state(), "64 earlier records spend the budget first");
        service.tick(102L);
        TransitRecord recovered = service.root().orElseThrow().transits().record(late).orElseThrow();
        assertEquals(TransitRecord.State.CLAIMED, recovered.state(), "the next pass did not resume at the record");
        assertEquals(DESTINATION, recovered.paidEndpoint());
    }
}
