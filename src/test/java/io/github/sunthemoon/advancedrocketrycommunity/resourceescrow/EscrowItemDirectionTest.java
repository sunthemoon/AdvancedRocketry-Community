package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class EscrowItemDirectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyLoaderToRocketLoadAndRocketToUnloaderUnloadAreValid() {
        var cases = new ArrayList<Executable>();
        for (var kind : EscrowItemFixtures.KINDS) {
            var op = EscrowItemFixtures.operation(kind, 1, 99);
            var rocket = EscrowCodec.endpoint(op.rocket());
            var loader = EscrowCodec.endpoint(op.device()); loader.putString("type", "advancedrocketrycommunity:rocket_item_loader");
            var unloader = loader.copy(); unloader.putString("type", "advancedrocketrycommunity:rocket_item_unloader");
            for (var source : List.of(loader, rocket, unloader)) {
                for (var destination : List.of(loader, rocket, unloader)) {
                    var encoded = EscrowCodec.operation(op);
                    encoded.put("source", source.copy()); encoded.put("destination", destination.copy());
                    // Keep reference bindings coherent so this tests roles, not an unrelated digest mismatch.
                    encoded.getCompound("native_binding").putString("src_ref", RocketNativeResources.signature(source));
                    encoded.getCompound("native_binding").putString("dst_ref", RocketNativeResources.signature(destination));
                    boolean allowed = kind.cargoLoad() ? source == loader && destination == rocket
                            : source == rocket && destination == unloader;
                    cases.add(() -> {
                        if (allowed) {
                            var decoded = assertDoesNotThrow(() -> EscrowCodec.operation(encoded));
                            assertEquals(encoded, EscrowCodec.operation(decoded));
                        } else {
                            EscrowItemStackCodecTest.refused(EscrowItemStackCodec.Status.INVALID_DATA,
                                    () -> EscrowCodec.operation(encoded));
                        }
                    });
                }
            }
        }
        assertAll(cases);
    }
}
