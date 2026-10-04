# Non-mutating native preflight verification01

Date:2026-10-04. Status:SOURCE_AND_REGRESSION_VERIFIED; native durable refusal
and whole-version admission remain open.

The [original independent review](../v1.8.0-c16a-recipe-signatures/reviews/native-retention-independent-01.zip)
reproduces one Medium:valid externally encoded empty typed lists are changed by
the original bounds writer before raw Signature capture; all three controllers'
outgoing snapshots are then admitted. Actual35 tests/5 suites include one failing
reviewer regression. These are native-JVM fixtures,not a historical world incident
or observed Item/Fluid/FE loss. Original bytes,probe,XML and failure remain frozen.

Root replaces the counting writer with bounded iterative native byte accounting.
It does not invoke native write,copy,equality or payload decode. Existing byte,
depth and node ceilings and method signature remain unchanged. It refuses shapes
the standard native writer cannot emit unchanged:typed empty lists,overlong
modified-UTF,illegal End payloads,noncanonical NaN encodings and foreign Tag
classes. Refusal leaves the original tags intact. Existing Signature/Pump/Tank
save consumers use their unchanged sticky whole-chunk bridge;no exception is
added inside saveAdditional and no legacy ID/hash migration is introduced.

[Independent static/caller review](reviews/bounded-nbt-static-review-01.zip)
and [independent correction replay](reviews/native-retention-correction-independent-02.zip)
have0 new C/H/M/L within the exact two-file correction scope. The latter repeats
the original probe unchanged:46 tests/7 suites/0FES,including1,024 fixed-seed
native framing cases and four unsafe roots across three actual controllers,
with repeated save-admission refusal and retained subtype10 references.
Direct forced NbtIo.write after refusal still normalizes those inputs;it is not
an admitted save. Native disk/restart refusal is therefore not claimed here.

Root's scoped run passes13 tests/2 suites;complete candidate14 regression passes
1,659 JUnit/316 suites,DataGen771/written0 and all460 required GameTests. The
GameTest log retains45 ERROR lines;no clean-log or blanket error waiver.
Source cohort2,949 SHA256
`df4ee13e6ecbaf3f7206ad5b8e7c285b89ba86f73a6fe052c722320adf6456c5`;
main `8e3cf31e0057252b4c794e1a989b959c0715335fa51050e59545c34f0a255f2a`.
Exact source,XML,commands,packages,GT and static results are in
[Root candidate14](../v1.8.0-c16a-integration/ROOT-INTEGRATION-14.zip),SHA256
`de2a59a294a5acd93add5eea170130252191a9b5c22cdc43bb259f9fdf74b652`.

Older satellite/Fuel raw passthrough without the shared sticky consumer,and
EndgameNbt's prior native sizing,remain separate inherited limitations. There
is no universal raw-emission/callback-free guarantee:foreign non-native Tag
getters are outside native-readable input admission. Hatch first-load/save/
shutdown bridges,real S1 recovery,menus,clients,performance and all Gates remain
open. The unrelated Signature fixture's native oracle failure is retained in
its separate packet;this regression record does not erase or pass it.
