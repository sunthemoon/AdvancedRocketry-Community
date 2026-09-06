import contextlib
import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts import validate_bootstrap_provenance as validator


class BootstrapVersionDispatchTests(unittest.TestCase):
    def run_dispatch(self, version, extra=()):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            if version is not None:
                status = root / "docs/status/CURRENT_VERSION.md"
                status.parent.mkdir(parents=True)
                status.write_text(f"# CURRENT_VERSION\n\n```yaml\ncurrent_version: {version}\n```\n")
            with patch.object(validator, "validate_bootstrap_provenance_at_commit", return_value=(["dispatch sentinel"], {})) as historical, \
                 patch.object(validator, "validate_bootstrap_provenance", return_value=(["dispatch sentinel"], {})) as mutable, \
                 contextlib.redirect_stdout(io.StringIO()):
                code = validator.main(["--repository-root", str(root), *extra])
            return code, root, historical.call_args, mutable.call_args

    def test_later_minor_and_major_versions_use_the_same_accepted_history(self):
        for version in ("v0.1.0", "v0.9.0", "v0.10.0", "v1.0.0", "v1.1.0", "v2.0.0", "v12.42.1"):
            with self.subTest(version=version):
                code, root, historical, mutable = self.run_dispatch(version)
                self.assertEqual(1, code)
                self.assertIsNone(mutable)
                self.assertEqual((root, validator.HISTORICAL_V002_RECORD_COMMIT, validator.DEFAULT_MANIFEST), historical.args)

    def test_bootstrap_or_unrecognized_status_keeps_mutable_validation(self):
        for version in (None, "v0.0.1", "v0.0.2", "v0.0.99", "DRAFT", "not-v1.0.0"):
            with self.subTest(version=version):
                code, root, historical, mutable = self.run_dispatch(version)
                self.assertEqual(1, code)
                self.assertIsNone(historical)
                self.assertEqual(root, mutable.kwargs["repository_root"])

    def test_explicit_selected_commit_still_takes_precedence(self):
        selected = "a" * 40
        code, root, historical, mutable = self.run_dispatch("v1.0.0", ("--selected-commit", selected))
        self.assertEqual(1, code)
        self.assertIsNone(mutable)
        self.assertEqual((root, selected, validator.DEFAULT_MANIFEST), historical.args)

    def test_historical_dispatch_cannot_prepare_an_approval_digest(self):
        for mode in ("--prepare-approval-digest", "--diagnostic-pending-digest"):
            with self.subTest(mode=mode):
                code, _, historical, mutable = self.run_dispatch("v1.0.0", (mode,))
                self.assertEqual(2, code)
                self.assertIsNone(historical)
                self.assertIsNone(mutable)

    def test_require_approved_review_still_rejects_pending_history(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            status = root / "docs/status/CURRENT_VERSION.md"
            status.parent.mkdir(parents=True)
            status.write_text("current_version: v1.0.0\n")
            with patch.object(validator, "validate_bootstrap_provenance_at_commit",
                              return_value=([], {"review_status": validator.PENDING_RECORD_STATUS})), \
                 patch.object(validator, "validate_bootstrap_provenance") as mutable, \
                 patch.object(validator, "_print_mechanical_pass"), \
                 patch.object(validator, "_print_review_state"), contextlib.redirect_stdout(io.StringIO()):
                code = validator.main(["--repository-root", str(root), "--require-approved-review"])
            self.assertEqual(1, code)
            mutable.assert_not_called()


if __name__ == "__main__":
    unittest.main()
