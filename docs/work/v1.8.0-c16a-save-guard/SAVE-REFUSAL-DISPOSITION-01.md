# Open save-refusal disclosure findings

Date: 2026-10-04. Status: factual correction reviewed; ADR proposal/risk remain open.
No save behavior, owner policy, root budget or Gate is changed by this record.

The [independent audit](reviews/save-refusal-impact-01/REVIEW-01.md) reports two
Medium findings against code `3f3d62aed3980186fe0acc9592cf93ca436405fb` and its
separately dated live documents. Report SHA-256 is
`3d04a317ce519df1314fe642681af0fa50127c081e9ecddfcbfd053afb60bb87`;
internal manifest SHA-256 is
`373dc11496fb4e326f8eef66548ef5bafcf2eebce34356888d76ce696ae809a9`.
Root verifies and preserves its 36 manifested files plus manifest. The 12
Python controls are static/source/historical-archive checks, not a new native
run. No risk is downgraded or accepted by that scoped severity count.

- Original M1 concerns the six guarded BEs/four consumers, non-byte/native-fidelity
  triggers, sticky lifetime and 257th-distinct-chunk Level saturation. These
  missing facts are now disclosed in the corrected R-021 and reviewed proposal;
  the accepted ADR-064 itself remains unchanged.
- Original M2 concerns historical EventBus/ChunkMap ERROR pairs and the absence
  of a guard log/retry quota. Corrected disclosure also states that dirty
  eligibility cannot prevent unload, acknowledge durability or coordinate
  player/entity/POI/SavedData/neighbor storage. No runtime or repair proof follows.

The [separate factual correction disposition](DISCLOSURE-REVIEW-DISPOSITION-01.md)
records the exact independently checked row/proposal, original failed header-pin
check and final 10 passing consistency controls. R-021 is not accepted or closed;
the explanatory amendment remains PROPOSED, not accepted ADR text. Original
Medium findings and historical evidence are preserved rather than overwritten.

Correcting the documents must not silently authorize a new quarantine, online
reset, resource discard, saturation policy or server-stop behavior. ADR impact,
duration, logging and backup-based recovery need an explicit reviewed amendment;
native mixed-chunk/saturation/unload/cross-store/logging/repair controls remain
unrun at the current artifact. On 2026-10-04 the owner replies in this conversation:
"授权仅同步 R-021 和 ADR 说明（推荐）". This allocates only correction of R-021
and a proposed ADR disclosure amendment, without changing save behavior,
accepting risk or editing other risk rows. The separate factual correction and
review are complete within those limits. The earlier K3 documentation phase
did not stage the risk register; this allocated correction is separately published.
User-maintained AGENTS.md is never staged.

The private K2/K3 calculations do not close these findings or admit a save
writer/GuardTicket. New guarded runtime admission and full v1.8 acceptance remain
blocked by their relevant unresolved requirements. Work can continue only in
independent current-version scope that does not infer that admission.
