package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.IdentityHashMap;
import java.util.Map;

/** Registry-owned measurements of current immutable residents, independent of lifecycle reservations. */
final class RecordEncodingIndex {
    private final RecordSizer sizer;
    private final Map<Object, RecordSizer.EncodingSize> sizes = new IdentityHashMap<>();
    private long bytes;
    private long heapBytes;
    private int unknown;

    RecordEncodingIndex(RecordSizer sizer) { this.sizer = sizer; }

    /** Called only after the owning map changes; returns its previous resident for the existing indexes. */
    <T> T changed(T previous, T next) {
        if (previous == next && sizes.containsKey(next)) { return previous; }
        if (previous != null) {
            var old = sizes.remove(previous);
            if (old != null) { account(old, -1); }
        }
        if (next != null) {
            RecordSizer.EncodingSize measured;
            try {
                measured = sizer.encodingSize(next);
                if (measured == null) { measured = RecordSizer.EncodingSize.UNKNOWN; }
            } catch (RuntimeException failedSizing) {
                // This is derived evidence only. The original preflight retries the actual encoding later.
                measured = RecordSizer.EncodingSize.UNKNOWN;
            }
            var old = sizes.put(next, measured);
            if (old != null) { account(old, -1); }
            account(measured, 1);
        }
        return previous;
    }

    RecordSizer.EncodingSize total() {
        return unknown == 0 ? new RecordSizer.EncodingSize(bytes, heapBytes) : RecordSizer.EncodingSize.UNKNOWN;
    }

    RecordSizer.EncodingSize resident(Object record) {
        return sizes.getOrDefault(record, RecordSizer.EncodingSize.ZERO);
    }

    private void account(RecordSizer.EncodingSize size, int direction) {
        if (!size.known()) { unknown += direction; }
        else {
            bytes += direction * size.bytes();
            heapBytes += direction * size.heapBytes();
        }
    }
}
