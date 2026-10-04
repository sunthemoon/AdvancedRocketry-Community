# Independent native Tank data-loss incident

One High finding: native attempt 2 logs save refusal but ultimately writes an
empty BlockEntity list; four resource carriers disappear while physical tank
blocks and the unrelated stone mutation persist. Actual patched Forge bytecode
shows unload can clear live BlockEntities after a refused save. A list-only
guard does not survive that retry. The exact successful retry write event was
not observed; the report does not assign it more narrowly.

Pump/combustion/signature guards have the same static pattern, not independently
reproduced native failures. No corrected result or version Gate is inferred.

[Portable evidence](native-incident-01-evidence.zip), SHA-256
`56e2d09bf0e53c8026d549da1827643b20301e191aa25a765a3d3b23ada0542d`,
665,494 bytes /85 entries. All 84 manifest entries and ZIP CRC verify.
Raw `INCIDENT-01.md` SHA-256:
`a469b5ff86e1a3f1b271f208d20bafe2ddee71cbfc8a5627a9b3dedd3d17f1ec`.

The separate [native outcome audit](native-incident-02-evidence.zip) confirms
the failed chunk diff and six clean phases without claiming control-flow or
historical-world invariance. Its ZIP SHA-256 is
`708c57cb6cb8e5308eb9e6025cd2636fda5bba777d9080d4f7ca6bc8e7f97070`
(6,144,221 bytes /105 entries, all 104 package entries verified). Raw report
SHA-256 is `4d2ae37dd8d993ab65c0de6796e59b5b570764bda5bbab289ce0a477a41b7c21`.
