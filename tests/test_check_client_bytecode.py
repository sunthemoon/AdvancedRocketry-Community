import contextlib
import io
import json
import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts import check_client_bytecode as checker


REPOSITORY = Path(__file__).resolve().parents[1]
MINECRAFT = "net/minecraft/client/Minecraft"
RENDER_SYSTEM = "com/mojang/blaze3d/systems/RenderSystem"
RETURN = b"\xb1"


def u2(value: int) -> bytes:
    return struct.pack(">H", value)


def u4(value: int) -> bytes:
    return struct.pack(">I", value)


class ClassBuilder:
    """Assembles a minimal class file. Pool indices are fixed when an entry is first added."""

    def __init__(self, name: str, superclass: str = "java/lang/Object", interfaces: tuple[str, ...] = ()):
        self.entries = bytearray()
        self.keys: dict[tuple, int] = {}
        self.next = 1
        self.this = self.class_ref(name)
        self.superclass = self.class_ref(superclass)
        self.interfaces = [self.class_ref(interface) for interface in interfaces]
        self.fields: list[bytes] = []
        self.methods: list[bytes] = []
        self.attributes: list[bytes] = []

    def _add(self, key: tuple, data: bytes, slots: int = 1) -> int:
        if key not in self.keys:
            self.keys[key] = self.next
            self.entries += data
            self.next += slots
        return self.keys[key]

    def utf8(self, text: str) -> int:
        raw = text.encode("utf-8")
        return self._add(("utf8", text), b"\x01" + u2(len(raw)) + raw)

    def class_ref(self, name: str) -> int:
        return self._add(("class", name), b"\x07" + u2(self.utf8(name)))

    def string(self, text: str) -> int:
        return self._add(("string", text), b"\x08" + u2(self.utf8(text)))

    def long_constant(self, value: int) -> int:
        return self._add(("long", value), b"\x05" + struct.pack(">q", value), slots=2)

    def name_and_type(self, name: str, descriptor: str) -> int:
        return self._add(("nat", name, descriptor), b"\x0c" + u2(self.utf8(name)) + u2(self.utf8(descriptor)))

    def method_ref(self, owner: str, name: str, descriptor: str) -> int:
        return self._add(("method", owner, name, descriptor),
                         b"\x0a" + u2(self.class_ref(owner)) + u2(self.name_and_type(name, descriptor)))

    def method_type(self, descriptor: str) -> int:
        return self._add(("methodtype", descriptor), b"\x10" + u2(self.utf8(descriptor)))

    def attribute(self, name: str, body: bytes) -> bytes:
        return u2(self.utf8(name)) + u4(len(body)) + body

    def field(self, name: str, descriptor: str) -> None:
        self.fields.append(u2(0x0002) + u2(self.utf8(name)) + u2(self.utf8(descriptor)) + u2(0))

    def method(self, name: str, descriptor: str, code: bytes | None = None, *, stack_map: bytes | None = None,
               catches: tuple[str, ...] = (), throws: tuple[str, ...] = (), signature: str | None = None) -> None:
        attributes = []
        if code is not None:
            nested = [self.attribute("StackMapTable", stack_map)] if stack_map is not None else []
            handlers = b"".join(u2(0) + u2(len(code)) + u2(0) + u2(self.class_ref(name_)) for name_ in catches)
            body = (u2(4) + u2(4) + u4(len(code)) + code + u2(len(catches)) + handlers
                    + u2(len(nested)) + b"".join(nested))
            attributes.append(self.attribute("Code", body))
        if throws:
            attributes.append(self.attribute(
                "Exceptions", u2(len(throws)) + b"".join(u2(self.class_ref(name_)) for name_ in throws)))
        if signature is not None:
            attributes.append(self.attribute("Signature", u2(self.utf8(signature))))
        self.methods.append(u2(0x0009) + u2(self.utf8(name)) + u2(self.utf8(descriptor))
                            + u2(len(attributes)) + b"".join(attributes))

    def calls(self, owner: str, name: str = "run", descriptor: str = "()V", in_method: str = "run") -> "ClassBuilder":
        self.method(in_method, "()V", b"\xb8" + u2(self.method_ref(owner, name, descriptor)) + RETURN)
        return self

    def inner_classes(self, *pairs: tuple[str, str]) -> None:
        body = u2(len(pairs)) + b"".join(
            u2(self.class_ref(inner)) + u2(self.class_ref(outer)) + u2(0) + u2(0x0008) for inner, outer in pairs)
        self.attributes.append(self.attribute("InnerClasses", body))

    def nest_members(self, *names: str) -> None:
        body = u2(len(names)) + b"".join(u2(self.class_ref(name)) for name in names)
        self.attributes.append(self.attribute("NestMembers", body))

    def nest_host(self, name: str) -> None:
        self.attributes.append(self.attribute("NestHost", u2(self.class_ref(name))))

    def build(self, major: int = 61) -> bytes:
        return (u4(0xCAFEBABE) + u2(0) + u2(major) + u2(self.next) + bytes(self.entries)
                + u2(0x0021) + u2(self.this) + u2(self.superclass)
                + u2(len(self.interfaces)) + b"".join(u2(index) for index in self.interfaces)
                + u2(len(self.fields)) + b"".join(self.fields)
                + u2(len(self.methods)) + b"".join(self.methods)
                + u2(len(self.attributes)) + b"".join(self.attributes))


def calling(name: str, *owners: str) -> bytes:
    builder = ClassBuilder(name)
    for number, owner in enumerate(owners):
        builder.calls(owner, in_method=f"step{number}")
    return builder.build()


def references(data: bytes) -> set[str]:
    return set(checker.parse_class(data).references)


class ParseClassTests(unittest.TestCase):
    def test_header_members_and_descriptors_are_references(self) -> None:
        builder = ClassBuilder("mod/a/Thing", "mod/a/Base", ("mod/a/Shape",))
        builder.field("players", "[Lnet/minecraft/client/player/LocalPlayer;")
        builder.method("convert", "(ILmod/a/Input;J)Lmod/a/Output;")
        builder.calls("mod/a/Helper", "apply", "(Lmod/a/Argument;)V")

        self.assertEqual(
            {"mod/a/Base", "mod/a/Shape", "net/minecraft/client/player/LocalPlayer", "mod/a/Input",
             "mod/a/Output", "mod/a/Helper", "mod/a/Argument"},
            references(builder.build()),
        )

    def test_metadata_only_nested_class_is_not_a_reference(self) -> None:
        builder = ClassBuilder("mod/a/Outer")
        builder.inner_classes(("mod/a/Outer$Render", "mod/a/Outer"))
        builder.nest_members("mod/a/Outer$Render")
        self.assertEqual({"java/lang/Object"}, references(builder.build()))

        nested = ClassBuilder("mod/a/Outer$Render")
        nested.inner_classes(("mod/a/Outer$Render", "mod/a/Outer"))
        nested.nest_host("mod/a/Outer")
        self.assertEqual({"java/lang/Object"}, references(nested.build()))

    def test_code_use_of_a_nested_class_is_a_reference(self) -> None:
        builder = ClassBuilder("mod/a/Outer")
        render = builder.class_ref("mod/a/Outer$Render")
        builder.method("check", "(Ljava/lang/Object;)Z", b"\x2b\xc1" + u2(render) + b"\xac")
        builder.inner_classes(("mod/a/Outer$Render", "mod/a/Outer"))
        builder.nest_members("mod/a/Outer$Render")

        self.assertIn("mod/a/Outer$Render", references(builder.build()))

    def test_stack_map_handlers_and_throws_are_references(self) -> None:
        # Nested classes, so that only these uses, and not the InnerClasses metadata, count.
        builder = ClassBuilder("mod/a/Frames")
        local = builder.class_ref("mod/a/Frames$Local")
        # same_locals_1_stack_item, then full_frame with one Object local and an empty stack.
        frames = u2(2) + bytes([64, 7]) + u2(local) + bytes([255]) + u2(0) + u2(1) + bytes([7]) + u2(local) + u2(0)
        builder.method("frames", "()V", RETURN, stack_map=frames, catches=("mod/a/Frames$Failure",),
                       throws=("mod/a/Frames$Checked",))
        builder.inner_classes(*((f"mod/a/Frames${name}", "mod/a/Frames") for name in ("Local", "Failure", "Checked")))

        self.assertEqual(
            {"java/lang/Object", "mod/a/Frames$Local", "mod/a/Frames$Failure", "mod/a/Frames$Checked"},
            references(builder.build()),
        )

    def test_reserved_stack_map_frame_is_rejected(self) -> None:
        builder = ClassBuilder("mod/a/Frames")
        builder.method("frames", "()V", RETURN, stack_map=u2(1) + bytes([200]))

        with self.assertRaises(checker.ClassFormatError):
            checker.parse_class(builder.build())

    def test_switches_wide_instructions_and_wide_constants_are_decoded(self) -> None:
        builder = ClassBuilder("mod/a/Switches")
        wide_value = builder.long_constant(7)
        text = builder.string("net/minecraft/client/Minecraft")
        target = builder.class_ref("mod/a/Switches$Target")
        builder.inner_classes(("mod/a/Switches$Target", "mod/a/Switches"))
        code = bytearray()
        code += b"\x14" + u2(wide_value) + b"\x58"        # ldc2_w, pop2
        code += b"\x12" + bytes([text]) + b"\x57"         # ldc of a string, pop
        code += b"\xc4\x84" + u2(300) + u2(1)             # wide iinc
        code += b"\x03\xaa"                               # iconst_0, tableswitch
        code += b"\x00" * ((4 - len(code) % 4) % 4)
        code += struct.pack(">iii", 0, 0, 1) + struct.pack(">ii", 0, 0)
        code += b"\x03\xab"                               # iconst_0, lookupswitch
        code += b"\x00" * ((4 - len(code) % 4) % 4)
        code += struct.pack(">ii", 0, 1) + struct.pack(">ii", 5, 0)
        code += b"\xbb" + u2(target) + b"\x57" + RETURN   # new, pop, return
        builder.method("decode", "()V", bytes(code))

        found = references(builder.build())
        self.assertIn("mod/a/Switches$Target", found)
        self.assertNotIn(MINECRAFT, found)

    def test_method_type_is_a_reference_and_a_generic_signature_is_not(self) -> None:
        builder = ClassBuilder("mod/a/Screens")
        builder.method_type("(Lmod/a/Menu;)Lnet/minecraft/client/gui/screens/Screen;")
        builder.method("register", "(Ljava/util/function/Consumer;)V", RETURN,
                       signature="(Ljava/util/function/Consumer<Lnet/minecraftforge/client/Ext;>;)V")

        found = references(builder.build())
        self.assertIn("net/minecraft/client/gui/screens/Screen", found)
        self.assertIn("mod/a/Menu", found)
        self.assertNotIn("net/minecraftforge/client/Ext", found)

    def test_malformed_class_files_are_rejected(self) -> None:
        valid = calling("mod/a/Valid", "mod/a/Other")
        unknown_opcode = ClassBuilder("mod/a/Bad")
        unknown_opcode.method("bad", "()V", b"\xfe")
        cases = {
            "magic": b"\x00" + valid[1:],
            "truncated": valid[:-3],
            "trailing": valid + b"\x00",
            "opcode": unknown_opcode.build(),
            "version": ClassBuilder("mod/a/Future").build(major=99),
        }
        for label, data in cases.items():
            with self.subTest(label):
                with self.assertRaises(checker.ClassFormatError):
                    checker.parse_class(data)


class AllowlistTests(unittest.TestCase):
    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.path = Path(directory.name) / "allowlist.txt"

    def read(self, text: str) -> list[checker.AllowEntry]:
        self.path.write_text(text, encoding="utf-8")
        return checker.read_allowlist(self.path)

    def test_entries_need_a_reason_and_valid_unique_patterns(self) -> None:
        cases = {
            "no reason": "mod/a/Packet mod/client/Handler\n",
            "duplicate": "mod/a/Packet mod/client/Handler first\nmod/a/Packet mod/client/Handler second\n",
            "client source": "mod/client/Screen net/minecraft/client/Minecraft not needed\n",
            "client source pattern": "mod/client/* net/minecraft/client/Minecraft not needed\n",
            "dotted name": "mod.a.Packet mod/client/Handler dotted\n",
            "glob in the middle": "mod/*/Packet mod/client/Handler glob\n",
        }
        for label, text in cases.items():
            with self.subTest(label):
                with self.assertRaises(checker.AllowlistError):
                    self.read(text)

    def test_comments_blank_lines_and_patterns_are_read(self) -> None:
        entries = self.read("# reviewed\n\nmod/a/gen/* net/forge/client/gen/** Data runs only.\n")

        self.assertEqual([checker.AllowEntry(3, "mod/a/gen/*", "net/forge/client/gen/**", "Data runs only.")],
                         entries)
        entry = entries[0]
        self.assertTrue(entry.matches("mod/a/gen/Models$Inner", "net/forge/client/gen/sub/Builder"))
        self.assertFalse(entry.matches("mod/a/gen/sub/Other", "net/forge/client/gen/Builder"))
        self.assertFalse(entry.matches("mod/a/General", "net/forge/client/gen/Builder"))

    def test_committed_allowlist_names_existing_sources(self) -> None:
        entries = checker.read_allowlist(checker.DEFAULT_ALLOWLIST)
        self.assertTrue(entries)
        sources = REPOSITORY / "src" / "main" / "java"
        for entry in entries:
            with self.subTest(entry.source):
                if entry.source.endswith("*"):
                    self.assertTrue((sources / entry.source.rsplit("/", 1)[0]).is_dir())
                else:
                    self.assertTrue((sources / (entry.source.split("$", 1)[0] + ".java")).is_file())
                self.assertGreater(len(entry.reason.split()), 5)


class ScanTests(unittest.TestCase):
    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.allowlist = self.root / "allowlist.txt"
        self.allowlist.write_text("", encoding="utf-8")

    def jar(self, classes: dict[str, bytes], extra: dict[str, bytes] | None = None) -> Path:
        path = self.root / "mod.jar"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in classes.items():
                archive.writestr(name + ".class", data)
            for name, data in (extra or {}).items():
                archive.writestr(name, data)
        return path

    def scan(self, classes: dict[str, bytes], allowlist: str = "") -> checker.ScanResult:
        self.allowlist.write_text(allowlist, encoding="utf-8")
        parsed, errors = checker.read_jar(self.jar(classes))
        self.assertEqual([], errors)
        return checker.scan(parsed, checker.read_allowlist(self.allowlist))

    def run_main(self, *arguments: str) -> tuple[int, str]:
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = checker.main(list(arguments))
        return code, output.getvalue()

    def test_direct_reference_from_common_code_fails(self) -> None:
        result = self.scan({"mod/rocket/RocketLogic": calling("mod/rocket/RocketLogic", MINECRAFT)})

        self.assertFalse(result.passed)
        self.assertEqual([{"source": "mod/rocket/RocketLogic", "target": MINECRAFT, "kind": "direct",
                           "chain": [MINECRAFT]}], result.violations)
        self.assertEqual(["mod/rocket/RocketLogic"], result.client_bound)

    def test_transitive_references_through_mod_classes_fail(self) -> None:
        result = self.scan({
            "mod/rocket/RocketLogic": calling("mod/rocket/RocketLogic", MINECRAFT),
            "mod/net/Channel": calling("mod/net/Channel", "mod/rocket/RocketLogic"),
            "mod/net/HudPacket": calling("mod/net/HudPacket", "mod/client/HudRenderer"),
            "mod/client/HudRenderer": calling("mod/client/HudRenderer", "mod/client/Batch"),
            "mod/client/Batch": calling("mod/client/Batch", RENDER_SYSTEM),
        })

        rows = {(row["source"], row["kind"], tuple(row["chain"])) for row in result.violations}
        self.assertEqual({
            ("mod/rocket/RocketLogic", "direct", (MINECRAFT,)),
            ("mod/net/Channel", "transitive", ("mod/rocket/RocketLogic", MINECRAFT)),
            ("mod/net/HudPacket", "transitive", ("mod/client/HudRenderer", "mod/client/Batch", RENDER_SYSTEM)),
        }, rows)

    def test_allowlisted_dist_executor_site_passes_and_does_not_propagate(self) -> None:
        packet = ClassBuilder("mod/net/VisorPacket")
        packet.calls("net/minecraftforge/fml/DistExecutor", "unsafeRunWhenOn",
                     "(Lnet/minecraftforge/api/distmarker/Dist;Ljava/util/function/Supplier;)V", in_method="handle")
        packet.calls("mod/client/VisorClient", "authorize", "(Z)V", in_method="lambda$handle$0")
        reason = "handle authorizes the visor through DistExecutor.unsafeRunWhenOn(Dist.CLIENT)."
        result = self.scan({
            "mod/net/VisorPacket": packet.build(),
            "mod/net/Network": calling("mod/net/Network", "mod/net/VisorPacket"),
            "mod/client/VisorClient": calling("mod/client/VisorClient", MINECRAFT),
        }, f"mod/net/VisorPacket mod/client/VisorClient {reason}\n")

        self.assertTrue(result.passed, result.violations)
        self.assertEqual([{"source": "mod/net/VisorPacket", "target": "mod/client/VisorClient", "kind": "transitive",
                           "chain": ["mod/client/VisorClient", MINECRAFT], "allowlist_line": 1,
                           "reason": reason}], result.allowed)
        self.assertEqual(["mod/client/VisorClient"], result.client_bound)

    def test_data_only_client_package_class_passes(self) -> None:
        layout = ClassBuilder("mod/client/HudLayout$Anchor", "java/lang/Enum")
        layout.method("values", "()[Lmod/client/HudLayout$Anchor;", RETURN)
        result = self.scan({
            "mod/config/ClientConfig": calling("mod/config/ClientConfig", "mod/client/HudLayout$Anchor"),
            "mod/client/HudLayout$Anchor": layout.build(),
        })

        self.assertTrue(result.passed)
        self.assertEqual([], result.client_bound)
        self.assertEqual([{"source": "mod/config/ClientConfig", "target": "mod/client/HudLayout$Anchor"}],
                         result.data_only_references)

    def test_client_only_class_inside_the_jar_is_a_root(self) -> None:
        result = self.scan({
            "net/minecraft/client/Shaded": ClassBuilder("net/minecraft/client/Shaded").build(),
            "mod/a/User": calling("mod/a/User", "net/minecraft/client/Shaded"),
            "mod/a/Caller": calling("mod/a/Caller", "mod/a/User"),
        })

        self.assertEqual({("mod/a/User", "direct"), ("mod/a/Caller", "transitive")},
                         {(row["source"], row["kind"]) for row in result.violations})

    def test_unused_allowlist_entry_fails(self) -> None:
        result = self.scan({"mod/a/Plain": calling("mod/a/Plain", "mod/a/Other")},
                           "mod/a/Plain mod/client/Gone The site was removed from the source tree.\n")

        self.assertFalse(result.passed)
        self.assertEqual([{"line": 1, "source": "mod/a/Plain", "target": "mod/client/Gone"}],
                         result.unused_allowlist)

    def test_main_reports_and_writes_json(self) -> None:
        jar = self.jar({
            "mod/net/VisorPacket": calling("mod/net/VisorPacket", "mod/client/VisorClient"),
            "mod/client/VisorClient": calling("mod/client/VisorClient", MINECRAFT),
        }, {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n", "assets/mod/lang/en_us.json": b"{}"})
        report = self.root / "out" / "client-bytecode.json"

        code, output = self.run_main(str(jar), "--allowlist", str(self.allowlist), "--report", str(report))
        self.assertEqual(1, code)
        self.assertIn("[FAIL] Transitive client reference: mod/net/VisorPacket -> mod/client/VisorClient -> "
                      + MINECRAFT, output)
        self.assertFalse(json.loads(report.read_text(encoding="utf-8"))["passed"])

        self.allowlist.write_text("mod/net/VisorPacket mod/client/VisorClient Reviewed: the packet is S2C only.\n",
                                  encoding="utf-8")
        code, output = self.run_main(str(jar), "--allowlist", str(self.allowlist), "--report", str(report))
        self.assertEqual(0, code)
        self.assertIn("[ALLOW] mod/net/VisorPacket -> mod/client/VisorClient (line 1)", output)
        self.assertIn("[PASS]", output)
        data = json.loads(report.read_text(encoding="utf-8"))
        self.assertTrue(data["passed"])
        self.assertEqual(2, data["classes"])
        self.assertEqual(checker.sha256(jar), data["sha256"])

    def test_unreadable_or_misplaced_classes_fail(self) -> None:
        valid = calling("mod/a/Real", "mod/a/Other")
        jar = self.jar({"mod/a/Moved": valid, "mod/a/Broken": b"\xca\xfe"})

        code, output = self.run_main(str(jar), "--allowlist", str(self.allowlist))
        self.assertEqual(1, code)
        self.assertIn("[FAIL] Unreadable class: mod/a/Moved.class: declares class mod/a/Real", output)
        self.assertIn("[FAIL] Unreadable class: mod/a/Broken.class: truncated class file", output)

    def test_missing_jar_and_bad_allowlist_fail(self) -> None:
        code, output = self.run_main(str(self.root / "missing.jar"), "--allowlist", str(self.allowlist))
        self.assertEqual(1, code)
        self.assertIn("[FAIL] Cannot read", output)

        self.allowlist.write_text("mod/a/Packet\n", encoding="utf-8")
        code, output = self.run_main(str(self.jar({})), "--allowlist", str(self.allowlist))
        self.assertEqual(1, code)
        self.assertIn("[FAIL] Allowlist:", output)


if __name__ == "__main__":
    unittest.main()
