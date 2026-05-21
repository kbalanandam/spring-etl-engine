## Summary

Describe what changed and why.

## Validation

- What did you test?
- What should reviewers pay special attention to?

## Architecture / documentation impact

- Does this PR change runtime flow, configuration shape, extension points, orchestration, generated model contracts, or transaction/restart behavior?
- If yes, which documentation file was updated?
- If a meaningful design decision or tradeoff was introduced, which ADR was added or updated?

## Checklist

- [ ] I updated tests when needed
- [ ] I considered security impact (config abuse, path/zip handling, secret leakage, dependency risk) and added or updated checks when needed
- [ ] I verified no credentials, tokens, or sensitive connection values are committed in this PR
- [ ] I updated `docs/architecture/*` if runtime/config/flow changed
- [ ] I updated a Mermaid diagram if control flow changed materially
- [ ] I added or updated an ADR if this PR introduced a meaningful design decision or tradeoff
- [ ] I reviewed `docs/README.md` for the documentation standard when the change affected architecture
- [ ] I did not commit developer-local `private-jobs/` contents; only `private-jobs/README.md` is allowed to stay visible in Git

## Helpful links

- [`docs/README.md`](https://github.com/kbalanandam/spring-etl-engine/blob/master/docs/README.md)
- [`docs/architecture/TEMPLATE.md`](https://github.com/kbalanandam/spring-etl-engine/blob/master/docs/architecture/TEMPLATE.md)
- [`docs/adr/TEMPLATE.md`](https://github.com/kbalanandam/spring-etl-engine/blob/master/docs/adr/TEMPLATE.md)
- [`private-jobs/README.md`](https://github.com/kbalanandam/spring-etl-engine/blob/master/private-jobs/README.md)

