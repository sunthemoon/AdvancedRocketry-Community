# C18a living-tick gravity: committed integration

Date: 2026-10-07. Status: **implemented-unverified**. This implements only leaf A
of [TASK-01](TASK-01.md); fall handling, parent delivery and all version Gates
remain open.

## Source and design

Root commits the four worker files at
`84e18ed0a0ea7bc3b20bb53cad26430944cca7d9`, fast-forwards main, then commits the
three central files at **`1b6071d14fb5801f4fcf18c7808a5938f7a96c25`**. The latter
is the complete source checkpoint, normally pushed to
`codex/v1.8.0-classic-content`. The worker checkpoint depends on the central
config/binding and is not independently claimed to compile or deliver.
[SOURCE-01](SOURCE-01.md) retains the author's pre-commit development observations;
this record supplies the later commit association without rewriting that history.

- [Controller](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialGravityController.java):
  existing registered server living-tick dispatch now handles non-player living
  entities using their actual Level gravity. Players retain field, position and
  Level precedence. Null attributes return before resolution; there is no scan,
  new event listener, ticket, forced chunk load or motion correction.
- [Config](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/config/CommonConfig.java)
  and [binding](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.java):
  `environment.classicGravityEnabled` defaults true. Disabling it removes only
  the non-player owned modifier, not existing player/station/device behavior.
- The existing UUID remains stable. Matching transient modifiers are idempotent;
  matching permanent residue is converted to transient. Factor one removes owned
  residue. Base values and foreign modifiers remain untouched. No new entity NBT
  root is introduced.

## Independent actual-source reviews

All three initial source reviews inspect actual bytes, not author summaries:
[central review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/CENTRAL-REVIEW-01.md),
[worker review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/WORKER-REVIEW-01.md)
and [GameTest review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/GAME-TEST-REVIEW-01.md).
No scoped Critical/High/Medium is established. Two reviewers independently find
the same Low: a forced-null getter cannot observe backing attribute changes.

The narrow correction adds independent native map/instance, complete immutable
modifier identity/count, base and serialized-map witnesses, for owned transient
absence and presence. The
[successor review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-source-independent-review-20261007/GAME-TEST-REVIEW-02.md)
addresses that Low with no new scoped finding. Its SHA-256 is
`8f1a7ad91c0abd08c2b87ba1c0c29cd03e881c33ef751052c693d7e773977942`.
An exact in-memory inverse proves all unrelated tests/helpers/budgets unchanged.
All source read pins are released before publication. No reviewer executes Java.

## Tests and actual execution

- [Controller JUnit](../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/celestial/service/CelestialGravityControllerTest.java):
  seven added declarations, two old test bodies preserved.
- [Config JUnit](../../../src/test/java/io/github/sunthemoon/advancedrocketrycommunity/config/ClassicGravityConfigTest.java):
  three new declarations for stable key/default, loaded switch and independent
  ephemeral override.
- [Living GameTests](../../../src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/LivingGravityGameTests.java):
  nine new declarations, each retaining a 100-tick budget. Synchronous native
  tick/travel, production config, entity NBT save/load, foreign modifiers,
  actual player field and station fixtures are distinguished from three direct
  controller fixtures. This is not natural scheduler, disk or restart proof.
  The 506-line holder has one gravity-test responsibility, not production logic.

Root commands `7b1990` and `b3aeed` exit 0: exact scoped staging/stat/whitespace,
seven reviewed file-pin checks, two commits, fast-forward integration, planning,
accepted-ledger and common/client-boundary checks, normal push and remote-SHA
equality. Index remains empty and user AGENTS bytes are preserved. The ledger
remains 653 units /186 PLANNED /154 REVIEW; no delivery row changes.

Earlier `00edb4` exits 1 before staging because the inspection helper strips a
leading Git status space; the corrected helper preserves it. Inspection
`cf348b` also emits a missing-plan-path error before the actual canonical path
is located. Neither is a Java failure or an erased successful check.

No local JVM/Gradle/native run starts with C: below 10 GB. At capture, C: has
7,467,044,864 free bytes. New evidence is under the D-local project parent.
The [first API capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/OBSERVATION-01.json),
SHA-256 `10f30e7a9b9c25a64459fa9696ea2c7f2a6e571844c89d514794765a1dbd87df`,
records **run 37578961754 /attempt 1 /job 112654063999**, exact complete source,
at **2026-10-07T05:59:05.732123Z**. Clean build is in progress; artifact,
DataGen and GameTest steps are pending. This is metadata only, not a pass or
test count. [Hosted run](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37578961754).

### Later failed cohort and separate fixture correction

The first dated running observation remains unchanged. The subsequently audited
[terminal result](../v1.8.0-ci/RESULT-23.md) records 369 XML /2,083 cases, one
strict inventory failure (expected 72, actual 73), zero errors/skips. All nine
controller and three new config cases pass, but DataGen and GameTests skip.
This does not establish the nine native cases or full slice qualification.

The one-file inventory correction preserves the obsolete-key ban and all 15
methods, adds exact registered-key identity, and retains exact cardinality at 73.
Its independent actual-diff review has no scoped finding. Root commits/pushes it
at **`8b3fdca990be3880ff04aae5a2354657d499b60f`**, the successor source checkpoint;
the original failed cohort remains failed. The
[separate successor capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-root-integration-20261007/SUCCESSOR-OBSERVATION-01.json),
SHA-256 `771bf5bfa182150f8c1015ad33f3036856cb178d6777aadd03fb914b56bdda03`,
at **06:13:47.247826Z** binds run **37580341390 /attempt 1 /job 112658310596**
to this exact successor. Tooling is in progress, build/DataGen/GameTests pending;
this is metadata only, not a successor pass or count.

The later [successor terminal result](../v1.8.0-ci/RESULT-24.md) records clean
build and all 2,083 unit cases passing, including the revised exact inventory;
both DataGen/clean checks and hosted artifact audit pass. The nine-test native
living-gravity batch executes without a named terminal failure. The full 518-case
GameTest cohort nevertheless fails the existing Tau Ceti roundtrip. That failure
occurs before the new batch starts, but its unique cause/repair is not established.
Original failures and dated pending captures remain; neither parent delivery nor
Gate approval follows. Root and different-agent successor raw audits independently
agree with zero input drift; both distinguish batch observations from absent
individual native pass records.

The earlier four-record documentation review has one Low concerning categorical
non-execution wording. The log now says no result/count had been verified at the
first capture, not that no hosted test had run. That original finding remains
in its [review record](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-living-gravity-integration-record-review-20261007/REVIEW-01.md).

## Cleanup and remaining work

After all worker/reviewer releases, Root verifies the exact owned worktree,
clean status, integrated ancestry and remote source, then normally removes only
`D:/GitHub/arce-v180-living-gravity-20261007` (`a5192c`, exit 0). Source commits,
branch, thin evidence, other worktrees and user files remain. No denied cleanup
is retried or bypassed.

The [previous audited regression](../v1.8.0-ci/RESULT-22.md) belongs to `7d7b474e`,
not these new source/tests. The original failed result is independently audited;
the successor has partial executed evidence but a failed full regression.
Packaged use, actual cross-dimension behavior and dedicated restart remain
unverified. Native fall facts now establish argument propagation, but not a
once-only remedy or closure of leaf B's Medium. No fall listener, native hook
exception or physical hatch authority is introduced. C16 remaining machines,
C17-C19, R-021, real clients/GPU/multiplayer, performance and G0-G9 stay open;
v1.8 remains **IN_PROGRESS /IMPLEMENTING**.
