"""Exercise launch flags and USB forwarding ownership without a phone or Minecraft."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

RUNNER = Path(__file__).resolve().parents[1] / "run.sh"


class RunScriptTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="craftlink run ")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / "scripts").mkdir()
        shutil.copyfile(RUNNER, self.root / "scripts/run.sh")
        (self.root / "web/node_modules").mkdir(parents=True)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        for tool in ("java", "node", "npm"):
            self.executable(self.bin / tool, "#!/bin/sh\nexit 0\n")
        self.executable(self.root / "gradlew", '#!/bin/sh\nprintf "%s\\n" "$@" > "$RUN_ARGS_LOG"\nexit "${FAKE_RUN_EXIT:-0}"\n')
        self.executable(self.bin / "adb", '''#!/bin/sh
printf '%s\n' "$*" >> "$ADB_CALLS_LOG"
if [ "$1" = '-d' ]; then printf 'test-usb-device\n'; exit 0; fi
if [ "$4" = '--list' ]; then printf '%s\n' "${ADB_EXISTING:-}"; fi
''')
        self.env = dict(os.environ, PATH=str(self.bin) + os.pathsep + os.environ["PATH"],
                        RUN_ARGS_LOG=str(self.root / "args"), ADB_CALLS_LOG=str(self.root / "adb_calls"))

    def executable(self, path, source):
        path.write_text(source)
        path.chmod(0o755)

    def launch(self, *args, **env):
        return subprocess.run(["bash", str(self.root / "scripts/run.sh"), *args],
                              cwd="/tmp", env=dict(self.env, **env), capture_output=True, text=True)

    def test_normal_offline_run_passes_flags_and_never_uses_adb(self):
        result = self.launch("--offline")
        self.assertEqual(0, result.returncode, result.stderr)
        args = (self.root / "args").read_text().splitlines()
        self.assertIn("runClient", args)
        self.assertIn("-PcraftlinkTransport=lan", args)
        self.assertIn("--offline", args)
        self.assertIn("-PwebOffline=true", args)
        self.assertFalse((self.root / "adb_calls").exists())

    def test_usb_forward_is_cleaned_after_failed_launch(self):
        result = self.launch("usb", "--port", "9876", FAKE_RUN_EXIT="7")
        self.assertEqual(7, result.returncode)
        calls = (self.root / "adb_calls").read_text()
        self.assertIn("reverse --no-rebind tcp:9876 tcp:9876", calls)
        self.assertIn("reverse --remove tcp:9876", calls)
        args = (self.root / "args").read_text()
        self.assertIn("-PcraftlinkTransport=usb", args)
        self.assertIn("-PcraftlinkPort=9876", args)

    def test_existing_matching_forward_is_reused_and_retained(self):
        result = self.launch("usb", ADB_EXISTING="UsbFfs tcp:8765 tcp:8765")
        self.assertEqual(0, result.returncode, result.stderr)
        calls = (self.root / "adb_calls").read_text()
        self.assertNotIn("--no-rebind", calls)
        self.assertNotIn("--remove", calls)

    def test_conflicting_forward_is_not_overwritten(self):
        result = self.launch("usb", ADB_EXISTING="UsbFfs tcp:8765 tcp:9999")
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.root / "args").exists())
        calls = (self.root / "adb_calls").read_text()
        self.assertNotIn("--no-rebind", calls)
        self.assertNotIn("--remove", calls)

    def test_prepare_usb_needs_no_phone(self):
        result = self.launch("usb", "--prepare")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("prepareRunClient", (self.root / "args").read_text())
        self.assertIn("classes", (self.root / "args").read_text())
        self.assertFalse((self.root / "adb_calls").exists())

    def test_invalid_port_stops_before_any_launch(self):
        result = self.launch("usb", "--port", "65536")
        self.assertEqual(2, result.returncode)
        self.assertFalse((self.root / "args").exists())
        self.assertFalse((self.root / "adb_calls").exists())


if __name__ == "__main__":
    unittest.main()
