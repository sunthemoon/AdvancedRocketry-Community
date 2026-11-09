# W336 native observation cut list

This worker uses `codex/v1.8.0-w336` from `cac0491fc`. Root reviews and merges;
the worker commits locally, does not push, and does not delegate. This design
is committed before implementation. Production admission remains disabled.

## Write scope and implementation

Only new W336 Python runner/oracle modules, their Python tests, and W336's two
records are writable. Existing runners, their tests, production Java, central
status files, risk register, ledger, build files and QUEUE remain unchanged.
External execution scripts, logs and results belong to
`D:/GitHub/ARCE-Task-Evidence/root-queue/W336/`; owned disposable servers belong
to that task's Temp directories. Existing source worlds are read-only templates.

| Family | Cuts and observations |
|---|---|
| Timing | Vanilla `time query gametime` before arming and after terminal events. D6 simultaneous gated deployments at two census sizes, per-claim first BAY_PROOF_TURN through READY_SOURCE, interval per turn and divided by census slots. D6 refusal/retry spacing, B13 registry failures and REGISTRY_UNPROVED, B12 D4/D7 receipt-store waits, and B16_GUARD parking. |
| Expiry and persistent waits | Reconnect expiry relative to login and one-second `execute if loaded` polling of census columns after READY_SOURCE or RELEASED_TO_ORDINARY. Held waits persist through a specified observation window and two restarts; finite observations are not upper bounds. |
| Shared denial pool | Stopped-world unsupported roots from another device family establish 255 distinct denied chunk coordinates. Each bay/cargo kind and a fuel-loader control supplies the next coordinates in separate disposable worlds. Terrain in a device-free chunk is checked before/after saves and both restarts. |
| Downgrade | A stopped world contains all five placed devices, receipts, an escrow ledger and a schema-3 transfer journal. Use W176's exact historical JAR and refusal/whole-world byte oracle, preserve metadata registry differences and raw protected stores, then restore the complete backup and start the supplied JAR. |

`ChunkSaveDenials.deny` accepts 256 distinct coordinates and saturates on the
257th, not the 256th. The saturation cuts must observe both 255 + 1 (not yet
saturated) and the overflow. This is an explicit task-wording discrepancy, not
a production change or a relaxed assertion.

## Evidence and verification

The runner accepts candidate JAR, SHA-256 and commit pins, verifies unchanged
production inputs, never builds a candidate, and labels local smokes separately.
Timing observations retain raw console lines and tick intervals: asynchronous
console delivery cannot establish an exact transition tick when its neighboring
game-time queries differ. No production instrumentation is added.

Every runner/oracle addition receives Python tests, including missing evidence,
wrong boundaries, corrupted roots and contradictory negative controls. Existing
runner tests remain unchanged. Run strict repository validation and the full
unit suite. Heavy build/native/GameTest steps run in detached lock-owning scripts
with the atomic backslash machine lock, checked free space, bounded waits,
owner-token release and owned-directory cleanup in the same `finally`. Release
between steps and wait at least 90 seconds before reacquiring.

Native smokes use a locally built, pinned JAR for each family and both restarts
where applicable. A scratch JAR with the save protection disabled must fail the
unchanged saturation oracle; discard only that worker-owned copy. Downgrade
storage failures remain failures under W176's oracle. No reference-node timing,
V1/V2, candidate qualification or Gate approval is claimed.

## Exclusions and open choices

NOT_LOADED and the retained pending pool belong to W332. Production behavior,
new schemas, IDs, migration, assets, network changes and risk acceptance are
excluded. Root must arrange the independent read-only review before integration.
Any unavailable fixture or native step is recorded as unfinished, never PASS.
