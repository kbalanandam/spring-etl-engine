#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from urllib.parse import urlparse
from pathlib import Path
from typing import Any

BACKLOG_SECTION_HEADING = "## Current Execution Board"
BACKLOG_SOURCE_PATH = "docs/product/product-backlog.md"
DEFAULT_REPOSITORY_REF = "master"
SYNC_MARKER_PATTERN = re.compile(r"<!--\s*backlog-sync-id:\s*([^\s]+)\s*-->")
MARKDOWN_LINK_PATTERN = re.compile(r"\[([^\]]+)\]\(([^)]+)\)")
CODE_SPAN_PATTERN = re.compile(r"`([^`]+)`")


@dataclass
class BacklogItem:
    backlog_id: str
    id_link: str | None
    item: str
    epic: str
    priority: str
    status: str
    milestone: str
    dependency: str
    notes: str

    @property
    def project_title(self) -> str:
        return f"{self.backlog_id} — {self.item}"


@dataclass
class ProjectField:
    field_id: str
    name: str
    data_type: str
    options_by_name: dict[str, str]


@dataclass
class ExistingProjectItem:
    item_id: str
    content_id: str | None
    content_type: str | None
    title: str
    body: str


@dataclass
class FieldBinding:
    logical_name: str
    resolved_name: str
    field: ProjectField
    value_getter: Any


SUPPORTED_PROJECT_FIELD_TYPES = {"SINGLE_SELECT", "TEXT"}

SINGLE_SELECT_OPTION_ALIASES: dict[str, dict[str, tuple[str, ...]]] = {
    "Status": {
        "Ready": ("Todo", "To do", "To-do"),
        "In Progress": ("In progress", "In-Progress", "InProgress"),
        "Done": ("Completed", "Complete"),
    }
}


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def resolve_repository_url(cli_value: str | None) -> str | None:
    candidate = (cli_value or os.environ.get("ONEFLOW_REPOSITORY_URL") or "").strip()
    if candidate:
        return candidate.rstrip("/")

    server_url = (os.environ.get("GITHUB_SERVER_URL") or "").strip().rstrip("/")
    repository = (os.environ.get("GITHUB_REPOSITORY") or "").strip().strip("/")
    if server_url and repository:
        return f"{server_url}/{repository}"
    return None


def resolve_repository_ref(cli_value: str | None) -> str:
    candidate = (cli_value or os.environ.get("ONEFLOW_REPOSITORY_REF") or os.environ.get("GITHUB_REF_NAME") or "").strip()
    return candidate or DEFAULT_REPOSITORY_REF


def is_absolute_url(value: str) -> bool:
    parsed = urlparse(value)
    return parsed.scheme in {"http", "https"} and bool(parsed.netloc)


def markdown_to_plain_text(value: str) -> str:
    value = MARKDOWN_LINK_PATTERN.sub(lambda match: match.group(1), value)
    value = CODE_SPAN_PATTERN.sub(lambda match: match.group(1), value)
    value = value.replace("**", "")
    value = value.replace("*", "")
    return re.sub(r"\s+", " ", value).strip()


def normalize_option_label(value: str) -> str:
    normalized = re.sub(r"[-_]+", " ", value.strip().lower())
    return re.sub(r"\s+", " ", normalized)


def resolve_single_select_option(field: ProjectField, value: str) -> tuple[str, str]:
    option_id = field.options_by_name.get(value)
    if option_id is not None:
        return option_id, value

    normalized_options: dict[str, list[tuple[str, str]]] = {}
    for option_name, existing_option_id in field.options_by_name.items():
        normalized_options.setdefault(normalize_option_label(option_name), []).append((option_name, existing_option_id))

    direct_matches = normalized_options.get(normalize_option_label(value), [])
    if len(direct_matches) == 1:
        option_name, matched_option_id = direct_matches[0]
        return matched_option_id, option_name
    if len(direct_matches) > 1:
        raise RuntimeError(
            f"Project field '{field.name}' has ambiguous normalized options for '{value}': "
            f"{sorted(option_name for option_name, _ in direct_matches)}"
        )

    alias_values = SINGLE_SELECT_OPTION_ALIASES.get(field.name, {}).get(value, ())
    alias_matches: list[tuple[str, str]] = []
    for alias_value in alias_values:
        alias_matches.extend(normalized_options.get(normalize_option_label(alias_value), []))

    if len(alias_matches) == 1:
        option_name, matched_option_id = alias_matches[0]
        return matched_option_id, option_name
    if len(alias_matches) > 1:
        raise RuntimeError(
            f"Project field '{field.name}' has ambiguous alias matches for '{value}': "
            f"{sorted(option_name for option_name, _ in alias_matches)}"
        )

    alias_hint = f" Tried aliases: {list(alias_values)}." if alias_values else ""
    raise RuntimeError(
        f"Project field '{field.name}' does not contain option '{value}'."
        f" Existing options: {sorted(field.options_by_name)}.{alias_hint}"
    )


def split_markdown_row(line: str, expected_columns: int | None = None) -> list[str]:
    stripped = line.strip()
    if not stripped.startswith("|"):
        raise ValueError(f"Not a Markdown table row: {line}")

    cells = [cell.strip() for cell in stripped.strip("|").split("|")]
    if expected_columns is not None and len(cells) > expected_columns:
        cells = cells[: expected_columns - 1] + [" | ".join(cells[expected_columns - 1 :]).strip()]
    return cells


def extract_backlog_table(markdown_text: str) -> list[str]:
    lines = markdown_text.splitlines()
    try:
        start_index = next(index for index, line in enumerate(lines) if line.strip() == BACKLOG_SECTION_HEADING)
    except StopIteration as exc:
        raise ValueError(f"Unable to find section heading '{BACKLOG_SECTION_HEADING}'.") from exc

    table_start = None
    for index in range(start_index + 1, len(lines)):
        if lines[index].strip().startswith("| ID |"):
            table_start = index
            break

    if table_start is None:
        raise ValueError("Unable to find execution-board table header after the section heading.")

    table_lines: list[str] = []
    for index in range(table_start, len(lines)):
        stripped = lines[index].strip()
        if not stripped.startswith("|"):
            break
        table_lines.append(lines[index])

    if len(table_lines) < 2:
        raise ValueError("Execution-board table is missing data rows.")

    return table_lines


def parse_backlog_items(markdown_text: str) -> list[BacklogItem]:
    table_lines = extract_backlog_table(markdown_text)
    header_cells = split_markdown_row(table_lines[0])
    expected_columns = len(header_cells)
    items: list[BacklogItem] = []

    for row in table_lines[2:]:
        cells = split_markdown_row(row, expected_columns=expected_columns)
        if len(cells) != expected_columns:
            raise ValueError(f"Unexpected column count in execution-board row: {row}")

        id_cell, item_cell, epic, priority, status, milestone, dependency, notes = cells
        link_match = MARKDOWN_LINK_PATTERN.fullmatch(id_cell)
        if link_match:
            backlog_id = link_match.group(1).strip()
            id_link = link_match.group(2).strip()
        else:
            backlog_id = markdown_to_plain_text(id_cell)
            id_link = None

        items.append(
            BacklogItem(
                backlog_id=backlog_id,
                id_link=id_link,
                item=markdown_to_plain_text(item_cell),
                epic=markdown_to_plain_text(epic),
                priority=markdown_to_plain_text(priority),
                status=markdown_to_plain_text(status),
                milestone=markdown_to_plain_text(milestone),
                dependency=markdown_to_plain_text(dependency),
                notes=notes.strip(),
            )
        )

    return items


def resolve_detail_page_target(
    item: BacklogItem,
    backlog_source_path: str,
    repository_url: str | None,
    repository_ref: str,
) -> tuple[str, str] | None:
    if not item.id_link:
        return None

    link_target = item.id_link.strip()
    if not link_target:
        return None

    if is_absolute_url(link_target):
        return link_target, link_target

    resolved_path = (Path(backlog_source_path).parent / link_target).resolve(strict=False)
    try:
        repo_relative_path = resolved_path.relative_to(Path.cwd().resolve())
    except ValueError:
        repo_relative_path = Path(backlog_source_path).parent / link_target

    repo_relative_posix = repo_relative_path.as_posix()
    if repository_url:
        return repo_relative_posix, f"{repository_url}/blob/{repository_ref}/{repo_relative_posix}"
    return repo_relative_posix, repo_relative_posix


def build_project_body(
    item: BacklogItem,
    public_mode: bool,
    backlog_source_path: str = BACKLOG_SOURCE_PATH,
    repository_url: str | None = None,
    repository_ref: str = DEFAULT_REPOSITORY_REF,
) -> str:
    lines = [
        f"<!-- backlog-sync-id:{item.backlog_id} -->",
        f"Synced from `{backlog_source_path}`.",
        "",
        f"- ID: `{item.backlog_id}`",
        f"- Epic: {item.epic}",
        f"- Priority: {item.priority}",
        f"- Status: {item.status}",
        f"- Milestone: {item.milestone}",
        f"- Dependency: {item.dependency}",
    ]

    if not public_mode:
        detail_page = resolve_detail_page_target(item, backlog_source_path, repository_url, repository_ref)
        if detail_page is not None:
            detail_label, detail_target = detail_page
            lines.extend(["", f"- Detail page: [{detail_label}]({detail_target})"])

    if not public_mode and item.notes:
        lines.extend(["", "## Notes", item.notes])

    if public_mode:
        lines.extend(["", "This project item is a sanitized public projection of the internal execution board."])

    return "\n".join(lines).strip() + "\n"


def extract_sync_marker(body: str) -> str | None:
    match = SYNC_MARKER_PATTERN.search(body or "")
    return match.group(1).strip() if match else None


def resolve_project_field(
    fields: dict[str, ProjectField],
    candidate_names: tuple[str, ...],
    supported_types: set[str] | None = None,
) -> tuple[str | None, ProjectField | None, ProjectField | None]:
    unsupported_match: ProjectField | None = None
    supported_types = supported_types or set()

    for name in candidate_names:
        field = fields.get(name)
        if field is None:
            continue
        if not supported_types or field.data_type in supported_types:
            return name, field, unsupported_match
        if unsupported_match is None:
            unsupported_match = field

    return None, None, unsupported_match


def build_field_bindings(
    fields: dict[str, ProjectField],
    field_mapping: dict[str, tuple[tuple[str, ...], Any]],
) -> list[FieldBinding]:
    bindings: list[FieldBinding] = []

    for logical_name, (candidate_names, value_getter) in field_mapping.items():
        resolved_name, field, unsupported_field = resolve_project_field(
            fields,
            candidate_names,
            supported_types=SUPPORTED_PROJECT_FIELD_TYPES,
        )
        if field is None:
            if unsupported_field is not None:
                if logical_name == "Milestone" and unsupported_field.data_type == "MILESTONE":
                    print(
                        "WARN  field 'Milestone': project field 'Milestone' uses unsupported type 'MILESTONE'; "
                        "create a custom text or single-select field named 'Execution Milestone' "
                        "(or replace the built-in field) and rerun."
                    )
                else:
                    print(
                        f"WARN  field '{logical_name}': project field '{unsupported_field.name}' uses unsupported type "
                        f"'{unsupported_field.data_type}'; values will be skipped."
                    )
            else:
                print(
                    f"WARN  field '{logical_name}': project field is missing; looked for {list(candidate_names)}; "
                    "values will be skipped."
                )
            continue

        if resolved_name != logical_name:
            if logical_name == "Milestone" and unsupported_field is not None and unsupported_field.data_type == "MILESTONE":
                print(
                    "INFO  field 'Milestone': built-in project field 'Milestone' uses unsupported type 'MILESTONE'; "
                    "using supported fallback field 'Execution Milestone'."
                )
            else:
                print(
                    f"INFO  field '{logical_name}': using project field '{resolved_name}'."
                )

        bindings.append(
            FieldBinding(
                logical_name=logical_name,
                resolved_name=resolved_name,
                field=field,
                value_getter=value_getter,
            )
        )

    return bindings


class GitHubProjectClient:
    def __init__(self, token: str, owner: str, project_number: int):
        self.token = token
        self.owner = owner
        self.project_number = project_number

    def graphql(
        self,
        query: str,
        variables: dict[str, Any],
        tolerated_not_found_roots: set[str] | None = None,
    ) -> dict[str, Any]:
        payload = json.dumps({"query": query, "variables": variables}).encode("utf-8")
        request = urllib.request.Request(
            url="https://api.github.com/graphql",
            data=payload,
            headers={
                "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json",
                "User-Agent": "spring-etl-engine-project-sync",
            },
            method="POST",
        )
        try:
            with urllib.request.urlopen(request) as response:
                parsed = json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            details = exc.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"GitHub GraphQL request failed with HTTP {exc.code}: {details}") from exc

        errors = filter_graphql_errors(parsed.get("errors") or [], tolerated_not_found_roots or set())
        if errors:
            raise RuntimeError(f"GitHub GraphQL returned errors: {json.dumps(errors, indent=2)}")

        return parsed["data"]

    def get_project(self) -> tuple[str, dict[str, ProjectField], dict[str, ExistingProjectItem]]:
        fields_query = """
query($login: String!, $number: Int!) {
  user(login: $login) {
    projectV2(number: $number) {
      id
      fields(first: 100) {
        nodes {
          __typename
          ... on ProjectV2FieldCommon {
            id
            name
            dataType
          }
          ... on ProjectV2SingleSelectField {
            options {
              id
              name
            }
          }
        }
      }
    }
  }
  organization(login: $login) {
    projectV2(number: $number) {
      id
      fields(first: 100) {
        nodes {
          __typename
          ... on ProjectV2FieldCommon {
            id
            name
            dataType
          }
          ... on ProjectV2SingleSelectField {
            options {
              id
              name
            }
          }
        }
      }
    }
  }
}
"""

        data = self.graphql(
            fields_query,
            {"login": self.owner, "number": self.project_number},
            tolerated_not_found_roots={"user", "organization"},
        )
        project = (data.get("user") or {}).get("projectV2") or (data.get("organization") or {}).get("projectV2")
        if project is None:
            raise RuntimeError(
                f"Unable to find project number {self.project_number} for owner '{self.owner}'."
            )

        fields: dict[str, ProjectField] = {}
        for node in project["fields"]["nodes"]:
            if not node:
                continue
            name = node["name"]
            options = {
                option["name"]: option["id"]
                for option in node.get("options", [])
                if option and option.get("name") and option.get("id")
            }
            fields[name] = ProjectField(
                field_id=node["id"],
                name=name,
                data_type=node["dataType"],
                options_by_name=options,
            )

        items: dict[str, ExistingProjectItem] = {}
        cursor: str | None = None
        items_query = """
query($login: String!, $number: Int!, $after: String) {
  user(login: $login) {
    projectV2(number: $number) {
      items(first: 100, after: $after) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          id
          content {
            __typename
            ... on DraftIssue {
              id
              title
              body
            }
            ... on Issue {
              id
              title
              body
            }
          }
        }
      }
    }
  }
  organization(login: $login) {
    projectV2(number: $number) {
      items(first: 100, after: $after) {
        pageInfo {
          hasNextPage
          endCursor
        }
        nodes {
          id
          content {
            __typename
            ... on DraftIssue {
              id
              title
              body
            }
            ... on Issue {
              id
              title
              body
            }
          }
        }
      }
    }
  }
}
"""

        while True:
            page = self.graphql(
                items_query,
                {"login": self.owner, "number": self.project_number, "after": cursor},
                tolerated_not_found_roots={"user", "organization"},
            )
            connection = ((page.get("user") or {}).get("projectV2") or (page.get("organization") or {}).get("projectV2"))["items"]
            for node in connection["nodes"]:
                if not node or not node.get("content"):
                    continue
                content = node["content"]
                marker = extract_sync_marker(content.get("body") or "")
                if marker:
                    items[marker] = ExistingProjectItem(
                        item_id=node["id"],
                        content_id=content.get("id"),
                        content_type=content.get("__typename"),
                        title=content.get("title") or "",
                        body=content.get("body") or "",
                    )

            if not connection["pageInfo"]["hasNextPage"]:
                break
            cursor = connection["pageInfo"]["endCursor"]

        return project["id"], fields, items

    def create_draft_item(self, project_id: str, title: str, body: str) -> ExistingProjectItem:
        mutation = """
mutation($projectId: ID!, $title: String!, $body: String!) {
  addProjectV2DraftIssue(input: {projectId: $projectId, title: $title, body: $body}) {
    projectItem {
      id
      content {
        __typename
        ... on DraftIssue {
          id
          title
          body
        }
      }
    }
  }
}
"""
        data = self.graphql(mutation, {"projectId": project_id, "title": title, "body": body})
        content = data["addProjectV2DraftIssue"]["projectItem"]["content"]
        return ExistingProjectItem(
            item_id=data["addProjectV2DraftIssue"]["projectItem"]["id"],
            content_id=content["id"],
            content_type=content["__typename"],
            title=content["title"],
            body=content["body"] or "",
        )

    def update_draft_issue(self, draft_issue_id: str, title: str, body: str) -> None:
        mutation = """
mutation($draftIssueId: ID!, $title: String!, $body: String!) {
  updateProjectV2DraftIssue(input: {draftIssueId: $draftIssueId, title: $title, body: $body}) {
    draftIssue {
      id
    }
  }
}
"""
        self.graphql(mutation, {"draftIssueId": draft_issue_id, "title": title, "body": body})

    def update_field_value(self, project_id: str, item_id: str, field: ProjectField, value: str) -> None:
        if field.data_type == "SINGLE_SELECT":
            option_id, resolved_option_name = resolve_single_select_option(field, value)
            mutation = """
mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
  updateProjectV2ItemFieldValue(
    input: {
      projectId: $projectId
      itemId: $itemId
      fieldId: $fieldId
      value: {singleSelectOptionId: $optionId}
    }
  ) {
    projectV2Item {
      id
    }
  }
}
"""
            self.graphql(
                mutation,
                {
                    "projectId": project_id,
                    "itemId": item_id,
                    "fieldId": field.field_id,
                    "optionId": option_id,
                },
            )
            if resolved_option_name != value:
                print(
                    f"INFO  {field.name}: resolved backlog value '{value}' to existing project option '{resolved_option_name}'."
                )
            return

        if field.data_type == "TEXT":
            mutation = """
mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $text: String!) {
  updateProjectV2ItemFieldValue(
    input: {
      projectId: $projectId
      itemId: $itemId
      fieldId: $fieldId
      value: {text: $text}
    }
  ) {
    projectV2Item {
      id
    }
  }
}
"""
            self.graphql(
                mutation,
                {
                    "projectId": project_id,
                    "itemId": item_id,
                    "fieldId": field.field_id,
                    "text": value,
                },
            )
            return

        raise RuntimeError(
            f"Project field '{field.name}' uses unsupported data type '{field.data_type}'."
        )


def print_parse_summary(items: list[BacklogItem]) -> None:
    print(f"Parsed {len(items)} execution-board items from {BACKLOG_SOURCE_PATH}.")
    for item in items:
        print(f" - {item.backlog_id}: {item.status} / {item.priority} / {item.milestone} :: {item.item}")


def filter_graphql_errors(errors: list[dict[str, Any]], tolerated_not_found_roots: set[str]) -> list[dict[str, Any]]:
    remaining: list[dict[str, Any]] = []
    for error in errors:
        path = error.get("path") or []
        if (
            error.get("type") == "NOT_FOUND"
            and path
            and path[0] in tolerated_not_found_roots
        ):
            continue
        remaining.append(error)
    return remaining


def sync_items(
    client: GitHubProjectClient,
    items: list[BacklogItem],
    dry_run: bool,
    public_mode: bool,
    backlog_source_path: str = BACKLOG_SOURCE_PATH,
    repository_url: str | None = None,
    repository_ref: str = DEFAULT_REPOSITORY_REF,
) -> int:
    project_id, fields, existing_items = client.get_project()
    synced_ids = set()
    actions = 0

    field_mapping = {
        "Status": (("Status",), lambda item: item.status),
        "Priority": (("Priority",), lambda item: item.priority),
        "Epic": (("Epic",), lambda item: item.epic),
        "Milestone": (("Milestone", "Execution Milestone"), lambda item: item.milestone),
        "Dependency": (("Dependency",), lambda item: item.dependency),
    }
    field_bindings = build_field_bindings(fields, field_mapping)

    for item in items:
        desired_title = item.project_title
        desired_body = build_project_body(
            item,
            public_mode=public_mode,
            backlog_source_path=backlog_source_path,
            repository_url=repository_url,
            repository_ref=repository_ref,
        )
        existing = existing_items.get(item.backlog_id)
        synced_ids.add(item.backlog_id)

        if existing is None:
            print(f"CREATE {item.backlog_id}: {desired_title}")
            actions += 1
            if dry_run:
                continue
            existing = client.create_draft_item(project_id, desired_title, desired_body)
        elif existing.content_type != "DraftIssue":
            print(
                f"WARN  {item.backlog_id}: existing synced item is a {existing.content_type}; title/body sync is skipped."
            )
        elif existing.title != desired_title or existing.body != desired_body:
            print(f"UPDATE {item.backlog_id}: title/body")
            actions += 1
            if not dry_run:
                client.update_draft_issue(existing.content_id, desired_title, desired_body)

        for binding in field_bindings:
            field_value = binding.value_getter(item)
            print(f"FIELD {item.backlog_id}: {binding.resolved_name} = {field_value}")
            actions += 1
            if not dry_run and existing is not None:
                client.update_field_value(project_id, existing.item_id, binding.field, field_value)

    stale_ids = sorted(set(existing_items) - synced_ids)
    for stale_id in stale_ids:
        print(
            f"STALE {stale_id}: synced project item no longer exists in the execution-board table; remove or archive it manually if needed."
        )

    return actions


def build_argument_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Sync the Product Backlog Current Execution Board Markdown table into a GitHub Project."
    )
    parser.add_argument(
        "--backlog-file",
        default=BACKLOG_SOURCE_PATH,
        help="Path to the product backlog Markdown file (default: docs/product/product-backlog.md).",
    )
    parser.add_argument(
        "--project-owner",
        default=os.environ.get("ONEFLOW_PROJECT_OWNER"),
        help="GitHub user or organization that owns the Project V2.",
    )
    parser.add_argument(
        "--project-number",
        type=int,
        default=int(os.environ["ONEFLOW_PROJECT_NUMBER"]) if os.environ.get("ONEFLOW_PROJECT_NUMBER") else None,
        help="Numeric Project V2 identifier from the project URL.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse the execution board and print planned actions without calling GitHub.",
    )
    parser.add_argument(
        "--public-mode",
        action="store_true",
        help="Publish a sanitized project body that omits internal notes and private relative links.",
    )
    parser.add_argument(
        "--token-env-var",
        default="GITHUB_TOKEN",
        help="Environment variable name that contains the GitHub token (default: GITHUB_TOKEN).",
    )
    parser.add_argument(
        "--repository-url",
        default=None,
        help=(
            "Repository web URL used to build absolute clickable detail-page links "
            "(default: ONEFLOW_REPOSITORY_URL or GITHUB_SERVER_URL + GITHUB_REPOSITORY when available)."
        ),
    )
    parser.add_argument(
        "--repository-ref",
        default=None,
        help=(
            "Repository ref/branch used in generated blob links "
            f"(default: ONEFLOW_REPOSITORY_REF, GITHUB_REF_NAME, or {DEFAULT_REPOSITORY_REF})."
        ),
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_argument_parser().parse_args(argv)
    backlog_path = Path(args.backlog_file)
    backlog_source_path = backlog_path.as_posix()
    items = parse_backlog_items(read_text(backlog_path))
    print_parse_summary(items)
    repository_url = resolve_repository_url(args.repository_url)
    repository_ref = resolve_repository_ref(args.repository_ref)

    token = os.environ.get(args.token_env_var)
    if args.dry_run and (not token or not args.project_owner or not args.project_number):
        print("Dry run completed without GitHub API access.")
        return 0

    if not token:
        raise SystemExit(
            f"Missing GitHub token. Set {args.token_env_var} or rerun with --dry-run for parse-only validation."
        )
    if not args.project_owner:
        raise SystemExit("Missing --project-owner (or ONEFLOW_PROJECT_OWNER).")
    if not args.project_number:
        raise SystemExit("Missing --project-number (or ONEFLOW_PROJECT_NUMBER).")

    client = GitHubProjectClient(token=token, owner=args.project_owner, project_number=args.project_number)
    actions = sync_items(
        client,
        items,
        dry_run=args.dry_run,
        public_mode=args.public_mode,
        backlog_source_path=backlog_source_path,
        repository_url=repository_url,
        repository_ref=repository_ref,
    )
    if args.dry_run:
        print(f"Dry run completed; {actions} project mutations would be attempted.")
    else:
        print(f"Sync completed; {actions} project mutations were attempted.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # pragma: no cover - defensive CLI boundary
        print(f"ERROR: {exc}", file=sys.stderr)
        raise
