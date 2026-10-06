# Seal detector tooltip unit fixture correction

Date: 2026-10-07. Status: **verified for the tooltip unit fixture only**.
Published commit `33a3156e309ca2b8f6a1fcc766501968bc21f138`, following the
reviewed records at `f40f0bdea2fb2c65d20815a65c933cdcd82893bc`.

The exact detector source's [failed hosted result](../v1.8.0-ci/RESULT-12.md)
reaches `:test`, then fails while constructing an unregistered mod item stack
in the tooltip fixture. Plain JUnit's existing `MinecraftBootstrap` deliberately
does not run full Forge mod registration. Adjacent canister tests already use
initialized vanilla PAPER, including custom tags, with that same bootstrap.

Only `SealDetectorItemTest.java` changes: add the `Items` import, explain the
plain-JUnit boundary and supply `new ItemStack(Items.PAPER)` to the real detector
tooltip method. Four test identities and every assertion remain unchanged;
custom tag, before/after equality, fixed tooltip key and count checks are retained.
No product, registry, bootstrap, timeout or test selection changes. A tagged
nonempty sentinel verifies that the tooltip ignores its argument; registered
detector identity and native interaction remain separate runtime obligations.

[Independent actual-delta review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/seal-clean-build-investigation-20261007-81e4a9/REPORT-01.md),
SHA `9c214d9a8b5665cfd93a1e61c98f8fdbd6f5e7c3310783113f1a878b6f1580f2`,
disposes the original Medium fixture issue without establishing an introduced
C/H/M/L in the narrow static successor. Twenty-one controls pass, five named
pins remain unchanged; reviewer inspection/check failures remain separate.
No Java, Jupiter or native replay occurs in that review.

Root publishes the exact 3,047-byte postimage, SHA
`ff7f5363cc898086925a6371cb57016daa8f48d8f8e112c1c619207fb40025b6`,
as a single-file commit (3 additions /1 deletion), then normally pushes and
checks remote equality and the owner AGENTS guard. These are actual Root tool
observations (`501cfa`), not an invented exported original shell receipt.
Scoped whitespace succeeds. C free space is below 10 GB, so no local full
Gradle/GameTest/native execution is admitted.

The original failed build remains failed. The [exact new regression](../v1.8.0-ci/RESULT-13.md)
now executes the corrected tooltip test successfully, with all original
assertions retained. Complete clean build/JUnit and DataGen pass; the whole
regression fails at the required Tau GameTest. Full detector delivery,
native/client checks and G0–G9 are not claimed.
