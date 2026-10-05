# NC1 committed source correction and fixed-commit verification

Date: 2026-10-05. Integrator: Root. Status: **SOURCE REPLAYED IN ISOLATION /
INTEGRATION PENDING**. Leaf `C17a-NC1-01`; focused correction `C17a-NC1-M1-02`.

## Scope and committed identity

Current candidate is `451bddb6450819e1438f516989b68239529ac44a`, parent
`4264a312098fe7ad4c35e84d928c00a33cfa8e3b`, on isolated task branch
`codex/v1.8.0-nc1-computation`. Root actually commits and non-force pushes it;
remote equality and clean task index are checked. Main source is not integrated.

| Exact repository path | Bytes / lines | SHA-256 |
| --- | --- | --- |
| `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketNativeFrame.java` | 15,855 /347 | `14b66ec800d3eade50e0aee57396aea7790a2510d0ac62dfe884c1ebfea35ec3` |
| `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketNativeFrameTest.java` | 22,548 /350 | `ecd622dbbcfe46dd598b6a3aa8ac324594686ececd9152e90538e208b8e17413` |

Only17 additions: three explicit TYPE guards after native array getters, before
length access; one14-line regression. Inverse checks reproduce the complete
original helper/test bytes; all22 original subjects/assertions remain unchanged.
No API, cap, enum, caller, schema, resource or writer change occurs. The adopted
23,442-byte private contract remains SHA89cd963e..., uncalled and package-private.

## Independent review and actual execution

The original4264 source review reports one Medium and separately reproduces six
public null-backed byte/int/long array NPEs. Original positive22 and negative6
records remain immutable. Source author c18 adds the focused repair; different
reviewer c17 independently reviews actual committed451b, not its own source.

[Correction review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-nc1-m1-review-20261005-c40391/REVIEW-02.md)
is11,208 B /SHA `bc9b9f74aca42403433194c8056ae3dc0d6d17c3be179ae9db60df885466cca6`.
It has no unresolved C/H/M/L in the exact correction; M1 is resolved only for
these three malformed-array refusals, not native persistence or whole-version
resource safety. Root reads it completely and fresh intake exits0, verifying
68 payloads/286,077 B/69 sums in
[SOURCE-REVIEW-INTAKE-02.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-nc1-root-replay-20261005-01/SOURCE-REVIEW-INTAKE-02.json).

| Actual cohort | Compile / JVM / outer | Observations |
| --- | --- | --- |
| Root development02, uncommitted repair | 0 /0 /0 | 23/23,0F/A/S/container failures; base4264 is not repair commit identity |
| Independent fixed451b Jupiter | 0 /0 /0 | 23/23,0F/A/S/container failures;27 inputs/config unchanged |
| Independent unchanged separate public-array control | 0 /0 /0 | Six observations/zero violations;25 inputs unchanged |
| Root fixed451b replay | 0 /0 /0 | 23/23,0F/A/S/container failures;27 inputs/config unchanged |

The six-observation control is not six extra Jupiter tests. Its source remains
3,031 B /b2c700e4..., identical to the original negative control. Both entrypoints
now produce their exact bounded refusals without normalization. Root's
[fixed-commit replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-nc1-root-replay-20261005-01/ROOT-REPLAY-RESULT-01.md)
independently binds Git helper/test/limits bytes before actual execution. No
development result is relabeled COMMITTED; no failed assertion or budget changes.

All cohorts retain exact JDK17.0.7/17cachedJAR/a003/harness pins, literal selector,
explicit source files,256MiB JVM/per-child120s/live2MiB stream limits and stated
direct-child/trusted-no-descendant qualifications. Capture is complete/error-free.
Elapsed/heap bounds are not allocation, GC, native-read or performance Gates.

## Cleanup and remaining acceptance

All new classes/home/temp/crash output is D project-parent only. Root development
and replay each safely remove18 own classes/62,139 logical B plus8 empty
directories; independent reviewer removes its28 own classes/93,683 logical B and
four output roots. Native literal no-reparse/inventory/hash/PID checks and actual
receipts are retained. Root additionally removes only four redundant original
publication source stdout copies/74,876 B after their reviewer releases pins;
compact hashes and fixed4264 Git-blob associations remain, with explicit removed
raw-copy qualification. Original runtime streams and sealed bundles are unchanged.

Full clean build/test/DataGen/GameTest and applicable integration checks remain
unperformed/unwaived while C is below10GB. No source delivery, ledger closure,
runtime caller, typed projection, reader/durability/first-event hold, ADR/risk or
Required Gate follows from this A0. Existing cargo/hash/save/migration behavior
and exclusive stable input/race/signed-zero limitations are unchanged. The main
historical integrated Java/Python cohorts are not reidentified as451b.

The next current-version action is completing applicable integration checks and
an explicit integration disposition; independently reviewed narrow numeric work
may proceed only through its own technical adoption and source assignment.
