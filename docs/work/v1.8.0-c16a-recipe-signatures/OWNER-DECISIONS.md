# C16a-01 legacy recipe provenance decision

Date: 2026-10-03. The owner selects preservation without automatic migration
when the old save cannot prove the authored recipe payload. A built-in recipe
ID plus a matching legacy resolved-Item hash is not accepted as proof: a custom
tag ingredient can resolve to the same items while having different JSON.

Unproved existing progress and pending journals retain their original roots
and resources, pause in an explicit repair-required state and receive no new
JSON-signature marker. They are not treated as new empty machines. Supported
new jobs may use the bounded JSON-signature format. This decision does not
authorize resource discard, a new migration-witness framework, a schema change,
unreviewed runtime admission or any version Gate.

The independently reviewed ADR-064 section 2.5 amendment and the actual source
revision must be recorded separately. Earlier candidate packets and the M2
finding remain historical evidence; the owner decision alone does not close
the source finding or establish native persistence safety.
