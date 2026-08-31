package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import java.util.UUID;

public interface RocketTransactionJournal {
    RocketTransactionJournal NO_OP = new RocketTransactionJournal() {
        @Override
        public void write(RocketTransactionRecord record) {
        }

        @Override
        public void remove(UUID transactionId) {
        }
    };

    void write(RocketTransactionRecord record);

    void remove(UUID transactionId);
}
