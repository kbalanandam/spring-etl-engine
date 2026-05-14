import importlib.util
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).resolve().parents[1] / "sync_project_board.py"
SPEC = importlib.util.spec_from_file_location("sync_project_board", MODULE_PATH)
sync_project_board = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = sync_project_board
SPEC.loader.exec_module(sync_project_board)


class SyncProjectBoardTests(unittest.TestCase):
    def test_filter_graphql_errors_ignores_expected_owner_not_found(self) -> None:
        errors = [
            {
                "type": "NOT_FOUND",
                "path": ["organization"],
                "message": "Could not resolve to an Organization.",
            },
            {
                "type": "NOT_FOUND",
                "path": ["user"],
                "message": "Could not resolve to a User.",
            },
        ]

        filtered = sync_project_board.filter_graphql_errors(errors, {"user", "organization"})

        self.assertEqual([], filtered)

    def test_filter_graphql_errors_keeps_non_tolerated_errors(self) -> None:
        errors = [
            {
                "type": "FORBIDDEN",
                "path": ["user", "projectV2"],
                "message": "Resource not accessible.",
            }
        ]

        filtered = sync_project_board.filter_graphql_errors(errors, {"user", "organization"})

        self.assertEqual(errors, filtered)

    def test_parse_backlog_items_reads_current_execution_board(self) -> None:
        markdown = textwrap.dedent(
            """
            # Product Backlog

            ## Current Execution Board

            | ID | Item | Epic | Priority | Status | Milestone | Dependency | Notes |
            |---|---|---|---|---|---|---|---|
            | [A4](backlog-items/A4.md) | Standardize generated-model naming and package derivation | Epic A | P1 | In Progress | M2 | A2 | Remaining work lives in [`Generated model naming standard`](../architecture/generated-model-naming-standard.md) |
            | T3 | Add conditional transformation rule support | Epic T | P1 | Deferred | M2 | T2 | Best introduced after expression contract is stable |

            ### Current working focus
            """
        ).strip()

        items = sync_project_board.parse_backlog_items(markdown)

        self.assertEqual(2, len(items))
        self.assertEqual("A4", items[0].backlog_id)
        self.assertEqual("backlog-items/A4.md", items[0].id_link)
        self.assertEqual("Standardize generated-model naming and package derivation", items[0].item)
        self.assertEqual("In Progress", items[0].status)
        self.assertEqual("T3", items[1].backlog_id)
        self.assertEqual("Deferred", items[1].status)

    def test_build_project_body_omits_internal_notes_in_public_mode(self) -> None:
        item = sync_project_board.BacklogItem(
            backlog_id="A4",
            id_link="backlog-items/A4.md",
            item="Standardize generated-model naming and package derivation",
            epic="Epic A",
            priority="P1",
            status="In Progress",
            milestone="M2",
            dependency="A2",
            notes="Internal note with [`private-link`](../architecture/generated-model-naming-standard.md).",
        )

        public_body = sync_project_board.build_project_body(item, public_mode=True)
        private_body = sync_project_board.build_project_body(item, public_mode=False)

        self.assertIn("<!-- backlog-sync-id:A4 -->", public_body)
        self.assertIn("sanitized public projection", public_body)
        self.assertNotIn("## Notes", public_body)
        self.assertNotIn("Detail page", public_body)
        self.assertIn("## Notes", private_body)
        self.assertIn("Detail page", private_body)

    def test_cli_dry_run_parses_workspace_file_without_token(self) -> None:
        markdown = textwrap.dedent(
            """
            # Product Backlog

            ## Current Execution Board

            | ID | Item | Epic | Priority | Status | Milestone | Dependency | Notes |
            |---|---|---|---|---|---|---|---|
            | E2 | Add packaged-run guidance for jar execution with scenario configs | Epic E | P1 | Ready | M1 | E1 | Important next portability step |
            """
        ).strip()

        with tempfile.TemporaryDirectory() as temp_dir:
            backlog_path = Path(temp_dir) / "product-backlog.md"
            backlog_path.write_text(markdown, encoding="utf-8")

            exit_code = sync_project_board.main(["--backlog-file", str(backlog_path), "--dry-run"])

        self.assertEqual(0, exit_code)


if __name__ == "__main__":
    unittest.main()

