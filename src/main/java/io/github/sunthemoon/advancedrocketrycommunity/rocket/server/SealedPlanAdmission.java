package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.world.level.ChunkPos;

/** Fixed-plan availability policy and its per-load diagnostic, not a new census or saved bound. */
final class SealedPlanAdmission {
    private final int slots;
    private boolean reported;

    SealedPlanAdmission(RocketStructureSnapshot snapshot) { this(count(snapshot)); }
    SealedPlanAdmission(int slots) {
        if (slots < 1 || slots > RocketOrdinaryRetirement.MAX_SLOTS) { throw new IllegalArgumentException("Invalid sealed proof census"); }
        this.slots = slots;
    }
    int slots() { return slots; }
    boolean allowed() { return SealedProofAdmission.fitsNativeWindow(slots); }
    SealedDisassembly.Result refuseNew(UUID rocket, Consumer<String> info) {
        if (allowed()) { return null; }
        info.accept("ARCE_DISASSEMBLY_PROOF_PLAN_REFUSED rocket=" + rocket + details());
        return SealedDisassembly.Result.PROOF_PLAN_LIMIT;
    }
    void reportRetained(UUID transaction, Consumer<String> warning) {
        if (allowed() || reported) { return; }
        reported = true;
        warning.accept("ARCE_DISASSEMBLY_PROOF_PLAN_HELD transaction=" + transaction + details() + " recovery=offline-whole-world");
    }
    private String details() { return " slots=" + slots + " limit=" + SealedProofAdmission.NATIVE_SLOT_LIMIT; }
    private static int count(RocketStructureSnapshot snapshot) {
        int terrain = (int) snapshot.blocks().stream().map(block -> snapshot.sourceOrigin().add(block.position()))
                .map(position -> new ChunkPos(position.x() >> 4, position.z() >> 4)).distinct().count();
        var entities = SealedEntityView.columns(snapshot, terrain);
        if (entities.isEmpty()) { throw new IllegalArgumentException("Sealed proof census exceeds existing bound"); }
        return terrain + entities.size();
    }
}
