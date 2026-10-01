import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch, MagicMock
import subprocess

spec = importlib.util.spec_from_file_location("converter_server", Path(__file__).with_name("server.py"))
server = importlib.util.module_from_spec(spec)
spec.loader.exec_module(server)


class ConversionTest(unittest.TestCase):
    def test_timeout_kills_process_group_and_cleans_work_directory(self):
        process = MagicMock(pid=123)
        process.wait.side_effect = [subprocess.TimeoutExpired("test", 120), 0]
        captured = {}

        def launch(command, **kwargs):
            captured["work"] = kwargs["cwd"]
            captured["command"] = command
            self.assertTrue(kwargs["start_new_session"])
            return process

        with patch.object(server.subprocess, "Popen", side_effect=launch), \
                patch.object(server.os, "killpg", create=True) as kill, \
                patch.object(server.signal, "SIGKILL", 9, create=True):
            with self.assertRaises(TimeoutError):
                server.convert(b"legacy-input", "doc")
            kill.assert_called_once_with(123, 9)
        self.assertFalse(captured["work"].exists())

    def test_success_requires_output_and_cleans_profile(self):
        process = MagicMock()
        process.wait.return_value = 0
        captured = {}

        def launch(command, **kwargs):
            captured["work"] = kwargs["cwd"]
            Path(command[3]).write_bytes(b"converted-output")
            policy = (kwargs["cwd"] / "profile/user/registrymodifications.xcu").read_text()
            self.assertIn("DisableMacrosExecution", policy)
            return process

        with patch.object(server.subprocess, "Popen", side_effect=launch):
            self.assertEqual(server.convert(b"legacy-input", "xls"), b"converted-output")
        self.assertFalse(captured["work"].exists())

    def test_no_output_is_failure(self):
        process = MagicMock()
        process.wait.return_value = 0
        with patch.object(server.subprocess, "Popen", return_value=process):
            with self.assertRaises(ValueError):
                server.convert(b"legacy-input", "doc")


if __name__ == "__main__":
    unittest.main()
