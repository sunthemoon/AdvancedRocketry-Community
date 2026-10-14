package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Gated vanilla chest/armor-stand fixtures; there are no test registry IDs or ordinary-world creations. */
public final class EscrowTestEndpoints {
    public static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    private static final String FIXTURE = "arce_escrow_fixture";
    private static final String SECTION = "resource_escrow";
    private EscrowTestEndpoints() { }
    public static void requireEnabled(MinecraftServer server) {
        EscrowTags.require(server.isSameThread() && (server instanceof GameTestServer
                || Boolean.getBoolean(HOOKS) && server.isDedicatedServer() && server.getPlayerList().getPlayerCount() == 0),
                "Escrow fixture requires a GameTestServer or an empty, gated release-test dedicated server");
    }
    public record Fixture(ServerLevel level, BlockEntity source, ArmorStand bank, UUID ledger, UUID logical) {
        public EscrowOperation operation(long sequence) {
            requireEnabled(level.getServer());
            var sourceState = state(source.getPersistentData(), ledger, false);
            var bankState = state(bank.getPersistentData(), ledger, true);
            BlockPos pos = source.getBlockPos(); var chunk = bank.chunkPosition();
            UUID device = source.getPersistentData().getCompound(FIXTURE).getUUID("identity");
            EscrowEndpoint from = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY,
                    level.dimension().location().toString(), device, null, "minecraft:chest", pos.getX(), pos.getY(), pos.getZ(),
                    EscrowEndpoint.Unit.TANK, sourceState.epoch(), sourceState.signature(), signature(device, amount(source.getPersistentData()) - 1_000));
            EscrowEndpoint to = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY,
                    level.dimension().location().toString(), bank.getUUID(), logical, "minecraft:armor_stand", chunk.x, 0, chunk.z,
                    EscrowEndpoint.Unit.PRIMARY, bankState.epoch(), bankState.signature(), signature(logical, amount(bank.getPersistentData()) + 1_000));
            CompoundTag payload = new CompoundTag(); payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
            return new EscrowOperation(UUID.randomUUID(), sequence, logical, level.getGameTime(), EscrowOperation.Phase.PREPARED,
                    EscrowReason.NONE, payload, RocketNativeResources.signature(payload), from, to,
                    new EscrowGuard(logical, null, 1, RocketNativeResources.hash("test-only checked no-transfer")));
        }
        public void prepare(EscrowCoordinator coordinator, EscrowOperation op, java.util.function.Consumer<Boolean> callback) {
            requireEnabled(level.getServer()); coordinator.prepareTest(op, callback);
        }
        public void apply(EscrowOperation op) {
            requireEnabled(level.getServer()); applySource(op); applyBank(op);
        }
        public void applySource(EscrowOperation op) {
            requireEnabled(level.getServer()); applyOne(source.getPersistentData(), ledger, op, false); source.setChanged();
        }
        public void applyBank(EscrowOperation op) { requireEnabled(level.getServer()); applyOne(bank.getPersistentData(), ledger, op, true); }
        public EscrowPrefix.State sourceState() { return state(source.getPersistentData(), ledger, false); }
        public EscrowPrefix.State bankState() { return state(bank.getPersistentData(), ledger, true); }
    }
    public static Fixture create(ServerLevel level, BlockPos pos, UUID ledger) {
        requireEnabled(level.getServer());
        EscrowTags.require(level.hasChunkAt(pos), "Fixture creation requires an already loaded chunk");
        level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState()); BlockEntity source = level.getBlockEntity(pos);
        UUID logical = UUID.randomUUID(); ArmorStand bank = new ArmorStand(level, pos.getX() + 2.5, pos.getY(), pos.getZ() + .5);
        bank.setNoGravity(true); bank.setInvulnerable(true);
        initialize(source.getPersistentData(), UUID.randomUUID(), 5_000, 16_000, ledger);
        initialize(bank.getPersistentData(), logical, 0, 2_000, ledger);
        level.addFreshEntity(bank); source.setChanged();
        return new Fixture(level, source, bank, ledger, logical);
    }
    public static Fixture chainSource(Fixture first, BlockPos position) {
        requireEnabled(first.level().getServer());
        EscrowTags.require(first.level().hasChunkAt(position), "Chain source requires an already loaded chunk");
        first.level().setBlockAndUpdate(position, Blocks.CHEST.defaultBlockState());
        BlockEntity source = first.level().getBlockEntity(position);
        initialize(source.getPersistentData(), UUID.randomUUID(), 5_000, 16_000, first.ledger()); source.setChanged();
        return new Fixture(first.level(), source, first.bank(), first.ledger(), first.logical());
    }
    private static void initialize(CompoundTag persistent, UUID identity, int amount, int capacity, UUID ledger) {
        CompoundTag fixture = new CompoundTag(); fixture.putUUID("identity", identity); fixture.putInt("amount", amount);
        fixture.putInt("capacity", capacity); persistent.put(FIXTURE, fixture);
        putState(persistent, new EscrowPrefix.State(ledger, 0, signature(identity, amount), List.of()), false);
    }
    public static int amount(CompoundTag persistent) { return persistent.getCompound(FIXTURE).getInt("amount"); }
    private static String signature(UUID identity, int amount) { return RocketNativeResources.hash("arce-escrow-fixture-1:" + identity + ":" + amount); }
    public static EscrowPrefix.State state(CompoundTag persistent, UUID ledger, boolean entity) {
        CompoundTag fixture = EscrowTags.compound(persistent, FIXTURE);
        EscrowTags.fields(fixture, "identity", "amount", "capacity"); UUID identity = EscrowTags.uuid(fixture, "identity");
        int amount = EscrowTags.integer(fixture, "amount"), capacity = EscrowTags.integer(fixture, "capacity");
        EscrowTags.require(amount >= 0 && amount <= capacity && capacity <= 16_000, "Invalid fixture resource bank");
        CompoundTag section = EscrowTags.compound(persistent, SECTION);
        EscrowTags.bound(section, entity ? EscrowLimits.ENTITY_MARKER_BYTES : EscrowLimits.BE_MARKER_BYTES, 8, 128);
        EscrowTags.fields(section, "schema_version", "ledger_id", "resource_epoch", "markers");
        EscrowTags.require(EscrowTags.integer(section, "schema_version") == 1 && ledger.equals(EscrowTags.uuid(section, "ledger_id")), "Foreign fixture ledger");
        var markers = new ArrayList<EscrowPrefix.Marker>();
        for (var tag : EscrowTags.list(section, "markers", entity ? 8 : 1)) {
            CompoundTag marker = (CompoundTag) tag; EscrowTags.fields(marker, "op_id", "seq", "role", "epoch_after");
            markers.add(new EscrowPrefix.Marker(EscrowTags.uuid(marker, "op_id"), EscrowTags.number(marker, "seq"),
                    EscrowTags.string(marker, "role"), EscrowTags.number(marker, "epoch_after")));
        }
        return new EscrowPrefix.State(ledger, EscrowTags.number(section, "resource_epoch"), signature(identity, amount), markers);
    }
    static void putState(CompoundTag persistent, EscrowPrefix.State state, boolean entity) {
        CompoundTag section = new CompoundTag(); section.putInt("schema_version", 1); section.putUUID("ledger_id", state.ledger());
        section.putLong("resource_epoch", state.epoch()); ListTag markers = new ListTag();
        for (EscrowPrefix.Marker marker : state.markers()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("op_id", marker.operation()); tag.putLong("seq", marker.sequence());
            tag.putString("role", marker.role()); tag.putLong("epoch_after", marker.epochAfter()); markers.add(tag);
        }
        section.put("markers", markers);
        EscrowTags.bound(section, entity ? EscrowLimits.ENTITY_MARKER_BYTES : EscrowLimits.BE_MARKER_BYTES, 8, 128);
        persistent.put(SECTION, section);
    }
    private static void applyOne(CompoundTag persistent, UUID ledger, EscrowOperation op, boolean destination) {
        EscrowEndpoint endpoint = destination ? op.destination() : op.source(); var before = state(persistent, ledger, destination);
        EscrowPrefix.Marker marker = new EscrowPrefix.Marker(op.id(), op.sequence(), destination ? "DESTINATION" : "SOURCE", endpoint.afterEpoch());
        if (before.markers().contains(marker)) { return; }
        EscrowTags.require(before.epoch() == endpoint.beforeEpoch() && before.signature().equals(endpoint.beforeSignature()), "Fixture is not exact BEFORE");
        int changed = amount(persistent) + (destination ? 1_000 : -1_000);
        var markers = new ArrayList<>(before.markers()); markers.add(marker);
        putState(persistent, new EscrowPrefix.State(ledger, endpoint.afterEpoch(), endpoint.afterSignature(), markers), destination);
        persistent.getCompound(FIXTURE).putInt("amount", changed);
    }
    public static Predicate<CompoundTag> validator(EscrowEndpoint endpoint, EscrowPrefix.State frontier) {
        // Captures only immutable identities/frontier, never a live BlockEntity, Entity or Level.
        return root -> {
            try { return frontier.equals(diskState(root, endpoint, frontier.ledger())); }
            catch (RuntimeException malformed) { return false; }
        };
    }
    public static EscrowPrefix.State diskState(CompoundTag root, EscrowEndpoint endpoint, UUID ledger) {
        String listKey = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? "Entities" : "block_entities";
        if (endpoint.kind() == EscrowEndpoint.Kind.ENTITY) {
            int[] pos = root.getIntArray("Position"); EscrowTags.require(pos.length == 2 && pos[0] == endpoint.x() && pos[1] == endpoint.z(), "Wrong entity chunk");
        } else { EscrowTags.require(EscrowTags.integer(root, "xPos") == endpoint.x() >> 4
                && EscrowTags.integer(root, "zPos") == endpoint.z() >> 4, "Wrong terrain chunk"); }
        CompoundTag found = null;
        for (var tag : EscrowTags.list(root, listKey, 65_536)) {
            CompoundTag object = (CompoundTag) tag;
            boolean matches = endpoint.kind() == EscrowEndpoint.Kind.ENTITY
                    ? object.hasUUID("UUID") && endpoint.identity().equals(object.getUUID("UUID"))
                    : EscrowTags.integer(object, "x") == endpoint.x() && EscrowTags.integer(object, "y") == endpoint.y()
                            && EscrowTags.integer(object, "z") == endpoint.z();
            if (matches) { EscrowTags.require(found == null && endpoint.type().equals(object.getString("id")), "Duplicate/wrong endpoint"); found = object; }
        }
        EscrowTags.require(found != null, "Missing disk endpoint"); CompoundTag persistent = EscrowTags.compound(found, "ForgeData");
        UUID expected = endpoint.kind() == EscrowEndpoint.Kind.ENTITY ? endpoint.logicalRocket() : endpoint.identity();
        EscrowTags.require(expected.equals(EscrowTags.uuid(EscrowTags.compound(persistent, FIXTURE), "identity")), "Disk placement identity changed");
        return state(persistent, ledger, endpoint.kind() == EscrowEndpoint.Kind.ENTITY);
    }
    public static void clearRetired(Fixture fixture) {
        var ids = java.util.stream.Stream.concat(fixture.sourceState().markers().stream(), fixture.bankState().markers().stream())
                .map(EscrowPrefix.Marker::operation).collect(java.util.stream.Collectors.toSet());
        clearRetired(fixture, ids);
    }
    public static void clearRetired(Fixture fixture, java.util.Set<UUID> retired) {
        requireEnabled(fixture.level().getServer());
        var source = fixture.sourceState(); var bank = fixture.bankState();
        putState(fixture.source().getPersistentData(), prune(source, retired), false);
        putState(fixture.bank().getPersistentData(), prune(bank, retired), true);
        fixture.source().setChanged();
    }
    static EscrowPrefix.State prune(EscrowPrefix.State state, java.util.Set<UUID> retired) {
        return new EscrowPrefix.State(state.ledger(), state.epoch(), state.signature(),
                state.markers().stream().filter(marker -> !retired.contains(marker.operation())).toList());
    }
}
