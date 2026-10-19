package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.nio.file.Path;

/** Read-only, bounded baseline check of the receipt file, shared by the pre-start check and the startup bind. */
public final class BayReceiptBaseline {
    private BayReceiptBaseline() { }

    /** The digest of the checked target, which must read back twice as exactly the loaded root; null for no file. */
    public static String require(Path file, BayReceiptRoot loaded) {
        var expected = BayReceiptCodec.encode(loaded);
        var first = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.BAY_RECEIPTS);
        var second = CheckedSavedDataFile.readPayload(file, ManagedSavedDataType.BAY_RECEIPTS);
        EscrowTags.require(first.equals(second) && (first.isPresent() ? first.get().equals(expected)
                : loaded.writerGeneration() == 0 && loaded.receipts().isEmpty()), "Bay receipt baseline differs from the loaded root");
        return first.isPresent() ? BayReceiptCodec.digest(expected) : null;
    }
}
