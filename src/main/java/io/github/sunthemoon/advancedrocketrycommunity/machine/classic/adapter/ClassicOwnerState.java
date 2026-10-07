package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Instance-owned guard/storage state. Retirement never erases the last raw checkpoint. */
final class ClassicOwnerState {
    private final BlockEntity owner;
    private Object lifetime = new Object();
    private Object held;
    private Object storageEpoch = new Object();
    private Object facadeEpoch = new Object();
    private ClassicPendingLoad pending;
    private boolean gameplayAvailable;
    private boolean loaded;

    ClassicOwnerState(BlockEntity owner) { this.owner = Objects.requireNonNull(owner, "owner"); }

    synchronized Object lifetime() { return lifetime; }
    synchronized Object facadeEpoch() { return facadeEpoch; }
    BlockEntity owner() { return owner; }
    synchronized boolean available() { return gameplayAvailable; }
    synchronized boolean loaded() { return loaded; }
    synchronized Optional<ClassicPendingLoad> pending() { return Optional.ofNullable(pending); }
    synchronized boolean heldBy(Object token) { return held != null && held == token; }
    synchronized boolean busy() { return held != null; }
    synchronized Object currentGuard() { return held; }

    synchronized Object acquire() {
        if (held != null) { return null; }
        held = new Object(); return held;
    }

    synchronized void release(Object token) {
        if (held != token || token == null) { throw new IllegalStateException("Foreign owner guard release"); }
        held = null;
    }

    synchronized void retire() { lifetime = new Object(); facadeEpoch = new Object(); gameplayAvailable = false; }
    synchronized void retireFacades() { facadeEpoch = new Object(); }

    synchronized void withholdGameplay() { gameplayAvailable = false; }

    synchronized void installed() {
        if (!owner.isRemoved()) { gameplayAvailable = true; }
    }

    synchronized Object storageEpoch() { return storageEpoch; }

    synchronized void captured(ClassicPendingLoad candidate, ClassicRawPermit permit) {
        permit.requireCurrent();
        if (permit.purpose() != ClassicRawPurpose.CAPTURE || permit.state() != this || !candidate.ownedBy(this)) {
            throw new IllegalStateException("Foreign raw publication");
        }
        pending = Objects.requireNonNull(candidate, "candidate"); loaded = true;
        gameplayAvailable = false;
        storageEpoch = new Object(); permit.advanceStorage(storageEpoch);
    }

    synchronized void decoded(ClassicPendingLoad expected, GuardTicket ticket) {
        ticket.requireValid();
        if (pending != expected) { throw new IllegalStateException("Pending load changed"); }
        pending = null;
        // Loaded provenance remains; missing roots never become a newly placed owner.
    }
}
