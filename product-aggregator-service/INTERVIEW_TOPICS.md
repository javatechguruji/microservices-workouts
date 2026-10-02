# product-aggregator-service: project revision questions

Use [the implementation guide](../docs/project-docs/catalog-aggregation-and-preferences.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why move JDBC work to boundedElastic?
2. Which lookup failures may use fallbacks and why?
3. Why can a catalog stock preview not replace a reservation?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
