# Survival-menu fixture: Root correction 12

2026-10-08. Root preserves Claude's unchanged two-file return at committed and
normally pushed `bb816ad469614a09293bc10b738d6cda27396a70`. Its original receipt
runner fails the working-tree check because the helper strips the first
porcelain line's leading space. A new public CAPTURE-CORRECTION-01.json checks
untrimmed status, source and both postimage hashes; all eleven checks pass.
The original receipt/helper/failure remain unchanged, not silently replaced.

This successor changes three test-only lines: cleanup-step aggregation catches
Throwable, not only RuntimeException, so an Error from a cleanup operation does
not itself skip later owned-resource cleanup. Primary setup/body failures remain
preserved by closeAfter/try-with-resources and the aggregate exception.
The original handoff11 remains the author's artifact; its every-step claim is
qualified by this additional change. Catastrophic VM/resource exhaustion is not
a claimed guaranteed cleanup environment.

No production, gameplay, schema, save policy, timing, assertion or network
change. Existing two tests and native menu operations remain unchanged.
Compilation, fault injection and actual runtime are unrun on this correction;
independent exact-source review/verification is required before main admission.
Neither author/source admission nor a Gate is concluded here.
