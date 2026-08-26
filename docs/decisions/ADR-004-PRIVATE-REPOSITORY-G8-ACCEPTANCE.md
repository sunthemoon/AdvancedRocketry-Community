# ADR-004 — Private repository G8 acceptance

```yaml
status: ACCEPTED
date: 2026-08-26
deciders: [sunthemoon]
owner: sunthemoon
target_version: v0.0.1
expires: before any change from private to public visibility
recovery_condition: reopen the signed-out review before public visibility or a public release
supersedes: ""
```

## Context

The v0.0.1 manual plan requested an authenticated GitHub governance review,
saved screenshots, and a signed-out content review. The repository remains
private by maintainer decision, so signed-out visitors correctly receive
`404` and cannot inspect repository content.

Seven authenticated screenshots were captured after pull request #1 merged.
Their source commit, dimensions, and SHA-256 values are indexed, and the
repository validator rejects altered, misplaced, oversized, or non-JPEG
evidence. Unauthenticated page and REST requests both returned `404`.

## Decision

Keep the repository private. For v0.0.1, accept the indexed authenticated
screenshots plus the anonymous `404` privacy check as sufficient Gate G8
evidence. Mark G8 and v0.0.1 as passed after recording this maintainer
decision.

The signed-out content review is not claimed to have occurred. It becomes a
mandatory precondition immediately before any future change to public
visibility or any public release.

## Alternatives

### A. Make the repository public now

- Would permit a direct signed-out content review.
- Would expose the repository and history earlier than the maintainer wants.
- Rejected for v0.0.1.

### B. Keep G8 open indefinitely

- Avoids accepting substitute evidence.
- Blocks the private planning repository from advancing despite complete
  authenticated evidence and an explicit maintainer review.
- Rejected for v0.0.1.

## Consequences

### Positive

- The repository remains private.
- The accepted evidence is reproducible and hash-checked.
- v0.0.2 work can begin without misrepresenting repository visibility.

### Negative

- Public navigation, rendering, and community links remain untested from a
  signed-out browser.
- GitHub continues to report the classic `main` protection rule as not
  enforced under the current private personal-account configuration.

## Validation

- [x] Seven authenticated screenshots visually reviewed
- [x] Screenshot path, JPEG signature, size, and SHA-256 checks pass
- [x] Anonymous repository page returns `404`
- [x] Anonymous REST repository endpoint returns `404`
- [x] Maintainer accepted the evidence for v0.0.1
- [x] Reopen condition recorded

## Revisit when

Reopen the signed-out review and branch-protection check before:

- changing repository visibility to public;
- publishing a public GitHub Release or package page;
- representing the repository as publicly accessible.
