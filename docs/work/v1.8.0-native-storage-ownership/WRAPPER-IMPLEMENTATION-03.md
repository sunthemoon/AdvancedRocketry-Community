# Public native storage wrapper experiment

Date: 2026-10-11, Asia/Taipei. Base: `ba89575fd05b595af3426227124c37683e7bf925`.
Status: `IMPLEMENTED_RUNTIME_NOT_RUN`.

This v1.8.0 test-only slice adds two required Forge GameTests. The running server
provides its actual initialized DataFixer. No product storage policy, world
schema, public contract, expected log rule or existing assertion/timeout changes.

- Four fixed small marker trees are submitted through public `ChunkStorage`,
  then read initially, after native flush, and through a fresh reopened wrapper.
  Values must persist. Root and child identity are reported without demanding a
  particular pending/disk scheduling result. Submitted inputs are never mutated.
- A currently loaded native chunk is serialized and bounded before deep copy.
  Public context injection and current-version conversion use exclusively owned
  unpublished inputs, actual dimension/storage context and an explicitly absent
  optional generator key. The result must preserve values and omit temporary
  conversion context. Identity is observed, not assumed.

The tests use independent newly allocated storage directories and try-with-
resources. `close_returned=true` records only that the public close call returned;
the native worker can log a storage-close failure without propagating it. Original
native logs must therefore be checked separately; successful handle release is
not established by that label. Future reads have five-second deadlines. Flush and close have native
internal joins, so the outer validation process must also be bounded. Each test
emits at most nine and two observation lines respectively. No NBT contents or
player data are printed. The two new tests use 100-tick budgets and do not alter
any existing test budget. Only the loaded test chunk is inspected; no arbitrary
chunk is requested or loaded.

The experiment does not call conversion on published NBT, inject a concurrent
writer, force pending scheduling, simulate a crash, or attribute hosted run
38064322640. Current-version/no-generator conversion is not an old-schema or
generator migration matrix. Native reopen is not power-loss durability. The
earlier direct-IOWorker experiment remains on its separate unintegrated branch.

Write scope is this GameTest class and this implementation record, on independent
branch `test/v1.8.0-native-wrapper-contract` in
`D:/GitHub/arce-v180-native-wrapper-contract-20261011-02`. Root owns implementation,
runtime validation, commit and push; independent reviewers only inspect source
and evidence. The owner-dirty Main implementation log and AGENTS are untouched.

Source has not been compiled or run at this checkpoint. External task/runtime
evidence starts at
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/native-wrapper-contract-20261011-02/TASK.md`.
R-021, hosted exception attribution, all Required Gates, full Python/resource
qualification and real-client obligations remain open.
