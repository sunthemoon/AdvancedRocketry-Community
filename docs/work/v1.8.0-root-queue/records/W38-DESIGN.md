# W38 design: the fall boundary for mounts and passengers (M1)

Scope: leaf B of C18a, the gravity-scaled fall under ADR-066 section 3.3. This note resolves
the open finding M1 from `docs/work/v1.8.0-c18a-living-gravity/TASK-01.md` before the
listener is written. Every claim below comes from the mapped Forge jar
`forge-1.20.1-47.4.10_mapped_official_1.20.1.jar` in the Gradle cache, read with
`javap -p -c`. A constant-pool scan of every `net/minecraft` class in that jar adds two
facts. Only `LivingEntity` calls `ForgeHooks.onLivingFall`. The classes that override or
call `causeFallDamage` are `Entity`, `LivingEntity`, `AbstractHorse`, `Llama`, `Creeper`,
`Player`, `Boat`, `MinecartTNT`, `FallingBlockEntity` and the blocks listed below.

## The native call chain

- `Block.fallOn(Level, BlockState, BlockPos, Entity, float)` calls
  `entity.causeFallDamage(distance, 1.0F, damageSources().fall())`. Overrides change only the
  arguments. Hay passes a 0.2 multiplier, a bed halves the distance, and pointed dripstone
  passes `distance + 2` with multiplier 2.
- `Entity.checkFallDamage(double, boolean, BlockState, BlockPos)` calls `fallOn` with the
  `fallDistance` field when the entity is on the ground and that field is above 0. It then
  calls `resetFallDistance()`. The landing tick's own movement is not added.
- `LivingEntity.causeFallDamage(float, float, DamageSource)`:
  - At offset 3 it calls `ForgeHooks.onLivingFall(this, distance, multiplier)`, which posts
    `LivingFallEvent` and returns null when the event is canceled. Null returns `false` at
    once, offsets 8 to 14.
  - It then overwrites both locals with the event's distance and multiplier (offsets 15 to
    24). It passes them through `invokespecial Entity.causeFallDamage` (offset 29) and to
    `calculateFallDamage` (offset 37).
  - The damage is `hurt(source, ceil((distance - 3 - jump) * multiplier))`.
- `Entity.causeFallDamage(float, float, DamageSource)` returns `false` when the type is in
  `fall_damage_immune` (offsets 0 to 14). Otherwise it calls
  `passenger.causeFallDamage(distance, multiplier, source)` for each direct passenger, with
  the three arguments it received (offsets 22 to 64).
- Therefore a living vehicle hands its passengers the distance **after** every listener on
  its own event, including ARCE's scaling.

## Where a rider's fall can come from

- `LivingEntity.rideTick()` calls `resetFallDistance()` after every ride tick (offset 18).
  A living rider never builds up a fall distance of its own while riding.
- `ServerPlayer.checkFallDamage` is empty. The server's only route is
  `ServerGamePacketListenerImpl.handleMovePlayer` → `ServerPlayer.doCheckFallDamage` →
  `Player.checkFallDamage`. For a passenger, `handleMovePlayer` branches at offset 247
  (`isPassenger()`) to `absMoveTo` and returns at offset 1366. It never reaches
  `doCheckFallDamage` (offsets 1175 and 1363).
- So a living passenger receives a `LivingFallEvent` only from its vehicle's
  `Entity.causeFallDamage`, or from code that calls `causeFallDamage` on it directly.

## Cases

1. **A living mount with a living rider, horse family.** `AbstractHorse.causeFallDamage`
   never calls `super` or `ForgeHooks.onLivingFall`. The same holds for the `Llama` override,
   and camels inherit `AbstractHorse`.
   - It computes `calculateFallDamage(distance, multiplier)` =
     `ceil((distance * 0.5 - 3) * multiplier)` on the raw distance (offset 20).
   - It hurts itself (offset 37). Then it calls `entity.hurt(source, damage)` for every entry
     of `getIndirectPassengers()` (offsets 48 to 91).
   - No `LivingFallEvent` exists for the horse or for any rider. A Forge listener cannot scale
     this landing.
2. **A living mount with a living rider, other living mounts** (pig, strider, spider jockey,
   ravager or `/ride`). The mount's event fires first. Its scaled distance then reaches each
   rider's event as the incoming distance. A second scaling there would multiply twice.
3. **A non-living vehicle with living passengers.**
   - `Boat.checkFallDamage` (offsets 11 to 61) acts only when the boat is not a passenger. It
     calls `causeFallDamage(fallDistance, 1.0F, fall())`.
   - A minecart uses `Entity.checkFallDamage` and `Block.fallOn`. `MinecartTNT` explodes, then
     calls `super`.
   - In both, `Entity.causeFallDamage` gives each passenger the vehicle's raw distance. No
     ARCE factor has been applied, because non-living gravity is out of scope.
4. **Nested passengers.** Propagation runs from the vehicle up through each direct passenger
   in turn. Every living link reposts the event with the distance it received.
   `Entity.startRiding` (offsets 19 to 43) refuses a vehicle whose own vehicle chain contains
   the rider, so the chain has no cycles and walking up `getVehicle()` always ends.
5. **Cancellation and immunity.**
   - *Cancellation.* A canceled event returns before `Entity.causeFallDamage`. A canceled
     mount therefore also spares its passengers. Padded boots (`PaddedBoots.onFall`, through
     `SuitMotionEvents` line 87) cancel at normal priority. `lowGravityBoots` reads the
     applied modifier, not the distance, so listener order does not matter.
   - *Creative flight.* `Player.causeFallDamage` with `abilities.mayfly` posts
     `PlayerFlyableFallEvent` instead and returns before `super`, so neither the player nor
     its passengers receive a `LivingFallEvent`.
   - *Slow falling.* `LivingEntity.aiStep` calls `resetFallDistance()` under slow falling or
     levitation (offsets 792 to 812), before `travel`. A slow-falling landing therefore
     reaches `fallOn` with distance 0 and posts no event.
     `LivingEntity.travel` adds the native `SLOW_FALLING` ADDITION modifier (-0.07) to the
     gravity attribute (offsets 45 to 98). This is one reason the fall must never use the
     complete attribute.
   - *The `fall_damage_immune` tag.* `calculateFallDamage` returns 0 for the tag, and
     `Entity.causeFallDamage` returns before the passenger loop. An immune living vehicle (a
     chicken, cat or iron golem, among others) still posts its own event, but its passengers
     receive no fall.

## The rule

The listener scales a `LivingFallEvent` only if no entity in the target's vehicle chain is a
`LivingEntity`. Otherwise the event is left untouched. When it scales, it uses the target's
own applied multiplier: the owned transient modifier, read by the one shared resolver that
`PaddedBoots.gravityMultiplier` now delegates to.

- **Once per landing.** The first living entity that a landing reaches is the lowest living
  link of its chain. That entity has no living vehicle, so the listener scales it exactly
  once. Every living passenger above it has a living vehicle, so it keeps the distance it
  received, which already carries one factor. Non-living links pass the distance unchanged.
  - A living root, such as a pig, scales once and its riders inherit.
  - A boat or minecart root gives raw distances. Each living passenger is then the lowest
    living link of its own branch and scales once with its own factor.
  - Nested chains follow by induction.
- **Player precedence.**
  - In or on a non-living vehicle, or on their own, a player uses their own factor: the field,
    then the station position, then the Level, as leaf A applied it.
  - Riding a living mount whose event fires, the player inherits the mount's scaled distance,
    which normally carries the mount's Level factor. The rider moved with the mount and
    landed at the mount's speed, so this is also the physical answer.
  - Rescaling by `rider / mount` was rejected. It needs a division that has no result when
    the mount's factor is 0, it would resolve a second, independent factor, and it would
    misread other mods' changes to the mount's distance.
- **Non-players.** For non-players in one Level, every link has the same Level factor, so
  "the lowest living link's factor" and "the entity's own factor" agree.

## What the rule does not do, and why the stop condition does not apply

- **No new mechanism.** It adds no marker, no state and no new interception. It reads only
  the native vehicle relation and leaf A's modifier. It does not touch ADR-068's hatch hooks.
- **Native damage is kept.** It never suppresses or adds passenger damage, never changes the
  damage multiplier, and never applies damage itself. Cancellation and immunity keep their
  native reach.
- **The horse family stays unscaled.** No Forge event fires, so falls by horses, donkeys,
  mules, llamas, camels and their riders keep their native, Overworld damage. They are scaled
  zero times, never twice. Scaling them needs an interception mechanism, such as a Mixin
  into `AbstractHorse.causeFallDamage`. ADR-073 allows that only with its own ADR and the
  owner's confirmation.
- **Why work continues.** The TASK's stop condition is about the once-only rule. That rule
  holds for every landing that reaches a `LivingFallEvent`. ADR-066 already treats a
  platform gap the same way: "Missing gravity attributes are skipped, not coremodded." The
  horse family is therefore an open item for Root, not a blocker. Root may overrule this
  reading.
- **Conservative fallbacks.** These cases fall back to zero scaling, which is the native
  behaviour:
  - a passenger whose fall comes from a non-living vehicle that itself rides a living
    entity (only possible with `/ride` or mods);
  - a direct `causeFallDamage` call by another mod on a mounted entity;
  - a living vehicle whose own event the listener left unchanged because the distance or
    factor was malformed.
