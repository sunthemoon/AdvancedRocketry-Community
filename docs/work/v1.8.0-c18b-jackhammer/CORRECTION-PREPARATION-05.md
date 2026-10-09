# C18b template resolution and source-history correction preparation

Date: 2026-10-09. Fixed candidate 53523bcf genuinely passes Root clean build,
explicit test and both runData/empty diffs. Its unfiltered native run exits 1
with an unexpected server-tick exception, before a final completion marker:
the new adapter authority test resolves empty in the adapter namespace, where
that template does not exist. Keep its complete raw failure. Existing adapter
tests explicitly use the host template namespace; apply that same narrow binding
to this new case, without removing it, filtering tests or changing its budget.

Independent fixed-source review also identifies a missing ADR-061 section 4.9
upstream-touch history list for the derived MIT recipe. No such committed linked
record has yet been found. Inspect primary history at the pinned upstream commit,
retain commands/raw results and provenance risks, then add the actual history
to that record with a recomputed pending-review digest. Do not invent origin
approval or delete/relabel the source attribution to pass checks. This source
history correction falls within the existing final provenance write scope.

Publish this preparation before corrective source/record edits. Independent
53523bcf qualification continues at its fixed commit and remains separate; a
new committed candidate requires actual applicable qualification/review. The
frozen external harness is a different identity and has no packaged result yet.
No gameplay, API, survival, asset approval, dimension/spawn/time or Gate policy
is changed by this preparation. Main still contains no tool source.
