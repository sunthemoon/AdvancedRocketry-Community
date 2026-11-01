# C18d W244: etcher pair and K5 capsule texture

Tested implementation source: `a61cbd985d6815cca60d7dc40ab9e3532ee3035b`, with W244-fix `a18ef8621` (R263). Root decision R259 under owner D8.
See [W244](../v1.8.0-root-queue/records/W244.md) for actual command results,
the pre-implementation design, external evidence identities and remaining work.

Covered ledger units are `event:ClientProxy.modelBakeEvent(ModelBakeEvent)`
and `entity:ARSpaceElevatorCapsule` (client effect `elevator_capsule`).

The plan and provenance reproduce three upstream byte copies plus one NEW
material library, authored at `a6b90e9cf115a859ce9aefb4e894dfd28bebf49f`.
Both ambiguous etcher predecessors have complete clear histories and identical
bytes. The provenance retains every candidate instead of choosing one.
All hashes, derivation, history, format, references and budgets are enforced.
Human provenance review remains PENDING_HUMAN_REVIEW.

The etcher uses its existing server formation and the strict transient formed
byte alongside active. Live-facing mismatch withholds its hull under R257.
Unit tests compare all 200 positions in four facings against the independent
legacy transform with width reflection. Pattern, collision and save schemas
are unchanged. The complete sixteen-model catalog has 35,268 budget vertices.

After review finding W244-R1, Root decision R263 replaces that cage with the
legacy Capsule group of the imported K4 spaceelevator.obj, drawn with the
capsule texture through the group's own UVs. One stationary capsule stands at
each endpoint with a pending ride, on the platform centre, placed by the
transcribed RenderElevatorCapsule transform (one block up, yaw 270 for the
north-facing anchor). Unit tests pin all 416 drawn corners against that
transform, and every UV-mapped face samples only texels the cutout keeps; six
unmapped contact faces lie against opaque faces. The doorway stays open, as
legacy drew Door only in motion, so riders stay visible through it. A frame
draws at most 64 capsules, and the catalog total is unchanged. The model and
texture are checked once per resource reload; a missing or unreadable one
keeps the original line cage (W244-R4). It adds no field, entity, packet,
ride action or persisted state. Existing endpoint/rider limits, lease and
lifecycle cleanup remain. V1 must judge the capsule's look, its countdown
tint, lighting and rider visibility.

Focused native model/capsule tests are not a full/sharded regression or an
actual tracking-client visual result. Root retains the sharded branch run,
independent review and integration. Forge parse/bake and V1 pending; V2,
resource-pack reload/fallback and maintainer sign-off are also pending.
No version or Required Gate is approved; both affected ledger units remain
REDESIGNED. No NEW bitmap art is added; only the NEW MTL is authored here.
