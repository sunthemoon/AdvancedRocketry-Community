# C16a-03a model-only verification

Status: verified pure model/codec; physical hatches/controllers remain pending.
No ledger row, native writer or version Gate is closed by this document.

Independent revision-02 replay: 39 JUnit /6 suites, no failures/errors/skips.
The original implementation passes its original35, then fails four of the39
regressions through positive int-to-byte count narrowing. The same84 observations
are run on both sources; the correction refuses over-limit live counts before
serialization and preserves valid quantities. Raw originals remain archived.

- [Author r2](reviews/bank-author-r2.zip), SHA256
  `11c3a2d26fc130a979c12c25ca5c01e8f742089e4ef07d0453a8b1dffc091d98`.
- [Original author closure](reviews/bank-author-r1-original-closure.zip), SHA256
  `aa9556cc9e6dbad46c36e5996c552df3e16bdcc60de33957e059743e2a2f9b86`.
- [Independent quantity replay](reviews/bank-independent-r2.zip), SHA256
  `6bc6d9ec7ad47ddc35b242540d3e54df3dc9e8afc8542eed6a471cd124b38539`.
- [Original API review](reviews/bank-api-original-review.zip), SHA256
  `8c5393d9c35bc926c1b16b4ffcd1152d249895a5ee08f7829bd77a55a7f7766c`;
  no Critical/High/Medium, one Low about callback wording.
- [Final qualification review](reviews/bank-api-final-qualification.zip), SHA256
  `82c030af906c95a0f2e0d8b138bf28c399d8e2f10f0fd5905a724c38116d2883`;
  no unresolved findings. Original Low is preserved, qualified rather than erased.

Root admits only [API-ACCEPTANCE](API-ACCEPTANCE.md)'s exact model/codec scope
under the owner's conditional authorization. All14 Java files match frozen
identities. Native callbacks are acknowledged; simulation is not a sandbox or
end-to-end callback-purity guarantee. Later adapters need separately reviewed
loaded-owner/busy/transaction/lifecycle and reentrancy enforcement.

[Root candidate evidence](../v1.8.0-c16a-integration/VERIFICATION.md) records
1,622 passing JUnit and unchanged generated output, but12 failed fullGT cases.
That integrated failure does not become a release pass or native hatch proof.

All five copied archives have full CRC, safe/unique entry names and complete
manifest hash/size checks. Root's raw receipt is in ROOT-INTEGRATION-07.zip.
