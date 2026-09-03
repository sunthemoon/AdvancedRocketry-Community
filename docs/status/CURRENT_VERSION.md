# CURRENT_VERSION

```yaml
current_version: v0.9.0
status: PASSED
next_action: Begin v1.0.0 community MVP work from the accepted Beta baseline
last_updated: 2026-09-03
prerequisite_version: v0.8.0
prerequisite_status: PASSED
prerequisite_merge_commit: 8e39b1ef440306632cf101b5017e0bcb1f12eef5
work_branch: codex/v0.9.0-beta-hardening
base_commit: 0d59c01da458e13ed0014e98f91379c6f783e19d
build: 1.20.1-0.9.0-beta.1
tested_implementation_commit: f6cd77cebdb0a851cab76accbf66de565473b545
reviewed_head_commit: 7841dcc0d30b26a207ee221b0efbd1e25d459ed3
merge_commit: a7196ff9b22220c344071a1af69a663036f76aef
artifact_sha256: fbddf66938000cba369a83d4a22ff36b5ff1c9c635a0abd14f672b454e3946ad
release_url: https://github.com/sunthemoon/AdvancedRocketry-Community/releases/tag/v0.9.0-beta.1
```

The immutable v0.8.0 acceptance, PR checks, merge identity, and exact
post-merge JAR reproduction are recorded in
[`../releases/v0.8.0/GATE-STATUS.md`](../releases/v0.8.0/GATE-STATUS.md).
The v0.9.0 feature freeze, support contract, and bounded G8 visual decision are
owner-approved. PR #13 and its merge commit passed all four checks; a clean
`main` build reproduced the accepted JAR and 758-entry manifest byte-for-byte.
The same download-verified artifact is available as GitHub pre-release
`v0.9.0-beta.1`. The next milestone is v1.0.0; this Beta does not claim stable
release status.
