package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

/** Independent accounting for the gated S6 mono fixture, not a production recovery decision. */
final class EscrowLoaderS6Accounting {
    record Partition(long sourceAvailable, long sourceHeld, long destinationAvailable, long destinationHeld,
                     long inTransit) { }
    static Partition classify(boolean known, long source, long destination, Long heldByRecord,
            boolean sourceAfter, boolean destinationAfter, int markers) {
        EscrowTags.require(known, "S6 UNKNOWN is not zero or an available resource image");
        if (heldByRecord == null) {
            EscrowTags.require(markers == 0 && sourceAfter && destinationAfter
                    && source == 4_000 && destination == 1_000 && source + destination == 5_000,
                    "S6 completed image differs or holds resources without a record");
            return new Partition(4_000, 0, 1_000, 0, 0);
        }
        long q = heldByRecord;
        EscrowTags.require(q == 1_000 && markers == (sourceAfter ? 1 : 0) + (destinationAfter ? 1 : 0),
                "S6 changed record quantity or marker frontier");
        long residual = 5_000 - q;
        long sourceHeld = sourceAfter ? 0 : q, destinationHeld = destinationAfter ? q : 0;
        EscrowTags.require(source == residual + sourceHeld && destination == destinationHeld,
                "S6 native image is not explained by its durable record");
        EscrowTags.require(source + destination == residual + sourceHeld + destinationHeld,
                "S6 raw native differs from available plus held");
        return new Partition(residual, sourceHeld, 0, destinationHeld,
                sourceAfter && !destinationAfter ? q : 0);
    }
    private EscrowLoaderS6Accounting() { }
}
