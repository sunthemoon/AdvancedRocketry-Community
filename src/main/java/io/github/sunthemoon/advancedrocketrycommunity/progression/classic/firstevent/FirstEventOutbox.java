package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * One source-owned world-first slot (W44 sections 1.1 and 2.1). It only moves forward: EMPTY, ELECTED, DELIVERED.
 * The event is absent exactly when the slot is EMPTY, and DELIVERED keeps the same immutable event.
 */
public record FirstEventOutbox(State state, FirstEvent event) {
    public static final int SCHEMA_VERSION = 1;
    private static final Set<String> EMPTY_KEYS = Set.of("schema_version", "state");
    private static final Set<String> EVENT_KEYS = Set.of("schema_version", "state", "event");
    private static final FirstEventOutbox EMPTY = new FirstEventOutbox(State.EMPTY, null);

    public enum State { EMPTY, ELECTED, DELIVERED }

    public FirstEventOutbox {
        Objects.requireNonNull(state, "state");
        if ((state == State.EMPTY) != (event == null)) {
            throw new IllegalArgumentException("A first-event outbox holds an event exactly when it is not EMPTY");
        }
    }

    public static FirstEventOutbox empty() { return EMPTY; }

    public static FirstEventOutbox elected(FirstEvent event) {
        return new FirstEventOutbox(State.ELECTED, Objects.requireNonNull(event, "event"));
    }

    public FirstEventOutbox delivered() {
        if (state != State.ELECTED) { throw new IllegalStateException("Only an ELECTED outbox can be delivered"); }
        return new FirstEventOutbox(State.DELIVERED, event);
    }

    public Optional<FirstEvent> eventOptional() { return Optional.ofNullable(event); }

    public boolean isEmpty() { return state == State.EMPTY; }

    /** Whether {@code next} is this slot or a forward move that keeps its event (W44 invariant I4). */
    public boolean precedesOrEquals(FirstEventOutbox next) {
        if (next.state.ordinal() < state.ordinal()) { return false; }
        return state == State.EMPTY || event.equals(next.event);
    }

    public CompoundTag encode(FirstEventKind kind) {
        if (event != null && event.kind() != kind) {
            throw new IllegalArgumentException("A first-event outbox holds another kind");
        }
        CompoundTag target = new CompoundTag();
        target.putInt("schema_version", SCHEMA_VERSION);
        target.putString("state", state.name());
        if (event != null) { target.put("event", FirstEventCodec.encode(event)); }
        if (!BoundedNbt.fits(target, kind.maxOutboxBytes(), FirstEventKind.MAX_DEPTH, FirstEventKind.MAX_NODES)) {
            throw new IllegalStateException("Encoded first-event outbox exceeds its bound");
        }
        return target;
    }

    /** Strict decode: exact keys, schema 1, a known state and an event exactly when it is not EMPTY. */
    public static FirstEventOutbox decode(Tag raw, FirstEventKind kind) {
        if (!(raw instanceof CompoundTag source)) {
            throw new IllegalArgumentException("Missing or invalid first-event outbox");
        }
        if (!BoundedNbt.fits(source, kind.maxOutboxBytes(), FirstEventKind.MAX_DEPTH, FirstEventKind.MAX_NODES)) {
            throw new IllegalArgumentException("First-event outbox exceeds its bound");
        }
        if (!source.contains("schema_version", Tag.TAG_INT) || source.getInt("schema_version") != SCHEMA_VERSION
                || !source.contains("state", Tag.TAG_STRING)) {
            throw new IllegalArgumentException("First-event outbox schema or state is missing or unknown");
        }
        State state;
        try { state = State.valueOf(source.getString("state")); }
        catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("First-event outbox state is unknown", unknown);
        }
        if (state == State.EMPTY) {
            FirstEventCodec.requireKeys(source, EMPTY_KEYS);
            return EMPTY;
        }
        FirstEventCodec.requireKeys(source, EVENT_KEYS);
        if (!source.contains("event", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("First-event outbox event is missing");
        }
        return new FirstEventOutbox(state, FirstEventCodec.decode(source.getCompound("event"), kind));
    }
}
