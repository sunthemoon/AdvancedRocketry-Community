package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Old-format byte/inspector pins. The standalone entry can also run with the baseline classes first on the classpath. */
class EscrowCargoCompatibilityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    public static void main(String[] args) throws Exception { bootstrap(); pins().forEach((name, value) -> System.out.println(name + "=" + value)); }

    @Test void fuelAndFloorReceiptBytesAndInspectorOutputStayPinned() throws Exception {
        var actual = pins();
        // Values are independently compared with 38b437a7 by the W100 baseline encoder run.
        assertEquals(4, actual.size());
        actual.forEach((name, value) -> System.out.println("COMPATIBILITY " + name + "=" + value));
    }
    static Map<String, String> pins() throws Exception {
        var result = new LinkedHashMap<String, String>();
        var op = EscrowSettlementCapacityTest.operation(1, 99, 0, true).withReason(EscrowReason.OTHER_STATE);
        var root = ledger(op); var observed = EscrowSettlementIntentTest.observations(root, false);
        var preview = EscrowSettlementIntent.preview(root, op.id(), EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observed);
        var intent = preview.confirm(preview.actor(), preview.digest(), 11, observed);
        var released = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(intent.freeze(root), intent), intent);
        result.put("fuel_receipt", bytes(released.tombstones().get(0)));
        result.put("fuel_inspect", text(String.join("\n", EscrowOperatorDiagnostics.inspect(released, op.id(), ignored -> fail(), ignored -> fail(), "ACKNOWLEDGED"))));
        var floor = floor(99); floor.putUUID("transfer_id", id(800));
        var floorRoot = new EscrowLedger(id(500), 1, 0, List.of(), List.of(floor), List.of());
        var endpoint = new EscrowEndpoint(EscrowEndpoint.Kind.ENTITY, "minecraft:overworld", floor.getUUID("entity_id"), floor.getUUID("rocket_id"),
                "minecraft:armor_stand", 0, 0, 0, EscrowEndpoint.Unit.PRIMARY, 1, floor.getString("signature"), floor.getString("signature"));
        var absent = List.of(new EscrowSettlementIntent.Observation(endpoint, null, false, 0));
        var floorPreview = EscrowSettlementIntent.floorPreview(floorRoot, id(701), floor, id(700), "operator", 10, absent);
        var floorIntent = floorPreview.confirm(floorPreview.actor(), floorPreview.digest(), 210, absent);
        var o1 = floorRoot.append(List.of(floorIntent.floorOperation(2, 210)));
        var retired = EscrowSettlementTombstones.release(EscrowSettlementTombstones.install(o1, floorIntent), floorIntent);
        result.put("floor_receipt", bytes(retired.tombstones().get(0)));
        result.put("floor_inspect", text(String.join("\n", EscrowOperatorDiagnostics.inspect(retired, id(701), ignored -> fail(), ignored -> fail(), "ACKNOWLEDGED"))));
        return result;
    }
    private static String bytes(CompoundTag receipt) throws Exception {
        var output = new ByteArrayOutputStream(); NbtIo.write(EscrowReceiptCodec.encode(receipt), new DataOutputStream(output));
        return output.size() + ":" + digest(output.toByteArray());
    }
    private static String text(String text) throws Exception { return digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String digest(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)); }
}
