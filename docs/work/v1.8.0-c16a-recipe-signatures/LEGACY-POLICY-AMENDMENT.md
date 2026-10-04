# Proposed ADR-064 section 2.5 amendment

Date: 2026-10-03. Revision: 2. Status: proposed; independent review pending.
Basis: [owner decision](OWNER-DECISIONS.md).
Scope: legacy recipe provenance and its section 11 persistence/test references;
all resource/schema/ownership bounds and other decisions remain unchanged.
ADR revision 3 remains canonical until this amendment is reviewed. Proposal
revision 1 and its independent Medium finding remain frozen reviewer evidence.

Replace section 2, item 5 with:

5. **Legacy signatures.** A new bounded `arce_recipe_signature` root
   (schema 1) marks the JSON-signature format for supported new jobs. An
   unmarked existing v1.2 progress/journal uses the legacy resolved-Item format.
   A resolved-Item hash, even together with a built-in recipe ID, cannot prove
   which authored JSON produced it: custom tag payloads may resolve identically.
   Where provenance cannot be proved, preserve the original progress, journal
   and resource roots, pause in an explicit repair-required state, and do not
   automatically convert or add the new marker. Do not re-plan a retained
   pending journal with current tags, reconcile it as a different new task,
   or treat an unmarked active/pending machine as an empty new machine. No
   migration-witness framework is added in this leaf. Supported new jobs store
   their signature and marker in the controller's same snapshot. S1 must cover
   all three v1.2 machines with unproved partial progress and pending journals,
   preserved resources and roots across two restarts, as well as supported new
   jobs and tag-only reload behavior. Automatic legacy conversion remains
   unavailable without separately reviewed provenance evidence and admission.

In section 11, change only the `arce_recipe_signature` root's contents description
to `recipe ID and format json_v1, format marker; same controller snapshot as new
JSON-signature progress`. Keep its schema and 1,024-byte bound unchanged.

Replace section 11's final S1 sentence with:

The S1 fixture must prove supported current-format save/restart and
pending-journal recovery, refusal and verbatim retention of unproved legacy
progress/journals through repeated restarts, unknown/future refusal, supported
new JSON-signature jobs and resource conservation separately. It must not
claim legacy conversion from resolved-hash matching alone.

Acceptance checks: compare the exact proposal with the owner receipt and the
existing signature/resource/journal bounds; report findings from that comparison.
The implementation, native save protection and Required Gates remain separate.
