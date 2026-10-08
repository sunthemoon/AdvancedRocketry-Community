# Thermite source review 06 correction

2026-10-08. Root owns these changes after reviewer session
37ecb5bb-0c5d-4398-96cd-aad311cdc7c1 releases all interests. Its report is an
incomplete static review: the required governance reads were omitted, an
unauthorized read-only shell command ran, and Windows PowerShell policy blocked
the permitted script before any JVM execution. The postprocessing runner also
fails on the unauthorized command. These failures remain recorded; none is
retroactively authorized. A fresh successor must independently review and rerun
the actual corrected source before integration admission.

M1 is addressed in source, not yet by execution: one separate synchronous native
test disables the small plate press while using the actual RecipeManager for
both thermite recipes, checks literal outputs and preserves input counts/NBT.
Its finally block restores the override. Existing C15a press conservation,
no-input and disabled-operation cases remain unchanged and must run with the
full suite. The external-tag cases remain in the independent adapter fixture.

L1 replaces the worker's new deprecated ResourceLocation constructor with the
existing tryParse convention. L2 is a claim clarification: only the explicit
force flag and 160-cell mutation budget are bounded by this fixture; joining
the observer also introduces ordinary player tickets. No total loaded-chunk
bound or production-query loading exemption is claimed. The original handoff
is unchanged. L3 remains a disclosed dev-only mapped-field dependency, following
the existing airlock timeout-listener convention, not a release API guarantee.
L4 is measured: the corrected A0 Java file at e82e5682 is 22,829 bytes, below
the 24 KiB task cap. The preserved author's file at 31fef56e remains available.
L5 uses the context-aware light-emission overload in the owned adapter/native
tests. N1 moves the two added core imports beside the other core/platform imports.

No production behavior, resource pixels/JSON, recipe ratio, deadline, fixture
budget, save policy, risk, ledger or Gate conclusion changes. The prior exact
e82e5682 full build has 2,145 passing unit tests; its full GameTest run has all
538 required tests passing and 62 ERROR headers / zero FATAL headers. Those
results do not test this successor, close historical intermittent failures,
or satisfy G8, S1, restart, V1/V2 or the complete v1.8 Required Gates.
