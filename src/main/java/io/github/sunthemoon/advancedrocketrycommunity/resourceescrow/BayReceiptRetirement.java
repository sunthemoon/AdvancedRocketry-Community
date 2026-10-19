package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketTransactionType;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;

/**
 * The one online receipt deletion (W27A-DESIGN section 5, N5): an orphaned REJECTED receipt, which never registered a
 * satellite and never blocks anything, is retired by an operator. Every condition is checked on the server thread
 * against the RAM index and the journals; the retained slot ends only at the exact removal acknowledgement.
 */
public final class BayReceiptRetirement {
    public enum Refusal {
        NOT_LOADED, UNAVAILABLE, NOT_FOUND, NOT_REJECTED, RELEASED, UNACKNOWLEDGED, ROCKET_PENDING, TRANSFER_RECORD, TRANSACTION_RECORD;
        public String translationKey() { return "message.advancedrocketrycommunity.bay_receipt.refused." + name().toLowerCase(Locale.ROOT); }
    }
    /** What the journals say about one rocket; production reads the transfer and transaction SavedData. */
    interface Journals {
        /** Operational, with no record for the rocket and none with the receipt's transfer ID. */
        boolean transferClear(UUID rocket, UUID transfer);
        /**
         * Operational, with no record whose transaction ID is the rocket, and no disassembly record at all: a disassembly
         * record names the physical entity, not the logical rocket, so any one of them might be this rocket's.
         */
        boolean transactionClear(UUID rocket);
    }
    public record Checked(@Nullable BayReceipt receipt, @Nullable Refusal refusal) {
        static Checked refused(Refusal refusal) { return new Checked(null, refusal); }
    }
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private BayReceiptRetirement() { }

    static Checked check(BayReceipts receipts, UUID satellite, Journals journals) {
        switch (receipts.state()) {
            case NOT_LOADED -> { return Checked.refused(Refusal.NOT_LOADED); }
            case NO_FILE -> { return Checked.refused(Refusal.NOT_FOUND); }
            case LOADED -> { }
        }
        BayReceiptStore store = receipts.store();
        if (store == null) { return Checked.refused(Refusal.NOT_LOADED); }
        return check(store, satellite, journals);
    }
    static Checked check(BayReceiptStore store, UUID satellite, Journals journals) {
        if (store.unavailable()) { return Checked.refused(Refusal.UNAVAILABLE); }
        BayReceipt receipt = store.find(satellite).orElse(null);
        if (receipt == null) { return Checked.refused(Refusal.NOT_FOUND); }
        if (receipt.phase() != BayReceipt.Phase.REJECTED) { return new Checked(receipt, Refusal.NOT_REJECTED); }
        if (receipt.released()) { return new Checked(receipt, Refusal.RELEASED); }
        if (!store.acknowledged(receipt.op())) { return new Checked(receipt, Refusal.UNACKNOWLEDGED); }
        if (store.holdsRocket(receipt.rocket())) { return new Checked(receipt, Refusal.ROCKET_PENDING); }
        if (!journals.transferClear(receipt.rocket(), receipt.transfer())) { return new Checked(receipt, Refusal.TRANSFER_RECORD); }
        if (!journals.transactionClear(receipt.rocket())) { return new Checked(receipt, Refusal.TRANSACTION_RECORD); }
        return new Checked(receipt, null);
    }

    static Journals journals(MinecraftServer server) {
        return new Journals() {
            @Override public boolean transferClear(UUID rocket, UUID transfer) {
                var journal = RocketTransferSavedData.get(server);
                return journal.operational() && journal.findByLogicalRocket(rocket).isEmpty() && journal.find(transfer).isEmpty();
            }
            @Override public boolean transactionClear(UUID rocket) {
                var journal = RocketTransactionSavedData.get(server);
                return journal.operational() && journal.entries().stream().noneMatch(entry -> entry.record().transactionId().equals(rocket)
                        || entry.record().type() == RocketTransactionType.DISASSEMBLY);
            }
        };
    }

    /**
     * Writes the removal through the receipt writer. {@code done} and the one audit line follow the final outcome; an
     * unknown outcome only retries the exact candidate.
     */
    static BayReceiptStore.Admission retire(BayReceiptStore store, BayReceipt receipt, String operator, Consumer<BayReceiptWriter.Outcome> done) {
        String audited = operator(operator);
        return store.remove(receipt.op(), outcome -> {
            if (outcome == BayReceiptWriter.Outcome.UNKNOWN) { return; }
            LOGGER.info("ARCE_BAY_RECEIPT_RETIREMENT operator={} rocket={} bay={},{},{} satellite={} op={} outcome={}", audited,
                    receipt.rocket(), receipt.bay().x(), receipt.bay().y(), receipt.bay().z(), receipt.satellite(), receipt.op(), outcome);
            done.accept(outcome);
        });
    }
    /** At most 80 printable characters, so a custom source name cannot forge or flood audit lines. */
    static String operator(String raw) {
        var line = new StringBuilder();
        for (int i = 0; i < raw.length() && line.length() < 80; i++) {
            char value = raw.charAt(i);
            line.append(value > 32 && value < 127 ? value : '_');
        }
        return line.isEmpty() ? "unknown" : line.toString();
    }
}
