"""InputBoundsCases for the bootstrap provenance facade."""
import copy
import json
from scripts.validate_bootstrap_provenance import PENDING_RECORD_STATUS, REVIEW_DIGEST_DOMAIN, compute_review_content_sha256


class InputBoundsCases:
    def test_selected_commit_validation_rejects_deep_json_without_traceback(self) -> None:
        nested: object = "leaf"
        for _ in range(80):
            nested = [nested]
        self.document["unexpected_deep_value"] = nested
        self.write_manifest()
        selected = self.commit_current_fixture("deep selected manifest")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(any("exceeds JSON depth" in error for error in errors), errors)

    def test_selected_commit_validation_rejects_nonfinite_json(self) -> None:
        self.document["unexpected_nonfinite_value"] = float("nan")
        self.write_manifest()
        selected = self.commit_current_fixture("nonfinite selected manifest")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(any("non-finite JSON number is forbidden" in error for error in errors), errors)

    def test_selected_commit_validation_rejects_overflowing_json_number(self) -> None:
        encoded = json.dumps(self.document, indent=2, sort_keys=True)
        self.manifest.write_text(
            encoded[:-1] + ',\n  "unexpected_overflow": 1e9999\n}\n',
            encoding="utf-8",
            newline="\n",
        )
        selected = self.commit_current_fixture("overflowing selected JSON number")

        errors, _ = self.validate_selected(selected)

        self.assertTrue(
            any("contains a non-finite JSON number" in error for error in errors),
            errors,
        )

    def test_selected_commit_array_cardinality_is_strict_and_bounded(self) -> None:
        original = copy.deepcopy(self.document["targets"][0])
        self.document["targets"].extend(copy.deepcopy(original) for _ in range(1_000))
        self.write_manifest()
        selected = self.commit_current_fixture("oversized target inventory")

        errors, details = self.validate_selected(selected)

        self.assertTrue(
            any("targets must contain exactly 11 entries" in error for error in errors),
            errors,
        )
        self.assertTrue(
            any("duplicate imported target path" in error for error in errors),
            errors,
        )
        self.assertLess(len(errors), 20, errors)
        self.assertEqual(11, details["targets"])

    def test_schema_version_two_is_rejected_after_git_snapshot_contract_change(
        self,
    ) -> None:
        self.document["schema_version"] = 2
        self.write_review_documents(approved=False)
        self.write_manifest()

        errors, _ = self.validate()

        self.assertTrue(any("schema_version must be integer 3" in e for e in errors))

    def test_schema_three_review_digest_domain_is_stable(self) -> None:
        self.assertEqual(
            b"arce-v0.0.2-bootstrap-provenance-review-v3\0",
            REVIEW_DIGEST_DOMAIN,
        )
        document = {
            "schema_version": 3,
            "scope_version": "v0.0.2",
            "review": {
                "record_status": PENDING_RECORD_STATUS,
                "reviewer": None,
                "reviewed_at": None,
                "final_status_after_review": None,
                "reviewed_audited_target_commit": None,
                "reviewed_content_sha256": None,
            },
        }
        record = b"""# deterministic digest fixture

```yaml
record_status: EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING
reviewer: null
reviewed_at: null
final_status_after_review: null
reviewed_audited_target_commit: null
reviewed_content_sha256: null
```
"""
        notice = b"# deterministic notice fixture\n"

        self.assertEqual(
            "65ce600cadbb390814a7f00454eb5891a43f29fa1f5c5545ac23e3a47405efcc",
            compute_review_content_sha256(document, record, notice),
        )

    def test_duplicate_json_key_is_rejected_instead_of_being_shadowed(self) -> None:
        content = self.manifest.read_bytes()
        self.assertTrue(content.startswith(b"{"))
        self.manifest.write_bytes(
            b'{\n  "schema_version": 999,' + content[len(b"{") :]
        )

        errors, _ = self.validate()

        self.assertTrue(
            any("duplicate JSON key: schema_version" in error for error in errors),
            errors,
        )
