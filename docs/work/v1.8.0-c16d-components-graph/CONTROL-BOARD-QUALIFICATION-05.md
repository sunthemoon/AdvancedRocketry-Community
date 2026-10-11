# Control board local functional qualification

2026-10-11. This component's committed-source local functional qualification,
independent inert reviews and Main local source fast-forward are complete.
The source branch is normally pushed. Record descendants, Main remote push,
new hosted CI and final sealing are separate observations, not additional JVM
sources. No full C16d delivery, human art approval or release Gate is accepted.

## Source and product boundary

Exact JVM-tested source c2934b83b9e1d1a89cf10fa437dd66cdc29d760a is the direct
generated-resource child of author d92c0838c3ecc0b0ecaeba3345158c10d83473e3;
author's direct parent is b3ad9020a19045c072fc3223f9e04697e314058b.
The [scoped adoption](CONTROL-BOARD-ADOPTION-05.md) follows independent actual
[contract](CONTROL-BOARD-CONTRACT-05.md)/preauthoring/caller review. It freezes
control_board and precision_control_board, both producers and modern counts one,
not the old precision loader's absent-count runtime default.

One plain 64-stack Item, one creative entry and no custom NBT/capability, fuel,
ticking, packet or world root. Crafting rvr/dwd/dpd uses redstone x2, quartz x1,
copper dust x4, wooden slab x1, iron plate x1 -> one board; copper dust OR existing
crafting recipe unlock, crafting-only reward. Precision ordered redstone dust,
copper plate, steel plate x1 each, trailing input channels 3/4 empty, output
channel 0 one board/channel 1 empty, 200 ticks x10 FE =2000 FE. Existing circuit,
Item IO/Liquid IO, stand-in recipe IDs, kernel/signature/journal/tag limits,
schema, hatches, build/workflow and log checker/manifest are unchanged.

Product files are new V180ControlBoardData, V180ControlBoardDataTest and
ControlBoardGameTests; additive ModItems/ModCreativeTabs, BootstrapDataGenerators
and sole V180LanguageProvider entries; five scoped model/icon/recipe/unlock
resources and two shared language additions; dedicated provenance/preauthoring/
contract/adoption/local record, CHANGELOG and only asset-plan order 244.
The record descendant changes only this qualification, local implementation
record and CURRENT_VERSION/COMPLETION-PLAN.

NEW 16x16 clock/control PCB PNG: 175 bytes, SHA-256
8fd8467ec9fb29146aa76ec2bbb87529057c7c1450181056023483bc3c0ee3a6.
All seven schema-2 entries remain PENDING_HUMAN_REVIEW. source_commit is the
authoring parent; authoring_commit identifies new generator source. Informational
review.content_digest hashes compact sorted pre-review JSON plus one LF; it
does not equal human reviewed_content_sha256 or confer human approval.

## Actual execution and retained output

Root alone ran the fixed caller, Java 17.0.7 wrapper with 64 MiB heap,
--no-daemon --max-workers=2, 1 GiB Gradle heap and external finite-native.gradle.
Full argv, original/enriched receipts, custody and complete streams are external.

| Command | Original exit / seconds | Actual result |
| --- | --- | --- |
| Wrapper clean build | 0 /194.3218001999776 | test task executes; 2239 testcase children /386 XML; zero failure/error/skip |
| Wrapper runData data-01 | 0 /27.74681610002881 | seven seed/committed byte identities and empty whole-tree diff |
| Wrapper runData data-02 | 0 /23.570002100022975 | same seven identities and empty diff |
| Wrapper runGameTestServer | 0 /281.441652999958 | 631 complete/required-pass; six-test control_board batch; final shutdown |
| python -B scripts/check_gametest_log.py copied-log --manifest scripts/gametest_expected_log.json | 0 /0.13693229999626055 | 62 ERROR /160 WARN /0 FATAL; 37 unchanged expectations |

Fresh authoring seed exits 0 /59.00481749995379, HashCache old count 0/written
870. Final repeats have old count 870/written 0: cached idempotence/byte checks,
not two cold-cache regenerations. Build stderr retains 29595 bytes/69 removal
compiler warnings and Gradle deprecations; data/native/checker stderr is empty.
Control/Item IO/Liquid IO each have eight actual successful JUnit children.
Repeated parameterized display labels do not become unique-name proof.

Retained XML archive is 319353 bytes, SHA-256
719fb24e236a70b8d727fea5a7e9482d2859cf4f6e4d0868cbe73e388e36bd80;
386 flat XML members total 1187469 uncompressed bytes. Original/copied server
log is 1930495 bytes, 6012 lines, SHA-256
d47acbeed49033a3c5f3e5ffa5db6e14aa8035539b050ca2a42675fe8e6ab992.
Completion/pass are lines 5986/5987, control batch 3228, final shutdown 6012.

Native fixtures check loaded memberships (copper dust distinct from plate/ingot),
plain ItemStack round trips/no fuel, crafting/slot refusal and ordered unique
precision matching against the installed catalog; one formed v1.2 assembler
consumes exactly three supplied inputs and 2000 injected FE without duplicate
output. Query/assemble does not debit a real player's crafting inventory.
Supplied steel/FE do not prove initial steel, ordinary survival acquisition,
full graph/access/discovery or shared classic physical hatch delivery.

All original captures have complete EOF/no overflow/errors and zero remaining
owned Job processes after retirement. Envelope: original 900 seconds plus owned
retirement 10 seconds, 2 MiB per stream, read-back-before-resume 6 GiB Windows
Job; observed native Xms256 MiB/Xmx2 GiB. These are not lifecycle peak/soak,
hostile isolation, natural all-descendant exit or independent JVM replay proof.
One allowed 2114 ms /42-tick overload WARN is not a performance pass.

## Independent reviews and corrections

External unchanged reports in control-board-acquisition-20261011-20:

| Review | Bytes | SHA-256 |
| --- | ---: | --- |
| Input assessment 01 | 22504 | b085a5205c833ebc2947facfb88547940c7aae45bf24533aac6cde67b02658af |
| Contract/caller 02 | 24412 | 61f51796ffc52831056910d8876a461ee2f6c8c57f55e7437d92fabe9970fe62 |
| Source 03 | 24751 | e8174b2763140f65e7f9d4e4a46818830572e9edd3722e811dfe1dae9e1d28e7 |
| Generated 04 | 27208 | ee54430bb8fd60e559d24aa394c995474f27f7a668215c2de0561c6adecb757e |
| Results 05 | 28593 | 05325cf807096d11e89f3992aae41c1d5a8aa35d76dea48e6e903790d35b571b |

No required product/generated/completed-cohort correction remains. Result
review independently checks all six original cohorts, archived/live XML,
resource/ancestry bindings and exact attribution of every ERROR/WARN without
importing/executing targets. Its conservative cumulative read is 28341787 bytes,
below 32 MiB. Reports retain worker-only failed decoder/sort/display/formatting
probes and earlier naming probes; none changes a target assertion or exit.
Input assessment's unscoped path-only status probe is disclosed, not represented
as a fully command-compliant independent runtime replay.

Root's first compiler-summary regex missed CRLF; original JSON remains distinct
as qualification-results-original-05.json. A separate CRLF-aware check finds
one 69-warning summary and 69 literal warning lines; only the array is corrected before
result review, which independently confirms that sole delta. Root filename/CSV/
PowerShell/display failures remain in EXPLORATION-FAILURES-01.md. No source test,
timing, budget or expected-log rule is relaxed. Helpers were copied/reviewed in
this new unsealed packet; no sealed helper, old cache deletion or world reuse.

## Integration, evidence and open duties

Main premerge at 2026-10-11T08:23:47.735304Z preserves original 149 raw status
rows, empty index and streamed owner hashes; owner bodies are not decoded,
edited or staged. Source branch push and Main --ff-only source move are retained
externally. Owner HEAD custody authorization remains the 2026-10-11 answer
"没有使用主检出的 HEAD，可以合入" to call_f0159b2821604ec38c63bc946bf8a9f8;
it does not approve art, ADR expansion or Gates. All source/result interests
released before moves. Final record/remote/seal observations follow separately.

The previous Liquid IO record descendant b3ad9020 hosted run38120569634,
regression job114414258216, is completed/success at the dated
2026-10-11T07:28:52.370446Z metadata observation in this packet. No raw Linux
counts/logs are rebound to the new Control source or treated as Gate acceptance.

Evidence: D:/GitHub/ARCE-Task-Evidence/v1.8.0/control-board-acquisition-20261011-20.
Native source world is retained in the isolated tree, not packaged as source.
Dedicated restart, current Python/repository-strict, raw hosted Linux, NEW human
art, V1/V2, initial steel/full graph, shared hooks and historical storage-CME
cause remain open. All G0-G9 remain open; v1.8 stays IN_PROGRESS / IMPLEMENTING.

## Final record-review association

Independent Record review 06 is retained unchanged as RECORD-REVIEW-06.md in
the external packet: 28568 bytes, SHA-256
9074aa5d062b78a44c591902b79a1468330760b5f6dd6d654d1f697e16e8a868.
It rehashes 78 selected inputs with no mismatch and finds no required correction
to the four-document snapshot; new/touched local link targets resolve.
R06-01 (Low) records the reviewer's initial unscoped status/diff-stat commands.
Those commands did not read unrelated bodies or write, but this review is not
represented as fully command-compliant and is not retroactively authorized.
All input, HEAD and read-freeze interests were released. This association is
added by Root after that release, outside the original reviewed snapshot; it
does not extend the review or grant any Required Gate or human art approval.
