# Primary-report companion locators

Date: 2026-10-04. This is an additive navigation correction, outside the
immutable 15-packet source/native index. No original report, manifest, checksum
or ZIP is modified. It supplies no new native result, policy or Gate.

Two primary reports preserve relative references to their earlier loose
evidence directories. Those companion files are not embedded in the referencing
ZIP, but are already preserved exactly in separately indexed packets:

| Source report and original relative reference | Existing companion packet/member | Companion SHA-256 |
|---|---|---|
| [PRIMARY-02](reviews/LIFECYCLE-PRIMARY-02.zip), `REVIEW-NATIVE02.md:42`, `../aglf-94222005ae/FEASIBILITY-01.md` | [PRIMARY-01](reviews/LIFECYCLE-PRIMARY-01.zip), member `FEASIBILITY-01.md` | `a39197d51f4077fd762bda24dd031e1c1dfca4787666ae878d0db41c7b9244ed` |
| [PRIMARY-03](reviews/LIFECYCLE-PRIMARY-03.zip), `REVIEW-NATIVE03.md:60`, `../agnf-1b7aa2336c/REVIEW-NATIVE02.md` | [PRIMARY-02](reviews/LIFECYCLE-PRIMARY-02.zip), member `REVIEW-NATIVE02.md` | `76f1053e12fab5497592dd527697e556a4a70b52cc80a861e2514ba40e84f387` |

For direct reading, open the named companion archive member and verify its
hash. To retain the reports' original relative navigation after extraction,
extract PRIMARY-01 into `aglf-94222005ae/`, PRIMARY-02 into
`agnf-1b7aa2336c/`, and PRIMARY-03 into `agn3-bcad519a7e/`, as sibling directories.
This does not duplicate or change any historical evidence payload.
