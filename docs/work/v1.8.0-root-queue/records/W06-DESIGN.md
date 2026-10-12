# W06 design note: jetpack intent channel and motion upgrades

Date: 2026-10-12. Specification: ADR-066 sections 2 and 5.2. This note covers the new network
surface, as AGENTS.md section 13 requires before implementation. No world, player-file or chunk
save format changes; every new state below is transient.

**Channel.** A new required channel, `advancedrocketrycommunity:suit_motion`, protocol `1`,
handled on the main thread. It has two messages:

- `JetpackIntentPacket` (C2S): an exact 2-byte frame, `[action][value]`. The action is
  `TOGGLE` (0), `CYCLE_MODE` (1) or `THRUST` (2). The value must be 0, except that `THRUST`
  carries the held boolean (0 or 1). Any other length, action or value, and any trailing byte,
  fails decoding. Decoding never mutates state, and no velocity, height, position or NBT is
  ever read from the client.
- `JetpackImpulsePacket` (S2C): three bounded floats, the velocity increment the server has
  just paid for. The client rejects non-finite values and values outside the server bounds.

Vanilla `SetEntityMotion` replaces the whole client velocity, and the server has no horizontal
input for a player, so a server-set velocity would erase horizontal momentum on every tick. The
server therefore computes and pays for the increment, and the client adds it with the same
shared, capped arithmetic.

**Validation.** Before any change, the server requires:

- a connected, alive, non-spectator `ServerPlayer` that is not a `FakePlayer` and is the
  player list's current object;
- that player's own Level;
- `equipment.classicEnabled`, for anything except switching the jetpack off.

Enabling also needs a correctly worn built-in chestplate with a valid equipment root that
contains `jetpack`. `CYCLE_MODE` into HOVER needs `hover_upgrade` in a correctly worn helmet.
Each correction rechecks all of this. It is also refused during elytra flight, while riding
any vehicle (which includes rocket seats), and during a pending elevator ride.

**Rate limit.** Per player, a sliding window accepts at most 10 jetpack packets in any 20 ticks.
Excess packets are dropped without any change. Window entries exist only for online players and
are removed at logout and server stop. The client sends `THRUST true` every 3 ticks while jump is
held with a suit chestplate worn, `THRUST false` once on release, and toggles only on key press.

**Intent expiry.** A heartbeat at tick `h` keeps thrust active on ticks `h` to `h + 4`. From
tick `h + 5` the intent has expired. `THRUST false`, logout, Level change, respawn, disabling
and server stop end it at once. Each player gets at most one correction per tick. The
correction runs from the server tick over the bounded set of online sessions.

**Hydrogen debit point.** Each correction does the following, in order:

1. Check everything above.
2. Plan the increment. It is `0.08 * jetPackForce` vertically (the force is 0.05 to 4, default
   1.3). The vertical target is 0.6 blocks/tick in NORMAL mode. In HOVER mode it is
   proportional to the distance below the transient reference height. For the plan, the
   server estimates motion from the player's last accepted displacement (vanilla validates
   these positions). The thrust never pushes down. Gravity above the increment still wins.
3. If the plan is zero, nothing is paid and nothing changes.
4. Otherwise, debit 1 unit from the worn chest's single HYDROGEN tank. This is the same
   detached-copy commit as the W03 oxygen debit. The identity of the worn stack is checked
   before publishing.
5. Only then apply the increment: to the server-side motion, then send it to that player.
   An upward correction also resets the server fall distance, as vanilla slow falling does.

An empty tank or a failed commit means no thrust. With `flight_speed_upgrade` at LEGS, a paid
correction adds 20% of the increment along the accepted horizontal motion, capped at 0.8
blocks/tick. No creative-flight flag or player capability is ever touched.

**Removal of each effect.**

- **Jetpack and HOVER.** The session is in memory only and is off at login. The HOVER reference
  resets on a mode change, a Level change, an expired or released intent, logout and a change
  in the worn-module summary. If `jetpack`, or HOVER's `hover_upgrade`, disappears, the jetpack
  is switched off.
- **`bionic_leg_upgrade`.** A transient `MOVEMENT_SPEED` modifier (fixed UUID, `MULTIPLY_TOTAL`
  0.2) exists only while the player sprints with the module in correctly worn leggings and
  equipment enabled. It is re-evaluated every server tick and on a LEGS equipment change, and
  removed at once otherwise and at logout. Only this UUID is ever added or removed, so other
  mods' modifiers are kept.
- **`padded_landing_boots`.** A server `LivingFallEvent` listener cancels only the fall for a
  `ServerPlayer` with the module in correctly worn boots. With `lowGravityBoots=true` (default
  false), it cancels only while the applied ARCE gravity multiplier is below 1. That
  multiplier is read from `CelestialGravityController`'s transient modifier. Other damage and
  client fall distance are untouched.
- **Visors.** These are physical-client only. Their state is recomputed each client tick from
  the local player's worn helmet with the bounded codec, and cleared at logout, Level change
  and resource reload. Anti-fog skips ARCE's atmospheric fog-distance override outside the
  Space Level. `effects.atmosphericNausea=false` disables that override for everyone. ARCE has
  no nausea visual yet. Earthbright brightens the lightmap through Forge's
  `adjustLightmapColors` in the Space Level only, with no potion effect.

**Known limit.** Vanilla kicks a floating player after 80 ticks unless `allow-flight=true`.
This applies on dedicated servers; the integrated server allows flight. The jetpack does not
bypass that check.
