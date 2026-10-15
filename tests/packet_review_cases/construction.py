"""ConstructionCases for the packet review facade."""
import hashlib
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import (
    DEFAULT_PACKET_DIRECTORY,
    MANIFEST_NAME,
    POST_DECISION_PACKET_DIRECTORY,
    QUESTION_DEFINITIONS,
    REVIEW_INSTRUCTIONS_NAME,
    TOOL_DEFINITIONS,
    PacketError,
    generate_packet,
    verify_packet,
)


class ConstructionCases:
    def test_pending_packet_is_deterministic_and_fully_commit_bound(self) -> None:
        generated, manifest = self.generate()

        self.assertEqual(self.snapshot(self.base_packet), self.snapshot(generated))
        self.assertEqual(3, manifest["schema_version"])
        self.assertEqual(self.commit, manifest["source_commit"])
        self.assertIn(
            "Forge/Gradle provenance and license subreview only",
            manifest["scope_statement"],
        )
        self.assertIn(
            "does not establish full-repository originality",
            manifest["scope_statement"],
        )
        self.assertIn("or final G0", manifest["scope_statement"])
        self.assertEqual(35, len(manifest["files"]))
        self.assertEqual(37, len(self.snapshot(generated)))
        self.assertEqual(
            36, manifest["packet_construction"]["total_payload_file_count"]
        )
        self.assertEqual(
            35, manifest["packet_construction"]["bound_payload_file_count"]
        )
        self.assertEqual(
            1, manifest["packet_construction"]["generated_payload_file_count"]
        )
        self.assertEqual(
            sum(
                path.stat().st_size
                for path in generated.rglob("*")
                if path.is_file() and path != generated / MANIFEST_NAME
            ),
            manifest["packet_construction"]["total_payload_bytes"],
        )
        self.assertEqual(
            "COMPLETE_SCHEMA3_PROVENANCE_SELECTED_COMMIT",
            manifest["mechanical_validation"]["scope"],
        )
        self.assertEqual("PASS", manifest["mechanical_validation"]["status"])
        self.assertEqual("NONE", manifest["mechanical_validation"]["human_approval_effect"])
        self.assertEqual(
            "PENDING_CONTENT_DIAGNOSTIC_ONLY",
            manifest["review_content_binding"]["classification"],
        )
        self.assertIn("must never be copied", manifest["review_content_binding"]["statement"])
        self.assertIsNone(
            manifest["review_content_binding"]["recorded_in_authoritative_review"]
        )

        tools = manifest["tool_identity"]["tools"]
        self.assertEqual(
            [(role, path) for role, path in TOOL_DEFINITIONS],
            [(item["role"], item["repository_path"]) for item in tools],
        )
        for item in tools:
            self.assertEqual(self.commit, item["tool_commit"])
            self.assertRegex(item["git_blob_oid"], r"^[0-9a-f]{40}$")
            self.assertRegex(item["raw_sha256"], r"^[0-9a-f]{64}$")
            self.assertGreater(item["size"], 0)

        section = manifest["question_section"]
        self.assertEqual(
            "V002_G0_PROVENANCE_HUMAN_DECISIONS_V1", section["id"]
        )
        self.assertEqual("PACKET_SCHEMA", section["id_owner"])
        self.assertEqual(4, section["authoritative_source_question_count"])
        self.assertEqual("PENDING_HUMAN_DECISION", section["workflow_state"])
        self.assertEqual(
            [item["id"] for item in QUESTION_DEFINITIONS],
            [item["id"] for item in section["questions"]],
        )
        for question in section["questions"]:
            self.assertNotIn("answer", question)
            self.assertNotIn("reviewer", question)
            self.assertNotIn("reviewed_at", question)
            self.assertEqual(section["id"], question["packet_question_section_id"])
            self.assertRegex(question["source_question_sha256"], r"^[0-9a-f]{64}$")

        repository_paths = {entry["repository_path"] for entry in manifest["files"]}
        self.assertIn("docs/releases/v0.0.2/INSTALLATION.md", repository_paths)
        self.assertIn(
            "docs/work/v0.0.2-test-machine-handoff.md", repository_paths
        )
        instruction_entry = manifest["reviewer_instructions"]
        self.assertEqual(REVIEW_INSTRUCTIONS_NAME, instruction_entry["packet_path"])
        instruction_content = (generated / REVIEW_INSTRUCTIONS_NAME).read_bytes()
        self.assertEqual(len(instruction_content), instruction_entry["size"])
        self.assertEqual(
            hashlib.sha256(instruction_content).hexdigest(),
            instruction_entry["raw_sha256"],
        )
        instructions = instruction_content.decode("utf-8")
        self.assertIn(
            "only the human provenance and license subreview for the\n"
            "recorded Forge MDK and Gradle Wrapper inputs",
            instructions,
        )
        self.assertIn("does not\nestablish the originality of the full repository", instructions)
        self.assertIn("approve unrelated content, or\ncomplete Gate G0", instructions)
        self.assertIn("Pending-review workflow", instructions)
        self.assertIn("clean-build both JARs", instructions)
        self.assertIn("refresh the artifact manifest", instructions)
        self.assertIn("packaging evidence from the pending notice bytes", instructions)
        self.assertIn("Commit the exact decision application", instructions)
        self.assertIn(
            "validate_bootstrap_provenance.py --require-approved-review",
            instructions,
        )
        self.assertIn(
            f"generate --commit HEAD --output {POST_DECISION_PACKET_DIRECTORY}",
            instructions,
        )
        self.assertIn(
            f"verify --commit HEAD --packet {POST_DECISION_PACKET_DIRECTORY}",
            instructions,
        )
        self.assertIn("pending packet cannot authenticate the later source edits", instructions)
        self.assertIn("substantive decision is negative or requires changes", instructions)
        self.assertIn("documented correction log", instructions)
        self.assertIn("before committing the revised pending material", instructions)
        self.assertIn("Authoritative exact-Git verification", instructions)
        self.assertIn("Weaker offline content-only check", instructions)
        self.assertIn("Never execute `files/scripts/prepare_v002_g0_review_packet.py`", instructions)
        self.assertIn("separately authenticated trusted checkout", instructions)
        self.assertIn("private, quiescent packet copy", instructions)
        self.assertIn(
            f"--packet {DEFAULT_PACKET_DIRECTORY}",
            instructions,
        )
        self.assertNotIn("build/<packet-directory>", instructions)
        self.assertNotIn(
            "python -I -S files/scripts/prepare_v002_g0_review_packet.py",
            instructions,
        )
        self.assertNotIn(section["authoritative_source_heading"], instructions)
        for definition in QUESTION_DEFINITIONS:
            self.assertIn(f"`{definition['id']}`", instructions)

        for entry in manifest["files"]:
            content = (generated / entry["packet_path"]).read_bytes()
            self.assertEqual(len(content), entry["size"])
            self.assertEqual(hashlib.sha256(content).hexdigest(), entry["raw_sha256"])
            self.assertEqual(
                self.git("rev-parse", f"{self.commit}:{entry['repository_path']}"),
                entry["git_blob_oid"],
            )
        self.assertEqual([], verify_packet(self.root, self.commit, generated))

    def test_explicit_commit_uses_git_objects_despite_dirty_checkout(self) -> None:
        dirty = b"mutable worktree bytes must not enter the packet\n"
        (self.root / "README.md").write_bytes(dirty)
        generated, _ = self.generate()
        committed = packet_module._git_blob(
            self.root, self.commit, "README.md"
        ).content

        self.assertEqual(committed, (generated / "files/README.md").read_bytes())
        self.assertNotEqual(dirty, (generated / "files/README.md").read_bytes())
        self.assertEqual([], verify_packet(self.root, self.commit, generated))
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            generate_packet(self.root, "HEAD", self.build / "dirty-head")

    def test_approved_packet_only_observes_valid_recorded_binding(self) -> None:
        approved_commit = self.approve_fixture()
        self.commit = approved_commit
        approved_packet = self.build / "approved"
        manifest = generate_packet(self.root, approved_commit, approved_packet)

        binding = manifest["review_content_binding"]
        self.assertEqual(
            "VALID_RECORDED_APPROVAL_BINDING_OBSERVED_ONLY",
            binding["classification"],
        )
        self.assertEqual(binding["value"], binding["recorded_in_authoritative_review"])
        self.assertIn("already recorded", binding["statement"])
        self.assertEqual(
            "VALID_APPROVED_RECORD_OBSERVATION_ONLY",
            manifest["question_section"]["workflow_state"],
        )
        self.assertTrue(
            all(
                question["workflow_state"]
                == "VALID_APPROVED_RECORD_OBSERVATION_ONLY"
                for question in manifest["question_section"]["questions"]
            )
        )
        instructions = (approved_packet / REVIEW_INSTRUCTIONS_NAME).read_text(
            encoding="utf-8"
        )
        self.assertIn("Approved-state observation", instructions)
        self.assertIn(
            "already contains a mechanically valid, digest-bound\n"
            "approved-state record",
            instructions,
        )
        self.assertIn("does not ask the reviewer to rewrite decisions", instructions)
        self.assertNotIn("Pending-review workflow", instructions)
        self.assertNotIn("Commit the exact decision application", instructions)
        self.assertNotIn("--require-approved-review", instructions)
        self.assertNotIn(
            manifest["question_section"]["authoritative_source_heading"],
            instructions,
        )
        self.assertEqual([], verify_packet(self.root, approved_commit, approved_packet))
