package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** W330 gated native owner-cap fixture. Synthetic REPAIR records charge the real shared authority across restarts. */
final class EscrowCargoOwnerCapProbe extends EscrowCargoCutProbe {
    static final Map<String, EscrowOperation.Kind> CASES = Map.of(
            "CQ_FLUID_LOAD", EscrowOperation.Kind.FLUID_CARGO_LOAD,
            "CQ_FLUID_UNLOAD", EscrowOperation.Kind.FLUID_CARGO_UNLOAD,
            "CQ_ITEM_LOAD", EscrowOperation.Kind.ITEM_CARGO_LOAD,
            "CQ_ITEM_UNLOAD", EscrowOperation.Kind.ITEM_CARGO_UNLOAD);
    private static final BlockPos OTHER_ROCKET = ROCKET.offset(0, 0, 4), OTHER_DEVICE = DEVICE.offset(0, 0, 4);
    private final EscrowOperation.Kind kind;
    private ResourceEscrowSavedData data;
    private RocketEntity capped, independent;
    private long began;
    private int step;
    private boolean admitted;

    EscrowCargoOwnerCapProbe(MinecraftServer server) { super(server); kind = CASES.get(name); }
    @Override void checkpoint(String label) { }

    @Override void tick() {
        if (done || stopped() || !loaded()) { return; }
        if (began == 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began < 6_000, "Owner-cap fixture exceeded 6,000 ticks");
        if (step == 0) {
            if (recovery) {
                data = ResourceEscrowSavedData.existing(server);
                if (data == null || !EscrowItemCutProbe.baseline(data, server)) { return; }
                var found = rockets(); if (found.size() < 2) { return; }
                EscrowTags.require(found.size() == 2, "Owner-cap restart duplicated rockets");
                capped = found.stream().filter(r -> r.getZ() == ROCKET.getZ() + 0.5D).findFirst().orElseThrow();
                independent = found.stream().filter(r -> r.getZ() == OTHER_ROCKET.getZ() + 0.5D).findFirst().orElseThrow();
            } else {
                capped = create(ROCKET); independent = create(OTHER_ROCKET);
                createDevice(DEVICE, capped); createDevice(OTHER_DEVICE, independent);
                var seed = seed(UUID.randomUUID(), capped.ownerId().orElseThrow()).generation(1);
                // Establish the fixture's checked durable baseline before attaching any coordinator/index.
                var file = ResourceEscrowSavedData.file(server);
                EscrowTags.require(!java.nio.file.Files.exists(file), "Owner-cap fixture refuses an existing ledger file");
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile.replace(file,
                        io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.RESOURCE_ESCROW,
                        () -> EscrowCodec.encode(seed));
                data = ResourceEscrowSavedData.load(EscrowCodec.encode(seed));
                EscrowTags.require(EscrowRuntime.of(server).replaceLedgerForTests(server, data), "Owner-cap seed retarget refused");
            }
            step = 1; return;
        }
        if (step == 1 && !EscrowItemCutProbe.baseline(data, server)) { return; }
        if (step == 1) {
            requireRefused();
            if (!recovery) { save(); pause("OWNER_PENDING_REFUSED"); return; }
            if (independent.escrowSection().epoch() == 0) {
                enable(OTHER_DEVICE, true); step = 2;
            } else { completed(); }
            return;
        }
        if (step == 2) {
            attempt(OTHER_DEVICE);
            var op = data.ledger().records().stream().filter(value -> value.rocket().identity().equals(independent.getUUID())).findFirst().orElse(null);
            if (op != null) {
                EscrowTags.require(op.kind() == kind && op.owner().equals(independent.ownerId().orElseThrow()), "Independent owner acquired wrong operation");
                admitted = true; enable(OTHER_DEVICE, false); step = 3;
            }
        }
        if (step == 3 && data.ledger().records().size() == 16 && independent.escrowSection().markers().isEmpty()
                && deviceSection(OTHER_DEVICE).markers().isEmpty()) {
            EscrowTags.require(admitted, "Independent owner was never admitted"); completed();
        }
    }

    private void completed() {
        requireRefused();
        EscrowTags.require(independent.escrowSection().epoch() == 1 && deviceSection(OTHER_DEVICE).epoch() == 1
                && independent.escrowSection().markers().isEmpty() && deviceSection(OTHER_DEVICE).markers().isEmpty()
                && amount(independent) == (kind.itemCargo() ? (kind.cargoLoad() ? 64 : 6) : (kind.cargoLoad() ? 1_000 : 4_000))
                && deviceAmount(OTHER_DEVICE) == (kind.itemCargo() ? (kind.cargoLoad() ? 6 : 64) : (kind.cargoLoad() ? 4_000 : 1_000)),
                "Independent owner transfer did not retire exactly once");
        save(); result("OWNER_CAP", "pending=16 refused=OWNER_PENDING_FULL independent_epoch=1");
    }

    /** Compare entire durable authority and native resource envelopes around the actual device attempt. */
    private void requireRefused() {
        var root = data.ledger(); var owner = capped.ownerId().orElseThrow();
        EscrowTags.require(root.records().size() == 16 && root.records().stream().allMatch(op -> op.owner().equals(owner)
                && op.phase() == EscrowOperation.Phase.REPAIR) && !owner.equals(independent.ownerId().orElseThrow())
                && data.bind(server).admissionAuthority().pendingCount() == 16, "Owner-cap charge changed");
        var ledger = EscrowCodec.encode(root); var rocket = EscrowNativeEnvelope.rocket(capped); var device = deviceTag(DEVICE);
        enable(DEVICE, true); var refused = attempt(DEVICE); enable(DEVICE, false);
        EscrowTags.require(refused == RocketCargoStatus.OWNER_PENDING_FULL && ledger.equals(EscrowCodec.encode(data.ledger()))
                && rocket.equals(EscrowNativeEnvelope.rocket(capped)) && device.equals(deviceTag(DEVICE)),
                "Owner cap failed to refuse without side effects: " + refused);
    }

    static EscrowLedger seed(UUID ledger, UUID owner) {
        var records = new ArrayList<EscrowOperation>();
        for (int n = 1; n <= 16; n++) {
            var logical = new UUID(330, n); var payload = new CompoundTag();
            payload.putString("FluidName", "minecraft:water"); payload.putInt("Amount", 1_000);
            var from = new EscrowEndpoint(EscrowEndpoint.Kind.BLOCK_ENTITY, "minecraft:overworld", new UUID(331, n), null,
                    "minecraft:chest", 10_000 + n, 100, 10_000, EscrowEndpoint.Unit.TANK, 0, hash(n), hash(n + 1));
            var to = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", new UUID(332, n), logical,
                    "minecraft:armor_stand", 625, 0, 625, EscrowEndpoint.Unit.PRIMARY, 0, hash(n), hash(n + 1));
            records.add(new EscrowOperation(new UUID(333, n), n, owner, 0, EscrowOperation.Phase.PREPARED, EscrowReason.NONE,
                    payload, RocketNativeResources.signature(payload), from, to, new EscrowGuard(logical, null, 1, hash(n))).withReason(EscrowReason.OTHER_STATE));
        }
        return new EscrowLedger(ledger, 16, 0, records, List.of(), List.of());
    }
    private static String hash(int n) { return RocketNativeResources.hash("W330 pending seed " + n); }
    private RocketEntity create(BlockPos at) {
        int amount = kind.itemCargo() ? (kind.cargoLoad() ? 60 : 10) : (kind.cargoLoad() ? 0 : 5_000);
        return kind.itemCargo() ? EscrowItemCutProbe.cargoRocket(level, at, amount) : cargoRocket(level, at, amount);
    }
    private void createDevice(BlockPos at, RocketEntity rocket) {
        var role = kind.cargoLoad() ? RocketCargoRole.LOAD : RocketCargoRole.UNLOAD;
        if (kind.itemCargo()) { EscrowItemCutProbe.device(level, at, rocket, role, kind.cargoLoad() ? 10 : 60); }
        else { device(at, rocket, role, kind.cargoLoad() ? 5_000 : 0); }
    }
    private UUID deviceId(BlockPos at) {
        return kind.itemCargo() ? EscrowItemCutProbe.id((RocketItemCargoBlockEntity) level.getBlockEntity(at)) : id(existingDevice(at));
    }
    private void enable(BlockPos at, boolean enabled) { EscrowCargoSwitches.forTests(server, kind, deviceId(at), enabled); }
    private RocketCargoStatus attempt(BlockPos at) {
        return kind.itemCargo() ? ((RocketItemCargoBlockEntity) level.getBlockEntity(at)).attempt() : existingDevice(at).attempt();
    }
    private CompoundTag deviceTag(BlockPos at) {
        return kind.itemCargo() ? ((RocketItemCargoBlockEntity) level.getBlockEntity(at)).root().orElseThrow().encode() : existingDevice(at).root().orElseThrow().encode();
    }
    private RocketEscrowSection deviceSection(BlockPos at) {
        return kind.itemCargo() ? EscrowItemCutProbe.section((RocketItemCargoBlockEntity) level.getBlockEntity(at)) : section(existingDevice(at));
    }
    private int deviceAmount(BlockPos at) {
        return kind.itemCargo() ? EscrowItemCutProbe.tank((RocketItemCargoBlockEntity) level.getBlockEntity(at)) : tank(existingDevice(at));
    }
    private int amount(RocketEntity rocket) { return kind.itemCargo() ? EscrowItemCutProbe.cargo(rocket) : cargo(rocket); }
}
