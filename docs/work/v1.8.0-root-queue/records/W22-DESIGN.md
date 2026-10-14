# W22 station notice design

This implementer owns the warp notices of `StationWarpService`, a new `station/notice`
package, its language entries and generated output, its tests, the warp GameTests that
asserted English notice prose, and the W22 records. It does not change the ledger, status
files, build scripts, the network channels or other station behaviour. ADR-065 section
5.3 governs, with ADR-046 UI-02.

## Translatable notices

Every warp notice becomes `Component.translatable` with a stable key in the v1.8 language
namespace, in English and Chinese. Each names the station as its first argument. The
outcome notices carry the existing code key `station.advancedrocketrycommunity.code.<code>`
as their second argument, so tests assert code tokens instead of English prose.

| Kind | Key (`station.advancedrocketrycommunity.notice.`) | Arguments | Sent | Stored |
|---|---|---|---|---|
| Start | `warp_started` | name, `WARP_STARTED`, from, to, seconds, class, cost | chat | no |
| Countdown | `warp_countdown` | name, to, seconds | action bar | no |
| Commit | `warp_committed` | name, `WARP_COMMITTED`, from, to, class, cost, balance | chat | yes |
| Abort | `warp_aborted` | name, reason code, to | chat | yes |
| Brief outcome | `warp_outcome` | name, `WARP_COMMITTED` or the reason code | — | yes |
| Login | `while_away` | one stored notice | chat | — |

A cancel is an abort with the code `WARP_CANCELLED`. The cost class is a translatable
`station.advancedrocketrycommunity.warp_class.<class>` key. Bodies and numbers are
literal text. No notice carries a player name, a UUID or a coordinate; the actor and every
detail stay in the existing `ARCE_STATION_WARP` audit lines. A countdown that a server
stop discards has no outcome, as today. (Correction, W55 on 2026-10-15, after W22 review
F9: no argument role is a player name, but the station-name argument can contain one,
because a default station name is the creator's player name followed by " Station".)

## Who is told

Recipients are computed from the live registry each time a notice is sent:
- the live owner and members who are online, wherever they are;
- the player who acted, when online;
- players in the Space Level whose block position is inside the live station's region.

Each player gets a notice at most once. When the registry is not operational, or the
station no longer exists, only the actor is told. Players in another station's region or in
the gap are not told, and the notice never mentions another region.

## The offline member-notice SavedData

`advancedrocketrycommunity_station_notices`, stored with the Overworld data, root schema 1:
`{schema_version, stations: [{id, rev, acks: [{id, rev}], outcome?: {key, args}, owner?: {id, rev}}]}`.

- **Bounds.** At most 4,096 entries (the station limit), and at most 32 member
  acknowledgements per entry; the owner's acknowledgement has its own field, because
  members exclude the owner in this code base. An outcome envelope is at most 512 bytes of
  NBT payload; the full commit or abort envelope is admitted only within that budget, and
  otherwise the brief outcome, which always fits, is stored. The root is preflighted at
  8 MiB, depth 16 and a fixed node count. The maximum population is about 7.8 MB, so a
  valid store always encodes inside the bound.
- **Updates.** Station creation adds an empty entry (revision 0). A commit or abort
  replaces the entry's outcome in place with revision + 1; nothing grows per event. The
  owner and members told live are acknowledged at that revision.
- **Membership.** Every touch reconciles the entry with the live station: acknowledgements
  of players who are no longer owner or member are dropped, and an owner transfer moves
  acknowledgements between the two fields. Member removal, ownership transfer and
  deletion through `StationManager` reconcile or drop the entry at once. Server start
  reconciles every entry, drops entries of missing stations and adds empty entries for
  stations without one.
- **Login.** For each live station whose owner or member is the player, an unacknowledged
  outcome is shown once as `while_away`, and the acknowledgement is recorded. At most 16
  notices are shown per login; the rest wait for a later login.
- **Refusal.** A root that does not decode is kept by reference and written back
  unchanged, with one WARN per load. The store is then unavailable: nothing is recorded,
  shown or acknowledged, and live notices continue. Nothing refuses a save or throws from
  serialization. The risk entry and ADR note text go in the W22 record for Root to add.

## Tests

Unit tests cover the envelope roles and the 512-byte admission, the schema, overflow at
4,096 entries and 32 acknowledgements, the maximum-population root under 8 MiB, a restart
through the NBT file writer, the removed-member privacy and the kept root. GameTests cover
visitor delivery, hidden-region privacy, exactly-once login acknowledgement, and code
tokens. The countdown is counted in server ticks, as in the existing warp GameTests; any
wait for loading would use `GameTestTickPacer`.

## Not in this slice

Other station behaviour, network channel changes, status files and the ledger.
