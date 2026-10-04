# Pump fixed-hook and harness review

Final bounded static/unit scopes have no unresolved findings. Original Medium
observations remain: command-block admission, then unstable NO_ENERGY during
cooldown. Hookr3 requires native-console authority and reports WAIT until
NO_ENERGY plus cooldown0. No ordinary protection or timing bound is bypassed.

Independent hookr3:7 JUnit /1 suite,exit0,31s. Unchanged final Python harness:
25 tests,exit0; separate WAIT4 to stable0 modeled response succeeds in2 polls.
Neither modeled nor unit result is native execution.

- [Hook r1](pump-hook-independent-r1.zip), SHA256
  `b0007c309899b491d72194d38f5173858bb01736eef89598a0216f200a717cd1`.
- [Hook r2](pump-hook-independent-r2.zip), SHA256
  `890c92062872dbde7ec08bffcaadc2260ce2c9600eebadcbd0e1178ecbf35ebc`.
- [Hook r3](pump-hook-independent-r3.zip), SHA256
  `50cc13c5748f22b726b9c9fed8eb37c54b18a96c8da0db1cbabae328e5a77247`.
- [Python author r2](pump-python-author-r2.zip), SHA256
  `02450879eb9fb81a65ea246f14bf0f97e3da384258ff137397c08ef8305a431e`.
- [Original harness finding](pump-python-independent-r1-original-finding.zip),
  SHA256 `8bce83ed060f6abef7492f89637af6e4c4721573bcfebf81ca3442da5873cc79`.
- [Final full portable review](pump-python-independent-final-portable.zip),
  SHA256 `e30de06c1d050862a0a4dac2fd6e6623f7f87b1f0b036225a15943b1badd4375`.

Root applies the exact two Python files and reproduces25 passing tests. Its
actual native attempt2 passes four clean-stop phases on frozen main58a5ab97…;
the failed first launcher never binds a world. Independent native audit is
separate and pending. Full rootGT fails12 Signature cases, so whole integrated
acceptance remains open. See [native task](../NATIVE-TASK.md) and
[root evidence](../../v1.8.0-c16a-integration/VERIFICATION.md).
No arbitrary-crash, client/GPU/performance or Required Gate claim follows.
