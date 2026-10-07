# Narrow empty-hatch removal admission amendment

Date: 2026-10-07. Status: proposed, not accepted or implemented.
Task: C16a-03b-02-EMPTY, authority dependency only.
Source: `7ab1b0879527f4d8d3e88f09f9360015175b911a`.

## Scope

Specify the missing completed two-LOAD to fresh one-hatch LIFECYCLE join identified
as F2 in the [independent proposal review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-empty-hatch-transition-independent-review-20261007/REVIEW-01.md),
SHA-256 `edd9c2eddfda9f850c1b821ce4c2c33679d4aa9e50dc17b4d9d3036c6f6b708b`.
This amendment can qualify admission separately; it does not qualify a native
removal, resource/drop mutation, placement or outgoing metadata retirement.
F1's pre-serialization/save join remains open. No physical registration or save
availability change is authorized by accepting only this admission dependency.

The existing conditional authority remains: "授权无未解决 Critical/High/Medium
的审核定稿继续实现；重大语义调整仍另行确认". Independent review must precede
adoption. The original sealed proposal is neither changed nor wholly accepted.

## Completed LOAD evidence

`ClassicHatchBlockEntity` replaces its three completed-join fields with one private
immutable completed-join token. Only the existing successful second LOAD recording
path may create it. It captures the exact service, owner lifetime and storage
epoch; all existing clear/retire/load paths clear it. The temporary first-LOAD
candidate is not this token. A completed join becomes selectable only after the
second ticket has closed and `preparingLoad` is false. Publication must preserve
the existing full validation, retained-owner insertion, availability and final
current-state checks. Failure clears the token through the existing retirement
path; no constructor, getter, boolean argument or package caller creates one.

The removal-specific local predicate requires the current private token, exact
RUNNING service/Level/server thread, matching lifetime/storage epoch, available
nonremoved owner, no preparing candidate, pending/rejection or handoff, supported
non-power kind, and an immutable checkpoint whose binding and bank key are absent.
Its encoding must be the supported preflighted owned encoding. It performs no
world lookup, provider callback or LOAD decoding. This predicate does not itself
confer authority; installation and retained provenance still need the new ticket
and the observer selection below.

## Fresh exclusive ticket and retained selection

1. Add a private ticket acquisition profile for exactly one empty non-power hatch.
   Its package entry is `acquireEmptyHatchRemoval(service, hatch)` and its purpose
   remains LIFECYCLE. Ordinary `acquire` continues to reject null-controller
   non-LOAD requests. No caller profile/success boolean broadens that entry.
2. Apply the existing RUNNING service/thread, actual installed owner/type/kind,
   nonloading FULL chunk, unique owner, not-busy, service/recipe/catalog epoch and
   lifetime checks. Additionally apply the local completed/empty predicate before
   acquisition and after each callback-dependent check. Capture, without refresh,
   the exact checkpoint, encoding, storage epoch and completed-join token in this
   new profile's private witness. It uses the existing ordered exclusive guard;
   no LOAD guard remains held, and no second nested guard is acquired. This
   acquisition-only state is private to the admission call and not a usable
   operation ticket. Ordinary `witnessesStillValid`, `requireValid` and guard
   access reject this profile until its observer selection has been attached.
3. With this fresh ticket held, select the existing observer's exact current
   Capture and retained entry. This is a separate package-private operation for
   the new profile; `retainedOwnerMatches` continues to accept LOAD only. Before
   entering the observer lock, run the internal acquisition verifier for captured
   service/owner/chunk identities. It does not call joined-only getters, publish
   authority or return a usable scope; it exists only for selection construction
   in this same admission call. Under the lock, use callback-free acquisition-local
   checks: legal nonrefused
   capture, exact expected row/type/kind, exact retained owner reference and the
   ticket's captured local witness. No NBT getter, provider call or world walk runs
   while that lock is held.
4. Selection creates an immutable, privately constructed observer witness bound
   to this exact ticket/guard, service, chunk, owner, Capture and retained-entry
   identities. It is not a native outcome receipt. The coordinator attaches it
   once to that ticket; missing, foreign or repeated selection closes admission.
   After callbacks, full validation rechecks installation/chunk and all captured
   service/owner/encoding/join identities, then selected Capture/retained identity,
   then a callback-free local current-state tail and nonloading map/chunk tail.
   No expected value is refreshed to make a changed object pass.
5. Attach-once changes the private acquisition phase to joined validation. Only
   then may ordinary ticket validation or guard access succeed. An unattached
   candidate cannot escape through another coordinator entry or be accepted by
   a native/resource operation. `enterEmptyHatchRemoval(hatch)` returns only the
   fully joined fresh ticket after a successful joined validation.
   Every failure or exception releases the acquired owner guard. Every later
   validation repeats this profile's completed/empty and selected-observer checks;
   it cannot fall back to ordinary controller LIFECYCLE validation. Closing,
   retirement, reentrant mutation, storage reload, a newer capture/entry or service
   epoch change invalidates the scope. Engine removal never lets it authorize
   post-removal work.

The witness stores only exact private references and bounded scalar identities,
not live tags, a copied checkpoint or persistent operation history. The target is
one owner in one already FULL chunk; no quota, schema, raw purpose or public API
is added. Existing controller LIFECYCLE, LOAD, CAPABILITY, COMPLETION and RECOVERY
admission and observer behavior remain unchanged.

## Source ownership and verification

Root will assign one independent worktree and explicit write scope after review:
`ClassicHatchBlockEntity`, `GuardTicket`, `ClassicAccessCoordinator`,
`ClassicChunkObservation`, the necessary package bridge in `ClassicSaveProtection`,
focused tests and that worker's task record. Central registration, canonical
status/ledger, all other source, AGENTS.md and unrelated work remain excluded.
Root alone integrates, stages, commits and normally pushes reviewed changes.

Review the literal implementation diff and exercise current versus foreign/stale
completed token, preparing/held LOAD, pending/rejected/bound/power states, occupied
guards, missing/wrong retained entry, Capture ABA, callback retirement and unchanged
other acquisition profiles. Pure/local checks qualify only their actual subjects.
Genuine installed native admission remains a separate obligation; a boolean model,
synthetic ticket or unregistered compilation cannot prove it. Build and native
verification use an eligible measured host; C: is below the local 10 GB threshold.

This dependency is not full C16a-03b-02-EMPTY completion. Native before-tool/unbind
and terminal classification, F1, generated/disk-Proto observation, final save/reload,
physical blocks/facades, resource conservation and Required Gates remain open.
