# C17a-NUM-01: private propulsion arithmetic

Date: 2026-10-05. Integrator and contract adopter: Root. Implementer:
delegated `c18_contract`; independent source reviewer: delegated `c17_contract`.
Status: **INTEGRATION_PENDING / NARROW TECHNICAL CONTRACT ADOPTED**. Isolated
source is reviewed and replayed in the [verification record](NUMERIC-SOURCE-VERIFICATION-01.md).
The exact files are now [introduced on the development branch](PRIVATE-INTEGRATION-CHECKPOINT-01.md);
completed main integration and delivery remain open. This is an internal prerequisite, not full T1/T2.

## Authority and reviewed object

The owner's received conversation selection is recorded in
[NUMERIC-OWNER-DECISION-01.md](NUMERIC-OWNER-DECISION-01.md), verbatim:
“采用建议规则：binary64 舍入＋按支持比例节省（推荐）”. It preserves basic/external
rockets and old captured values. The earlier conditional authorization is,
verbatim: “授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认”.
These are conversation replies without separate supplied timestamps, not new
owner approval of every clause below.

Under that conditional authorization and ADR-065's scoped review requirement,
Root adopts only sections 3–5 of the exact
[private proposal](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-numeric-leaf-preparation-20261005-6da184/PROPOSED-LEAF-01.md),
13,619 bytes / SHA-256
`35b6454e3f160060ecd26b7b77de2b20300f18f7efe26a9ae496d4e2de1349ce`,
together with its exclusions and verification requirements. Sections 1 and 6
are exclusions or future recommendations, not accepted caller stages. Its
historical PROPOSAL status is superseded only within this private scalar scope.

The different-agent
[contract review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-numeric-private-review-20261005-f60271/REVIEW-01.md),
13,285 bytes / SHA-256
`32c6907b996e41a5b8e3fe4ea940ea95caacabe58f922d2246dfc30dfaac14af`,
has no unresolved C/H/M/L in that exact scope. Root read both complete texts and
freshly verified author 32 payloads /178,634 bytes /33 checksums and reviewer
29 payloads /113,869 bytes /30 checksums; see
[Root intake](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17a-numeric-root-intake-20261005-01/INTAKE-02.json).
The review's 20 finite controls are not Java results. Its original source-selector
failure remains preserved. No accepted ADR bytes or risk disposition are changed.

## Observable outcome and frozen private surface

One package-private final `RocketPropulsionNumbers`, private constructor, with
`captureMultiplierUnits(double)`, `scaleHostRounded(long,long)` and
`nuclearSupport(long,long)`. Its private-construction immutable nested support
result exposes only the five primitive getters in the proposal. JDK arithmetic
and unchanged `RocketLimits.MAX_BLOCKS` are the only dependencies.

- Capture finite binary64 values in inclusive 0.1..4.0 exactly using D=2^56.
  Units are 7,205,759,403,792,794..288,230,376,151,711,744 on the binary64 grid.
- Scale base 0..10,240,000 with bounded, at-most-82-bit product arithmetic,
  nearest-even binary64 rounding, retained carry and then integer floor.
  Validate BASE before MULTIPLIER, including invalid units at base zero.
- For nominal 1..7,168,000 and support 1..819,200,000, return E=min(N,C),
  canonical E/N and 2E/(7N). This does not perform a Q ceiling or fuel debit.
- Invalid input produces only the proposal's fixed BASE/MULTIPLIER/SUPPORT
  IllegalArgumentException messages, without input values, causes or logging.

These domains bound arithmetic, not external APIs or hardware legality. Positive
support is a calculator domain, not an alternative launch validator.

## Ownership and checkout

Only NEW files may be written:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketPropulsionNumbers.java`
2. `src/test/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/model/RocketPropulsionNumbersTest.java`

Root will create `D:/GitHub/arce-v180-propulsion-numbers-20261005`, branch
`codex/v1.8.0-propulsion-numbers`, from the published commit containing this task;
the preparation parent is `bdda2e21662ce23deb4b351d9fe6f8e9dc0142ed`.
Root's assignment message must supply the actual task/base SHA before writing.
The implementer reads effective user-maintained main AGENTS, checks the task
checkout and writes no existing/central file. Root alone commits and pushes.
All helpers, process temporary directories and outputs stay under a fresh
`D:/GitHub/ARCE-Task-Evidence/v1.8.0` leaf. Do not copy committed source into
evidence or alter sealed packets. No other worktree is adopted or cleaned.

## Verification and acceptance

Verify range/grid/raw-bit capture, invalid and nonfinite input, validation
priority, R versus exact-floor goldens, nearest-even/carry/extrema, aggregate
versus per-term distinction, support bounds/min/gcd/P fractions, fixed errors
and immutable primitive results. Use literal vectors and independent actual
Java 17 multiplication references, not only duplicate helper arithmetic.
Review visibility/dependencies, absent callers and unchanged legacy source.

The author may inspect and run lightweight static checks only. Java execution
requires Root's separately bound serial A0 runner/config grant: explicit four
source files (the two new files, unchanged limits and existing external Jupiter
harness), one test class, cached pinned JUnit, Java 17, Xmx256m, D-only output/temp,
120-second child and 2 MiB stream bounds. No Gradle/native/server/client execution
or new downloads are assigned. Root preserves actual argv, streams, exits, source
pins and original failures; an uncommitted run is only development evidence.

Publish the exact source commit/non-force push, then obtain independent actual
source review with critical-test replay and Root's fixed-commit replay. Clean
only owned fresh class outputs with literal checked native commands afterward.
Those checks can verify this isolated source, not deliver full propulsion.
Main integration still requires the applicable compile/unit/GameTest/resource
regression in the parallel-development policy. Current C free space is below
10 GB: heavy commands remain prohibited, not waived. No integration/delivery or
ledger checkbox is assigned while those checks remain absent.

## Explicit non-goals and open joins

No caller, config, registry, engine/core/tank geometry, per-engine allocation,
global application stage, final resource rounding, frame/schema/hash/migration,
fuel transfer, writer, first-event hold or transaction permission. Do not apply
this helper to basic/external or old captures. New advanced abstract non-default
applicability and connected-core/global/tank stage ordering need separate exact
caller adoption; a material semantic change still needs owner confirmation.
T3–T7/O3, shared lifecycle/recovery, R-021 and G0–G9 remain open.
