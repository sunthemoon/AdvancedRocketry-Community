# C16a-03 draft review 01 dispositions

Date: 2026-10-03. Author: root integrator. Status: corrected draft; independent
re-review pending. The runtime interfaces/codecs remain unimplemented.

M1 identified contradictory rotation semantics: a key independent of rotation
and same-key reuse were paired with a blanket ban on rotated-hatch reuse.
The correction keeps the declared absolute-position/exact-kind identity.
Rotation alone does not create a new bank. After a legal new formation, the
same absolute position and exact kind may reattach to that bank under a new
exact instance/generation binding; a moved or different-kind hatch cannot.
All retained-bank limits, role isolation, transaction locks and source
protection remain unchanged. This clarifies the existing draft; it does not
admit a movable controller or transfer fluid to a different physical position.

The independent original report SHA-256 is
`48e916e0ebbe26d5c05c46c115951eff43364fa4c3d94c6b710a2599e43e22f7`.
The separate Fluid-retention amendment had no standalone blocking finding;
its acceptance must not imply acceptance of runtime Java interfaces.
