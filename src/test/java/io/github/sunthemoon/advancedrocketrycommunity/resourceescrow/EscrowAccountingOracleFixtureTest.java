package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowFluidAccount.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Real saved codecs generate small, reproducible Python reader inputs, never native campaign evidence. */
class EscrowAccountingOracleFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static void save(String name, EscrowLedger root) throws Exception {
        var encoded = EscrowCodec.encode(root);
        assertEquals(root, EscrowCodec.decode(encoded));
        var outer = new CompoundTag(); outer.put("data", encoded);
        var directory = Path.of("build", "w365-fixtures"); Files.createDirectories(directory);
        NbtIo.writeCompressed(outer, directory.resolve(name + ".dat").toFile());
    }

    @Test void generateBothRootsAndKnownOrMissingReceiptStagesWithRealCodecs() throws Exception {
        for (boolean cargo : List.of(false, true)) {
            var op = (cargo ? EscrowCargoFixtures.operation(EscrowOperation.Kind.FLUID_CARGO_LOAD, 1, 99)
                    : operation(1)).withReason(EscrowReason.OTHER_STATE);
            String prefix = cargo ? "cargo-" : "fuel-";
            save(prefix + "root2", ledger(op));
            var old = ledger(op); var oldIntent = EscrowAccountingFormatTest.intent(old, List.of());
            save(prefix + "root2-receipt", EscrowSettlementTombstones.release(
                    EscrowSettlementTombstones.install(oldIntent.freeze(old), oldIntent), oldIntent));
            var planned = EscrowAccountingFormatTest.planned(op); save(prefix + "planned", planned);
            for (boolean missing : List.of(false, true)) {
                var base = EscrowAccountingFormatTest.baseline(op);
                var row = new Row(op.id(), base, new Frozen(missing ? Knowledge.UNKNOWN_ENDPOINT : Knowledge.KNOWN,
                        op.id(), List.of(op.id()), List.of(new FrozenEntry(base.moved(), OptionalLong.of(4000),
                        missing ? OptionalLong.empty() : OptionalLong.of(1000),
                        missing ? OptionalLong.empty() : OptionalLong.of(0), op.id()))));
                var rows = List.of(row);
                var observed = List.of(new EscrowSettlementIntent.Observation(op.source(),
                        new EscrowPrefix.State(planned.id(), 1, op.source().afterSignature(), List.of()), true, 4000),
                        new EscrowSettlementIntent.Observation(op.destination(), missing ? null
                                : new EscrowPrefix.State(planned.id(), op.destination().afterEpoch(), op.destination().afterSignature(), List.of()),
                                !missing, missing ? 0 : 1000));
                var preview = EscrowSettlementIntent.preview(planned, op.id(), missing ? EscrowSettlementIntent.Choice.ABANDON
                        : EscrowSettlementIntent.Choice.ACCEPT_CURRENT, id(700), "operator", 10, observed).accounting(rows);
                var intent = preview.confirm(preview.actor(), preview.digest(), missing ? 210 : 11, observed);
                var o1 = intent.freeze(planned, rows);
                var o3 = EscrowSettlementTombstones.install(o1, intent);
                var o4 = EscrowSettlementTombstones.release(o3, intent);
                String kind = prefix + (missing ? "missing-" : "known-");
                save(kind + "o1", o1); save(kind + "o3", o3); save(kind + "o4", o4);
                var receipt = o4.tombstones().get(0);
                assertTrue(EscrowSettlementProbe.accountingMatches(o4, receipt));
                var bad = receipt.copy(); bad.putString("accounting_status", missing ? "KNOWN" : "UNKNOWN");
                assertFalse(EscrowSettlementProbe.accountingMatches(o4, bad));
                bad = receipt.copy(); bad.putString("accounting_digest", hash(999));
                assertFalse(EscrowSettlementProbe.accountingMatches(o4, bad));
                assertFalse(EscrowSettlementProbe.accountingMatches(ledger(op), receipt));
                var legacy = receipt.copy(); legacy.putInt("schema_version", 3); legacy.remove("accounting_digest");
                legacy.putString("accounting_status", "UNKNOWN"); assertTrue(EscrowSettlementProbe.accountingMatches(ledger(op), legacy));
                assertFalse(EscrowSettlementProbe.accountingMatches(o4, legacy));
                legacy.putString("accounting_status", "KNOWN"); assertFalse(EscrowSettlementProbe.accountingMatches(ledger(op), legacy));
            }
        }
        NbtIo.writeCompressed(EscrowFluidAccountCodec.encode(EscrowFluidAccountSizeTest.worstRow(14),
                EscrowFluidAccountCodec.SAVED).value(), Path.of("build", "w365-fixtures", "worst-row.nbt").toFile());
    }
}
