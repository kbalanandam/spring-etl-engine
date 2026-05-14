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

    def test_resolve_single_select_option_matches_normalized_status_value(self) -> None:
        field = sync_project_board.ProjectField(
            field_id="field-status",
            name="Status",
            data_type="SINGLE_SELECT",
            options_by_name={"In progress": "opt-in-progress"},
        )

        option_id, resolved_name = sync_project_board.resolve_single_select_option(field, "In Progress")

        self.assertEqual("opt-in-progress", option_id)
        self.assertEqual("In progress", resolved_name)

    def test_resolve_single_select_option_uses_status_aliases(self) -> None:
        field = sync_project_board.ProjectField(
            field_id="field-status",
            name="Status",
            data_type="SINGLE_SELECT",
            options_by_name={"Todo": "opt-todo"},
        )

        option_id, resolved_name = sync_project_board.resolve_single_select_option(field, "Ready")

        self.assertEqual("opt-todo", option_id)
        self.assertEqual("Todo", resolved_name)

    def test_resolve_single_select_option_reports_aliases_on_failure(self) -> None:
        field = sync_project_board.ProjectField(
            field_id="field-status",
            name="Status",
            data_type="SINGLE_SELECT",
            options_by_name={"Blocked": "opt-blocked"},
        )

        with self.assertRaises(RuntimeError) as context:
            sync_project_board.resolve_single_select_option(field, "In Progress")

        self.assertIn("Tried aliases", str(context.exception))
        self.assertIn("In progress", str(context.exception))

    def test_resolve_project_field_uses_supported_execution_milestone_alias(self) -> None:
        fields = {
            "Milestone": sync_project_board.ProjectField(
                field_id="field-milestone-built-in",
                name="Milestone",
                data_type="MILESTONE",
                options_by_name={},
            ),
            "Execution Milestone": sync_project_board.ProjectField(
                field_id="field-execution-milestone",
                name="Execution Milestone",
                data_type="SINGLE_SELECT",
                options_by_name={"M1": "opt-m1"},
            ),
        }

        resolved_name, field, unsupported_field = sync_project_board.resolve_project_field(
            fields,
            ("Milestone", "Execution Milestone"),
            supported_types=sync_project_board.SUPPORTED_PROJECT_FIELD_TYPES,
        )

        self.assertEqual("Execution Milestone", resolved_name)
        self.assertIsNotNone(field)
        self.assertEqual("SINGLE_SELECT", field.data_type)
        self.assertIsNotNone(unsupported_field)
        self.assertEqual("MILESTONE", unsupported_field.data_type)

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

