package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Frozen O1 decision. Observations are deliberately not O2 durability proofs. */
public record EscrowSettlementIntent(Choice choice, UUID ledger, UUID actor, String actorName,
                                     long previewTick, long confirmedTick, List<Member> members,
                                     List<Observation> observations, CompoundTag floor, String digest) {
    static final int NEW_ACTOR_NAME_LIMIT = 16;
    static final String ACTOR_NAME_REFUSAL = "Settlement actor name must contain 1 to 16 ASCII characters (33-126, no spaces); use the server console for a nonconforming player profile.";
    public enum Choice { ACCEPT_CURRENT, ABANDON, TRANSFER_FLOOR_RETIRE }
    public record Member(UUID id, EscrowOperation.Phase phase, EscrowReason reason, String digest) {
        public Member {
            java.util.Objects.requireNonNull(id); java.util.Objects.requireNonNull(phase);
            java.util.Objects.requireNonNull(reason); EscrowTags.digest(digest);
        }
        static Member of(EscrowOperation op) {
            return new Member(op.id(), op.phase(), op.reason(), RocketNativeResources.signature(EscrowCodec.operation(op)));
        }
        boolean matches(EscrowOperation op) {
            CompoundTag tag = EscrowCodec.operation(op); tag.remove("settlement");
            tag.putString("phase", phase.name()); tag.putString("reason", reason.name());
            return id.equals(op.id()) && digest.equals(RocketNativeResources.signature(tag));
        }
    }
    public record Observation(EscrowEndpoint endpoint, EscrowPrefix.State state, boolean bound, long quantity,
                              EscrowSignatureKind signatureKind, String invariantDigest) {
        public Observation(EscrowEndpoint endpoint, EscrowPrefix.State state, boolean bound, long quantity) {
            this(endpoint, state, bound, quantity, EscrowSignatureKind.settlement(endpoint), EscrowInvariant.reference(endpoint));
        }
        public Observation {
            java.util.Objects.requireNonNull(endpoint);
            java.util.Objects.requireNonNull(signatureKind); EscrowTags.digest(invariantDigest);
            EscrowTags.require(signatureKind == EscrowSignatureKind.settlement(endpoint), "Unexpected settlement signature projection");
            EscrowTags.require(quantity >= 0 && (state != null || quantity == 0 && !bound), "Invalid missing observation");
            EscrowTags.require(signatureKind != EscrowSignatureKind.ITEM_BUFFER_V1 || quantity == 0,
                    "Item buffer quantity is an unused zero diagnostic");
            EscrowTags.require(state == null || bound || state.epoch() == 0 && state.markers().isEmpty(), "Invalid neutral observation");
        }
        public boolean missing() { return state == null; }
        public Observation checkpointed() {
            return !missing() && !bound && signatureKind == EscrowSignatureKind.FUEL_VECTOR_V1
                    ? new Observation(endpoint, state, true, quantity, signatureKind, invariantDigest) : this;
        }
        public RocketEscrowSection normalizedWrapper() {
            EscrowTags.require(!missing() && signatureKind == EscrowSignatureKind.FUEL_VECTOR_V1, "Not a native survivor");
            return new RocketEscrowSection(state.ledger(), state.epoch(), state.markers());
        }
    }
    public EscrowSettlementIntent {
        java.util.Objects.requireNonNull(choice); java.util.Objects.requireNonNull(ledger); java.util.Objects.requireNonNull(actor);
        EscrowTags.require(actorName != null && !actorName.isBlank()
                && actorName.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 128, "Invalid audit actor name");
        EscrowTags.require(previewTick >= 0 && (confirmedTick == -1 || confirmedTick >= previewTick), "Invalid settlement observation ticks");
        EscrowTags.bound(floor, 1_024, 16, 128);
        members = List.copyOf(members); observations = List.copyOf(observations); floor = floor.copy();
        EscrowTags.require(!members.isEmpty() && members.size() <= EscrowLimits.OPERATIONS
                && !observations.isEmpty() && observations.size() <= EscrowLimits.OPERATIONS * 2, "Settlement component limit exceeded");
        var ids = new HashSet<UUID>(); var keys = new HashSet<EscrowEndpoint.Key>();
        for (Member member : members) {
            EscrowTags.require(ids.add(member.id()), "Duplicate settlement member");
            EscrowTags.require(choice == Choice.TRANSFER_FLOOR_RETIRE
                    ? member.phase() == EscrowOperation.Phase.SETTLING_ABANDON && member.reason() == EscrowReason.FLOOR_CONFLICT
                    : member.phase() == EscrowOperation.Phase.PREPARED || member.phase() == EscrowOperation.Phase.REPAIR
                    || member.phase() == EscrowOperation.Phase.STALLED, "Invalid original settlement member phase");
        }
        for (Observation observation : observations) {
            EscrowTags.require(keys.add(observation.endpoint().key())
                    && (observation.missing() || ledger.equals(observation.state().ledger())), "Duplicate or foreign settlement observation");
        }
        boolean missing = observations.stream().anyMatch(Observation::missing);
        EscrowTags.require(choice == Choice.ACCEPT_CURRENT ? !missing : missing, "Settlement choice does not match missing endpoints");
        if (choice == Choice.TRANSFER_FLOOR_RETIRE) {
            EscrowCodec.validateSection(floor, false);
            UUID protectedIdentity = floor.getUUID("entity_id"), logical = floor.getUUID("rocket_id");
            EscrowTags.require(floor.hasUUID("transfer_id") && members.size() == 1
                    && observations.stream().allMatch(Observation::missing)
                    && observations.stream().anyMatch(observation -> observation.endpoint().identity().equals(protectedIdentity)
                    && logical.equals(observation.endpoint().logicalRocket())), "Invalid floor absence decision");
        } else { EscrowTags.require(floor.isEmpty(), "Unexpected floor intent"); }
        EscrowTags.digest(digest);
        EscrowTags.require(digest.equals(hash(choice, ledger, actor, actorName, previewTick, members, observations, floor)), "Settlement digest mismatch");
        EscrowTags.bound(base(choice, ledger, actor, actorName, previewTick, members, observations, floor),
                EscrowLimits.OP_BYTES, 14, 480);
    }
    @Override public CompoundTag floor() { return floor.copy(); }
    public Set<UUID> ids() { return members.stream().map(Member::id).collect(java.util.stream.Collectors.toUnmodifiableSet()); }
    public List<Observation> checkpointed() { return observations.stream().map(Observation::checkpointed).toList(); }

    public static EscrowSettlementIntent preview(EscrowLedger root, UUID selected, Choice choice, UUID actor,
                                                 String actorName, long tick, List<Observation> observations) {
        requireNewActorName(actorName);
        EscrowTags.require(choice != Choice.TRANSFER_FLOOR_RETIRE, "Use an explicit floor preview");
        var component = component(root.records(), selected);
        EscrowTags.require(component.stream().allMatch(op -> op.kind().transfer()
                && op.settlement().isEmpty() && (op.phase() == EscrowOperation.Phase.PREPARED
                || op.phase() == EscrowOperation.Phase.REPAIR || op.phase() == EscrowOperation.Phase.STALLED)
                && op.reason() != EscrowReason.RELOCATED_ENDPOINT && op.reason() != EscrowReason.FLOOR_CONFLICT),
                "Component requires offline repair or is not eligible for settlement");
        EscrowTags.require(component.stream().anyMatch(op -> op.phase() == EscrowOperation.Phase.REPAIR
                || op.phase() == EscrowOperation.Phase.STALLED), "Component has no manual conflict or persisted stall");
        var members = component.stream().map(Member::of).toList();
        requireReceiptRoom(root, members.stream().map(Member::id).toList());
        var sorted = sorted(observations); requireEndpoints(component, sorted);
        return create(choice, root.id(), actor, actorName, tick, -1, members, sorted, new CompoundTag());
    }
    public static EscrowSettlementIntent floorPreview(EscrowLedger root, UUID operation, CompoundTag floor, UUID actor,
                                                      String actorName, long tick, List<Observation> observations) {
        requireNewActorName(actorName);
        EscrowTags.require(root.records().stream().noneMatch(op -> op.guard().rocket().equals(floor.getUUID("rocket_id"))),
                "Floor still has an unretired operation");
        EscrowTags.require(root.floors().stream().anyMatch(floor::equals), "Floor is not authoritative");
        requireReceiptRoom(root, List.of(operation));
        return create(Choice.TRANSFER_FLOOR_RETIRE, root.id(), actor, actorName, tick, -1,
                List.of(new Member(operation, EscrowOperation.Phase.SETTLING_ABANDON, EscrowReason.FLOOR_CONFLICT,
                        RocketNativeResources.signature(floor))), sorted(observations), floor);
    }
    /** New decisions use the login-name bound; persisted decisions retain their decode bound. */
    static void requireNewActorName(String name) {
        EscrowTags.require(name != null && !name.isEmpty() && name.length() <= NEW_ACTOR_NAME_LIMIT
                && name.chars().allMatch(character -> character >= 33 && character <= 126), ACTOR_NAME_REFUSAL);
    }
    public EscrowSettlementIntent confirm(UUID confirmingActor, String suppliedDigest, long tick, List<Observation> current) {
        EscrowTags.require(actor.equals(confirmingActor) && suppliedDigest != null
                && suppliedDigest.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 512
                && digest.equals(suppliedDigest), "Preview actor or digest changed");
        EscrowTags.require(observations.equals(sorted(current)), "Endpoint observations changed since preview");
        EscrowTags.require(tick >= previewTick && (observations.stream().noneMatch(Observation::missing)
                || tick - previewTick >= 200), "Missing endpoints require observations at least 200 ticks apart");
        return create(choice, ledger, actor, actorName, previewTick, tick, members, observations, floor);
    }
    public void requireConfirmed() {
        EscrowTags.require(confirmedTick >= previewTick
                && (observations.stream().noneMatch(Observation::missing) || confirmedTick - previewTick >= 200),
                "Unconfirmed missing observation");
    }
    public EscrowLedger freeze(EscrowLedger root) {
        requireConfirmed(); EscrowTags.require(root.id().equals(ledger), "Settlement ledger changed");
        EscrowTags.require(choice != Choice.TRANSFER_FLOOR_RETIRE, "Floor intent needs an explicit operation");
        var component = component(root.records(), members.get(0).id());
        EscrowTags.require(component.stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet()).equals(ids()),
                "Connected component changed since preview");
        for (Member member : members) {
            var op = component.stream().filter(candidate -> candidate.id().equals(member.id())).findFirst().orElseThrow();
            EscrowTags.require(op.settlement().isEmpty() && op.phase() == member.phase() && op.reason() == member.reason()
                    && member.matches(op), "Record changed since preview");
        }
        var frozen = root.replace(
                root.records().stream().map(op -> ids().contains(op.id()) ? op.settle(this) : op).toList(), root.floors(), root.tombstones());
        EscrowSettlementCapacity.preflight(frozen); EscrowCodec.encode(frozen);
        frozen.records().stream().filter(op -> ids().contains(op.id())).forEach(op -> EscrowSettlementTombstones.create(op, this));
        return frozen;
    }
    public EscrowOperation floorOperation(long sequence, long tick) {
        requireConfirmed(); EscrowTags.require(choice == Choice.TRANSFER_FLOOR_RETIRE, "Not a floor intent");
        var endpoint = observations.stream().map(Observation::endpoint)
                .filter(value -> value.identity().equals(floor.getUUID("entity_id"))).findFirst().orElseThrow();
        return new EscrowOperation(members.get(0).id(), sequence, actor, tick, EscrowOperation.Phase.SETTLING_ABANDON,
                EscrowReason.FLOOR_CONFLICT, floor, RocketNativeResources.signature(floor), endpoint, endpoint,
                EscrowCodec.floorGuard(floor), EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE, encode());
    }
    public static List<EscrowOperation> component(List<EscrowOperation> records, UUID selected) {
        EscrowTags.require(records.size() <= EscrowLimits.OPERATIONS, "Component traversal limit exceeded");
        var first = records.stream().filter(op -> op.id().equals(selected)).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown operation"));
        var result = new HashSet<EscrowOperation>(); result.add(first);
        for (int pass = 0; pass < EscrowLimits.OPERATIONS; pass++) {
            var snapshot = List.copyOf(result); int before = result.size();
            records.stream().filter(op -> snapshot.stream().anyMatch(other -> touches(op, other))).forEach(result::add);
            if (before == result.size()) { break; }
        }
        return result.stream().sorted(Comparator.comparingLong(EscrowOperation::sequence)).toList();
    }
    private static boolean touches(EscrowOperation a, EscrowOperation b) {
        return a.guard().rocket().equals(b.guard().rocket())
                || a.source().identity().equals(b.source().identity()) || a.source().identity().equals(b.destination().identity())
                || a.destination().identity().equals(b.source().identity()) || a.destination().identity().equals(b.destination().identity());
    }
    static void validateComponents(List<EscrowOperation> records) {
        var checked = new HashSet<String>();
        for (EscrowOperation op : records) {
            if (op.settlement().isEmpty()) { continue; } // Earlier development settling phases are retained locked.
            var intent = decode(op.settlement()); intent.requireConfirmed();
            if (!checked.add(intent.digest())) { continue; }
            var component = component(records, op.id());
            EscrowTags.require(component.stream().map(EscrowOperation::id).collect(java.util.stream.Collectors.toSet()).equals(intent.ids()),
                    "Settlement omits a connected operation");
            for (EscrowOperation member : component) {
                EscrowTags.require(member.settlement().equals(op.settlement()), "Split settlement decision");
                var frozen = intent.members().stream().filter(value -> value.id().equals(member.id())).findFirst().orElseThrow();
                EscrowTags.require(member.kind() == EscrowOperation.Kind.TRANSFER_FLOOR_RETIRE
                        ? member.payload().equals(intent.floor()) && frozen.digest().equals(member.payloadHash())
                        : frozen.matches(member), "Frozen settlement record changed");
            }
            if (intent.choice() != Choice.TRANSFER_FLOOR_RETIRE) { requireEndpoints(component, intent.observations()); }
        }
    }
    static void reserve(EscrowLedger root) {
        var digests = new HashSet<String>(); int count = root.tombstones().size();
        for (var op : root.records()) {
            if (op.settlement().isEmpty()) { continue; }
            var intent = decode(op.settlement());
            if (digests.add(intent.digest())) {
                count += (int) intent.ids().stream().filter(id -> root.tombstones().stream().noneMatch(tag -> tag.getUUID("op_id").equals(id))).count();
            }
        }
        EscrowTags.require(count <= EscrowLimits.TOMBSTONES, RECEIPTS_FULL);
        if (digests.isEmpty()) { return; }
        long bytes = io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize.uncompressedBytes(EscrowCodec.encode(root));
        EscrowTags.require(bytes + (count - root.tombstones().size()) * 2_048L <= EscrowLimits.ROOT_BYTES,
                "Settlement retirement byte reservation exhausted");
    }
    static final String RECEIPTS_FULL = "Permanent settlement receipt capacity exhausted (64 members across accept-current, abandon and floor settlement); stopped-server whole-world offline repair required. No receipt eviction.";
    static int reservedReceipts(EscrowLedger root) {
        var ids = receiptIds(root);
        return ids.size() - root.tombstones().size();
    }
    private static Set<UUID> receiptIds(EscrowLedger root) {
        var ids = root.tombstones().stream().map(tag -> tag.getUUID("op_id")).collect(java.util.stream.Collectors.toSet());
        root.records().stream().filter(op -> !op.settlement().isEmpty()).map(EscrowOperation::id).forEach(ids::add);
        return ids;
    }
    static void requireReceiptRoom(EscrowLedger root, java.util.Collection<UUID> members) {
        var ids = receiptIds(root); ids.addAll(members);
        EscrowTags.require(ids.size() <= EscrowLimits.TOMBSTONES, RECEIPTS_FULL);
    }
    private static void requireEndpoints(List<EscrowOperation> component, List<Observation> observations) {
        var keys = component.stream().flatMap(op -> java.util.stream.Stream.of(op.source().key(), op.destination().key()))
                .collect(java.util.stream.Collectors.toSet());
        EscrowTags.require(keys.equals(observations.stream().map(value -> value.endpoint().key()).collect(java.util.stream.Collectors.toSet())),
                "Settlement needs every connected endpoint observation");
    }
    private static List<Observation> sorted(List<Observation> observations) {
        return observations.stream().sorted(Comparator.comparing(value -> RocketNativeResources.signature(EscrowCodec.endpoint(value.endpoint())))).toList();
    }
    private static EscrowSettlementIntent create(Choice choice, UUID ledger, UUID actor, String name, long preview, long confirmed,
                                                List<Member> members, List<Observation> observations, CompoundTag floor) {
        requireUniqueCandidates(observations);
        return new EscrowSettlementIntent(choice, ledger, actor, name, preview, confirmed, members, observations, floor,
                hash(choice, ledger, actor, name, preview, members, observations, floor));
    }
    /** Decision-time check only: older duplicate-candidate intents must still decode and remain held. */
    static void requireUniqueCandidates(List<Observation> observations) {
        var nativeLogicals = new HashSet<UUID>();
        for (var observation : observations) {
            if (!observation.missing() && observation.signatureKind() == EscrowSignatureKind.FUEL_VECTOR_V1) {
                EscrowTags.require(nativeLogicals.add(observation.endpoint().logicalRocket()),
                        "Settlement has multiple physical candidates for one logical rocket");
            }
        }
    }
    private static String hash(Choice choice, UUID ledger, UUID actor, String name, long preview,
                               List<Member> members, List<Observation> observations, CompoundTag floor) {
        return RocketNativeResources.signature(base(choice, ledger, actor, name, preview, members, observations, floor));
    }
    public CompoundTag encode() {
        var tag = base(choice, ledger, actor, actorName, previewTick, members, observations, floor);
        tag.putLong("confirmed_tick", confirmedTick); tag.putString("digest", digest);
        EscrowTags.bound(tag, EscrowLimits.OP_BYTES, 14, 480); return tag;
    }
    private static CompoundTag base(Choice choice, UUID ledger, UUID actor, String name, long preview,
                                    List<Member> members, List<Observation> observations, CompoundTag floor) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 2); tag.putString("choice", choice.name());
        tag.putUUID("ledger_id", ledger); tag.putUUID("actor", actor); tag.putString("actor_name", name); tag.putLong("preview_tick", preview);
        var list = new ListTag();
        for (Member member : members) {
            var item = new CompoundTag(); item.putUUID("op_id", member.id()); item.putString("phase", member.phase().name());
            item.putString("reason", member.reason().name()); item.putString("digest", member.digest()); list.add(item);
        }
        tag.put("members", list); list = new ListTag();
        for (Observation observation : observations) { list.add(encodeObservation(observation)); }
        tag.put("observations", list); if (!floor.isEmpty()) { tag.put("floor", floor.copy()); } return tag;
    }
    static CompoundTag encodeObservation(Observation observation) {
            var item = new CompoundTag(); item.put("endpoint", EscrowCodec.endpoint(observation.endpoint()));
            item.putBoolean("missing", observation.missing()); item.putBoolean("bound", observation.bound()); item.putLong("quantity", observation.quantity());
            item.putString("signature_kind", observation.signatureKind().name()); item.putString("invariant_digest", observation.invariantDigest());
            if (!observation.missing()) { item.put("state", state(observation.state())); }
            if (!observation.missing() && observation.signatureKind() == EscrowSignatureKind.FUEL_VECTOR_V1) {
                item.put("normalized_wrapper", observation.normalizedWrapper().encode());
            }
            return item;
    }
    private static CompoundTag state(EscrowPrefix.State state) {
        var tag = new CompoundTag(); tag.putUUID("ledger_id", state.ledger()); tag.putLong("epoch", state.epoch()); tag.putString("signature", state.signature());
        var list = new ListTag();
        for (var marker : state.markers()) {
            var item = new CompoundTag(); item.putUUID("op_id", marker.operation()); item.putLong("seq", marker.sequence());
            item.putString("role", marker.role()); item.putLong("epoch_after", marker.epochAfter()); list.add(item);
        }
        tag.put("markers", list); return tag;
    }
    private static EscrowPrefix.State state(CompoundTag tag) {
        EscrowTags.fields(tag, "ledger_id", "epoch", "signature", "markers"); var markers = new ArrayList<EscrowPrefix.Marker>();
        for (var raw : EscrowTags.list(tag, "markers", EscrowLimits.PER_ROCKET)) {
            var item = (CompoundTag) raw; EscrowTags.fields(item, "op_id", "seq", "role", "epoch_after");
            markers.add(new EscrowPrefix.Marker(EscrowTags.uuid(item, "op_id"), EscrowTags.number(item, "seq"),
                    EscrowTags.string(item, "role"), EscrowTags.number(item, "epoch_after")));
        }
        return new EscrowPrefix.State(EscrowTags.uuid(tag, "ledger_id"), EscrowTags.number(tag, "epoch"), EscrowTags.string(tag, "signature"), markers);
    }
    public static EscrowSettlementIntent decode(CompoundTag tag) {
        EscrowTags.bound(tag, EscrowLimits.OP_BYTES, 14, 480);
        EscrowTags.fields(tag, "schema_version", "choice", "ledger_id", "actor", "actor_name", "preview_tick", "confirmed_tick", "members", "observations", "floor", "digest");
        EscrowTags.require(EscrowTags.integer(tag, "schema_version") == 2, "Unsupported settlement schema");
        var members = new ArrayList<Member>(); var observations = new ArrayList<Observation>();
        for (var raw : EscrowTags.list(tag, "members", EscrowLimits.OPERATIONS)) {
            var item = (CompoundTag) raw; EscrowTags.fields(item, "op_id", "phase", "reason", "digest");
            members.add(new Member(EscrowTags.uuid(item, "op_id"), EscrowTags.enumeration(EscrowOperation.Phase.class, item, "phase"),
                    EscrowTags.enumeration(EscrowReason.class, item, "reason"), EscrowTags.string(item, "digest")));
        }
        for (var raw : EscrowTags.list(tag, "observations", EscrowLimits.OPERATIONS * 2)) { observations.add(decodeObservation((CompoundTag) raw)); }
        return new EscrowSettlementIntent(EscrowTags.enumeration(Choice.class, tag, "choice"), EscrowTags.uuid(tag, "ledger_id"),
                EscrowTags.uuid(tag, "actor"), EscrowTags.string(tag, "actor_name"), EscrowTags.number(tag, "preview_tick"),
                EscrowTags.number(tag, "confirmed_tick"), members, observations,
                tag.contains("floor") ? EscrowTags.compound(tag, "floor") : new CompoundTag(), EscrowTags.string(tag, "digest"));
    }
    static Observation decodeObservation(CompoundTag item) {
            EscrowTags.fields(item, "endpoint", "missing", "bound", "quantity", "state", "signature_kind", "invariant_digest", "normalized_wrapper");
            byte missing = flag(item, "missing"), bound = flag(item, "bound");
            EscrowTags.require(item.contains("state") == (missing == 0), "Missing observation has a state");
            var observation = new Observation(EscrowCodec.endpoint(EscrowTags.compound(item, "endpoint")),
                    missing == 1 ? null : state(EscrowTags.compound(item, "state")), bound == 1, EscrowTags.number(item, "quantity"),
                    EscrowTags.enumeration(EscrowSignatureKind.class, item, "signature_kind"), EscrowTags.string(item, "invariant_digest"));
            boolean normalized = !observation.missing() && observation.signatureKind() == EscrowSignatureKind.FUEL_VECTOR_V1;
            EscrowTags.require(item.contains("normalized_wrapper") == normalized && (!normalized
                    || observation.normalizedWrapper().encode().equals(EscrowTags.compound(item, "normalized_wrapper"))), "Changed O1 normalization");
            return observation;
    }
    private static byte flag(CompoundTag tag, String key) {
        EscrowTags.require(tag.contains(key, net.minecraft.nbt.Tag.TAG_BYTE) && (tag.getByte(key) == 0 || tag.getByte(key) == 1), "Invalid settlement flag");
        return tag.getByte(key);
    }
}
