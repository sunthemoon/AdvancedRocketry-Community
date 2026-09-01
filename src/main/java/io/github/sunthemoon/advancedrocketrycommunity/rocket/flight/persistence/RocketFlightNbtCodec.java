package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightDecodeResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerSeat;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict, bounded schema-1 codec for entity flight data. */
public final class RocketFlightNbtCodec {
    private static final String SCHEMA = "schema_version";
    private static final String LOGICAL_ROCKET_ID = "logical_rocket_id";
    private static final String STATE = "state";
    private static final String FUEL = "fuel";
    private static final String PLAN = "plan";
    private static final String PASSENGERS = "passengers";
    private static final String CURRENT_BODY = "current_body";
    private static final String CURRENT_DIMENSION = "current_dimension";
    private static final String CURRENT_ORIGIN = "current_origin";
    private static final String STATE_STARTED = "state_started_game_time";
    private static final String ACTIVE_TRANSFER_ID = "active_transfer_id";

    private RocketFlightNbtCodec() {
    }

    public static CompoundTag encode(RocketFlightData data) {
        Objects.requireNonNull(data, "data");
        CompoundTag target = new CompoundTag();
        target.putInt(SCHEMA, data.schemaVersion());
        target.putUUID(LOGICAL_ROCKET_ID, data.logicalRocketId());
        target.putString(STATE, data.state().name());
        target.put(FUEL, encodeFuel(data.fuel()));
        data.plan().ifPresent(plan -> target.put(PLAN, encodePlan(plan)));
        target.put(PASSENGERS, encodePassengers(data.passengers()));
        target.putString(CURRENT_BODY, data.currentBody().toString());
        target.putString(CURRENT_DIMENSION, data.currentDimension().toString());
        target.putIntArray(CURRENT_ORIGIN, position(data.currentOrigin()));
        target.putLong(STATE_STARTED, data.stateStartedGameTime());
        data.activeTransferId().ifPresent(id -> target.putUUID(ACTIVE_TRANSFER_ID, id));
        if (RocketNbtSize.uncompressedBytes(target) > RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES) {
            throw new IllegalArgumentException("Encoded rocket flight data exceeds the fixed NBT limit");
        }
        return target;
    }

    public static RocketFlightDecodeResult decode(CompoundTag source) {
        Objects.requireNonNull(source, "source");
        CompoundTag preserved = source.copy();
        try {
            if (RocketNbtSize.uncompressedBytes(source) > RocketFlightLimits.MAX_FLIGHT_DATA_NBT_BYTES) {
                return RocketFlightDecodeResult.invalid(preserved, "Rocket flight payload exceeds the fixed size limit");
            }
            int schema = requireInt(source, SCHEMA);
            if (schema > RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION) {
                return RocketFlightDecodeResult.future(preserved, schema);
            }
            if (schema != RocketFlightLimits.FLIGHT_DATA_SCHEMA_VERSION) {
                return RocketFlightDecodeResult.invalid(preserved, "Unsupported old rocket flight schema " + schema);
            }
            UUID logicalRocketId = requireUuid(source, LOGICAL_ROCKET_ID);
            RocketFlightState state = RocketFlightState.valueOf(requireString(source, STATE, 64));
            RocketFuelState fuel = decodeFuel(requireCompound(source, FUEL));
            RocketFlightPlan plan = source.contains(PLAN)
                    ? decodePlan(requireCompound(source, PLAN))
                    : null;
            RocketPassengerManifest passengers = decodePassengers(requireCompound(source, PASSENGERS));
            ResourceLocation currentBody = requireLocation(source, CURRENT_BODY);
            ResourceLocation currentDimension = requireLocation(source, CURRENT_DIMENSION);
            RocketPosition currentOrigin = requirePosition(source, CURRENT_ORIGIN);
            long stateStarted = requireNonNegativeLong(source, STATE_STARTED);
            UUID activeTransferId = optionalUuid(source, ACTIVE_TRANSFER_ID);
            return RocketFlightDecodeResult.valid(RocketFlightData.restore(
                    schema,
                    logicalRocketId,
                    state,
                    fuel,
                    plan,
                    passengers,
                    currentBody,
                    currentDimension,
                    currentOrigin,
                    stateStarted,
                    activeTransferId
            ));
        } catch (RuntimeException exception) {
            return RocketFlightDecodeResult.invalid(preserved, safeMessage(exception));
        }
    }

    private static CompoundTag encodeFuel(RocketFuelState fuel) {
        CompoundTag target = new CompoundTag();
        target.putLong("capacity", fuel.capacity());
        target.putLong("amount", fuel.amount());
        ListTag debits = new ListTag();
        for (UUID transactionId : fuel.committedDebits()) {
            CompoundTag debit = new CompoundTag();
            debit.putUUID("transaction_id", transactionId);
            debits.add(debit);
        }
        target.put("committed_debits", debits);
        return target;
    }

    private static RocketFuelState decodeFuel(CompoundTag source) {
        long capacity = requireNonNegativeLong(source, "capacity");
        long amount = requireNonNegativeLong(source, "amount");
        ListTag debitTags = requireList(source, "committed_debits", Tag.TAG_COMPOUND);
        if (debitTags.size() > RocketFlightLimits.MAX_COMMITTED_FUEL_DEBITS) {
            throw new IllegalArgumentException("Fuel debit history exceeds the fixed bound");
        }
        ArrayList<UUID> debits = new ArrayList<>(debitTags.size());
        for (Tag raw : debitTags) {
            debits.add(requireUuid((CompoundTag) raw, "transaction_id"));
        }
        return RocketFuelState.restore(capacity, amount, debits);
    }

    private static CompoundTag encodePlan(RocketFlightPlan plan) {
        CompoundTag target = new CompoundTag();
        target.putInt(SCHEMA, plan.schemaVersion());
        target.putUUID("request_id", plan.requestId());
        target.putString("source_body", plan.sourceBody().toString());
        target.putString("destination_body", plan.destinationBody().toString());
        target.putString("source_dimension", plan.sourceDimension().toString());
        target.putString("destination_dimension", plan.destinationDimension().toString());
        target.putLong("required_fuel", plan.requiredFuel());
        target.putLong("created_at_game_time", plan.createdAtGameTime());
        return target;
    }

    private static RocketFlightPlan decodePlan(CompoundTag source) {
        return new RocketFlightPlan(
                requireInt(source, SCHEMA),
                requireUuid(source, "request_id"),
                requireLocation(source, "source_body"),
                requireLocation(source, "destination_body"),
                requireLocation(source, "source_dimension"),
                requireLocation(source, "destination_dimension"),
                requireNonNegativeLong(source, "required_fuel"),
                requireNonNegativeLong(source, "created_at_game_time")
        );
    }

    private static CompoundTag encodePassengers(RocketPassengerManifest passengers) {
        CompoundTag target = new CompoundTag();
        target.putInt("seat_capacity", passengers.seatCapacity());
        ListTag assignments = new ListTag();
        for (RocketPassengerSeat seat : passengers.assignments()) {
            CompoundTag assignment = new CompoundTag();
            assignment.putUUID("passenger_id", seat.passengerId());
            assignment.putInt("seat_index", seat.seatIndex());
            assignments.add(assignment);
        }
        target.put("assignments", assignments);
        return target;
    }

    private static RocketPassengerManifest decodePassengers(CompoundTag source) {
        int capacity = requireInt(source, "seat_capacity");
        ListTag assignmentTags = requireList(source, "assignments", Tag.TAG_COMPOUND);
        if (assignmentTags.size() > RocketFlightLimits.MAX_PASSENGERS) {
            throw new IllegalArgumentException("Passenger assignment list exceeds the fixed bound");
        }
        ArrayList<RocketPassengerSeat> assignments = new ArrayList<>(assignmentTags.size());
        for (Tag raw : assignmentTags) {
            CompoundTag assignment = (CompoundTag) raw;
            assignments.add(new RocketPassengerSeat(
                    requireUuid(assignment, "passenger_id"),
                    requireInt(assignment, "seat_index")
            ));
        }
        return RocketPassengerManifest.restore(capacity, assignments);
    }

    private static int requireInt(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT)) {
            throw new IllegalArgumentException("Missing or invalid integer " + key);
        }
        int value = source.getInt(key);
        if (value < 0) {
            throw new IllegalArgumentException("Negative integer " + key);
        }
        return value;
    }

    private static long requireNonNegativeLong(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_LONG)) {
            throw new IllegalArgumentException("Missing or invalid long " + key);
        }
        long value = source.getLong(key);
        if (value < 0L) {
            throw new IllegalArgumentException("Negative long " + key);
        }
        return value;
    }

    private static String requireString(CompoundTag source, String key, int maximumLength) {
        if (!source.contains(key, Tag.TAG_STRING)) {
            throw new IllegalArgumentException("Missing or invalid string " + key);
        }
        String value = source.getString(key);
        if (value.isEmpty() || value.length() > maximumLength) {
            throw new IllegalArgumentException("String " + key + " is outside the fixed length bound");
        }
        return value;
    }

    private static ResourceLocation requireLocation(CompoundTag source, String key) {
        String value = requireString(source, key, RocketLimits.MAX_IDENTIFIER_LENGTH);
        ResourceLocation location = ResourceLocation.tryParse(value);
        if (location == null) {
            throw new IllegalArgumentException("Invalid resource location " + key);
        }
        return location;
    }

    private static UUID requireUuid(CompoundTag source, String key) {
        if (!source.hasUUID(key)) {
            throw new IllegalArgumentException("Missing or invalid UUID " + key);
        }
        return source.getUUID(key);
    }

    private static UUID optionalUuid(CompoundTag source, String key) {
        if (!source.contains(key)) {
            return null;
        }
        return requireUuid(source, key);
    }

    private static CompoundTag requireCompound(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("Missing or invalid compound " + key);
        }
        return source.getCompound(key);
    }

    private static ListTag requireList(CompoundTag source, String key, byte elementType) {
        Tag raw = source.get(key);
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != elementType)) {
            throw new IllegalArgumentException("Missing or invalid list " + key);
        }
        return list;
    }

    private static RocketPosition requirePosition(CompoundTag source, String key) {
        if (!source.contains(key, Tag.TAG_INT_ARRAY)) {
            throw new IllegalArgumentException("Missing or invalid position " + key);
        }
        int[] values = source.getIntArray(key);
        if (values.length != 3) {
            throw new IllegalArgumentException("Position " + key + " has the wrong length");
        }
        return new RocketPosition(values[0], values[1], values[2]);
    }

    private static int[] position(RocketPosition value) {
        return new int[]{value.x(), value.y(), value.z()};
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
