# Independent kernel recipe registration/package review

Scope: the eight Root-authored registration/package/test/generated changes,
not the reviewer's earlier selector authorship or Signature runtime source.
Final packaging11 has no unresolved Critical/High/Medium/Low findings.

The three current process recipes change only selectors. Exact stable IDs,
accounting and output ordering are unchanged. Both runtime and sources JARs
contain one current copy; all nine other machine/port crafting recipes and all
61 historical v1.2 inputs remain byte-identical. Independent DataGen exits0
with no content/input changes (a fresh cache writes769 identical files).
Scoped replay passes12 JUnit /2 suites. Root's second cached DataGen pass and
complete integration/GameTest remain separate requirements.

[Independent packet](kernel-tags-independent-01.zip):
SHA256 `c09f33cf7a2b095a110429bd85ed35d8881c7741878bb3a7f80dc1932945bc6b`,
22,781,490 bytes /51 entries; manifest
`153c07bdc5ebea07e5ccb887bc03997fab24ba1a22b105938242018e0e4fb6ce`.
Raw report hash `1dfd6f174c712fe67daf4879d4b29174ebbb3c2fdbbc52f690e6ae5e31929721`.
Root verifies all CRC/entry hashes before copying.

[Final candidate11](kernel-tags-candidate-11.zip):
SHA256 `6b5cfe74879687c469f8ffa81c89d337da92d1d7bc3c6c7712f2118421b35598`,
4,640,661 bytes /2,980 entries; eight-source manifest
`4478c0289dcea8addcddd4394a6d7ff537d460f5a1e07e2e23376db71ba8949c`.
The exact LF diff checks/applies0 and reproduces all eight postimages.

Historical packaging08's missing long-path companion Medium and09/10's patch
newline/CRLF Low are retained, separately resolved, never normalized away.
No historical data, migration policy, feature/ledger closure or Gate changes.
