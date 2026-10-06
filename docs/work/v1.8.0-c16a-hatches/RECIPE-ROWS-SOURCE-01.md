# Immutable classic recipe-row source checkpoint

Date: 2026-10-07. Source checkpoint published and bounded fixed-commit replay
complete. This is the three-row data slice, not completion of shared hatches,
the full validated recipe, lathe formation or any release Gate.

## Committed scope and design

Commit `60f1564528de782fa15269884fd1355d3c0b9ff1` adds exactly the three
immutable records, nine-subject test and [assignment](RECIPE-ROWS-TASK-01.md).
Normal push and exact remote verification succeed (Root tool `16fe06`, exit 0).
The independently reviewed five postimages match the committed bytes.
Owner AGENTS and unrelated inherited files remain unchanged and unstaged.

- [ClassicItemInput](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicItemInput.java)
  retains canonical ingredient JSON and count 1..64. Its shallow strict parser
  accepts the existing vanilla item/tag grammar, including shorthand, bounds
  text to 4,096 characters and alternatives to 32, then checks shared grammar
  and canonical JSON equality. It neither normalizes input nor resolves tags.
- [ClassicItemOutput](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicItemOutput.java)
  and [ClassicFluidRow](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/machine/classic/adapter/ClassicFluidRow.java)
  bound IDs to 128 characters, counts to 1..64 and fluid amounts to 1..16,000.
  No constructor asserts native registration, existence, stack limits or
  resource-transfer authority.

These are the exact accepted inventory section 5 records. No GuardTicket stub,
native getter, public structural-frame admission, bank edit, registry,
serializer, network, asset or upstream import accompanies this slice.

## Actual verification

Different-agent [source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c16-classic-recipe-rows-review-20261007-8b77a1/REVIEW-01.md),
SHA `0b1311db4caa3d3a4a4f06b903897d6e4e3c12440862a11ac42038c150adbe2b`,
establishes no introduced C/H/M/L in this five-file scope. Its own javac and
actual Jupiter exit 0: nine original subjects plus four reviewer boundary
controls, **13/13 pass /0 failed, aborted, skipped or container failures**.
Forty-four selected inputs remain unchanged; 19 finite static controls pass.
That review execution is an uncommitted development observation, not relabeled
as the later delivery replay.

Root's fresh [fixed-commit replay](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-classic-recipe-rows-fixed-20261007-01/RESULT-01.json),
SHA `a7aaa9bc9c56d50eae71de3ac7f333df26b4e6b13ce08233535f5606fe5e904a`,
actually exits 0 (tool `b56287`). Each selected project input matches the
fixed Git object and the five new files match the released review pins.
Java 17 javac exits 0; actual Jupiter executes all nine original methods once:
**9/9 pass /0 failed, aborted, skipped or container failures**.
Forty-three selected inputs show no drift before/after execution.

This bounded command selects 11 explicit sources including the external real
Jupiter harness, 27 pinned cached JARs, fresh D output, empty sourcepath,
`-proc:none`, 256 MiB, two CPUs and 120 seconds per child. No cached project
classes, Minecraft bootstrap, Gradle or native server is used. Four
ResourceLocation constructor deprecation warnings and the unchanged shared
validator note are retained, not suppressed.

The original development attempt remains preserved: javac 0, Jupiter 1,
two of nine subjects pass and seven fail missing native Authlib Property.
The separately numbered successor adds only the pinned Authlib dependency;
unchanged source/tests pass all nine subjects. Neither run is rewritten or
substituted for the fixed committed replay.

## Cleanup and remaining work

Root safely removes only its three ended row-run class/home/temp/empty-source
trees, after checking exact ownership, containment, no reparse points and
retained output hashes. [Cleanup receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-row-output-cleanup-20261007-01/RECEIPT-01.json),
SHA `567b9d8575134daa514db144d2c3fe5f38cbda523f3c4d922f6df77888e8cdb0`,
records 48 class files /162,843 logical bytes removed; physical allocation
is not measured. Raw commands/results/streams remain. Old refused targets
and other agents' outputs are not touched or retried.

Complete guarded owner/lifecycle/frame/native-hash integration, recipe-wide
validation and native planning, physical hatches/lathe, registration and
restart/client/content acceptance remain incomplete. The exact committed full
regression now succeeds with audited raw/JAR results in
[RESULT-15](../v1.8.0-ci/RESULT-15.md); previous failures remain historical in
RESULT-14, not rebound to this row commit. C below 10 GB prevents local
full/native runs. All G0-G9 remain open.

The [new source workflow](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37519061729)
was observed at 2026-10-06T19:34:28Z as IN_PROGRESS, attempt 1 /job 112459397257.
That dated observation is superseded by RESULT-15. Root's original GET-only
[observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-row-ci-observation-20261007-01/OBSERVATION-01.json),
SHA `98b11d8c3456fa81ec9c214fd6e948bf39ac470a9eeedc7906f3a5d5cc9f67be`,
exits 0 (tool `6ff743`). No retry, workflow mutation or Gate result is inferred.
