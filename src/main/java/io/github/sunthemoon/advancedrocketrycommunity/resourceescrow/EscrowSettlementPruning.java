package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Exact released groups remove only a leading marker prefix, never resources or epochs. */
public final class EscrowSettlementPruning {
    private EscrowSettlementPruning() { }
    record Endpoint(EscrowEndpoint reference, CompoundTag survivor, long generation) { }
    record Group(String digest, Set<UUID> members, List<Endpoint> endpoints) { }
    record Visit(Group group, Endpoint endpoint) { }

    /** Scheduling only: all authority comes from acknowledged receipts, including after restart. */
    static final class Cursor {
        private EscrowLedger cached;
        private List<Group> groups = List.of();
        private final ArrayDeque<String> priority = new ArrayDeque<>();
        private final Map<String, Integer> endpoints = new java.util.HashMap<>();
        private int cursor;
        void enqueue(EscrowLedger root, EscrowCoordinator.SettlementAcknowledgement ack) {
            EscrowTags.require(ack.committed() && root.id().equals(ack.ledger())
                    && root.writerGeneration() == ack.generation(), "Pruning needs the exact O4 acknowledgement");
            var group = released(root).stream().filter(value -> value.digest().equals(ack.digest())).findFirst().orElseThrow();
            EscrowTags.require(group.members().equals(ack.members()), "O4 pruning group changed");
            priority.remove(ack.digest());
            EscrowTags.require(priority.size() < EscrowLimits.TOMBSTONES, "Pruning queue exceeded");
            priority.addLast(ack.digest());
        }
        Visit next(EscrowLedger root, Set<UUID> unresolved) {
            return next(root, unresolved, ignored -> false);
        }
        Visit next(EscrowLedger root, Set<UUID> unresolved, java.util.function.Predicate<EscrowEndpoint.Key> busy) {
            released(root);
            if (groups.isEmpty()) { return null; }
            String first = priority.pollFirst();
            Group group = first == null ? groups.get(Math.floorMod(cursor++, groups.size()))
                    : groups.stream().filter(value -> value.digest().equals(first)).findFirst().orElse(null);
            if (group == null || group.endpoints().isEmpty() || group.members().stream().anyMatch(unresolved::contains)) { return null; }
            int index = endpoints.getOrDefault(group.digest(), 0);
            endpoints.put(group.digest(), (index + 1) % group.endpoints().size());
            var endpoint = group.endpoints().get(index);
            return busy.test(endpoint.reference().key()) ? null : new Visit(group, endpoint);
        }
        List<Group> released(EscrowLedger root) {
            if (cached != root) {
                groups = EscrowSettlementPruning.groups(root); cached = root;
                var live = groups.stream().map(Group::digest).collect(java.util.stream.Collectors.toSet());
                endpoints.keySet().retainAll(live); priority.removeIf(digest -> !live.contains(digest));
            }
            return groups;
        }
        void tick(MinecraftServer server, EscrowCoordinator coordinator) {
            var root = coordinator.ledger(); var unresolved = coordinator.unresolvedIds();
            var visit = next(root, unresolved, coordinator::pruningBusy);
            if (visit != null) {
                try { apply(server, root, visit, unresolved, groups); }
                catch (IllegalArgumentException | java.util.NoSuchElementException unavailable) {
                    // A malformed/unloaded survivor cannot starve another released group.
                }
            }
        }
    }

    static boolean busy(EscrowEndpoint.Key endpoint, List<EscrowOperation> durable,
            java.util.Collection<EscrowOperation> queued, java.util.Collection<EscrowOperation> captured,
            Set<EscrowEndpoint.Key> frozen) {
        return frozen.contains(endpoint) || durable.stream().filter(op -> !op.settlement().isEmpty()).anyMatch(op -> names(op, endpoint))
                || java.util.stream.Stream.concat(queued.stream(), captured.stream()).anyMatch(op -> names(op, endpoint));
    }
    private static boolean names(EscrowOperation op, EscrowEndpoint.Key endpoint) {
        // A floor intent names both snapshot slots of L; it is safe to skip every physical slot of that L.
        return op.source().key().equals(endpoint) || op.destination().key().equals(endpoint)
                || endpoint.kind() == EscrowEndpoint.Kind.ENTITY && op.guard().rocket().equals(endpoint.logicalRocket());
    }

    static List<Group> groups(EscrowLedger root) {
        var tags = new LinkedHashMap<String, List<CompoundTag>>();
        for (var tag : root.tombstones()) {
            if (tag.getInt("schema_version") == EscrowSettlementTombstones.SCHEMA && tag.getBoolean("released")) {
                tags.computeIfAbsent(tag.getString("decision_digest"), ignored -> new ArrayList<>()).add(tag);
            }
        }
        var result = new ArrayList<Group>();
        for (var entry : tags.entrySet()) {
            var references = new LinkedHashMap<EscrowEndpoint.Key, Endpoint>();
            for (var tag : entry.getValue()) {
                for (var raw : EscrowTags.list(tag, "survivors", 2)) {
                    var survivor = (CompoundTag) raw;
                    var reference = EscrowCodec.endpoint(survivor.getCompound("endpoint"));
                    var old = references.putIfAbsent(reference.key(), new Endpoint(reference, survivor.copy(), tag.getLong("release_generation")));
                    EscrowTags.require(old == null || old.survivor().equals(survivor), "Released group has contradictory survivors");
                }
            }
            result.add(new Group(entry.getKey(), EscrowSettlementTombstones.retiredIds(entry.getValue()), List.copyOf(references.values())));
        }
        return List.copyOf(result);
    }

    /** A later-group removal waits instead of leaving a hole or throwing during ordinary saving. */
    static List<EscrowPrefix.Marker> prune(List<EscrowPrefix.Marker> markers, Set<UUID> retired, Set<UUID> unresolved) {
        if (retired.stream().anyMatch(unresolved::contains)) { return markers; }
        int removed = 0;
        while (removed < markers.size() && retired.contains(markers.get(removed).operation())) { removed++; }
        for (int i = removed; i < markers.size(); i++) {
            if (retired.contains(markers.get(i).operation())) { return markers; }
        }
        return List.copyOf(markers.subList(removed, markers.size()));
    }

    public static RocketEscrowSection rocket(RocketEntity rocket, RocketEscrowSection section) {
        if (section.markers().isEmpty()) { return section; }
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) { return section; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || section.markers().isEmpty() || !data.ledger().id().equals(section.ledger())) { return section; }
        UUID first = section.markers().get(0).operation();
        var root = data.ledger(); var coordinator = data.bind(level.getServer());
        var groups = EscrowRuntime.of(level.getServer()).settlementGroups(root);
        for (var group : groups) {
            if (group.members().contains(first)) { return rocket(rocket, section, group.digest(), group.members(), root, groups, coordinator); }
        }
        return section;
    }
    public static RocketEscrowSection rocket(RocketEntity rocket, RocketEscrowSection section, String digest, Set<UUID> members) {
        if (section.markers().isEmpty()) { return section; }
        if (!(rocket.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || !rocket.operational()) { return section; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null || !data.ledger().id().equals(section.ledger())) { return section; }
        var coordinator = data.bind(level.getServer());
        var root = data.ledger();
        return rocket(rocket, section, digest, members, root, EscrowRuntime.of(level.getServer()).settlementGroups(root), coordinator);
    }
    private static RocketEscrowSection rocket(RocketEntity rocket, RocketEscrowSection section, String digest, Set<UUID> members,
            EscrowLedger root, List<Group> groups, EscrowCoordinator coordinator) {
        var unresolved = coordinator.unresolvedIds();
        for (var group : groups) {
            if (!group.digest().equals(digest) || !group.members().equals(members)) { continue; }
            for (var chosen : group.endpoints()) {
                var reference = chosen.reference();
                if (reference.kind() != EscrowEndpoint.Kind.ENTITY || !reference.identity().equals(rocket.getUUID())
                        || RocketEscrowAudit.placement(reference, rocket) != EscrowReason.NONE || coordinator.pruningBusy(reference.key())) { continue; }
                var fuel = rocket.flightData().orElseThrow().fuel();
                if (!chosen.survivor().getString("signature_kind").equals(EscrowSignatureKind.FUEL_VECTOR_V1.name())
                        || !chosen.survivor().getString("invariant_digest").equals(EscrowInvariant.rocket(rocket))) { continue; }
                var state = new EscrowPrefix.State(root.id(), section.epoch(), fuel.resourceSignature(), section.markers());
                if (frontier(root, groups, reference, state, fuel.totalAmount(), fuel.resourceSignature(), unresolved)) {
                    return new RocketEscrowSection(section.ledger(), section.epoch(), prune(section.markers(), members, unresolved));
                }
            }
        }
        return section;
    }

    static boolean frontier(EscrowLedger root, EscrowEndpoint endpoint, EscrowPrefix.State current,
            long quantity, String nativeSignature, Set<UUID> unresolved) {
        return frontier(root, groups(root), endpoint, current, quantity, nativeSignature, unresolved);
    }
    static boolean frontier(EscrowLedger root, List<Group> groups, EscrowEndpoint endpoint, EscrowPrefix.State current,
            long quantity, String nativeSignature, Set<UUID> unresolved) {
        CompoundTag latest = null; long generation = -1; var trusted = new java.util.HashSet<UUID>();
        var auditedMarkers = new java.util.HashSet<EscrowPrefix.Marker>();
        for (var group : groups) {
            for (var chosen : group.endpoints()) {
                if (!chosen.reference().key().equals(endpoint.key()) || !chosen.survivor().getBoolean("bound")) { continue; }
                trusted.addAll(group.members());
                auditedMarkers.addAll(markers(chosen.survivor()));
                if (latest == null || generation < chosen.generation()) { latest = chosen.survivor(); generation = chosen.generation(); }
            }
        }
        if (latest == null || !root.id().equals(current.ledger()) || current.epoch() < latest.getLong("epoch")) { return false; }
        if (current.epoch() == latest.getLong("epoch")) {
            var expected = markers(latest); int removed = expected.size() - current.markers().size();
            return current.signature().equals(latest.getString("signature")) && quantity == latest.getLong("quantity") && removed >= 0
                    && expected.subList(0, removed).stream().allMatch(marker -> trusted.contains(marker.operation()) && !unresolved.contains(marker.operation()))
                    && expected.subList(removed, expected.size()).equals(current.markers());
        }
        trusted.removeAll(unresolved);
        if (current.markers().stream().anyMatch(marker -> trusted.contains(marker.operation()) && !auditedMarkers.contains(marker))) { return false; }
        var chain = EscrowPrefix.chains(root.records()).get(endpoint.key());
        if (chain == null) { return false; }
        var live = new EscrowPrefix.State(root.id(), current.epoch(), nativeSignature,
                current.markers().stream().filter(marker -> !trusted.contains(marker.operation())).toList());
        var classified = EscrowPrefix.classify(root.id(), chain, live);
        return classified == EscrowPrefix.Classification.PREFIX || classified == EscrowPrefix.Classification.AFTER;
    }
    private static List<EscrowPrefix.Marker> markers(CompoundTag survivor) {
        return EscrowTags.list(survivor, "markers", 8).stream().map(raw -> {
            var marker = (CompoundTag) raw;
            return new EscrowPrefix.Marker(EscrowTags.uuid(marker, "op_id"), EscrowTags.number(marker, "seq"),
                    EscrowTags.string(marker, "role"), EscrowTags.number(marker, "epoch_after"));
        }).toList();
    }

    private static void apply(MinecraftServer server, EscrowLedger root, Visit visit, Set<UUID> unresolved, List<Group> groups) {
        var endpoint = visit.endpoint().reference();
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.tryParse(endpoint.level())));
        if (level == null) { return; }
        if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
            if (level.getEntity(endpoint.identity()) instanceof RocketEntity rocket) {
                rocket.pruneSettledEscrow(visit.group().digest(), visit.group().members());
            }
        } else if (server instanceof net.minecraft.gametest.framework.GameTestServer || Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) {
            EscrowTestEndpoints.requireEnabled(server);
            var chunk = level.getChunkSource().getChunkNow(endpoint.x() >> 4, endpoint.z() >> 4);
            if (chunk == null) { return; }
            var source = chunk.getBlockEntity(new BlockPos(endpoint.x(), endpoint.y(), endpoint.z()));
            if (source == null || !endpoint.identity().equals(source.getPersistentData().getCompound("arce_escrow_fixture").getUUID("identity"))
                    || !endpoint.type().equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(source.getType()).toString())) { return; }
            var state = EscrowTestEndpoints.state(source.getPersistentData(), root.id(), false);
            if (state.markers().isEmpty() || !frontier(root, groups, endpoint, state, EscrowTestEndpoints.amount(source.getPersistentData()), state.signature(), unresolved)) { return; }
            var markers = prune(state.markers(), visit.group().members(), unresolved);
            if (markers.equals(state.markers())) { return; }
            EscrowTestEndpoints.putState(source.getPersistentData(), new EscrowPrefix.State(root.id(), state.epoch(), state.signature(), markers), false);
            source.setChanged();
        }
    }
}
