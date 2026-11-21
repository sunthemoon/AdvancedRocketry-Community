package io.github.sunthemoon.arceintenttest;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Independent wire consumer, not a copy or subclass of a product packet. */
public final class CaseBuilder {
    public static final int MAX_PAYLOAD_BYTES = 156;
    private static final Pattern BODY = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");

    public record Input(int container, int kind, int entity, String body, UUID request) { }

    public record Packet(IntentCase intentCase, byte[] payload, String values) {
        public Packet {
            Objects.requireNonNull(intentCase);
            payload = payload.clone();
            if (payload.length < 3 || payload.length > MAX_PAYLOAD_BYTES) {
                throw new IllegalArgumentException("Payload outside fixed fixture bounds");
            }
            Objects.requireNonNull(values);
        }
        @Override public byte[] payload() { return payload.clone(); }
    }

    public static Packet build(IntentCase which, Input input) {
        Objects.requireNonNull(which);
        Objects.requireNonNull(input);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            // SimpleChannel's one-byte discriminator 0, followed by the exact pinned frame.
            out.writeByte(0);
            String values;
            if (which.flight()) {
                if (input.entity() < 0) { throw new IllegalArgumentException("Negative entity ID"); }
                String body = Objects.requireNonNull(input.body());
                UUID request = Objects.requireNonNull(input.request());
                boolean station = which == IntentCase.FLIGHT_PRIVATE_STATION;
                int action = which == IntentCase.FLIGHT_FORBIDDEN_SURFACE || station ? 0 : 1; // LAUNCH or CANCEL
                out.writeByte(action);
                varInt(out, input.entity());
                out.writeByte(1); // TravelTarget schema
                out.writeByte(station ? 2 : 0); // Station or BodySurface
                if (station) {
                    UUID target = UUID.fromString(body);
                    if (!target.toString().equals(body)) { throw new IllegalArgumentException("Use a canonical station UUID"); }
                    out.writeLong(target.getMostSignificantBits());
                    out.writeLong(target.getLeastSignificantBits());
                } else {
                    if (body.length() > 128 || !BODY.matcher(body).matches()) {
                        throw new IllegalArgumentException("Body must be a canonical resource ID of at most 128 ASCII chars");
                    }
                    byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
                    varInt(out, encoded.length);
                    out.write(encoded);
                }
                out.writeLong(request.getMostSignificantBits());
                out.writeLong(request.getLeastSignificantBits());
                out.writeBoolean(false); // No named landing pad
                values = "action=" + action + " entity=" + input.entity() + " target=" + (station ? "station:" : "body_surface:") + body
                        + " request=" + request + " pad=absent";
            } else if (which.classic()) {
                if (input.container() < 1 || input.container() > 255 || input.kind() < 0 || input.kind() > 4) {
                    throw new IllegalArgumentException("Container/kind outside pinned bounds");
                }
                // Warp CANCEL; gravity/altitude/orientation step 0; monitor DISARM (action 5).
                int action = switch (input.kind()) { case 0 -> 4; case 4 -> 5; default -> 0; };
                out.writeByte(1); // Classic control schema
                out.writeByte(input.container());
                out.writeByte(input.kind());
                out.writeByte(action);
                out.writeByte(0);
                out.writeLong(0);
                values = "schema=1 container=" + input.container() + " kind=" + input.kind()
                        + " action=" + action + " value=0 token=0";
            } else {
                int action = which == IntentCase.MOTION_NO_HOVER ? 1 : which == IntentCase.MOTION_DISABLED ? 2 : 0;
                int held = which == IntentCase.MOTION_DISABLED ? 1 : 0;
                out.writeByte(action);
                out.writeByte(held);
                values = "action=" + action + " held=" + held;
            }
            return new Packet(which, bytes.toByteArray(), values);
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory encoding failed", impossible);
        }
    }

    private static void varInt(DataOutputStream out, int value) throws IOException {
        do {
            int next = value & 127;
            value >>>= 7;
            out.writeByte(value == 0 ? next : next | 128);
        } while (value != 0);
    }

    private CaseBuilder() { }
}
