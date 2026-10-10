from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import parse_player_lifecycle
from scripts.run_dedicated_server_smoke import SERVER_PROPERTIES_IDENTITY_FILE
from scripts.run_dedicated_server_smoke import server_configuration_payload as smoke_server_configuration_payload


class MismatchPropertiesCases:
    """Mismatch world and active Java properties."""

    def test_mismatch_server_must_use_the_harness_world(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        (server_root / SERVER_PROPERTIES_IDENTITY_FILE).write_text(
            "server-ip=127.0.0.1\n"
            "server-port=25565\n"
            "level-name=other-world\n",
            encoding="utf-8",
        )

        errors, _, output = self.collect(session, "wrong-mismatch-world")

        self.assertTrue(
            any("harness-owned ASCII canonical" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_mismatch_server_rejects_noncanonical_java_properties_syntax(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        properties = server_root / SERVER_PROPERTIES_IDENTITY_FILE
        properties.write_text(
            properties.read_text(encoding="utf-8") + "level-name:other-world\n",
            encoding="utf-8",
        )

        errors, _, output = self.collect(session, "alternate-properties-separator")

        self.assertTrue(
            any("harness-owned ASCII canonical" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_java_rewritten_runtime_properties_do_not_break_startup_binding(self) -> None:
        session = self.ready_session()
        server_root = self.jar_paths["server"].parent.parent
        canonical_lines = smoke_server_configuration_payload(25565, True).decode(
            "ascii"
        ).splitlines()
        rewritten_lines = [
            line.replace("level-type=minecraft:normal", "level-type=minecraft\\:normal")
            for line in reversed(canonical_lines)
        ]
        (server_root / "server.properties").write_text(
            "#Minecraft server properties\n"
            "#Fri Aug 29 12:00:00 CST 2026\n"
            + "\n".join(rewritten_lines)
            + "\n",
            encoding="iso-8859-1",
        )
        self.refresh_mismatch_receipt(session)

        errors, record, _ = self.collect(session, "java-rewritten-properties")

        self.assertEqual([], errors)
        self.assertIsNotNone(record)

    def test_mismatch_world_load_requires_exact_dedicated_server_logger(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = source.read_text(encoding="utf-8").replace(
            "[minecraft/DedicatedServer]: Preparing level \"world\"",
            "[evil/FakeDedicatedServer]: Preparing level \"world\"",
        )
        self.replace_mismatch_server_log(session, payload)
        output = self.build / "fake-world-load-logger"

        errors, record = collect_evidence(
            self.write_session(session, "fake-world-load-logger-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(
            any("Preparing level" in error or "world-load" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())


class MismatchConnectionCases:
    """Logger-anchored mismatch connection evidence."""

    def test_strict_mismatch_accepts_server_connection_marker_without_client_marker(self) -> None:
        session = self.ready_session()
        self.replace_client_connection_marker(
            session, "missing-mod client result without a connection log marker"
        )
        output = self.build / "server-connection-marker-only"

        errors, record = collect_evidence(
            self.write_session(session, "server-connection-marker-only-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("READY_FOR_HUMAN_GATE_REVIEW", record["review_readiness"]["status"])
        self.assertEqual(
            "server",
            record["log_excerpts"]["mismatch_server_attempt_save_stop"][
                "connection_attempt_marker"
            ]["source"],
        )

    def test_strict_mismatch_accepts_bound_client_marker_without_server_marker(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        output = self.build / "client-connection-marker-only"

        errors, record = collect_evidence(
            self.write_session(session, "client-connection-marker-only-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertEqual([], errors)
        assert record is not None
        self.assertEqual("READY_FOR_HUMAN_GATE_REVIEW", record["review_readiness"]["status"])
        self.assertEqual(
            "client",
            record["log_excerpts"]["mismatch_attempt"][
                "connection_attempt_marker"
            ]["source"],
        )

    def test_strict_mismatch_accepts_log4j_xml_client_marker(self) -> None:
        session = self.ready_session()
        server_role = "mismatch_server_attempt_save_stop"
        server_source = self.root / session["log_excerpts"][server_role]["source"]
        server_payload = "\n".join(
            line
            for line in server_source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, server_payload)

        client_role = "mismatch_attempt"
        client_item = session["log_excerpts"][client_role]
        client_source = self.root / client_item["source"]
        client_source.write_text(
            """\
  <log4j:Event logger="net.minecraftforge.client.ForgeHooksClient" timestamp="1" level="WARN" thread="Netty Client IO #0">
    <log4j:Message><![CDATA[Server has additional mods that may be needed on the client]]></log4j:Message>
  </log4j:Event>
  <log4j:Event logger="net.minecraft.client.gui.screens.ConnectScreen" timestamp="2" level="INFO" thread="Render thread">
    <log4j:Message><![CDATA[Connecting to 127.0.0.1, 25565]]></log4j:Message>
  </log4j:Event>
""",
            encoding="utf-8",
        )
        client_item.update(
            line_start=1,
            line_end=6,
            warning_disposition={
                "status": "ACCEPTED",
                "warning_count": 1,
                "origins": ["ForgeHooksClient"],
                "explanation": "Reviewed structured Forge client warning.",
            },
        )
        output = self.build / "xml-client-connection-marker"

        errors, record = collect_evidence(
            self.write_session(session, "xml-client-connection-marker-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertEqual([], errors)
        assert record is not None
        marker = record["log_excerpts"][client_role]["connection_attempt_marker"]
        self.assertEqual("client", marker["source"])
        self.assertEqual(
            ["net.minecraft.client.gui.screens.connectscreen"], marker["loggers"]
        )
        self.assertTrue(marker["target_verified"])
        self.assertEqual(
            1,
            record["log_excerpts"][client_role]["source_audit"]["audit_counts"][
                "warning_count"
            ],
        )

    def test_strict_mismatch_rejects_client_marker_for_other_port(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session,
            "[Render thread/INFO] [minecraft/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25566",
        )
        output = self.build / "wrong-client-connection-port"

        errors, record = collect_evidence(
            self.write_session(session, "wrong-client-connection-port-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_rejects_forged_client_logger(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session,
            "[Render thread/INFO] [evil/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25565",
        )
        output = self.build / "forged-client-connection-logger"

        errors, record = collect_evidence(
            self.write_session(session, "forged-client-connection-logger-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_rejects_when_both_connection_markers_are_absent(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        source = self.root / session["log_excerpts"][role]["source"]
        payload = "\n".join(
            line
            for line in source.read_text(encoding="utf-8").splitlines()
            if "Disconnecting VANILLA connection attempt" not in line
        ) + "\n"
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session, "missing-mod result without any connection marker"
        )
        output = self.build / "no-connection-markers"

        errors, record = collect_evidence(
            self.write_session(session, "no-connection-markers-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("connection-attempt marker" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_strict_mismatch_requires_logger_anchored_connection_attempt(self) -> None:
        session = self.ready_session()
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        payload = source.read_text(encoding="utf-8").replace(
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client",
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client",
        )
        self.replace_mismatch_server_log(session, payload)
        self.replace_client_connection_marker(
            session, "missing-mod client result without a connection log marker"
        )
        output = self.build / "missing-connection-marker"

        errors, record = collect_evidence(
            self.write_session(session, "missing-connection-marker-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(
            any("connection-attempt marker" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_embedded_minecraft_logger_text_cannot_forge_player_lifecycle(self) -> None:
        session = self.ready_session()
        role = "server_first_join_leave_save_stop"
        first_log = self.root / session["log_excerpts"][role]["source"]
        first_log.write_text(
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/Chat]: injected "
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "Saved the game\n"
            "Stopping server\n",
            encoding="utf-8",
        )
        self.refresh_summary_log_hash(session, "first-start", first_log)

        errors, _, output = self.collect(session, "embedded-player-spoof")

        self.assertTrue(
            any("exactly one player join and one player leave" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_similar_minecraft_logger_name_cannot_forge_player_lifecycle(self) -> None:
        payload = (
            "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [evil/FakeMinecraftServerChat]: "
            "SecretPlayer left the game\n"
        )

        with self.assertRaisesRegex(ValueError, "exactly one player join"):
            parse_player_lifecycle(payload, "fake-logger")
