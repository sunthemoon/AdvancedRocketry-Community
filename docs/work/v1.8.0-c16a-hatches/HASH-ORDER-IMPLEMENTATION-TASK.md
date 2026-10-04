# C16a-03b-01-K1 pure native-hash key ordering

Date:2026-10-04. Status:source independently reviewed,Root integration pending.
Owner:delegated source author;Root owns all main/central/status writes and
assigns a different final reviewer.

Authority:[ordering adoption01](HASH-ORDER-ACCEPTANCE-01.md) and its exact
proposal/review. Observable result:a package-private pure Java comparator
implements complete unsigned ordinary UTF-8 primary order,with original
unsigned UTF-16 units and prefix length only on a complete primary tie.
No normalization,extra rejected-string class or modified-UTF wire change.

Write boundary:two fresh files,`machine/classic/adapter/ClassicNbtKeyOrder.java`
and its new focused test,inside a fresh Temp candidate only. Current25 admitted
values/tests,all Bank APIs,frame/native codecs,GuardTicket,world/resource/Item
writers,central registrations,generated resources and every frozen menu packet
remain read-only. This helper is not public API or a structural-frame authority.
Full hash framing is a separate leaf;this comparator alone does not close it.

Use an exact named baseline and independently frozen source/test delta.
Evidence covers unsigned byte comparisons,complete-primary versus prefix
behavior,JDK UTF-8 replacement collisions and raw UTF-16 ties,nested caller
independence,deterministic finite insertion orders and preservation of every
nonzero primary sign. Preserve failed attempts and their original inputs.
Do not use Tag.equals or Java natural String order as a substitute for native
hash canonicalization;no native hash/framing/persistence result is inferred.

Bounded Python/static reads are allowed.Java17/offline/no-daemon/workers2/
heap2G compile/scoped tests require a separate Root slot grant.No fullbuild,
DataGen,GT/native/client,asset import,commits,tags,push or protected bundle access.
Report exact pre/post source pins,actual commands/XML/results and limitations.
Independent actual-diff review precedes Root application.

- [x] Exact source/test proposal and actual focused Java evidence:8/1 suites/0FES.
- [x] Independent source review and applicable differential replay:15/2/0FES,
  zero introduced C/H/M/L in this exact two-file comparator leaf.
- [x] Root exact integration/build/DataGen/GT regression;full stock19 has a
  separate preserved UI-marker failure,not whole-cohort Gate admission.

Frozen [author source](native-key-order-implementation-01.zip)
`1ac02a13...` and [independent review](reviews/native-key-order-independent-01.zip)
`0c876ca5...` preserve exact source/commands/XML. Root verifies every archive
entry and records. No native framing/hash/GuardTicket/world/API result follows.
