# ADR-0013: Keep `spring-etl-engine` Technical Identity and `OneFlow` Product-Facing Name

- Status: Accepted
- Date: 2026-05-28

## Context
The product roadmap has evolved beyond ETL-only capabilities and now includes optional control-plane, scheduling, and operator-facing direction. During roadmap growth, naming drift risk increases: contributors may attempt broad renames of repository, package names, Maven coordinates, and runtime contracts to match product-facing copy.

Current repository guidance already distinguishes between:

- technical identity (`spring-etl-engine`) for code and runtime contracts
- product-facing identity (`OneFlow`) for marketing and GitHub presentation copy

Without a formal decision record, naming changes can become inconsistent, create unnecessary migration churn, and break downstream consumers that depend on existing technical identifiers.

## Decision
Keep `spring-etl-engine` as the stable technical identity and keep `OneFlow` as product-facing copy.

- Preserve `spring-etl-engine` for repository naming, package naming, Maven coordinates, code symbols, and runtime/config contract references.
- Use `OneFlow` in product-facing documentation and promotion copy where branding is intended.
- Do not perform blind repository-wide rename operations from technical identifiers to product-facing terms.
- Route future brand-refresh work through the tracked product path (`E3`) and targeted doc updates rather than technical identifier churn.
- Revisit repository rename only when objective operational triggers exist (for example ecosystem confusion, ownership boundary change, or measurable migration benefit that outweighs compatibility risk).

## Consequences

### Positive
- Keeps technical contracts stable for contributors, scripts, tests, and downstream automation.
- Reduces avoidable breakage risk in package paths, Maven coordinates, and integration references.
- Allows brand-facing evolution without forcing immediate runtime identifier migrations.
- Aligns roadmap growth with a predictable naming policy during control-plane and UI expansion.

### Negative
- Maintains dual naming in docs (`spring-etl-engine` and `OneFlow`), which requires editorial discipline.
- Some contributors may still expect a product-name-first repository rename and need onboarding guidance.
- Future rename (if later approved) will still require a managed migration plan.

## Alternatives considered
- Rename repository and technical identifiers now to match product-facing naming.
- Keep naming implicit in scattered docs without an ADR-level decision.

## Notes
- This ADR formalizes existing guidance in `README.md`, `AGENTS.md`, and product backlog naming direction.
- This ADR does not block future renaming; it requires a separate explicit decision plus migration plan if objective triggers are met.
- If future rename work is approved, include compatibility strategy for package names, Maven coordinates, scripts, docs links, and archived evidence references.

