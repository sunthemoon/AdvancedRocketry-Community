# Planetary sky profiles

Use a client resource pack to customize sky, sun, stars, fog and additional
ambient sounds. These settings never change server gravity, oxygen, damage,
travel permission or fuel. This feature belongs to the v1.4 development build;
real-GPU and multiplayer acceptance is not yet complete.

## Connect a body to a profile

1. In the server's celestial definition, set `visual_profile` to a stable
   resource ID such as `example:rust_sky`. The body must uniquely map to the
   player's physical Level. This does not create a dimension.
2. The dimension type must select `"effects": "advancedrocketrycommunity:planetary"`.
   For an airless space world retaining End-style lightmap/fallback behavior,
   use `advancedrocketrycommunity:planetary_space`. Dimension-type changes take
   effect on normal world load, not by hot-registering a dimension.
3. Add `assets/example/celestial_visuals/rust_sky.json` to the client resource
   pack, enable it, and reload resources. Reusing an existing profile ID replaces
   its appearance according to ordinary resource-pack priority.

Built-in Mars, Venus and Moon use the surface effect. Shared Space uses the
space effect and a generic space sky, not a station-specific parent-planet disc.
Earth retains its normal renderer. Gas-giant definitions have no surface sky.
The four built-in profile IDs are `advancedrocketrycommunity:moon`, `:space`,
`:mars` and `:venus` (each abbreviated ID uses the same namespace).
Both effects suppress visible clouds and rain without changing server weather.

## Profile schema 1

```json
{
  "schema_version": 1,
  "day_color": 11304048,
  "night_color": 592668,
  "fog_color": 12487796,
  "sun_color": 15460863,
  "sun_radius": 3.0,
  "sun_opacity": 0.95,
  "star_brightness": 0.85,
  "fog_start": 0.4,
  "fog_end": 192.0,
  "ambient_sound": "minecraft:ambient.basalt_deltas.additions",
  "sound_volume": 0.18
}
```

| Field | Allowed value and meaning |
|---|---|
| `schema_version` | Integer `1`; future schemas are rejected |
| Four `*_color` values | Integers 0–16777215 (`0xRRGGBB` expressed in decimal JSON) |
| `sun_radius` | 0.5–12 degrees before solar-intensity scaling; final radius is also clamped |
| `sun_opacity`, `star_brightness` | 0–1 |
| `fog_start` | 0–0.95 of the shortened far distance; never increases the original near plane |
| `fog_end` | 8–512 blocks; cannot increase the ordinary terrain far plane |
| `ambient_sound` | Optional registered sound-event ID, at most 128 characters; missing event is silent |
| `sound_volume` | 0–0.5, also controlled by the game's Ambient/Environment volume setting |

All fields except `ambient_sound` are required. Omit that field for silence;
do not supply `null`. Numbers must be finite and of the indicated JSON type.
Day/night uses the dimension's time. Synchronized solar intensity scales only
the displayed sun (zero hides it); pressure attenuates daytime stars. This is
stylized presentation, not astronomical simulation or a damage model.

Additional ambience plays only in open sky and air, at most one non-looping
sound and no more often than every 400 unpaused client ticks. Entering cover,
changing world/profile, reloading resources or disconnecting stops it. These
transitions do not reset its cooldown. Moon and Space add no ambient sound;
ordinary game sounds are not globally muted. Built-ins reference vanilla events
without distributing copies of their sound files.

## Reload and compatibility

There are at most 128 winning profile files, 16384 UTF-8 bytes per file, 16
levels of JSON nesting, and 128 characters per profile ID. Duplicate keys,
invalid UTF-8 or invalid fields reject the complete candidate map. A diagnostic
identifies the failure; the last valid map stays active. Before a first valid
reload, built-in authoring defaults provide the fallback map. Correct the pack
and reload to retry. Successful removal of a profile does not retain its old
entry.

Missing profile IDs, unavailable catalogs and ambiguous physical Level mappings
use the effect's vanilla fallback instead of retaining a previous body's sky.
Water, lava, powder snow, blindness and darkness preserve ordinary rendering
restrictions. A renderer error disables custom sky drawing until resource reload
and logs the error. Shaders and rendering-mod compatibility require separate
client testing; source/unit checks do not establish GPU correctness.

No saved body/Level identity or inventory is changed. Existing Moon/Space type
resources only replace their `effects` property; higher-priority data packs
which replace those types need to opt into the desired effects themselves.
Removing the presentation changes does not require a world-data migration.
