# inventory-service: project revision questions

Use [the implementation guide](../docs/project-docs/shopping-and-fulfillment.md) to answer each question by tracing
the actual source and an observable outcome. These questions replace older
checklists that described already-implemented features as pending.

1. Why is conditional UPDATE safer than read-then-decrement?
2. What happens when the second SKU lacks stock?
3. Why does commit not decrement stock again?

Compare the implementation with its stated limitations. Future work is maintained
once in [the project roadmap](../Topics.md); a discussion topic is not proof
that the feature exists. For a runnable exercise use
[manual verification](../docs/project-docs/manual-verification.md).
