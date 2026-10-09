# C18b derived recipe upstream-touch history

Date: 2026-10-09. Primary source is an owned, non-shallow, bare Git clone of the
original Advanced-Rocketry/AdvancedRocketry 1.12 branch, examined only through
pinned commit c5cd5af62fc07cd4e0d24f06a16033f181c47c04. No asset tree is imported.
The first GitHub API browser attempt is inaccessible; it supplies no history.

Exact source file:
src/main/resources/assets/advancedrocketry/recipes/jackhammer.json.
Its pinned 666-byte content matches the accepted manifest SHA-256:
a2d717f1afe921977dfced20123272c8ceca26b457b2d52d37d23b9f6e249a45.

Actual `git log --follow` and name-status history identify one touching commit:
[12d238cb2a883533be5b3dabb8c5b7fb3297a99e](https://github.com/Advanced-Rocketry/AdvancedRocketry/commit/12d238cb2a883533be5b3dabb8c5b7fb3297a99e),
authored by zmaster587 on 2019-01-20T04:42:45-05:00, with the subject
Switch recipes over to the 1.12+ JSON system. This is an added JSON recipe, not
a rename. No later touching commit exists through the pin in this path history.
This one-file record is not complete provenance of earlier algorithm/gameplay
ideas or an approval of other assets. Human provenance review remains pending.

Actual primary commands all exit 0: bare filtered clone, non-shallow check,
follow-renames log, name-status log and pinned source read. UTC execution spans
02:43:05.245613 to 02:43:10.905753. Raw command receipts and source/history outputs
are retained in Root's recipe-history-01 evidence and will be included in its
compact qualification package, not referenced from a mutable Temp checksum.
History stdout SHA-256:
d200036e4862803fb85020a11ca6975cf864c3dd24007dbc68c42026373eed93.
Name-status stdout SHA-256:
3a75deea11185d08798fd00fb509d26bc9a35ecb115caeb6d0e2544e07e46c96.

The original pre-authoring/final records omitted this ADR-061 section 4.9 list.
This correction records that omission rather than claiming it was inspected
before original conversion. The current final entry adds the actual history and
recomputes its pending-review digest; generated target bytes remain unchanged.
No issue/PR-derived content is identified in the inspected touching metadata,
but technical review is not a substitute for the maintainer's authorship review.
