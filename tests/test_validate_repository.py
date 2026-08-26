import unittest

from scripts.validate_repository import normalize_link_target, parse_current_identity


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


if __name__ == "__main__":
    unittest.main()
