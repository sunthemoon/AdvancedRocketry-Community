package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import io.github.sunthemoon.advancedrocketrycommunity.client.equipment.JetpackClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * ADR-066 section 5.2 S2C: the velocity increment the server has just paid for, as three floats. The client adds it
 * with {@link JetpackPhysics#apply}; vanilla's motion packet would instead replace the whole client velocity.
 */
public record JetpackImpulsePacket(float x, float y, float z) {
    public static final int ENCODED_BYTES = 12;

    public JetpackImpulsePacket {
        if (!new JetpackPhysics.Impulse(x, y, z).bounded()) {
            throw new IllegalArgumentException("Jetpack impulse is outside the server bounds");
        }
    }

    public static JetpackImpulsePacket of(JetpackPhysics.Impulse impulse) {
        return new JetpackImpulsePacket((float) impulse.x(), (float) impulse.y(), (float) impulse.z());
    }

    public JetpackPhysics.Impulse impulse() { return new JetpackPhysics.Impulse(x, y, z); }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(z);
    }

    public static JetpackImpulsePacket decode(FriendlyByteBuf buffer) {
        int frame = buffer.readableBytes();
        if (frame != ENCODED_BYTES) {
            throw new IllegalArgumentException("Jetpack impulse frame must be exactly 12 bytes, not " + frame);
        }
        return new JetpackImpulsePacket(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    public static void handle(JetpackImpulsePacket packet, Supplier<NetworkEvent.Context> context) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> JetpackClient.applyImpulse(packet.impulse()));
        context.get().setPacketHandled(true);
    }
}
