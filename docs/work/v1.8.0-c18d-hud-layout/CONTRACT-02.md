# C18d-HUD-ENV-O2-01: environment and oxygen layout contract

Date: 2026-10-07. Technical leaf adopted by Root under the owner's existing
authorization to implement reviewed contracts without unresolved C/H/M;
major semantics still require separate confirmation. Source is not implemented.
Code basis: `2a59cfac2e5c6713a5e6039149d0b4f2830ff8d7`; the relevant HUD/config
postimages are unchanged from the reviewed `f9117b599e10b1f25789741248a6b73db60a0785`.
This leaf implements only eight of ADR-066 section 7.2's presentation settings.
Authorization is the existing owner response in this task conversation,
also recorded in [the airlock task](../v1.8.0-c18a-airlock/TASK-01.md):
"授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".
This technical adoption does not change that conditional scope or approve runtime.

## Configuration and retained display truth

| CLIENT key | Admitted values | Default |
| --- | --- | --- |
| hud.environment.x | integral, -4096..4096 | -6 |
| hud.environment.y | integral, -4096..4096 | 6 |
| hud.environment.anchorX | START / CENTER / END | END |
| hud.environment.anchorY | START / CENTER / END | START |
| hud.oxygen.x | integral, -4096..4096 | -6 |
| hud.oxygen.y | integral, -4096..4096 | 30 |
| hud.oxygen.anchorX | START / CENTER / END | END |
| hud.oxygen.anchorY | START / CENTER / END | START |

Offsets are scaled GUI pixels. Use existing CLIENT ClientConfig.SPEC and native
enum correction. Each offset is ConfigValue<Number> defined with Builder.define
and a predicate accepting only Byte/Short/Integer/Long whose longValue is within
the inclusive range. Integer defaults and admitted intValue conversions are
exact. Reject Float/Double even when integral-valued, other Number classes,
boolean/string/null and out-of-range values. Native specification correction
restores invalid/missing paths to their individual defaults; no custom loader,
silent truncation, new listener or per-frame exception suppression.
Unloaded accessors return defaults. Loaded environmentHudSettings() and
oxygenHudSettings() return immutable settings. Same-instance updates mean the
normal correction/load/reload route, including afterReload cache invalidation;
arbitrary direct ConfigValue.set or raw-map edits are not promised validation.
Retain all three existing effects/sky settings and their behavior.

The registered life_support overlay retains its player/F1/snapshot fences and
one captured PlayerLifeSupportSnapshot. Both panels retain full translated
components, all status tokens, protected/PENDING accent precedence, exact oxygen
units, 2000-unit working capacity and existing suit count. No new visibility
predicate, hydrogen/suit panel, authoritative value, cache or server query.

## Pure geometry interface

One new stdlib-only public final client/LifeSupportHudLayout.java, with no
Minecraft/Forge/config/snapshot/rendering dependency or mutable global state:

```java
enum Anchor { START, CENTER, END }
record PanelSettings(int x, int y, Anchor anchorX, Anchor anchorY) {}
record Size(int width, int height) {}
record Rect(long x, long y, int width, int height) {}
enum Mode { REQUESTED, DISPLACED, COMPACT, UNFIT }
record Layout(Rect environment, Rect oxygen, Mode mode) {}
static Layout place(int screenWidth, int screenHeight,
    PanelSettings environment, PanelSettings oxygen,
    Size environmentNormal, Size oxygenNormal,
    Size environmentCompact, Size oxygenCompact);
```

Nested types and the entry are accessible to the existing HUD/config adapter.
Validate viewport dimensions nonnegative, all sizes positive, all inputs and
anchors nonnull and offsets within the range. Rect validates its positive
dimensions, not viewport containment; Layout validates nonnull components/mode.
Invalid internal callers are rejected, not converted to fabricated display truth.
Every coordinate, midpoint, edge, clamp and containment/separation calculation
uses long; never narrow logical origins or endpoints to int.

For viewport extent V, size S, offset d: START=d; CENTER=floor((V-S)/2)+d;
END=V-S+d. For a fitting extent clamp to [4,V-4-S]. Independently clamp both
normal panels only if each fits the margin; do not use inverted clamp intervals.
Requested rectangles E/O are retained if separated by at least four pixels on
either axis. Otherwise retain E and try exactly these O candidates in order:

| Candidate | x | y |
| --- | --- | --- |
| below | O.x | E.y + E.height + 4 |
| above | O.x | E.y - 4 - O.height |
| left | E.x - 4 - O.width | O.y |
| right | E.x + E.width + 4 | O.y |

Each keeps O's dimensions and requested perpendicular coordinate. Do not clamp
candidates again; reject out-of-margin or insufficiently separated candidates.
If either normal extent cannot fit, skip normal placement/displacement.
If no normal arrangement fits, try compact vertical then horizontal packing:
E=(4,4), O=(4,8+E.height) or O=(8+E.width,4), with both inside the margin.
At most one requested, four displaced and two compact arrangements; no pixel
search, other-overlay scan or world work. If none fits, UNFIT returns the full
compact vertical logical stack. Int-max E.height yields O.y=2147483655 without
wrap/saturation/invalid-input substitution. UNFIT promises neither containment
nor physical readability; actual viewport clipping remains possible.

## Existing renderer binding

Read both settings once per visible render, measure the captured components
using the current Font/scaled dimensions, and call place exactly once. Normal
widths=max(136,full text width+14); environment height=line height+10, oxygen
height=line height+19. Retain colors and the six-pixel bar, overflow-safe fill
against the same 2000 capacity. Compact has no 136 minimum, two-pixel padding,
two-pixel bar/text gap and full Font-wrapped components at max(1,width-12).
Measure actual wrapped line extents; selected mode and actual drawing must use
the same normal/compact text, padding, bar and line geometry.

Each panel uses a scoped GuiGraphics pose: push, translate logical x/y as
doubles, draw at local int coordinates, and pop in finally. Measurements and
local positions use checked/widened arithmetic before native int drawing.
No int cast of UNFIT origins, intentional text scissor, ellipsis, omitted digits,
artificial overlap or text shrink. Recompute on every visible frame for config,
GUI scale/resizing, locale/font reload; no second overlay or layout cache.
Defaults favor top-right, not an avoidance guarantee for arbitrary overlays or
expanded scoreboards. Huge resource-pack metrics/GPU precision remain limits.

## Verification and preserved boundaries

Pure JUnit covers all nine anchor pairs on both axes; signed offsets and both
inclusive +/-4096 endpoints; odd CENTER floor rounding; invalid/null inputs;
long/widened coordinates and edges, including int-max compact height/UNFIT;
exact edge/gap contacts; independently fitting requested panels; all four
candidate coordinates/order/no second clamp; compact vertical-before-horizontal
preference; impossible viewports, deterministic output and bounded candidate work.

Config JUnit checks exact two hud categories/eight paths/defaults/enums/types/
ranges, unloaded/loaded accessors, independent panels and axes, and same-instance
native correction/load/reload. Execute actual correction of missing, wrong-type,
out-of-range and invalid-enum values, recording the resulting individual defaults;
test Byte/Short/Integer/Long admission and Float/Double/other Number rejection.
Expand existing category assertions additively while retaining every old effects/
sky setting/default/toggle assertion. No arbitrary direct setter validation claim.

Combined source/adapter review checks actual call on the registered overlay, one
snapshot read, unchanged visibility/value/status IDs and 2000 API, complete text,
no new cache/server paths, selected normal/compact measurements matching actual
text/bar/padding/line drawing, logical long/local-int boundary and exception-safe
pose cleanup. Pure geometry passes alone do not establish Font/native binding.
Committed-source build/unit, twice runData with tracked AND untracked cleanliness
checks, and full GameTests remain required. Real-GPU V1 covers en_us/zh_cn, every status including PENDING, oxygen
0/1/1999/2000 and suit 0..4; normal/small/impossible viewports; GUI scales and
resizing; all anchors/extreme offsets/collisions; live config and locale/font
reload; F1/no-player/no-snapshot behavior; expanded scoreboard/bossbar/vanilla HUD
readability. Use honest captured server snapshots, not an authoritative-value
substitute. V2 uses two actual clients with different layouts, correct individual
server snapshots, reconnect/reset and no server/gameplay change. V0 alone cannot
close V1/V2. None of these actual runtime/visual checks has executed for this leaf.

No source/resource/schema/network/save/registry/asset/provenance additions beyond
this eight-key CLIENT leaf. No parent C18d closure, content delivery or Gate.

## Review association

Effective reviewed input: original CONTRACT-01 SHA-256
`458363cdd714afeda9839964766db3362e18a777ee050968f80c2aecc8ea439a` plus
Root ADDENDUM-02 SHA-256 `971d530ebddae3f2d2ae6c644c4a3fa5a44ead7ed1f2902905206bf2bf837e3d`.
The original sealed review retains three Mediums; the distinct successor
[REVIEW-02](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-hud-layout-successor-independent-review-20261007-r1-9a7c42/REVIEW-02.md)
SHA-256 `46209af04c1d577b88e4a39af3537b106b88495fe87efb1f5a88acae6ef7abe7`
reports no open C/H/M contract finding, with 18 static controls and named limits.
Root fully reads/hashes that review (`1624f9`, exit 0). These are contract/model
facts, not executed Forge/TOML/Java/Font/pose/client evidence. This repository
projection itself requires independent factual review before publication.
