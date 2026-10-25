package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Bounded immutable operator view; contains no writer, world lookup or recovery action. */
public record RocketRejoinInspection(int total, List<Row> rows) {
    public static final int MAX_ROWS = 8;
    public RocketRejoinInspection {
        rows = List.copyOf(rows);
        if (total < rows.size() || rows.size() > MAX_ROWS) { throw new IllegalArgumentException("Inspection bounds"); }
    }
    public record Row(RocketRejoinRoot.Batch batch, RocketRejoinRoot.Entry entry, String stage) { }

    public static RocketRejoinInspection of(RocketRejoinRoot root, List<RocketReleasePlan> admissions,
                                            Function<UUID, String> stage, UUID player) {
        var batches = new HashMap<UUID, RocketRejoinRoot.Batch>();
        root.batches().forEach(b -> batches.put(b.release(), b));
        var entries = new HashMap<RocketRejoinRoot.Key, RocketRejoinRoot.Entry>();
        root.entries().forEach(e -> entries.put(e.key(), e));
        for (var plan : admissions) {
            if (batches.putIfAbsent(plan.batch().release(), plan.batch()) == null) {
                plan.entries().forEach(e -> entries.put(e.key(), e));
            }
        }
        var rows = new ArrayList<Row>();
        for (var entry : entries.values()) {
            if (player == null || player.equals(entry.player())) {
                rows.add(new Row(batches.get(entry.release()), entry, stage.apply(entry.release())));
            }
        }
        rows.sort(Comparator.comparingLong((Row row) -> row.batch().sequence())
                .thenComparingInt(row -> row.entry().seatIndex()).thenComparing(row -> row.entry().player()));
        return new RocketRejoinInspection(rows.size(), rows.stream().limit(MAX_ROWS).toList());
    }
}
