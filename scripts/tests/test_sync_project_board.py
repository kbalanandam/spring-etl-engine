import importlib.util
import io
import os
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path
from contextlib import redirect_stdout


MODULE_PATH = Path(__file__).resolve().parents[1] / "sync_project_board.py"
SPEC = importlib.util.spec_from_file_location("sync_project_board", MODULE_PATH)
sync_project_board = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = sync_project_board
SPEC.loader.exec_module(sync_project_board)


class SyncProjectBoardTests(unittest.TestCase):
    @staticmethod
    def make_item(backlog_id: str) -> object:
        return sync_project_board.BacklogItem(
            backlog_id=backlog_id,
            id_link=None,
            item=f"Item {backlog_id}",
            epic="Epic A",
            priority="P1",
            status="Ready",
            milestone="M1",
            dependency="none",
            notes="",
        )

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

    def test_build_field_bindings_warns_once_for_unsupported_builtin_milestone(self) -> None:
        field_mapping = {
            "Status": (("Status",), lambda item: item.status),
            "Milestone": (("Milestone", "Execution Milestone"), lambda item: item.milestone),
        }
        fields = {
            "Status": sync_project_board.ProjectField(
                field_id="field-status",
                name="Status",
                data_type="TEXT",
                options_by_name={},
            ),
            "Milestone": sync_project_board.ProjectField(
                field_id="field-milestone",
                name="Milestone",
                data_type="MILESTONE",
                options_by_name={},
            ),
        }

        output = io.StringIO()
        with redirect_stdout(output):
            bindings = sync_project_board.build_field_bindings(fields, field_mapping)

        self.assertEqual(["Status"], [binding.logical_name for binding in bindings])
        self.assertEqual(1, output.getvalue().count("unsupported type 'MILESTONE'"))

    def test_sync_items_uses_execution_milestone_fallback_without_repeated_warning(self) -> None:
        item_one = self.make_item("A1")
        item_two = self.make_item("A2")

        class FakeClient:
            def get_project(self) -> tuple[str, dict[str, object], dict[str, object]]:
                fields = {
                    "Status": sync_project_board.ProjectField(
                        field_id="field-status",
                        name="Status",
                        data_type="TEXT",
                        options_by_name={},
                    ),
                    "Priority": sync_project_board.ProjectField(
                        field_id="field-priority",
                        name="Priority",
                        data_type="TEXT",
                        options_by_name={},
                    ),
                    "Epic": sync_project_board.ProjectField(
                        field_id="field-epic",
                        name="Epic",
                        data_type="TEXT",
                        options_by_name={},
                    ),
                    "Milestone": sync_project_board.ProjectField(
                        field_id="field-milestone-built-in",
                        name="Milestone",
                        data_type="MILESTONE",
                        options_by_name={},
                    ),
                    "Execution Milestone": sync_project_board.ProjectField(
                        field_id="field-execution-milestone",
                        name="Execution Milestone",
                        data_type="TEXT",
                        options_by_name={},
                    ),
                    "Dependency": sync_project_board.ProjectField(
                        field_id="field-dependency",
                        name="Dependency",
                        data_type="TEXT",
                        options_by_name={},
                    ),
                }
                existing_items = {
                    "A1": sync_project_board.ExistingProjectItem(
                        item_id="item-a1",
                        content_id="draft-a1",
                        content_type="DraftIssue",
                        title=item_one.project_title,
                        body=sync_project_board.build_project_body(item_one, public_mode=False),
                    ),
                    "A2": sync_project_board.ExistingProjectItem(
                        item_id="item-a2",
                        content_id="draft-a2",
                        content_type="DraftIssue",
                        title=item_two.project_title,
                        body=sync_project_board.build_project_body(item_two, public_mode=False),
                    ),
                }
                return "project-1", fields, existing_items

        output = io.StringIO()
        with redirect_stdout(output):
            actions = sync_project_board.sync_items(FakeClient(), [item_one, item_two], dry_run=True, public_mode=False)

        rendered = output.getvalue()
        self.assertEqual(10, actions)
        self.assertEqual(1, rendered.count("using supported fallback field 'Execution Milestone'"))
        self.assertEqual(0, rendered.count("create a custom text or single-select field named 'Execution Milestone'"))
        self.assertIn("FIELD A1: Execution Milestone = M1", rendered)
        self.assertIn("FIELD A2: Execution Milestone = M1", rendered)

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

    def test_resolve_detail_page_target_builds_absolute_repository_blob_url(self) -> None:
        item = sync_project_board.BacklogItem(
            backlog_id="A6",
            id_link="backlog-items/A6-retire-internal-generated-model-package-bridge.md",
            item="Retire remaining internal generated-model package bridge",
            epic="Epic A",
            priority="P2",
            status="Deferred",
            milestone="M2",
            dependency="A4",
            notes="",
        )

        label, target = sync_project_board.resolve_detail_page_target(
            item,
            backlog_source_path="docs/product/product-backlog.md",
            repository_url="https://github.com/kbalanandam/spring-etl-engine",
            repository_ref="master",
        )

        self.assertEqual("docs/product/backlog-items/A6-retire-internal-generated-model-package-bridge.md", label)
        self.assertEqual(
            "https://github.com/kbalanandam/spring-etl-engine/blob/master/"
            "docs/product/backlog-items/A6-retire-internal-generated-model-package-bridge.md",
            target,
        )

    def test_resolve_epic_page_target_builds_absolute_repository_blob_url(self) -> None:
        item = sync_project_board.BacklogItem(
            backlog_id="F1",
            id_link="backlog-items/F1-restart-semantics-per-execution-mode.md",
            item="Define restart semantics per execution mode",
            epic="Epic F",
            priority="P1",
            status="Deferred",
            milestone="M2",
            dependency="A1, C1",
            notes="",
        )

        label, target = sync_project_board.resolve_epic_page_target(
            item,
            repository_url="https://github.com/kbalanandam/spring-etl-engine",
            repository_ref="master",
        )

        self.assertEqual("Epic F", label)
        self.assertEqual(
            "https://github.com/kbalanandam/spring-etl-engine/blob/master/"
            "docs/product/epics/epic-f-restartability-and-recovery-semantics.md",
            target,
        )

    def test_resolve_epic_page_target_returns_none_for_unknown_epic(self) -> None:
        item = sync_project_board.BacklogItem(
            backlog_id="Z1",
            id_link=None,
            item="Unknown item",
            epic="Epic Z",
            priority="P1",
            status="Ready",
            milestone="M1",
            dependency="none",
            notes="",
        )

        self.assertIsNone(
            sync_project_board.resolve_epic_page_target(
                item,
                repository_url="https://github.com/kbalanandam/spring-etl-engine",
                repository_ref="master",
            )
        )

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
        private_body = sync_project_board.build_project_body(
            item,
            public_mode=False,
            repository_url="https://github.com/kbalanandam/spring-etl-engine",
            repository_ref="master",
        )

        self.assertIn("<!-- backlog-sync-id:A4 -->", public_body)
        self.assertIn("sanitized public projection", public_body)
        self.assertNotIn("## Notes", public_body)
        self.assertNotIn("Detail page", public_body)
        self.assertNotIn("[Epic A]", public_body)
        self.assertIn("## Notes", private_body)
        self.assertIn(
            "- Epic: [Epic A](https://github.com/kbalanandam/spring-etl-engine/blob/master/docs/product/epics/epic-a-runtime-contract-and-model-governance.md)",
            private_body,
        )
        self.assertIn(
            "- Detail page: [docs/product/backlog-items/A4.md](https://github.com/kbalanandam/spring-etl-engine/blob/master/docs/product/backlog-items/A4.md)",
            private_body,
        )

    def test_resolve_repository_url_uses_github_actions_environment_by_default(self) -> None:
        original_server = os.environ.get("GITHUB_SERVER_URL")
        original_repository = os.environ.get("GITHUB_REPOSITORY")
        try:
            os.environ["GITHUB_SERVER_URL"] = "https://github.com"
            os.environ["GITHUB_REPOSITORY"] = "kbalanandam/spring-etl-engine"

            repository_url = sync_project_board.resolve_repository_url(None)

            self.assertEqual("https://github.com/kbalanandam/spring-etl-engine", repository_url)
        finally:
            if original_server is None:
                os.environ.pop("GITHUB_SERVER_URL", None)
            else:
                os.environ["GITHUB_SERVER_URL"] = original_server
            if original_repository is None:
                os.environ.pop("GITHUB_REPOSITORY", None)
            else:
                os.environ["GITHUB_REPOSITORY"] = original_repository

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

