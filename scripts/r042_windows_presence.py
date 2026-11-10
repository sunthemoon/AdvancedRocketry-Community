"""Harness-only reversible Windows UNKNOWN-presence trigger (W340's delete-pending probe).

The disposition is always cleared before closing the retained handle. No ACL or production seam is changed.
"""
from contextlib import contextmanager
import ctypes as c
from ctypes import wintypes as w
import os
from pathlib import Path
import subprocess

from run_v180_escrow_forced_stop import digest
from run_dedicated_server_smoke import SmokeError, is_link_or_junction


class WindowsDisposition:
    def __init__(self):
        if os.name != "nt":
            raise SmokeError("The UNKNOWN-presence fixture requires Windows")
        self.kernel = c.WinDLL("kernel32", use_last_error=True)
        self.kernel.CreateFileW.argtypes = [w.LPCWSTR, w.DWORD, w.DWORD, c.c_void_p, w.DWORD, w.DWORD, w.HANDLE]
        self.kernel.CreateFileW.restype = w.HANDLE
        self.kernel.SetFileInformationByHandle.argtypes = [w.HANDLE, c.c_int, c.c_void_p, w.DWORD]
        self.kernel.SetFileInformationByHandle.restype = w.BOOL
        self.kernel.CloseHandle.argtypes = [w.HANDLE]
        self.kernel.CloseHandle.restype = w.BOOL

    def open(self, file):
        handle = self.kernel.CreateFileW(str(file), 0x10000 | 0x80, 7, None, 3, 0, None)
        if handle == w.HANDLE(-1).value:
            raise c.WinError(c.get_last_error())
        return handle

    def disposition(self, handle, pending):
        value = w.BYTE(pending)
        if not self.kernel.SetFileInformationByHandle(handle, 4, c.byref(value), c.sizeof(value)):
            raise c.WinError(c.get_last_error())

    def close(self, handle):
        if not self.kernel.CloseHandle(handle):
            raise c.WinError(c.get_last_error())


@contextmanager
def delete_pending(file: Path, api=None):
    if is_link_or_junction(file) or not file.is_file():
        raise SmokeError(f"Not a regular fixture file: {file}")
    before = digest(file)
    api = api or WindowsDisposition()
    handle = api.open(file.resolve())
    try:
        api.disposition(handle, True)
        yield dict(file=str(file), sha256_before=before)
    finally:
        # Never close a still-delete-pending handle: clearing must succeed first.
        api.disposition(handle, False)
        api.close(handle)
        if digest(file) != before:
            raise SmokeError(f"Delete-pending restoration changed the fixture: {file}")


# Reflection keeps this harness source independent of mapped/SRG method names. It executes the pinned JAR's
# actual presence and migration methods, with the packaged server's libraries, not checkout build classes.
PROBE_SOURCE = r'''
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
class R042WindowsPresenceProbe {
    public static void main(String[] args) throws Exception {
        Path world = Path.of(args[0]), file = world.resolve("data").resolve(args[1]);
        Class<?> allow = Class.forName("net.minecraft.world.level.validation.PathAllowList");
        Object deny = allow.getConstructor(List.class).newInstance(List.of());
        Class<?> validator = Class.forName("net.minecraft.world.level.validation.DirectoryValidator");
        Object instance = validator.getConstructor(allow).newInstance(deny);
        Method validate = Arrays.stream(validator.getMethods()).filter(m ->
            Arrays.equals(m.getParameterTypes(), new Class<?>[]{Path.class, boolean.class}) &&
            List.class.isAssignableFrom(m.getReturnType())).findFirst().orElseThrow();
        System.out.println("VANILLA_VALIDATE=" + validate.invoke(instance, world, true));
        String base = "io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.";
        Class<?> checked = Class.forName(base + "CheckedSavedDataFile");
        System.out.println("ACTUAL_ARCE_PRESENCE=" + checked.getMethod("presence", Path.class).invoke(null, file));
        Class<?> service = Class.forName(base + "WorldDataMigrationService");
        try {
            System.out.println("MIGRATION=" + service.getMethod("migrate", Path.class)
                .invoke(service.getConstructor().newInstance(), world));
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            System.out.println("MIGRATION_REFUSED=" + cause);
            System.out.println("PRESENCE_UNKNOWN=" + cause.getClass().getMethod("isPresenceUnknown").invoke(cause));
            System.out.println("DATA_TYPE=" + cause.getClass().getMethod("dataType").invoke(cause));
            Object diagnostic = cause.getClass().getMethod("diagnosticId").invoke(cause);
            System.out.println("DIAGNOSTIC=" + diagnostic.getClass().getMethod("code").invoke(diagnostic));
        }
    }
}
'''


def probe(java: str, jar: Path, session: Path, world: Path, filename: str, log: Path) -> str:
    source = session / "R042WindowsPresenceProbe.java"
    source.write_text(PROBE_SOURCE, encoding="ascii")
    # Forge's installed srg server JAR precedes the installer/bundler and vanilla obfuscated copies.
    libraries = sorted((session / "libraries").rglob("*.jar"), key=lambda p: ("-srg.jar" not in p.name, str(p)))
    classpath = os.pathsep.join(str(p.resolve()) for p in [jar, *libraries])
    arguments = session / "presence-probe.args"
    arguments.write_text('\n'.join('"' + arg.replace('\\', '/') + '"' for arg in
        ["-cp", classpath, "--source", "17", str(source), str(world), filename]), encoding="utf-8")
    try:
        result = subprocess.run([java, "@" + str(arguments)], cwd=session, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", timeout=60)
    except subprocess.TimeoutExpired as failure:
        raise SmokeError("Presence probe exceeded 60 seconds") from failure
    log.write_text(result.stdout, encoding="utf-8")
    if result.returncode:
        raise SmokeError(f"Presence probe exited {result.returncode}: {log}")
    return result.stdout
