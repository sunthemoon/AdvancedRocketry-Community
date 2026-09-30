# v1.5 multiplayer permission report (A1, development copy)

ADR-046 UI-03. This is the development copy; the candidate-bound copy belongs in
`docs/releases/v1.5.0/`. It is evidence for the maintainer, **not a Gate approval**.
S1 (packaged JAR on a native dedicated server) and V2 (two real clients) are not
part of this report: S1 is planned for C3 and V2 stays open for ACC-02.

## Identity

- Base commit: `cd3233e510c54ad6edce32de732f1bb03cd6e3ec`; the tested implementation files are bound by hash in
  `root-checks.json` (`tested_implementation_files`).
- Minecraft 1.20.1, Forge 47.4.10, Java 17.0.7; mod `1.20.1-1.5.0-dev`.
- `advancedrocketry-community-1.20.1-1.5.0-dev-api.jar`: 48471 bytes, SHA-256 `97d1aaad82c5ecf02342aa2f5e9d8f30689290a76c2ffd118bd04e421fa6ee64`.
- `advancedrocketry-community-1.20.1-1.5.0-dev-sources.jar`: 1298409 bytes, SHA-256 `efbef8d9996bf7f6dfa0539f9f7999fd690ceefa33b329817a791cbff5a3c4f1`.
- `advancedrocketry-community-1.20.1-1.5.0-dev.jar`: 2872162 bytes, SHA-256 `b435f36b2fda0ed1a1bdb39de09c1a544931e1ef5e018b4b54b7b0a203d606df`.
- Command: `./gradlew clean build test runData runGameTestServer`, exit 0 (full run 02):
  1042 JUnit tests with 0 failures and 0 errors; all 256 required
  GameTests passed.
- Source of every row: the `ARCE_STATION_PERMISSION_MATRIX` lines of run 02
  (`run-02/gametest-latest.log` in `root-checks.zip`).

## Method

- Table revision 1: `StationPermissionMatrix.expected(action, actor)`. The GameTest
  `everyCellOfTheStationAuthorityMatrixMatchesTheTable` runs every cell through the real
  commands, the block-break event or the flight service's VISIT predicate, and fails on any
  difference.
- Outcomes come from the replies the source receives. Silent sources and FakePlayers receive
  no replies, so for them the outcome is read from the command's `ARCE_STATION_*` audit line
  and its return value. For `accept` and `decline` they carry no audit line, so the reason of a
  refusal is not observable for them (`REJECTED`).
- `UNKNOWN_COMMAND` means Brigadier did not parse the command for that source (a permission
  requirement). `REACHED` means it parsed and ran. `NOT_PLAYER` is Brigadier's "A player is
  required to run this command here".
- Before each cell: the registry encoding and the loaded chunk-holder count of every Level.
  After it: only allowed team cells may change the registry, and they are reset and checked.
  No other cell may change any Level's holder count.
- `ADMIN_CREATE_DELETE` runs for OPERATOR and CONSOLE only; the others are checked by parse.
  It must leave every other Level unchanged and load at most the 2 x 2 platform chunks of the
  new cell (`cell_full_chunks`, full chunks inside the cell's region). Its Space holder count
  is recorded but not compared: vanilla adds a light ticket around every chunk it loads, and
  on a never-generated cell this creates holders for neighbouring chunks (676 in run 02).
- The matrix waits until the loaded chunk counts are stable for 40 ticks after the players
  join, so each cell's delta is its own.

## Actors

| Actor | Construction |
|---|---|
| OWNER | connected mock player (embedded channel), the station owner, on its platform |
| MEMBER | connected mock player, a member, on the platform |
| INVITEE | connected mock player with a pending invitation, on the platform |
| OUTSIDER | connected mock player with no relation to the station, on the platform |
| OPERATOR | connected mock player with a level-4 ops entry, on the platform |
| OPERATOR_OUTSIDE | connected mock player with a level-4 ops entry, in Space in the gap west of the cell |
| OTHER_STATION_OWNER | connected mock player owning a second station, standing in this one |
| REMOVED_MEMBER | connected mock player added as a member and then removed |
| PREVIOUS_OWNER | connected mock player who created the station and transferred it (kept as a member) |
| DEOPPED_OPERATOR | connected mock player whose level-4 ops entry was removed before the matrix |
| CONSOLE | server command source (permission level 4) |
| EXECUTE_AS_OWNER | `/execute as <owner> at <owner> run ...` from the console (console permission level) |
| SILENT_OWNER | the owner's own source with `withSuppressedOutput()` (as `/function` or a reward) |
| FAKE_PLAYER_OWNER | Forge `FakePlayer` with the owner's UUID on the platform |
| STALE_OWNER | the owner's first player object, after it logged out and the owner joined again |

## Summary grid (actual outcome; every cell equals the expected value)

| Action | OWNER | MEMBER | INVITEE | OUTSIDER | OPERATOR | OPERATOR OUTSIDE | OTHER STATION OWNER | REMOVED MEMBER | PREVIOUS OWNER | DEOPPED OPERATOR | CONSOLE | EXECUTE AS OWNER | SILENT OWNER | FAKE PLAYER OWNER | STALE OWNER |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| EXPAND_CONFIRM | NO_CONFIRMATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NO_CONFIRMATION | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| EXPAND | ISSUED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | ISSUED | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| GRAVITY | GRAVITY_UNCHANGED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | GRAVITY_UNCHANGED | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| WARP_CONFIRM | WARP_NO_CONFIRMATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | WARP_NO_CONFIRMATION | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| WARP_CANCEL | WARP_NO_COUNTDOWN | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | WARP_NO_COUNTDOWN | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| WARP_REQUEST | WARP_INSUFFICIENT_ENERGY | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | WARP_INSUFFICIENT_ENERGY | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER |
| WARP_STATUS | WARP_STATUS | WARP_STATUS | UNAUTHORIZED | UNAUTHORIZED | WARP_STATUS | NOT_IN_STATION | UNAUTHORIZED | UNAUTHORIZED | WARP_STATUS | UNAUTHORIZED | NOT_PLAYER | WARP_STATUS | WARP_STATUS | WARP_STATUS | WARP_STATUS |
| ENVIRONMENT | IDENTIFIED | IDENTIFIED | ANONYMOUS | ANONYMOUS | IDENTIFIED | NOT_IN_STATION | ANONYMOUS | ANONYMOUS | IDENTIFIED | ANONYMOUS | NOT_PLAYER | IDENTIFIED | ALLOWED | ALLOWED | IDENTIFIED |
| INVITE | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | ALLOWED | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| REMOVE | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | ALLOWED | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| REMOVE_UUID | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | ALLOWED | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | UNAUTHORIZED | NOT_PLAYER | ALLOWED | ALLOWED | ALLOWED | ALLOWED |
| ACCEPT | INVITATION_MISSING | INVITATION_MISSING | ALLOWED | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | NOT_PLAYER | INVITATION_MISSING | REJECTED | REJECTED | INVITATION_MISSING |
| DECLINE | INVITATION_MISSING | INVITATION_MISSING | ALLOWED | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | INVITATION_MISSING | NOT_PLAYER | INVITATION_MISSING | REJECTED | REJECTED | INVITATION_MISSING |
| LIST | SEES | SEES | SEES | HIDDEN | SEES | SEES | HIDDEN | HIDDEN | SEES | HIDDEN | SEES | SEES | SEES | SEES | SEES |
| ADMIN_INSPECT | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_DUMP | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_RECOVER_RESERVATIONS | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_WARP | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_ELEVATOR_CHECK | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_TRANSFER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| ADMIN_CREATE_DELETE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND | REACHED | REACHED | UNKNOWN_COMMAND | UNKNOWN_COMMAND | UNKNOWN_COMMAND |
| BUILD | ALLOWED | ALLOWED | CANCELLED | CANCELLED | ALLOWED | ALLOWED | CANCELLED | CANCELLED | ALLOWED | CANCELLED | N/A | N/A | N/A | ALLOWED | N/A |
| VISIT | ALLOWED | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | ALLOWED | ALLOWED | UNAUTHORIZED | UNAUTHORIZED | ALLOWED | UNAUTHORIZED | N/A | N/A | N/A | N/A | N/A |

## Cells

### EXPAND_CONFIRM

Precondition: no confirmation pending.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | NO_CONFIRMATION | NO_CONFIRMATION | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | NO_CONFIRMATION | NO_CONFIRMATION | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### EXPAND

Precondition: station not expanded.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ISSUED | ISSUED | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | ISSUED | ISSUED | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### GRAVITY

Precondition: the current gravity is requested.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | GRAVITY_UNCHANGED | GRAVITY_UNCHANGED | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | GRAVITY_UNCHANGED | GRAVITY_UNCHANGED | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### WARP_CONFIRM

Precondition: no warp confirmation pending.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | WARP_NO_CONFIRMATION | WARP_NO_CONFIRMATION | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | WARP_NO_CONFIRMATION | WARP_NO_CONFIRMATION | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### WARP_CANCEL

Precondition: no countdown running.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | WARP_NO_COUNTDOWN | WARP_NO_COUNTDOWN | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | WARP_NO_COUNTDOWN | WARP_NO_COUNTDOWN | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### WARP_REQUEST

Precondition: looking at a warp core; balance 0.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | WARP_INSUFFICIENT_ENERGY | WARP_INSUFFICIENT_ENERGY | PASS | no | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | WARP_INSUFFICIENT_ENERGY | WARP_INSUFFICIENT_ENERGY | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| SILENT_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| FAKE_PLAYER_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |
| STALE_OWNER | NOT_LOCAL_PLAYER | NOT_LOCAL_PLAYER | PASS | no | none |

### WARP_STATUS

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| MEMBER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | WARP_STATUS | WARP_STATUS | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| SILENT_OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| FAKE_PLAYER_OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |
| STALE_OWNER | WARP_STATUS | WARP_STATUS | PASS | no | none |

### ENVIRONMENT

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | IDENTIFIED | IDENTIFIED | PASS | no | none |
| MEMBER | IDENTIFIED | IDENTIFIED | PASS | no | none |
| INVITEE | ANONYMOUS | ANONYMOUS | PASS | no | none |
| OUTSIDER | ANONYMOUS | ANONYMOUS | PASS | no | none |
| OPERATOR | IDENTIFIED | IDENTIFIED | PASS | no | none |
| OPERATOR_OUTSIDE | NOT_IN_STATION | NOT_IN_STATION | PASS | no | none |
| OTHER_STATION_OWNER | ANONYMOUS | ANONYMOUS | PASS | no | none |
| REMOVED_MEMBER | ANONYMOUS | ANONYMOUS | PASS | no | none |
| PREVIOUS_OWNER | IDENTIFIED | IDENTIFIED | PASS | no | none |
| DEOPPED_OPERATOR | ANONYMOUS | ANONYMOUS | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | IDENTIFIED | IDENTIFIED | PASS | no | none |
| SILENT_OWNER | ALLOWED | ALLOWED | PASS | no | none |
| FAKE_PLAYER_OWNER | ALLOWED | ALLOWED | PASS | no | none |
| STALE_OWNER | IDENTIFIED | IDENTIFIED | PASS | no | none |

### INVITE

Precondition: target online and not invited; reset after an allowed cell.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OPERATOR_OUTSIDE | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| SILENT_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| FAKE_PLAYER_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| STALE_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |

### REMOVE

Precondition: target an online member; reset after an allowed cell.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OPERATOR_OUTSIDE | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| SILENT_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| FAKE_PLAYER_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| STALE_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |

### REMOVE_UUID

Precondition: target an offline member by UUID; reset after an allowed cell.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OPERATOR_OUTSIDE | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| SILENT_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| FAKE_PLAYER_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| STALE_OWNER | ALLOWED | ALLOWED | PASS | yes (reset) | none |

### ACCEPT

Precondition: one pending invitation (the invitee's); reset after an allowed cell.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| MEMBER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| INVITEE | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OUTSIDER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OPERATOR | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OPERATOR_OUTSIDE | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OTHER_STATION_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| REMOVED_MEMBER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| PREVIOUS_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| DEOPPED_OPERATOR | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| SILENT_OWNER | REJECTED | REJECTED | PASS | no | none |
| FAKE_PLAYER_OWNER | REJECTED | REJECTED | PASS | no | none |
| STALE_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |

### DECLINE

Precondition: one pending invitation (the invitee's); reset after an allowed cell.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| MEMBER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| INVITEE | ALLOWED | ALLOWED | PASS | yes (reset) | none |
| OUTSIDER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OPERATOR | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OPERATOR_OUTSIDE | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| OTHER_STATION_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| REMOVED_MEMBER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| PREVIOUS_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| DEOPPED_OPERATOR | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| CONSOLE | NOT_PLAYER | NOT_PLAYER | PASS | no | none |
| EXECUTE_AS_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |
| SILENT_OWNER | REJECTED | REJECTED | PASS | no | none |
| FAKE_PLAYER_OWNER | REJECTED | REJECTED | PASS | no | none |
| STALE_OWNER | INVITATION_MISSING | INVITATION_MISSING | PASS | no | none |

### LIST

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | SEES | SEES | PASS | no | none |
| MEMBER | SEES | SEES | PASS | no | none |
| INVITEE | SEES | SEES | PASS | no | none |
| OUTSIDER | HIDDEN | HIDDEN | PASS | no | none |
| OPERATOR | SEES | SEES | PASS | no | none |
| OPERATOR_OUTSIDE | SEES | SEES | PASS | no | none |
| OTHER_STATION_OWNER | HIDDEN | HIDDEN | PASS | no | none |
| REMOVED_MEMBER | HIDDEN | HIDDEN | PASS | no | none |
| PREVIOUS_OWNER | SEES | SEES | PASS | no | none |
| DEOPPED_OPERATOR | HIDDEN | HIDDEN | PASS | no | none |
| CONSOLE | SEES | SEES | PASS | no | none |
| EXECUTE_AS_OWNER | SEES | SEES | PASS | no | none |
| SILENT_OWNER | SEES | SEES | PASS | no | none |
| FAKE_PLAYER_OWNER | SEES | SEES | PASS | no | none |
| STALE_OWNER | SEES | SEES | PASS | no | none |

### ADMIN_INSPECT

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_DUMP

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_RECOVER_RESERVATIONS

Precondition: no stale reservation.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_WARP

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_ELEVATOR_CHECK

Precondition: none.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_TRANSFER

Precondition: transfer to the current owner (no change).

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | none |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### ADMIN_CREATE_DELETE

Precondition: create then delete a station; executed for OPERATOR and CONSOLE.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| INVITEE | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OUTSIDER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| OPERATOR | REACHED | REACHED | PASS | no | {ResourceKey[minecraft:dimension / advancedrocketrycommunity:space]=676}; cell full chunks 4 |
| OPERATOR_OUTSIDE | REACHED | REACHED | PASS | no | none |
| OTHER_STATION_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| REMOVED_MEMBER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| PREVIOUS_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| DEOPPED_OPERATOR | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| CONSOLE | REACHED | REACHED | PASS | no | none; cell full chunks 4 |
| EXECUTE_AS_OWNER | REACHED | REACHED | PASS | no | none |
| SILENT_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| FAKE_PLAYER_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |
| STALE_OWNER | UNKNOWN_COMMAND | UNKNOWN_COMMAND | PASS | no | none |

### BUILD

Precondition: block break event on the platform.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ALLOWED | ALLOWED | PASS | no | none |
| MEMBER | ALLOWED | ALLOWED | PASS | no | none |
| INVITEE | CANCELLED | CANCELLED | PASS | no | none |
| OUTSIDER | CANCELLED | CANCELLED | PASS | no | none |
| OPERATOR | ALLOWED | ALLOWED | PASS | no | none |
| OPERATOR_OUTSIDE | ALLOWED | ALLOWED | PASS | no | none |
| OTHER_STATION_OWNER | CANCELLED | CANCELLED | PASS | no | none |
| REMOVED_MEMBER | CANCELLED | CANCELLED | PASS | no | none |
| PREVIOUS_OWNER | ALLOWED | ALLOWED | PASS | no | none |
| DEOPPED_OPERATOR | CANCELLED | CANCELLED | PASS | no | none |
| CONSOLE | N/A | N/A | PASS | no | none |
| EXECUTE_AS_OWNER | N/A | N/A | PASS | no | none |
| SILENT_OWNER | N/A | N/A | PASS | no | none |
| FAKE_PLAYER_OWNER | ALLOWED | ALLOWED | PASS | no | none |
| STALE_OWNER | N/A | N/A | PASS | no | none |

### VISIT

Precondition: the flight service's VISIT predicate and inputs.

| Actor | Expected | Actual | Result | Registry changed | Chunk delta |
|---|---|---|---|---|---|
| OWNER | ALLOWED | ALLOWED | PASS | no | none |
| MEMBER | ALLOWED | ALLOWED | PASS | no | none |
| INVITEE | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OUTSIDER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| OPERATOR | ALLOWED | ALLOWED | PASS | no | none |
| OPERATOR_OUTSIDE | ALLOWED | ALLOWED | PASS | no | none |
| OTHER_STATION_OWNER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| REMOVED_MEMBER | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| PREVIOUS_OWNER | ALLOWED | ALLOWED | PASS | no | none |
| DEOPPED_OPERATOR | UNAUTHORIZED | UNAUTHORIZED | PASS | no | none |
| CONSOLE | N/A | N/A | PASS | no | none |
| EXECUTE_AS_OWNER | N/A | N/A | PASS | no | none |
| SILENT_OWNER | N/A | N/A | PASS | no | none |
| FAKE_PLAYER_OWNER | N/A | N/A | PASS | no | none |
| STALE_OWNER | N/A | N/A | PASS | no | none |

## Forged and stale identifiers

| Case | Test |
|---|---|
| Unknown station UUID | `StationElevatorGameTests` (elevator check), `ElevatorEndpointValidatorTest.anUnknownStationIsMissingEvenForAnOperator` |
| Another station's confirmation; a confirmation after a change (`STATION_CHANGED`) | `StationExpansionGameTests.localOwnerAndOperatorExpandOnlyThroughConfirmedCheckedCommit`, `StationWarpGameTests.cancelledChangedOrRepricedCountdownsNeverWarpOrCharge`, `StationExpansionConfirmationsTest.confirmationCannotTransferToAnotherPlayerStationOrAuthority` |
| Consumed confirmation (replay) | `StationExpansionGameTests` ("consumed confirmation"), `StationExpansionConfirmationsTest.confirmationIsOneShotAndReturnsTheObservedState`, `StationWarpStateTest.confirmationsAreOneShotBoundAndBounded` |
| Unknown or non-orbitable warp target; a target removed during the countdown | `StationWarpGameTests.requestsWithoutAuthorityCoreTargetEnergyOrKnownRocketStateAreRejected`, `StationWarpGameTests.aTargetRemovedDuringTheCountdownAborts` |
| Flight to a private or deleted station | `RocketNavigationGameTests` ("Private station or control permission leaked"), `PlanetaryAdmissionGameTests` (source station access denial) |
| A confirmation consumed by another source | `StationWarpGameTests.ownerWarpsInSystemAfterConfirmationAndCountdown` (`/execute` confirm refused before the confirmation is taken) |

## Contention

| Case | Test |
|---|---|
| Different stations never interfere; due countdowns commit one per tick | `StationWarpGameTests.countdownsDueTogetherCommitOnePerTick`, `StationWarpGameTests.tenChargingStationsDirtyTheRegistryOnlyOncePerFoldWindow` |
| One station: the first commit wins, the rest get `STATION_CHANGED` or `WARP_COUNTDOWN_ACTIVE` | `StationWarpGameTests.cancelledChangedOrRepricedCountdownsNeverWarpOrCharge`, `StationWarpStateTest.countdownsAreOnePerStationBoundedAnnouncedAndCommittedInOrder` |

## Caps and replay

| Case | Test |
|---|---|
| 128 warp confirmations, 64 countdowns | `StationWarpStateTest.confirmationsAreOneShotBoundAndBounded`, `StationWarpStateTest.countdownsAreOnePerStationBoundedAnnouncedAndCommittedInOrder` |
| Expansion confirmation capacity | `StationExpansionConfirmationsTest.capacityRejectsNewPlayersUntilEntriesExpireOrClear` |
| 32 members, 32 invitations; inviting the owner, a member or an invitee changes nothing | `StationTeamCapsTest` |
| Registry storage bound for every growth path | `StationRegistryStorageBoundTest.everyGrowthPathIsRefusedOnItsOwnAtTheBound` |

## Rate

- Non-operator players are bound by vanilla `detectRateSpam` (checked with `javap` in the
  contract review): +20 per chat command, decaying by 1 per tick, disconnect above 200.
  Operators are trusted.
- Gravity and warp have 100-tick per-station cooldowns; expansion confirmations are one-shot.
- Team commands flush the registry; with the vanilla limiter one non-operator causes at most
  about one flush per second. No per-station team cooldown is added.

## Totals

- Cells: 345 (23 actions x 15 actors); executed 336; not applicable 9.
- PASS: 345; FAIL: 0.
- GameTest summary line: `table=1 cells=345 executed=336 not_applicable=9 pass=345 fail=0`.

## Known gaps

- VISIT is decided with the flight service's own predicate and inputs
  (`StationAccessService.allowed(station, player UUID, hasPermissions(2), VISIT)`, as in
  `RocketFlightService`); the packet path itself is covered by the tests listed above.
- For silent sources and FakePlayers, `accept` and `decline` refusals have no observable reason.
- The `elevator` literal's own permission requirement is defence in depth: today the shared
  `admin` node already requires level 2, so removing the leaf requirement changes no cell
  (mutation G1 survived; see VERIFICATION).
- Notices do not name the station; visitors are not notified; offline members get no notice at
  login (ADR-046 UI-02).
- S1 and V2 are not covered here.

This report is not a Gate approval.
