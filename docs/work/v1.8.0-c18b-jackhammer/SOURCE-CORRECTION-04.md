# C18b-JACKHAMMER-01 native configuration and recipe-record correction

Date: 2026-10-09. Preparation commit 9e4a8a4e precedes these edits. Root's
61cada9e and bab14562 failed command cohorts remain separate and unchanged;
the independent reviewers continue qualification of those fixed commits.

The configuration test follows pinned ForgeConfigSpec.Builder's actual native
validator: Boolean values and case-insensitive true/false strings are accepted;
unsupported strings, whitespace-padded strings, numbers and null are rejected.
The production configuration, mining hooks and test budgets are unchanged.

The opt-in packaged continuation fixture requires the actual native server
console CommandSource, in addition to permission four and no source entity.
Its new native GameTest exercises the accepted console and rejected permission
three and CommandSource.NULL inputs. This restricts diagnostic authority; it
does not add production commands, change gameplay or alter the public API.

One exact first-match asset-plan row registers the already authored MIT-derived
jackhammer recipe as IMPORTED. Following rule numbers change mechanically.
The external manifest check confirms every other effective asset handling is
unchanged. The origin allowlist, all ledger units and provenance hashes are
unchanged. Provenance remains pending human review: this is neither bitmap
approval, complete item delivery nor a Gate pass. The first patch-rendering
attempt failed because it used the wrong manifest column name; no asset-plan
write occurred before correcting that external helper to source_path.

This candidate contains nine new JUnit cases and fourteen new native cases.
Qualification of this correction has not yet run. In particular, the original
strict-validator timeout, independent inherited link failures, survival inputs,
packaged restart, actual-client and human-review boundaries remain open.
