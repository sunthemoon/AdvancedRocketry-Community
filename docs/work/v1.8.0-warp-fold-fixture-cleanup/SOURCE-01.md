# Charging-station fixture teardown

The ten-station GameTest measures the existing periodic credit-fold cadence and
dirty-state behavior. Its former teardown deleted those stations without first
draining the service's pending credits. A later server tick could then report
credit refusal outside the originating batch.

The retained component candidate at `4951a326` completed 613 required test
assertions but exited 1 during shutdown. Its separate strict checker also
failed: the credit-refusal warning appeared in `precision_unload_prepared`,
while the manifest incorrectly described passenger logout as its intentional
source. Matching station creation/deletion IDs and credit totals support the
charging-fixture explanation, but the warning itself does not log map IDs;
this record does not claim complete runtime attribution or CME causality.

The correction preserves all earlier measurements and assertions. After them,
one additional accepted credit makes nonempty teardown deterministic. The test
checks that the full pending total is credited while the stations still exist,
all fixture pending balances are zero and the total remains conserved. Cleanup
folds before deleting fixtures, including the already-covered setup-failure
branch. No production service, logger, cadence, timeout or checker changes.

The incorrect expected credit-refusal rule is removed under ADR-072's source
and rule maintenance policy. It is not replaced by additional allowed batches.
The new checker regression verifies that the warning shape is unclassified in
the originating, observed and previously declared batches. The 17 ERROR rules
requiring 62 entries and other WARN rules remain unchanged; recurrence must fail
the strict checker.

This is prepared source, not a delivered or accepted version slice. Independent
diff review and any later actual committed-source native verification will be
recorded in the external packet
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/warp-fold-fixture-cleanup-20261011-01`.
Prior failed runs and their source worlds remain preserved. All v1.8 Gates remain
open, including hosted Linux, persistence, client and asset qualification.
