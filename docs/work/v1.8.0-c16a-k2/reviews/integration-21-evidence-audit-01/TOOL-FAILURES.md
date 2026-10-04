# Preserved reviewer tooling observations

These are reviewer intake/parser failures, not Root source defects or failed
Minecraft executions. No production, budget, assertion, or archived evidence
was modified.

1. Initial inline mandatory-document read used the nonexistent
   `docs/versions/v1.8.0.md` and exited 1 with
   `FileNotFoundError: [Errno 2] No such file or directory:
   'docs\\versions\\v1.8.0.md'`. The initial PowerShell console also displayed
   UTF-8 Chinese through a mismatched output encoding. The tool conversation
   retains that original output. `governance02.py` names the actual
   `docs/versions/V1.8.0-CLASSIC-CONTENT-COMPLETION.md` and writes explicit
   UTF-8 output; [governance-02.log](governance-02.log) and
   [governance-02.exit](governance-02.exit) retain the corrected exit 0.
2. `measure01.py` exited 0, but its first generated-resource domain selected
   29 historical `src/generated/resources` entries, not the 771 v1.8 entries.
   Two historical Minecraft block tags are subsequently overlaid in the
   packaged JARs. The original [ARTIFACT-MEASUREMENT-01.json](ARTIFACT-MEASUREMENT-01.json)
   deliberately retains those two different-byte observations. The actual
   v1.8 domain is independently checked in
   [GENERATED-FRAMING-02.json](GENERATED-FRAMING-02.json); all 771 agree in both
   non-API JARs. This is not a normalization or ignored-byte waiver.
3. [audit-02.log](audit-02.log) / [audit-02.exit](audit-02.exit) retain exit 1:
   the reviewer header selector only recognized `[HH:mm:ss]`, whereas the
   native log uses `[04Oct2026 HH:mm:ss.SSS]`. No ERROR count was accepted from
   that failed parser. `audit03.py` changed only this selector (and verbose
   batch printing), then reached a separate named-manifest lookup failure.
4. [audit-03.log](audit-03.log) / [audit-03.exit](audit-03.exit) retain exit 1:
   the author archive's root manifest is `MANIFEST.json`, not one of the
   first helper's two guessed manifest filenames. `audit04.py` explicitly
   adds that actual filename; all selected archive SHA, CRC, complete
   manifest coverage and exact postimages then verify, exit 0.
5. [gt-context-04.log](gt-context-04.log) / [gt-context-04.exit](gt-context-04.exit)
   retain exit 1 after correct 128/464 batch and 61 ERROR measurements:
   the reviewer guessed nonexistent `RecipeSignatureGameTests.java` and
   `MachineRecipeRepository.java` source names. `gt_context05.py` uses the
   actual manifest-named `RecipeSignatureMigrationGameTests.java` and
   `DeferredProcessDefinition.java`, with unchanged context classification
   and count checks; exit 0. The original helper remains present.

Root's separate frozen non-passing ledger closure, dirty diff, initial AGENTS
collection failure, relocation failure, author 19-test/1-failure result,
and author/reviewer packaging attempts remain in their original named
packets or selected compact receipts. They are not relabelled as passing here.
