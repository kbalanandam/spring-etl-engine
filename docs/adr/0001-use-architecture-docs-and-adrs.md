# ADR-0001: Use in-repo architecture docs and ADRs

- Status: Accepted
- Date: 2026-04-18

## Context

The project is growing beyond a small set of runtime paths. New features such as relational database support, stored procedures, and richer orchestration will increase design complexity.

Without a lightweight documentation standard, architectural knowledge will drift into:
- chat history
- memory
- ad hoc diagrams
- pull request comments

## Decision

The project will maintain architecture documentation directly in the repository using:

- Markdown for design notes
- Mermaid for diagrams
- ADRs for important design decisions

## Consequences

### Positive
- architecture stays versioned with code
- design changes can be reviewed in pull requests
- new contributors get a stable map of the system
- diagrams are diffable and easier to keep current than binary assets

### Negative
- contributors must spend time updating docs
- documentation can still become stale if not enforced in reviews

## Follow-up rule

For each significant enhancement, contributors should update:
1. one architecture note if runtime/config/flow changes
2. one Mermaid diagram if control flow changes materially
3. one ADR if a meaningful design choice or tradeoff is introduced

