import os
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "verify-recent-changes.ps1"


@unittest.skipUnless(os.name == "nt", "Windows-only PowerShell smoke script test")
class VerifyRecentChangesTimeoutTests(unittest.TestCase):
    def test_forced_timeout_exits_with_124(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            repo_root = Path(temp_dir)
            env = os.environ.copy()
            env["ETL_VERIFY_RECENT_CHANGES_TEST_FORCE_TIMEOUT"] = "1"

            result = subprocess.run(
                [
                    "powershell.exe",
                    "-ExecutionPolicy",
                    "Bypass",
                    "-File",
                    str(SCRIPT_PATH),
                    "-RepoRoot",
                    str(repo_root),
                    "-ScenarioTimeoutMinutes",
                    "1",
                ],
                env=env,
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(
                124,
                result.returncode,
                msg=(
                    "Expected timeout exit code 124.\n"
                    f"stdout:\n{result.stdout}\n"
                    f"stderr:\n{result.stderr}"
                ),
            )
            self.assertIn("TIMED_OUT:", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()

