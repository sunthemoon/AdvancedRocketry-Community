package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** State-only FP5: the root event remains authoritative after all transfer reservations have been released. */
class FirstEventStateFixturesTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void zeroRecordElectedSourceAndItsReceiptRoundTripThroughTheProductionCodecs() throws Exception {
        var event = FirstEventFixtures.moon(5, List.of(FirstEventCutSeams.P1, FirstEventCutSeams.P2));
        var journal = new RocketTransferSavedData();
        assertTrue(journal.electFirstMoonLanding(event));
        CompoundTag root = journal.save(new CompoundTag());
        var loaded = RocketTransferSavedData.load(root.copy());
        assertTrue(loaded.operational());
        assertTrue(loaded.entries().isEmpty());
        assertEquals(FirstEventOutbox.elected(event), loaded.firstMoonLandingRam());
        var progression = ClassicProgressionSavedData.provenAbsent();
        progression.insert(event);
        CompoundTag receipt = progression.save(new CompoundTag());
        assertEquals(event, ClassicProgressionSavedData.load(receipt.copy()).confirmedReceipt(event.kind()).orElseThrow());
        Path directory = Files.createDirectories(Path.of("build/w44l-state-fixtures/FP5"));
        write(directory.resolve(RocketTransferSavedData.DATA_NAME + ".dat"), root);
        write(directory.resolve(ClassicProgressionSavedData.DATA_NAME + ".dat"), receipt);
    }

    private static void write(Path file, CompoundTag data) throws Exception {
        CompoundTag outer = new CompoundTag();
        outer.put("data", data);
        NbtUtils.addCurrentDataVersion(outer);
        NbtIo.writeCompressed(outer, file.toFile());
    }
}
