package io.github.sunthemoon.advancedrocketrycommunity.rocket.scan;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBounds;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketSnapshotException;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketBlockMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStatsCalculator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketStatsValidator;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationIssue;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Resumable six-neighbor scanner. It never owns or requests a chunk ticket. */
public final class RocketStructureScanTask {
    private static final List<RocketPosition> NEIGHBORS = List.of(
            new RocketPosition(-1, 0, 0),
            new RocketPosition(1, 0, 0),
            new RocketPosition(0, -1, 0),
            new RocketPosition(0, 1, 0),
            new RocketPosition(0, 0, -1),
            new RocketPosition(0, 0, 1)
    );

    private final RocketScanWorld world;
    private final ResourceLocation sourceDimension;
    private final RocketPosition sourceOrigin;
    private final UUID snapshotId;
    private final long createdAtGameTime;
    private final ArrayDeque<RocketPosition> pending = new ArrayDeque<>();
    private final Set<RocketPosition> scheduled = new HashSet<>();
    private final List<RocketBlock> blocks = new ArrayList<>();
    private final List<RocketPosition> passengerAnchors = new ArrayList<>();
    private final Set<RocketBlockState> palette = new HashSet<>();
    private final Map<RocketBlockState, RocketBlockMetrics> metricsByState = new HashMap<>();
    private int totalInspections;
    private int blockEntities;
    private RocketPosition minimum;
    private RocketPosition maximum;
    private RocketScanResult terminalResult;

    public RocketStructureScanTask(
            RocketScanWorld world,
            ResourceLocation sourceDimension,
            RocketPosition sourceOrigin,
            UUID snapshotId,
            long createdAtGameTime
    ) {
        this.world = Objects.requireNonNull(world, "world");
        this.sourceDimension = Objects.requireNonNull(sourceDimension, "sourceDimension");
        this.sourceOrigin = Objects.requireNonNull(sourceOrigin, "sourceOrigin");
        this.snapshotId = Objects.requireNonNull(snapshotId, "snapshotId");
        if (createdAtGameTime < 0L) {
            throw new IllegalArgumentException("createdAtGameTime must not be negative");
        }
        this.createdAtGameTime = createdAtGameTime;
        pending.add(sourceOrigin);
        scheduled.add(sourceOrigin);
    }

    public RocketScanResult step(int inspectionBudget) {
        if (inspectionBudget <= 0 || inspectionBudget > RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK) {
            throw new IllegalArgumentException(
                    "inspectionBudget must be in [1, "
                            + RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK + "]"
            );
        }
        if (terminalResult != null) {
            return terminalResult;
        }

        int inspected = 0;
        while (inspected < inspectionBudget && !pending.isEmpty()) {
            if (totalInspections >= RocketLimits.MAX_SCAN_INSPECTIONS) {
                return fail(
                        issue(
                                RocketValidationCode.SCAN_BUDGET_EXCEEDED,
                                pending.peek(),
                                Map.of("limit", Integer.toString(RocketLimits.MAX_SCAN_INSPECTIONS))
                        ),
                        null,
                        inspected
                );
            }
            RocketPosition absolute = pending.removeFirst();
            inspected++;
            totalInspections++;
            RocketScanObservation observation;
            try {
                observation = Objects.requireNonNull(world.observe(absolute), "world observation");
            } catch (RuntimeException exception) {
                return fail(
                        issue(
                                RocketValidationCode.WORLD_CHANGED,
                                absolute,
                                Map.of("detail", safeMessage(exception))
                        ),
                        null,
                        inspected
                );
            }

            switch (observation.kind()) {
                case EMPTY -> {
                    if (absolute.equals(sourceOrigin)) {
                        return fail(issue(RocketValidationCode.EMPTY_STRUCTURE, absolute, Map.of()), null, inspected);
                    }
                }
                case BOUNDARY -> {
                    if (absolute.equals(sourceOrigin)) {
                        return fail(
                                issue(
                                        RocketValidationCode.BLOCK_NOT_MOVABLE,
                                        absolute,
                                        Map.of("detail", observation.detail())
                                ),
                                null,
                                inspected
                        );
                    }
                }
                case UNLOADED -> {
                    return fail(issue(RocketValidationCode.UNLOADED_CHUNK, absolute, Map.of()), null, inspected);
                }
                case FORBIDDEN -> {
                    return fail(
                            issue(
                                    RocketValidationCode.FORBIDDEN_BLOCK,
                                    absolute,
                                    Map.of("detail", observation.detail())
                            ),
                            null,
                            inspected
                    );
                }
                case UNSUPPORTED_BLOCK_ENTITY -> {
                    return fail(
                            issue(
                                    RocketValidationCode.UNSUPPORTED_BLOCK_ENTITY,
                                    absolute,
                                    Map.of("detail", observation.detail())
                            ),
                            null,
                            inspected
                    );
                }
                case MOVABLE -> {
                    RocketScanResult invalid = captureMovable(absolute, observation, inspected);
                    if (invalid != null) {
                        return invalid;
                    }
                }
            }
        }

        if (pending.isEmpty()) {
            return finish(inspected);
        }
        return RocketScanResult.running(
                inspected,
                totalInspections,
                blocks.size(),
                pending.size()
        );
    }

    public boolean terminal() {
        return terminalResult != null;
    }

    private RocketScanResult captureMovable(
            RocketPosition absolute,
            RocketScanObservation observation,
            int inspectedThisStep
    ) {
        if (blocks.size() >= RocketLimits.MAX_BLOCKS) {
            return fail(
                    issue(
                            RocketValidationCode.TOO_MANY_BLOCKS,
                            absolute,
                            Map.of("limit", Integer.toString(RocketLimits.MAX_BLOCKS))
                    ),
                    null,
                    inspectedThisStep
            );
        }
        RocketPosition relative;
        try {
            relative = absolute.subtract(sourceOrigin);
        } catch (RocketSnapshotException exception) {
            return fail(issue(exception.code(), absolute, Map.of()), null, inspectedThisStep);
        }

        RocketPosition prospectiveMinimum = minimum == null
                ? relative
                : new RocketPosition(
                        Math.min(minimum.x(), relative.x()),
                        Math.min(minimum.y(), relative.y()),
                        Math.min(minimum.z(), relative.z())
                );
        RocketPosition prospectiveMaximum = maximum == null
                ? relative
                : new RocketPosition(
                        Math.max(maximum.x(), relative.x()),
                        Math.max(maximum.y(), relative.y()),
                        Math.max(maximum.z(), relative.z())
                );
        try {
            new RocketBounds(prospectiveMinimum, prospectiveMaximum);
        } catch (RocketSnapshotException exception) {
            return fail(issue(exception.code(), absolute, Map.of()), null, inspectedThisStep);
        }

        boolean newPaletteEntry = !palette.contains(observation.state());
        if (newPaletteEntry && palette.size() >= RocketLimits.MAX_PALETTE_ENTRIES) {
            return fail(
                    issue(
                            RocketValidationCode.TOO_MANY_PALETTE_ENTRIES,
                            absolute,
                            Map.of("limit", Integer.toString(RocketLimits.MAX_PALETTE_ENTRIES))
                    ),
                    null,
                    inspectedThisStep
            );
        }
        if (observation.payload().isPresent() && blockEntities >= RocketLimits.MAX_BLOCK_ENTITIES) {
            return fail(
                    issue(
                            RocketValidationCode.TOO_MANY_BLOCK_ENTITIES,
                            absolute,
                            Map.of("limit", Integer.toString(RocketLimits.MAX_BLOCK_ENTITIES))
                    ),
                    null,
                    inspectedThisStep
            );
        }

        RocketBlockMetrics previousMetrics = metricsByState.putIfAbsent(
                observation.state(),
                observation.metrics()
        );
        if (previousMetrics != null && !previousMetrics.equals(observation.metrics())) {
            return fail(
                    issue(
                            RocketValidationCode.WORLD_CHANGED,
                            absolute,
                            Map.of("detail", "inconsistent metrics for one block state")
                    ),
                    null,
                    inspectedThisStep
            );
        }

        minimum = prospectiveMinimum;
        maximum = prospectiveMaximum;
        palette.add(observation.state());
        RocketBlock block = new RocketBlock(
                relative,
                observation.state(),
                observation.payload().orElse(null)
        );
        blocks.add(block);
        if (observation.metrics().seat()) {
            passengerAnchors.add(relative);
        }
        if (observation.payload().isPresent()) {
            blockEntities++;
        }

        for (RocketPosition offset : NEIGHBORS) {
            RocketPosition neighbor;
            try {
                neighbor = absolute.add(offset);
            } catch (RocketSnapshotException exception) {
                return fail(issue(exception.code(), absolute, Map.of()), null, inspectedThisStep);
            }
            if (scheduled.add(neighbor)) {
                pending.addLast(neighbor);
            }
        }
        return null;
    }

    private RocketScanResult finish(int inspectedThisStep) {
        RocketStats stats;
        try {
            stats = RocketStatsCalculator.calculate(blocks, metricsByState::get);
        } catch (RuntimeException exception) {
            return fail(
                    issue(
                            RocketValidationCode.WORLD_CHANGED,
                            sourceOrigin,
                            Map.of("detail", safeMessage(exception))
                    ),
                    null,
                    inspectedThisStep
            );
        }
        RocketValidationResult validation = RocketStatsValidator.validate(stats);
        if (!validation.valid()) {
            terminalResult = RocketScanResult.failed(
                    validation.issues(),
                    stats,
                    inspectedThisStep,
                    totalInspections,
                    blocks.size(),
                    0
            );
            return terminalResult;
        }

        try {
            RocketStructureSnapshot snapshot = RocketStructureSnapshot.create(
                    snapshotId,
                    sourceDimension,
                    sourceOrigin,
                    blocks,
                    passengerAnchors,
                    stats,
                    createdAtGameTime
            );
            RocketSnapshotNbtCodec.encode(snapshot);
            terminalResult = RocketScanResult.success(snapshot, inspectedThisStep, totalInspections);
            return terminalResult;
        } catch (RocketSnapshotException exception) {
            RocketPosition position = exception.position()
                    .map(relative -> {
                        try {
                            return sourceOrigin.add(relative);
                        } catch (RocketSnapshotException ignored) {
                            return sourceOrigin;
                        }
                    })
                    .orElse(sourceOrigin);
            return fail(
                    issue(exception.code(), position, Map.of("detail", exception.getMessage())),
                    stats,
                    inspectedThisStep
            );
        }
    }

    private RocketScanResult fail(
            RocketValidationIssue issue,
            RocketStats stats,
            int inspectedThisStep
    ) {
        terminalResult = RocketScanResult.failed(
                List.of(issue),
                stats,
                inspectedThisStep,
                totalInspections,
                blocks.size(),
                pending.size()
        );
        return terminalResult;
    }

    private static RocketValidationIssue issue(
            RocketValidationCode code,
            RocketPosition absolute,
            Map<String, String> parameters
    ) {
        HashMap<String, String> withSpace = new HashMap<>(parameters);
        withSpace.put("coordinate_space", "absolute");
        return new RocketValidationIssue(code, absolute, withSpace);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
