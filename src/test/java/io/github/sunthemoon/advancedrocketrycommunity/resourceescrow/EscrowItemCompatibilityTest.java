package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowItemCompatibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void existingFuelFloorAndFluidReceiptBytesAndInspectorOutput() throws Exception {
        new EscrowCargoCompatibilityTest().fuelAndFloorReceiptBytesAndInspectorOutputStayPinned();
        for (var kind : EscrowCargoFixtures.KINDS) {
            var op = EscrowCargoFixtures.operation(kind, 1, 99).withReason(EscrowReason.OTHER_STATE);
            var root = ledger(op); var intent = EscrowCargoFixtures.intent(root, op, 0, 0, false);
            var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
            var output = new ByteArrayOutputStream();
            NbtIo.write(EscrowReceiptCodec.encode(released.tombstones().get(0)), new DataOutputStream(output));
            String receipt = output.size() + ":" + digest(output.toByteArray());
            String inspector = digest(String.join("\n", EscrowOperatorDiagnostics.inspect(released, op.id(),
                    ignored -> fail(), ignored -> fail(), "ACKNOWLEDGED")).getBytes(StandardCharsets.UTF_8));
            System.out.println("BASELINE " + kind + " receipt=" + receipt + " inspector=" + inspector);
            boolean load = kind == EscrowOperation.Kind.FLUID_CARGO_LOAD;
            // Captured with unchanged 20a82b88 production classes before W110 implementation.
            assertEquals(load ? "1721:95aff3128c7712bb8e07fd65777fabac0be48ac478c5649fd85c4f6b4650274c"
                    : "1723:9c9ca9f9583b5a2972e4459ea06a35c75962c87f6327c01cc413116c8447d27c", receipt);
            assertEquals(load ? "2ea79f895d46e1321056a79f9125349ea81684d4355478d3e3888a7376c8fd1a"
                    : "4cdcbc828dc60aa4d034864c65879f28bfad434be44cf1f2bb699666f3f729ef", inspector);
        }
    }
    private static String digest(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
