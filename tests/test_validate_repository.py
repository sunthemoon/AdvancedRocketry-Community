import hashlib
import unittest

from scripts.validate_repository import (
    GRADLE_WRAPPER_PATH,
    ROOT,
    is_audited_v001_evidence,
    is_approved_gradle_wrapper,
    is_approved_third_party_license,
    normalize_link_target,
    parse_current_identity,
)


class IdentityParsingTests(unittest.TestCase):
    def test_current_draft_is_not_confused_with_approved_example(self) -> None:
        document = '''
Example:
identity_status: "APPROVED"

当前值：

reviewed_by: ""
reviewed_at: ""
identity_status: "DRAFT"
'''

        self.assertEqual(("DRAFT", None, None), parse_current_identity(document))

    def test_current_approved_values_are_parsed(self) -> None:
        document = '''
当前值：

reviewed_by: "sunthemoon"
reviewed_at: "2026-08-26"
identity_status: "APPROVED"
'''

        self.assertEqual(
            ("APPROVED", "sunthemoon", "2026-08-26"),
            parse_current_identity(document),
        )


class MarkdownTargetTests(unittest.TestCase):
    def test_angle_brackets_and_url_encoding_are_normalized(self) -> None:
        self.assertEqual(
            "docs/My File.md#section",
            normalize_link_target("<docs/My%20File.md#section>"),
        )

    def test_optional_markdown_title_is_removed(self) -> None:
        self.assertEqual(
            "docs/file.md",
            normalize_link_target('docs/file.md "title"'),
        )


class EvidenceAssetTests(unittest.TestCase):
    def setUp(self) -> None:
        self.relative = "docs/releases/v0.0.1/evidence/github-home-authenticated.jpg"
        self.content = b"\xff\xd8\xffauthenticated evidence"
        digest = hashlib.sha256(self.content).hexdigest()
        self.index = (
            "| [Repository homepage](github-home-authenticated.jpg) | evidence | "
            f"`{digest}` |"
        )

    def test_indexed_jpeg_with_matching_hash_is_allowed(self) -> None:
        self.assertTrue(
            is_audited_v001_evidence(self.relative, self.content, self.index)
        )

    def test_changed_evidence_content_is_rejected(self) -> None:
        self.assertFalse(
            is_audited_v001_evidence(
                self.relative, self.content + b"changed", self.index
            )
        )

    def test_image_outside_release_evidence_directory_is_rejected(self) -> None:
        self.assertFalse(
            is_audited_v001_evidence(
                "src/main/resources/texture.jpg", self.content, self.index
            )
        )


class ApprovedBinaryTests(unittest.TestCase):
    def test_pinned_gradle_wrapper_is_allowed(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertTrue(is_approved_gradle_wrapper(GRADLE_WRAPPER_PATH, content))

    def test_other_jar_path_is_rejected(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertFalse(is_approved_gradle_wrapper("libs/dependency.jar", content))

    def test_changed_wrapper_is_rejected(self) -> None:
        content = (ROOT / GRADLE_WRAPPER_PATH).read_bytes()

        self.assertFalse(
            is_approved_gradle_wrapper(GRADLE_WRAPPER_PATH, content + b"changed")
        )


class ApprovedThirdPartyLicenseTests(unittest.TestCase):
    def test_pinned_license_copies_are_allowed(self) -> None:
        for relative in (
            "docs/licenses/GRADLE-8.1.1-LICENSE.txt",
            "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt",
        ):
            with self.subTest(relative=relative):
                self.assertTrue(
                    is_approved_third_party_license(
                        relative, (ROOT / relative).read_bytes()
                    )
                )

    def test_changed_license_copy_is_rejected(self) -> None:
        relative = "docs/licenses/GRADLE-8.1.1-LICENSE.txt"

        self.assertFalse(
            is_approved_third_party_license(
                relative, (ROOT / relative).read_bytes() + b"changed"
            )
        )

    def test_unlisted_license_path_is_rejected(self) -> None:
        self.assertFalse(is_approved_third_party_license("docs/licenses/other.txt", b""))


if __name__ == "__main__":
    unittest.main()
