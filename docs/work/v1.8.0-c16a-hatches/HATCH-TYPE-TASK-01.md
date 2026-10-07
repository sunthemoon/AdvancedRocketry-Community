# Native hatch type dependency

Date: 2026-10-07. Status: implemented-unverified private source delta.
Base: `cb0a0019a5e535e45902872fc814f966590b3b61`.
Scope: ClassicHatchBlockEntity constructor and this task record only.

The hatch accepts an actual nonnull `BlockEntityType<ClassicHatchBlockEntity>`
from its caller instead of resolving an undeclared central registry field. This
follows the adjacent controller's native constructor injection and preserves the
existing actual BlockState-to-hatch-kind lookup. It adds no default type, stub,
registered constructor, block/item/schema/public ID or gameplay authority.
The assembled tree contains no callers of the previous constructor.

The registry import is removed and the actual native BlockEntityType import is
added. All fields, LOAD candidate/recording, ticket/lifetime checks, capabilities,
save/retention/resource behavior and tests remain unchanged. Actual registration
will have to supply the genuine type; this edit does not activate that service.

Root's declared isolated scope is
`D:/GitHub/arce-v180-hatch-type-injection-20261007`; main and the original assembly
HEAD/index/source are not moved. Scripts and thin evidence are in
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/classic-hatch-type-injection-20261007`.

Verification required: exact constructor inverse and other-source preservation,
independent actual-source review and later bounded genuine dependency compilation.
No Java/test/native execution is claimed by this source edit. Full local runs
remain barred below 10 GB. Uncompiled owner/comparator declarations, installed
physical/save qualification, R-021, ledger closure and all Required Gates remain
open. Publication is a later Root action after review and read-pin release.
